package com.aigraph.channels;

import com.aigraph.core.exceptions.EmptyChannelException;
import com.aigraph.core.exceptions.InvalidUpdateException;

import java.util.List;

/**
 * A channel that provides values that can only be consumed once.
 * <p>
 * Ephemeral channels are useful for one-time events or messages that
 * should not persist across execution steps.
 * <p>
 * Characteristics:
 * <ul>
 *   <li>Value is cleared after {@link #consume()} is called</li>
 *   <li>Throws exception when reading after consumption</li>
 *   <li>Accepts only one update per step (like LastValueChannel)</li>
 *   <li>Cannot be checkpointed (checkpoint returns null)</li>
 * </ul>
 * <p>
 * Example use cases:
 * <ul>
 *   <li>One-time notifications</li>
 *   <li>Trigger signals</li>
 *   <li>Transient state that shouldn't be persisted</li>
 * </ul>
 * <p>
 * Example:
 * <pre>{@code
 * var trigger = new EphemeralChannel<String>("trigger", String.class);
 * trigger.update(List.of("START"));
 * String value = trigger.get(); // "START"
 * trigger.consume();
 * trigger.get(); // Throws EmptyChannelException
 * }</pre>
 *
 * @param <V> the value type
 * @author AIGraph Team
 * @since 0.0.8
 */
public final class EphemeralChannel<V> extends BaseChannel<V, V, Void> {

    private volatile V value;
    private volatile boolean hasValue;
    private volatile boolean consumed;

    /**
     * Creates a new EphemeralChannel.
     *
     * @param name the channel name
     * @param type the value type
     */
    public EphemeralChannel(String name, Class<V> type) {
        super(name, type, type);
        this.value = null;
        this.hasValue = false;
        this.consumed = false;
    }

    /**
     * Private constructor for copy (checkpoint not supported).
     */
    private EphemeralChannel(String name, Class<V> type, V value, boolean hasValue, boolean consumed) {
        super(name, type, type);
        this.value = value;
        this.hasValue = hasValue;
        this.consumed = consumed;
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
                "EphemeralChannel cannot accept multiple updates. Got " + values.size() + " updates."
            );
        }

        V newValue = values.get(0);
        // Allow null updates to support conditional writes
        if (newValue == null) {
            return false;
        }

        this.value = newValue;
        this.hasValue = true;
        this.consumed = false;
        markUpdated();
        return true;
    }

    @Override
    public V get() {
        if (!hasValue || consumed) {
            throw new EmptyChannelException(
                name,
                consumed ? "Value has already been consumed" : "No value has been set"
            );
        }
        return value;
    }

    @Override
    public Void checkpoint() {
        // Ephemeral channels cannot be checkpointed
        throw new EmptyChannelException(name, "Ephemeral channels do not support checkpointing");
    }

    @Override
    @SuppressWarnings("unchecked")
    public Channel<V, V, Void> fromCheckpoint(Void checkpoint) {
        // Return empty ephemeral channel
        return new EphemeralChannel<>(name, valueType);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Channel<V, V, Void> copy() {
        return new EphemeralChannel<>(name, valueType, value, hasValue, consumed);
    }

    @Override
    public synchronized boolean consume() {
        if (hasValue && !consumed) {
            consumed = true;
            // Optionally clear the value to help GC
            value = null;
            return true;
        }
        return false;
    }

    @Override
    public boolean isEmpty() {
        return !hasValue || consumed;
    }

    /**
     * Checks if the value has been consumed.
     *
     * @return true if consumed
     */
    public boolean isConsumed() {
        return consumed;
    }
}
