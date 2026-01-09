package com.aigraph.pregel;

import com.aigraph.channels.ChannelManager;
import com.aigraph.nodes.NodeRegistry;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

/**
 * Main Pregel implementation.
 */
public class Pregel<I, O> implements PregelGraph<I, O> {

    private final ChannelManager channelManager;
    private final NodeRegistry nodeRegistry;
    private final PregelConfig config;
    private final PregelExecutor executor;
    private final ReactivePregelExecutor reactiveExecutor;
    private com.aigraph.checkpoint.Checkpointer checkpointer; // Optional

    public Pregel(ChannelManager channelManager, NodeRegistry nodeRegistry, PregelConfig config) {
        this.channelManager = channelManager;
        this.nodeRegistry = nodeRegistry;
        this.config = config;
        this.executor = new PregelExecutor(config);
        this.reactiveExecutor = new ReactivePregelExecutor(config);
    }

    @Override
    @SuppressWarnings("unchecked")
    public O invoke(I input) {
        return invoke(input, RuntimeConfig.defaults());
    }

    @Override
    @SuppressWarnings("unchecked")
    public O invoke(I input, RuntimeConfig runtimeConfig) {
        // 清除之前执行可能残留的更新标记
        channelManager.clearUpdatedFlags();

        // 收集需要标记为"已更新"的输入通道
        Set<String> initialUpdatedChannels = new HashSet<>();

        // 初始化输入通道
        if (!config.inputChannels().isEmpty()) {
            for (String inputChannel : config.inputChannels()) {
                // 尝试更新通道（如果 input 不为 null）
                if (input != null) {
                    channelManager.update(inputChannel, List.of(input));
                }
                // 无论 input 是否为 null，都标记输入通道为"已更新"以触发订阅的节点
                // 这样可以支持 Void 类型输入的场景（如 AggregateExample）
                initialUpdatedChannels.add(inputChannel);
            }
        }

        // 创建执行上下文，传入初始的已更新通道集合
        ExecutionContext context = new ExecutionContext(
                runtimeConfig.threadId(),
                channelManager,
                nodeRegistry,
                initialUpdatedChannels,
                config
        );

        ExecutionResult result = executor.execute(context);

        if (!result.success()) {
            throw new com.aigraph.core.exceptions.ExecutionException(
                    "Execution failed", result.error()
            );
        }

        // 提取输出
        if (!config.outputChannels().isEmpty()) {
            String outputChannel = config.outputChannels().get(0);
            try {
                return (O) channelManager.get(outputChannel).get();
            } catch (Exception e) {
                throw new com.aigraph.core.exceptions.ExecutionException(
                        "Failed to read output channel: " + outputChannel, e
                );
            }
        }

        return null;
    }

    @Override
    public CompletableFuture<O> invokeAsync(I input) {
        return CompletableFuture.supplyAsync(() -> invoke(input));
    }

    @Override
    public Stream<ExecutionStep> stream(I input) {
        return stream(input, RuntimeConfig.defaults());
    }

    /**
     * Executes the graph and returns a stream of execution steps.
     * Note: This executes the entire graph before returning the stream.
     * For true reactive streaming, use {@link #streamReactive(Object)}.
     *
     * @param input the input data
     * @param runtimeConfig the runtime configuration
     * @return stream of execution steps
     */
    @SuppressWarnings("unchecked")
    public Stream<ExecutionStep> stream(I input, RuntimeConfig runtimeConfig) {
        // Clear previous execution flags
        channelManager.clearUpdatedFlags();

        // Collect initial updated channels
        Set<String> initialUpdatedChannels = new HashSet<>();

        // Initialize input channels
        if (!config.inputChannels().isEmpty()) {
            for (String inputChannel : config.inputChannels()) {
                if (input != null) {
                    channelManager.update(inputChannel, List.of(input));
                }
                initialUpdatedChannels.add(inputChannel);
            }
        }

        // Create execution context
        ExecutionContext context = new ExecutionContext(
                runtimeConfig.threadId(),
                channelManager,
                nodeRegistry,
                initialUpdatedChannels,
                config
        );

        // Execute and capture the result with step history
        ExecutionResult result = executor.execute(context);

        if (!result.success()) {
            throw new com.aigraph.core.exceptions.ExecutionException(
                    "Stream execution failed", result.error()
            );
        }

        // Return the step history as a stream
        return result.steps().stream();
    }

    /**
     * Sets the checkpointer for this Pregel instance.
     *
     * @param checkpointer the checkpointer
     */
    public void setCheckpointer(com.aigraph.checkpoint.Checkpointer checkpointer) {
        this.checkpointer = checkpointer;
    }

    /**
     * Resumes execution from a saved checkpoint.
     * <p>
     * Loads the checkpoint, restores MessageContext and channel states,
     * and continues execution from where it left off.
     *
     * @param threadId     the thread ID
     * @param checkpointId the checkpoint ID (null for latest checkpoint)
     * @return the output result
     */
    @Override
    @SuppressWarnings("unchecked")
    public O resumeFrom(String threadId, String checkpointId) {
        if (checkpointer == null) {
            throw new IllegalStateException("Checkpointer not configured. Call setCheckpointer() first.");
        }

        // Restore execution context from checkpoint
        ExecutionContext restoredContext = CheckpointSupport.resumeFromCheckpoint(
            checkpointer,
            threadId,
            checkpointId,
            channelManager,
            nodeRegistry,
            config
        );

        // Continue execution
        ExecutionResult result = executor.execute(restoredContext);

        if (!result.success()) {
            throw new com.aigraph.core.exceptions.ExecutionException(
                "Resume execution failed", result.error()
            );
        }

        // Extract output
        if (!config.outputChannels().isEmpty()) {
            String outputChannel = config.outputChannels().get(0);
            try {
                return (O) channelManager.get(outputChannel).get();
            } catch (Exception e) {
                throw new com.aigraph.core.exceptions.ExecutionException(
                    "Failed to read output channel: " + outputChannel, e
                );
            }
        }

        return null;
    }

    @Override
    public PregelConfig getConfig() {
        return config;
    }

    /**
     * Reactively invokes the graph and returns the result as a Mono.
     * <p>
     * The Mono is cold - execution starts when subscribed.
     *
     * @param input the input data
     * @return Mono containing the result
     */
    @Override
    @SuppressWarnings("unchecked")
    public Mono<O> invokeReactive(I input) {
        return Mono.fromCallable(() -> {
            // Clear previous execution flags
            channelManager.clearUpdatedFlags();

            // Collect initial updated channels
            Set<String> initialUpdatedChannels = new HashSet<>();

            // Initialize input channels
            if (!config.inputChannels().isEmpty()) {
                for (String inputChannel : config.inputChannels()) {
                    if (input != null) {
                        channelManager.update(inputChannel, List.of(input));
                    }
                    initialUpdatedChannels.add(inputChannel);
                }
            }

            // Create execution context
            ExecutionContext context = new ExecutionContext(
                    RuntimeConfig.defaults().threadId(),
                    channelManager,
                    nodeRegistry,
                    initialUpdatedChannels,
                    config
            );

            ExecutionResult result = executor.execute(context);

            if (!result.success()) {
                throw new com.aigraph.core.exceptions.ExecutionException(
                        "Reactive execution failed", result.error()
                );
            }

            // Extract output
            if (!config.outputChannels().isEmpty()) {
                String outputChannel = config.outputChannels().get(0);
                try {
                    return (O) channelManager.get(outputChannel).get();
                } catch (Exception e) {
                    throw new com.aigraph.core.exceptions.ExecutionException(
                            "Failed to read output channel: " + outputChannel, e
                    );
                }
            }

            return null;
        });
    }

    /**
     * Reactively streams execution steps as a Flux.
     * <p>
     * The Flux is cold - execution starts when subscribed.
     * Each ExecutionStep is emitted as it completes.
     *
     * @param input the input data
     * @return Flux of execution steps
     */
    @Override
    public Flux<ExecutionStep> streamReactive(I input) {
        return Flux.defer(() -> {
            // Clear previous execution flags
            channelManager.clearUpdatedFlags();

            // Collect initial updated channels
            Set<String> initialUpdatedChannels = new HashSet<>();

            // Initialize input channels
            if (!config.inputChannels().isEmpty()) {
                for (String inputChannel : config.inputChannels()) {
                    if (input != null) {
                        channelManager.update(inputChannel, List.of(input));
                    }
                    initialUpdatedChannels.add(inputChannel);
                }
            }

            // Create execution context
            ExecutionContext context = new ExecutionContext(
                    RuntimeConfig.defaults().threadId(),
                    channelManager,
                    nodeRegistry,
                    initialUpdatedChannels,
                    config
            );

            // Execute reactively and stream steps
            return reactiveExecutor.executeReactive(context);
        });
    }
}