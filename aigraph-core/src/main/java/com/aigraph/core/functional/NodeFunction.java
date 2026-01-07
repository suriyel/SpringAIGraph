package com.aigraph.core.functional;

/**
 * Functional interface for node processing logic.
 * <p>
 * Represents a function that accepts an input of type {@code I}
 * and produces an output of type {@code O}. This is the core
 * abstraction for defining node behavior in the graph.
 * <p>
 * Example usage:
 * <pre>{@code
 * NodeFunction<String, String> uppercase = String::toUpperCase;
 * NodeFunction<Integer, Integer> doubleIt = x -> x * 2;
 * }</pre>
 *
 * @param <I> the input type
 * @param <O> the output type
 * @author AIGraph Team
 * @since 0.0.8
 */
@FunctionalInterface
public interface NodeFunction<I, O> {

    /**
     * Applies this function to the given input.
     *
     * @param input the input value
     * @return the output value
     * @throws Exception if the function execution fails
     */
    O apply(I input) throws Exception;

    /**
     * Returns a composed function that first applies this function
     * to its input, and then applies the {@code after} function to the result.
     *
     * @param <V>   the type of output of the {@code after} function
     * @param after the function to apply after this function
     * @return a composed function
     */
    default <V> NodeFunction<I, V> andThen(NodeFunction<? super O, ? extends V> after) {
        return (I input) -> after.apply(apply(input));
    }

    /**
     * Returns a composed function that first applies the {@code before}
     * function to its input, and then applies this function to the result.
     *
     * @param <V>    the type of input to the {@code before} function
     * @param before the function to apply before this function
     * @return a composed function
     */
    default <V> NodeFunction<V, O> compose(NodeFunction<? super V, ? extends I> before) {
        return (V input) -> apply(before.apply(input));
    }

    /**
     * Returns a function that always returns its input argument.
     *
     * @param <T> the type of the input and output
     * @return an identity function
     */
    static <T> NodeFunction<T, T> identity() {
        return t -> t;
    }
}
