package com.aigraph.nodes;

import java.util.concurrent.CompletableFuture;

/**
 * Context-aware node interface that receives execution context.
 * <p>
 * This interface extends the basic Node interface to provide access to
 * execution context, including message history and metadata. This is
 * particularly useful for Spring AI integration where nodes need to
 * access conversation history and state.
 * <p>
 * Note: The context parameter type uses Object to avoid circular dependency
 * with the pregel module. Implementations should cast to ExecutionContext.
 *
 * @param <I> input type
 * @param <O> output type
 * @author AIGraph Team
 * @since 0.0.9
 */
public interface ContextAwareNode<I, O> extends Node<I, O> {

    /**
     * Invokes the node with input and execution context.
     *
     * @param input   the input data
     * @param context the execution context (cast to ExecutionContext in implementations)
     * @return the output data
     */
    O invokeWithContext(I input, Object context);

    /**
     * Async version of invokeWithContext.
     *
     * @param input   the input data
     * @param context the execution context
     * @return CompletableFuture containing the output
     */
    default CompletableFuture<O> invokeWithContextAsync(I input, Object context) {
        return CompletableFuture.supplyAsync(() -> invokeWithContext(input, context));
    }

    /**
     * Default implementation delegates to context-aware version with null context.
     */
    @Override
    default O invoke(I input) {
        return invokeWithContext(input, null);
    }

    /**
     * Default implementation delegates to context-aware async version.
     */
    @Override
    default CompletableFuture<O> invokeAsync(I input) {
        return invokeWithContextAsync(input, null);
    }
}
