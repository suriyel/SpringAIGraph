package com.aigraph.core.functional;

/**
 * Functional interface for context-aware node processing.
 * <p>
 * Similar to {@link NodeFunction} but includes execution context
 * as a parameter, allowing nodes to access message history, metadata,
 * and other contextual information.
 * <p>
 * Note: The context parameter uses Object type to avoid circular
 * dependency. Implementations should cast to ExecutionContext.
 *
 * @param <I> input type
 * @param <O> output type
 * @author AIGraph Team
 * @since 0.0.9
 */
@FunctionalInterface
public interface ContextAwareNodeFunction<I, O> {

    /**
     * Applies this function to the given input and context.
     *
     * @param input   the input value
     * @param context the execution context (cast to ExecutionContext in use)
     * @return the function result
     * @throws Exception if processing fails
     */
    O apply(I input, Object context) throws Exception;
}
