package com.aigraph.channels;

import com.aigraph.core.exceptions.InvalidUpdateException;
import com.aigraph.core.utils.ValidationUtils;

import java.util.List;

/**
 * Abstract base class for channel implementations.
 * <p>
 * Provides common functionality for:
 * <ul>
 *   <li>Name and type management</li>
 *   <li>Update tracking</li>
 *   <li>Validation utilities</li>
 * </ul>
 *
 * @param <V> the value type
 * @param <U> the update type
 * @param <C> the checkpoint type
 * @author AIGraph Team
 * @since 0.0.8
 */
public abstract class BaseChannel<V, U, C> implements Channel<V, U, C> {

    protected final String name;
    protected final Class<V> valueType;
    protected final Class<U> updateType;
    protected volatile boolean updated;

    /**
     * Creates a new base channel.
     *
     * @param name       the channel name
     * @param valueType  the value type class
     * @param updateType the update type class
     */
    protected BaseChannel(String name, Class<V> valueType, Class<U> updateType) {
        this.name = ValidationUtils.requireNonBlank(name, "name");
        this.valueType = ValidationUtils.requireNonNull(valueType, "valueType");
        this.updateType = ValidationUtils.requireNonNull(updateType, "updateType");
        this.updated = false;
    }

    /**
     * Creates a new base channel with specified update flag.
     * Used by copy() methods to preserve updated state.
     *
     * @param name       the channel name
     * @param valueType  the value type class
     * @param updateType the update type class
     * @param updated    the updated flag value
     */
    protected BaseChannel(String name, Class<V> valueType, Class<U> updateType, boolean updated) {
        this.name = ValidationUtils.requireNonBlank(name, "name");
        this.valueType = ValidationUtils.requireNonNull(valueType, "valueType");
        this.updateType = ValidationUtils.requireNonNull(updateType, "updateType");
        this.updated = updated;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Class<V> getValueType() {
        return valueType;
    }

    @Override
    public Class<U> getUpdateType() {
        return updateType;
    }

    @Override
    public boolean isUpdated() {
        return updated;
    }

    /**
     * Marks the channel as updated.
     */
    protected void markUpdated() {
        this.updated = true;
    }

    /**
     * Resets the updated flag.
     */
    protected void resetUpdated() {
        this.updated = false;
    }

    /**
     * Validates that update values are of the correct type.
     *
     * @param values the values to validate
     * @throws InvalidUpdateException if validation fails
     */
    protected void validateUpdates(List<U> values) {
        if (values == null) {
            throw new InvalidUpdateException(name, "Update list cannot be null");
        }

        for (int i = 0; i < values.size(); i++) {
            U value = values.get(i);
            if (value != null && !updateType.isInstance(value)) {
                throw new InvalidUpdateException(
                    name,
                    "Update at index " + i + " has invalid type. Expected: " +
                    updateType.getName() + ", got: " + value.getClass().getName()
                );
            }
        }
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{name='" + name + "', type=" + valueType.getSimpleName() + "}";
    }
}
