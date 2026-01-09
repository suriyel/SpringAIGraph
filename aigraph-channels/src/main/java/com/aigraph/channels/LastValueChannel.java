package com.aigraph.channels;

import com.aigraph.core.exceptions.EmptyChannelException;
import com.aigraph.core.exceptions.InvalidUpdateException;

import java.util.List;

/**
 * A channel that stores only the most recent value.
 * <p>
 * Characteristics:
 * <ul>
 *   <li>Accepts exactly one update per step (rejects multiple updates)</li>
 *   <li>Always returns the last written value</li>
 *   <li>Thread-safe with volatile field</li>
 *   <li>Checkpoint type is the same as value type</li>
 * </ul>
 * <p>
 * This is the most common channel type, suitable for scalar state
 * that should be overwritten on each update.
 * <p>
 * Example:
 * <pre>{@code
 * var channel = new LastValueChannel<String>("status", String.class);
 * channel.update(List.of("processing"));
 * System.out.println(channel.get()); // "processing"
 * }</pre>
 *
 * @param <V> the value type
 * @author AIGraph Team
 * @since 0.0.8
 */
public final class LastValueChannel<V> extends BaseChannel<V, V, V> {

    private volatile V value;
    private volatile boolean hasValue;

    /**
     * Creates a new LastValueChannel.
     *
     * @param name the channel name
     * @param type the value type
     */
    public LastValueChannel(String name, Class<V> type) {
        super(name, type, type);
        this.value = null;
        this.hasValue = false;
    }

    /**
     * Private constructor for copy/checkpoint restoration.
     */
    private LastValueChannel(String name, Class<V> type, V initialValue, boolean hasValue, boolean updated) {
        super(name, type, type, updated);
        this.value = initialValue;
        this.hasValue = hasValue;
    }

    @Override
    public synchronized boolean update(List<V> values) {
        validateUpdates(values);

        if (values.isEmpty()) {
            return false;
        }

        if (values.size() > 1) {
            throw new InvalidUpdateException(
                name,
                "LastValueChannel cannot accept multiple updates. Got " + values.size() + " updates. " +
                "Use TopicChannel or BinaryOperatorChannel for multi-value updates."
            );
        }

        V newValue = values.get(0);
        // Allow null updates to support conditional writes
        if (newValue == null) {
            return false;
        }

        this.value = newValue;
        this.hasValue = true;
        markUpdated();
        return true;
    }

    @Override
    public V get() {
        if (!hasValue) {
            throw new EmptyChannelException(name, "No value has been set");
        }
        return value;
    }

    @Override
    public V checkpoint() {
        if (!hasValue) {
            throw new EmptyChannelException(name, "Cannot checkpoint empty channel");
        }
        return value;
    }

    @Override
    public Channel<V, V, V> fromCheckpoint(V checkpoint) {
        // Checkpoints are restored with updated=false (fresh state)
        return new LastValueChannel<>(name, valueType, checkpoint, true, false);
    }

    @Override
    public Channel<V, V, V> copy() {
        // Copy preserves the updated flag
        return new LastValueChannel<>(name, valueType, value, hasValue, this.updated);
    }

    @Override
    public boolean isEmpty() {
        return !hasValue;
    }
}
