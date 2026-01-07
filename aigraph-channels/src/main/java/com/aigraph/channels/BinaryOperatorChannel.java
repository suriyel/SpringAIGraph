package com.aigraph.channels;

import com.aigraph.core.exceptions.EmptyChannelException;
import com.aigraph.core.utils.ValidationUtils;

import java.util.List;
import java.util.function.BinaryOperator;

/**
 * A channel that aggregates values using a binary operator.
 * <p>
 * This channel reduces multiple updates into a single value using
 * a reduction function (similar to {@link java.util.stream.Stream#reduce}).
 * <p>
 * Characteristics:
 * <ul>
 *   <li>Accepts multiple updates per step</li>
 *   <li>Combines values using a {@link BinaryOperator}</li>
 *   <li>Requires an identity value for initialization</li>
 *   <li>Thread-safe with synchronized updates</li>
 * </ul>
 * <p>
 * Common use cases:
 * <ul>
 *   <li>Sum: {@code new BinaryOperatorChannel<>("sum", Integer.class, Integer::sum, 0)}</li>
 *   <li>Product: {@code new BinaryOperatorChannel<>("product", Integer.class, (a, b) -> a * b, 1)}</li>
 *   <li>Max: {@code new BinaryOperatorChannel<>("max", Integer.class, Math::max, Integer.MIN_VALUE)}</li>
 *   <li>String concat: {@code new BinaryOperatorChannel<>("concat", String.class, String::concat, "")}</li>
 * </ul>
 * <p>
 * Example:
 * <pre>{@code
 * var sumChannel = new BinaryOperatorChannel<>(
 *     "total",
 *     Integer.class,
 *     Integer::sum,
 *     0
 * );
 * sumChannel.update(List.of(10, 20, 30));
 * System.out.println(sumChannel.get()); // 60
 * }</pre>
 *
 * @param <V> the value type
 * @author AIGraph Team
 * @since 0.0.8
 */
public final class BinaryOperatorChannel<V> extends BaseChannel<V, V, V> {

    private final BinaryOperator<V> operator;
    private final V identity;
    private volatile V value;
    private volatile boolean initialized;

    /**
     * Creates a new BinaryOperatorChannel.
     *
     * @param name     the channel name
     * @param type     the value type
     * @param operator the binary reduction operator
     * @param identity the identity value (initial value and neutral element)
     */
    public BinaryOperatorChannel(String name, Class<V> type,
                                  BinaryOperator<V> operator, V identity) {
        super(name, type, type);
        this.operator = ValidationUtils.requireNonNull(operator, "operator");
        this.identity = identity; // Can be null for some operators
        this.value = identity;
        this.initialized = false;
    }

    /**
     * Private constructor for copy/checkpoint.
     */
    private BinaryOperatorChannel(String name, Class<V> type,
                                   BinaryOperator<V> operator, V identity,
                                   V currentValue, boolean initialized) {
        super(name, type, type);
        this.operator = operator;
        this.identity = identity;
        this.value = currentValue;
        this.initialized = initialized;
    }

    @Override
    public synchronized boolean update(List<V> values) {
        validateUpdates(values);

        if (values.isEmpty()) {
            return false;
        }

        boolean modified = false;

        for (V update : values) {
            // Skip null values
            if (update == null) {
                continue;
            }

            // First real value initializes the channel
            if (!initialized) {
                value = update;
                initialized = true;
                modified = true;
            } else {
                value = operator.apply(value, update);
                modified = true;
            }
        }

        if (modified) {
            markUpdated();
        }

        return modified;
    }

    @Override
    public V get() {
        if (!initialized) {
            throw new EmptyChannelException(name, "Channel has not been initialized with any values");
        }
        return value;
    }

    @Override
    public V checkpoint() {
        if (!initialized) {
            return identity;
        }
        return value;
    }

    @Override
    public Channel<V, V, V> fromCheckpoint(V checkpoint) {
        boolean wasInitialized = checkpoint != null && !checkpoint.equals(identity);
        return new BinaryOperatorChannel<>(
            name, valueType, operator, identity,
            checkpoint != null ? checkpoint : identity,
            wasInitialized
        );
    }

    @Override
    public Channel<V, V, V> copy() {
        return new BinaryOperatorChannel<>(
            name, valueType, operator, identity, value, initialized
        );
    }

    @Override
    public boolean isEmpty() {
        return !initialized;
    }

    /**
     * Resets the channel to its identity value.
     */
    public synchronized void reset() {
        this.value = identity;
        this.initialized = false;
        resetUpdated();
    }

    /**
     * Gets the identity value used by this channel.
     *
     * @return the identity value
     */
    public V getIdentity() {
        return identity;
    }
}
