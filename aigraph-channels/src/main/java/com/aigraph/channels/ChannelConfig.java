package com.aigraph.channels;

import java.util.Map;

/**
 * Configuration record for channel creation.
 * <p>
 * This record encapsulates all necessary information to create
 * a channel instance, supporting factory patterns and serialization.
 * <p>
 * Example:
 * <pre>{@code
 * var config = new ChannelConfig(
 *     "messages",
 *     TopicChannel.class,
 *     String.class,
 *     Map.of("accumulate", true, "unique", false)
 * );
 * }</pre>
 *
 * @param name        the channel name
 * @param channelType the channel implementation class
 * @param valueType   the value type class
 * @param options     additional channel-specific options
 * @author AIGraph Team
 * @since 0.0.8
 */
public record ChannelConfig(
    String name,
    Class<? extends Channel<?, ?, ?>> channelType,
    Class<?> valueType,
    Map<String, Object> options
) {
    /**
     * Compact constructor with validation.
     */
    public ChannelConfig {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Channel name must not be blank");
        }
        if (channelType == null) {
            throw new IllegalArgumentException("Channel type must not be null");
        }
        if (valueType == null) {
            throw new IllegalArgumentException("Value type must not be null");
        }
        // Make options immutable
        options = options != null ? Map.copyOf(options) : Map.of();
    }

    /**
     * Creates a config with no options.
     *
     * @param name        the channel name
     * @param channelType the channel type
     * @param valueType   the value type
     */
    public ChannelConfig(String name, Class<? extends Channel<?, ?, ?>> channelType, Class<?> valueType) {
        this(name, channelType, valueType, Map.of());
    }

    /**
     * Gets an option value with a default.
     *
     * @param <T>          the option type
     * @param key          the option key
     * @param defaultValue the default value if not present
     * @return the option value or default
     */
    @SuppressWarnings("unchecked")
    public <T> T getOption(String key, T defaultValue) {
        return (T) options.getOrDefault(key, defaultValue);
    }

    /**
     * Checks if an option exists.
     *
     * @param key the option key
     * @return true if present
     */
    public boolean hasOption(String key) {
        return options.containsKey(key);
    }
}
