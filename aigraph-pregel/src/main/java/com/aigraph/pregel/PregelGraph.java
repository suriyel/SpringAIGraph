package com.aigraph.pregel;

import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

/**
 * Main interface for the Pregel execution engine.
 *
 * @param <I> input type
 * @param <O> output type
 * @author AIGraph Team
 * @since 0.0.8
 */
public interface PregelGraph<I, O> {

    O invoke(I input);

    O invoke(I input, RuntimeConfig config);

    CompletableFuture<O> invokeAsync(I input);

    Stream<ExecutionStep> stream(I input);

    O resumeFrom(String threadId, String checkpointId);

    PregelConfig getConfig();
}
