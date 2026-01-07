package com.aigraph.channels;

import java.util.List;

/**
 * Core abstraction for message passing between components in the graph.
 * <p>
 * A Channel manages the state communication between nodes in a LangGraph execution.
 * It defines three type parameters:
 * <ul>
 *   <li>{@code V} - Value type: the type of data stored and returned by {@link #get()}</li>
 *   <li>{@code U} - Update type: the type of updates accepted by {@link #update(List)}</li>
 *   <li>{@code C} - Checkpoint type: the serializable representation for state persistence</li>
 * </ul>
 * <p>
 * Channels are updated at the end of each execution step in the BSP (Bulk Synchronous Parallel)
 * model, ensuring deterministic and thread-safe state transitions.
 * <p>
 * Example implementations:
 * <ul>
 *   <li>{@link LastValueChannel} - stores only the last value</li>
 *   <li>{@link TopicChannel} - accumulates all values in a list</li>
 *   <li>{@link BinaryOperatorChannel} - aggregates values using a binary operator</li>
 *   <li>{@link EphemeralChannel} - provides values that are consumed once</li>
 * </ul>
 *
 * @param <V> the value type
 * @param <U> the update type
 * @param <C> the checkpoint type
 * @author AIGraph Team
 * @since 0.0.8
 */
public interface Channel<V, U, C> {

    /**
     * Gets the unique name of this channel.
     *
     * @return the channel name
     */
    String getName();

    /**
     * Gets the runtime class of the value type.
     *
     * @return the value type class
     */
    Class<V> getValueType();

    /**
     * Gets the runtime class of the update type.
     *
     * @return the update type class
     */
    Class<U> getUpdateType();

    /**
     * Updates the channel with a sequence of values.
     * <p>
     * This method is called at the end of each execution step with all
     * updates collected from node executions. The order of updates is
     * not guaranteed unless specified by the implementation.
     * <p>
     * Implementations must ensure this operation is idempotent within
     * a single step and thread-safe when called from multiple threads.
     *
     * @param values the list of update values (may be empty)
     * @return {@code true} if the channel was modified, {@code false} otherwise
     * @throws com.aigraph.core.exceptions.InvalidUpdateException if the update is invalid
     */
    boolean update(List<U> values);

    /**
     * Returns the current value of the channel.
     * <p>
     * This method is called by nodes to read channel state.
     *
     * @return the current value
     * @throws com.aigraph.core.exceptions.EmptyChannelException if the channel has no value
     */
    V get();

    /**
     * Creates a checkpoint of the current channel state.
     * <p>
     * The checkpoint must be serializable and contain all information
     * necessary to restore the channel to its current state.
     *
     * @return the checkpoint data
     * @throws com.aigraph.core.exceptions.EmptyChannelException if channel doesn't support checkpointing
     */
    C checkpoint();

    /**
     * Creates a new channel instance from a checkpoint.
     * <p>
     * This is a factory method that returns a new channel initialized
     * with the state from the checkpoint.
     *
     * @param checkpoint the checkpoint data
     * @return a new channel instance
     */
    Channel<V, U, C> fromCheckpoint(C checkpoint);

    /**
     * Marks the channel as consumed by subscriber nodes.
     * <p>
     * This lifecycle hook is called after subscriber nodes have executed.
     * Some channel types (like {@link EphemeralChannel}) use this to
     * clear their state.
     *
     * @return {@code true} if the channel was modified
     */
    default boolean consume() {
        return false;
    }

    /**
     * Marks the end of a Pregel execution run.
     * <p>
     * This lifecycle hook allows channels to perform cleanup or
     * finalization when execution completes.
     *
     * @return {@code true} if the channel was modified
     */
    default boolean finish() {
        return false;
    }

    /**
     * Creates a deep copy of this channel.
     * <p>
     * The copy must have independent state but identical configuration.
     *
     * @return a copy of this channel
     */
    Channel<V, U, C> copy();

    /**
     * Checks if the channel is empty (has no value).
     *
     * @return {@code true} if empty
     */
    default boolean isEmpty() {
        return false;
    }

    /**
     * Checks if the channel was updated in the current step.
     *
     * @return {@code true} if updated
     */
    default boolean isUpdated() {
        return false;
    }
}
