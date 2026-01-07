package com.aigraph.nodes;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * Core abstraction for executable units in a LangGraph.
 * <p>
 * A Node represents a processing unit that:
 * <ul>
 *   <li>Subscribes to one or more channels (triggers)</li>
 *   <li>Reads from additional channels (context)</li>
 *   <li>Executes business logic transforming input to output</li>
 *   <li>Writes results to target channels</li>
 * </ul>
 * <p>
 * Nodes are the vertices in the graph and contain the actual
 * processing logic. They are executed by the Pregel engine when
 * their subscribed channels are updated.
 * <p>
 * Example:
 * <pre>{@code
 * Node<String, String> node = NodeBuilder.<String, String>create("process")
 *     .subscribeOnly("input")
 *     .process(String::toUpperCase)
 *     .writeTo("output")
 *     .build();
 * }</pre>
 *
 * @param <I> the input type
 * @param <O> the output type
 * @author AIGraph Team
 * @since 0.0.8
 * @see NodeBuilder
 * @see FunctionalNode
 */
public interface Node<I, O> {

    /**
     * Gets the unique name of this node.
     *
     * @return the node name
     */
    String getName();

    /**
     * Gets the set of channels this node subscribes to.
     * <p>
     * When any of these channels are updated, the node will be
     * scheduled for execution in the next step.
     *
     * @return immutable set of channel names
     */
    Set<String> getSubscribedChannels();

    /**
     * Gets the set of channels this node reads from (but doesn't subscribe to).
     * <p>
     * These channels provide context but don't trigger execution.
     *
     * @return immutable set of channel names
     */
    default Set<String> getReadChannels() {
        return Collections.emptySet();
    }

    /**
     * Gets the write target configuration.
     * <p>
     * Maps channel names to optional mapper functions that transform
     * the node output before writing. If mapper is null, output is
     * written directly.
     *
     * @return immutable map of channel name to mapper function
     */
    Map<String, Function<O, ?>> getWriteTargets();

    /**
     * Synchronously executes the node logic.
     * <p>
     * This method is called by the execution engine when the node
     * is scheduled to run.
     *
     * @param input the input value
     * @return the output value
     * @throws Exception if execution fails
     */
    O invoke(I input) throws Exception;

    /**
     * Asynchronously executes the node logic.
     * <p>
     * Default implementation wraps {@link #invoke(Object)} in a
     * CompletableFuture. Implementations can override for true
     * async execution.
     *
     * @param input the input value
     * @return future completing with the output value
     */
    default CompletableFuture<O> invokeAsync(I input) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return invoke(input);
            } catch (Exception e) {
                throw new RuntimeException("Node execution failed: " + getName(), e);
            }
        });
    }

    /**
     * Gets the metadata for this node.
     *
     * @return the node metadata
     */
    NodeMetadata getMetadata();
}
