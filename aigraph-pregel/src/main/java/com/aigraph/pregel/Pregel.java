package com.aigraph.pregel;

import com.aigraph.channels.ChannelManager;
import com.aigraph.nodes.NodeRegistry;

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
        // Initialize input channels
        if (!config.inputChannels().isEmpty()) {
            String inputChannel = config.inputChannels().get(0);
            channelManager.update(inputChannel, java.util.List.of(input));
        }

        ExecutionContext context = new ExecutionContext(
            runtimeConfig.threadId(),
            channelManager,
            nodeRegistry,
            config
        );

        ExecutionResult result = executor.execute(context);

        if (!result.success()) {
            throw new com.aigraph.core.exceptions.ExecutionException(
                "Execution failed", result.error()
            );
        }

        // Extract output
        if (!config.outputChannels().isEmpty()) {
            String outputChannel = config.outputChannels().get(0);
            return (O) channelManager.get(outputChannel).get();
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
