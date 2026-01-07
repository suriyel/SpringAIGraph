package com.aigraph.channels;

import com.aigraph.core.exceptions.EmptyChannelException;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A channel that accumulates values in a list (Pub/Sub pattern).
 * <p>
 * Characteristics:
 * <ul>
 *   <li>Accumulates all updates into a list</li>
 *   <li>Supports accumulate mode (keep previous values) vs replace mode</li>
 *   <li>Supports unique mode (deduplicate values)</li>
 *   <li>Thread-safe with CopyOnWriteArrayList</li>
 *   <li>Returns immutable list copy</li>
 * </ul>
 * <p>
 * Configuration:
 * <ul>
 *   <li><b>accumulate=true</b>: Keep previous values and append new ones</li>
 *   <li><b>accumulate=false</b>: Replace all values with new updates</li>
 *   <li><b>unique=true</b>: Filter duplicate values</li>
 * </ul>
 * <p>
 * Example:
 * <pre>{@code
 * var channel = new TopicChannel<String>("messages", String.class, true, false);
 * channel.update(List.of("msg1"));
 * channel.update(List.of("msg2"));
 * System.out.println(channel.get()); // ["msg1", "msg2"]
 * }</pre>
 *
 * @param <V> the value type
 * @author AIGraph Team
 * @since 0.0.8
 */
public final class TopicChannel<V> extends BaseChannel<List<V>, V, List<V>> {

    private final CopyOnWriteArrayList<V> values;
    private final boolean accumulate;
    private final boolean unique;
    private final Set<V> seen; // For unique mode

    /**
     * Creates a new TopicChannel with default settings (non-accumulating, non-unique).
     *
     * @param name the channel name
     * @param type the value type
     */
    public TopicChannel(String name, Class<V> type) {
        this(name, type, false, false);
    }

    /**
     * Creates a new TopicChannel with custom settings.
     *
     * @param name       the channel name
     * @param type       the value type
     * @param accumulate whether to accumulate values across steps
     * @param unique     whether to deduplicate values
     */
    public TopicChannel(String name, Class<V> type, boolean accumulate, boolean unique) {
        super(name, type, type);
        this.values = new CopyOnWriteArrayList<>();
        this.accumulate = accumulate;
        this.unique = unique;
        this.seen = unique ? new LinkedHashSet<>() : null;
    }

    /**
     * Private constructor for copy/checkpoint.
     */
    @SuppressWarnings("unchecked")
    private TopicChannel(String name, Class<V> type, List<V> initialValues,
                         boolean accumulate, boolean unique) {
        this(name, type, accumulate, unique);
        if (initialValues != null && !initialValues.isEmpty()) {
            this.values.addAll(initialValues);
            if (unique) {
                this.seen.addAll(initialValues);
            }
        }
    }

    @Override
    public synchronized boolean update(List<V> updates) {
        validateUpdates(updates);

        if (updates.isEmpty()) {
            return false;
        }

        // In non-accumulate mode, clear previous values
        if (!accumulate && !values.isEmpty()) {
            values.clear();
            if (unique) {
                seen.clear();
            }
        }

        boolean modified = false;

        for (V value : updates) {
            // Skip null values
            if (value == null) {
                continue;
            }

            // In unique mode, skip duplicates
            if (unique) {
                if (seen.add(value)) {
                    values.add(value);
                    modified = true;
                }
            } else {
                values.add(value);
                modified = true;
            }
        }

        if (modified) {
            markUpdated();
        }

        return modified;
    }

    @Override
    public List<V> get() {
        if (values.isEmpty()) {
            throw new EmptyChannelException(name, "No values have been added");
        }
        // Return immutable copy
        return List.copyOf(values);
    }

    @Override
    public List<V> checkpoint() {
        return List.copyOf(values);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Channel<List<V>, V, List<V>> fromCheckpoint(List<V> checkpoint) {
        return new TopicChannel<>(name, valueType, checkpoint, accumulate, unique);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Channel<List<V>, V, List<V>> copy() {
        return new TopicChannel<>(name, valueType, new ArrayList<>(values), accumulate, unique);
    }

    @Override
    public boolean isEmpty() {
        return values.isEmpty();
    }

    /**
     * Clears all values in this channel.
     */
    public synchronized void clear() {
        values.clear();
        if (unique) {
            seen.clear();
        }
        resetUpdated();
    }

    /**
     * Gets the current size of the value list.
     *
     * @return the number of values
     */
    public int size() {
        return values.size();
    }
}
