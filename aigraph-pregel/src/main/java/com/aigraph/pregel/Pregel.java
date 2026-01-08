package com.aigraph.pregel;

import com.aigraph.channels.ChannelManager;
import com.aigraph.nodes.NodeRegistry;

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

    public Pregel(ChannelManager channelManager, NodeRegistry nodeRegistry, PregelConfig config) {
        this.channelManager = channelManager;
        this.nodeRegistry = nodeRegistry;
        this.config = config;
        this.executor = new PregelExecutor(config);
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
        invoke(input);
        ExecutionContext ctx = new ExecutionContext(
                RuntimeConfig.defaults().threadId(),
                channelManager,
                nodeRegistry,
                Set.of(),
                config
        );
        return ctx.getStepHistory().stream();
    }

    @Override
    public O resumeFrom(String threadId, String checkpointId) {
        throw new UnsupportedOperationException("Checkpoint resume not yet implemented");
    }

    @Override
    public PregelConfig getConfig() {
        return config;
    }
}