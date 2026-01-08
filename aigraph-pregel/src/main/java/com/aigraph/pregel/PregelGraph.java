package com.aigraph.pregel;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

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

    /**
     * Synchronously invokes the graph with input.
     *
     * @param input the input data
     * @return the output result
     */
    O invoke(I input);

    /**
     * Synchronously invokes the graph with input and runtime config.
     *
     * @param input  the input data
     * @param config runtime configuration
     * @return the output result
     */
    O invoke(I input, RuntimeConfig config);

    /**
     * Asynchronously invokes the graph using CompletableFuture.
     *
     * @param input the input data
     * @return CompletableFuture containing the result
     */
    CompletableFuture<O> invokeAsync(I input);

    /**
     * Streams execution steps as they occur (blocking).
     *
     * @param input the input data
     * @return Stream of execution steps
     */
    Stream<ExecutionStep> stream(I input);

    /**
     * Reactively invokes the graph using Project Reactor.
     * <p>
     * Returns a Mono that emits the result when execution completes.
     * The Mono is cold and execution starts on subscription.
     *
     * @param input the input data
     * @return Mono containing the result
     * @since 0.0.9
     */
    default Mono<O> invokeReactive(I input) {
        return Mono.fromCallable(() -> invoke(input));
    }

    /**
     * Reactively streams execution steps as they occur.
     * <p>
     * Returns a Flux that emits ExecutionStep objects as the graph executes.
     * The Flux is cold and execution starts on subscription.
     *
     * @param input the input data
     * @return Flux of execution steps
     * @since 0.0.9
     */
    default Flux<ExecutionStep> streamReactive(I input) {
        return Flux.fromStream(stream(input));
    }

    /**
     * Resumes execution from a saved checkpoint.
     *
     * @param threadId     the thread ID
     * @param checkpointId the checkpoint ID
     * @return the output result
     */
    O resumeFrom(String threadId, String checkpointId);

    /**
     * Gets the configuration for this graph.
     *
     * @return the Pregel configuration
     */
    PregelConfig getConfig();
}
