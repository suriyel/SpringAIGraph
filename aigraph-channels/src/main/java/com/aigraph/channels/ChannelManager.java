package com.aigraph.channels;

import com.aigraph.core.exceptions.InvalidUpdateException;
import com.aigraph.core.utils.ValidationUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.function.Supplier;

/**
 * Thread-safe manager for all channels in a graph execution.
 * <p>
 * The ChannelManager is responsible for:
 * <ul>
 *   <li>Registering and retrieving channels</li>
 *   <li>Batch updating multiple channels</li>
 *   <li>Tracking which channels have been updated in the current step</li>
 *   <li>Checkpoint and restore operations</li>
 * </ul>
 * <p>
 * Thread Safety:
 * <ul>
 *   <li>Uses {@link ConcurrentHashMap} for channel storage</li>
 *   <li>Uses {@link CopyOnWriteArraySet} for tracking updates</li>
 *   <li>All public methods are thread-safe</li>
 * </ul>
 * <p>
 * Example usage:
 * <pre>{@code
 * var manager = new ChannelManager();
 * manager.register("input", new LastValueChannel<>("input", String.class));
 * manager.register("output", new LastValueChannel<>("output", String.class));
 *
 * manager.update("input", List.of("hello"));
 * Set<String> updated = manager.getUpdatedChannels(); // ["input"]
 * }</pre>
 *
 * @author AIGraph Team
 * @since 0.0.8
 */
public class ChannelManager {

    private static final Logger log = LoggerFactory.getLogger(ChannelManager.class);

    private final ConcurrentHashMap<String, Channel<?, ?, ?>> channels;
    private final CopyOnWriteArraySet<String> updatedChannels;

    /**
     * Creates a new empty ChannelManager.
     */
    public ChannelManager() {
        this.channels = new ConcurrentHashMap<>();
        this.updatedChannels = new CopyOnWriteArraySet<>();
    }

    /**
     * Registers a channel with this manager.
     *
     * @param name    the channel name
     * @param channel the channel instance
     * @throws IllegalArgumentException if a channel with this name already exists
     */
    public void register(String name, Channel<?, ?, ?> channel) {
        ValidationUtils.requireNonBlank(name, "name");
        ValidationUtils.requireNonNull(channel, "channel");

        Channel<?, ?, ?> existing = channels.putIfAbsent(name, channel);
        if (existing != null) {
            throw new IllegalArgumentException(
                "Channel '" + name + "' is already registered"
            );
        }

        log.debug("Registered channel: {} [type={}]", name, channel.getClass().getSimpleName());
    }

    /**
     * Gets a channel by name.
     *
     * @param <V>  the value type
     * @param <U>  the update type
     * @param <C>  the checkpoint type
     * @param name the channel name
     * @return the channel
     * @throws NoSuchElementException if channel doesn't exist
     */
    @SuppressWarnings("unchecked")
    public <V, U, C> Channel<V, U, C> get(String name) {
        ValidationUtils.requireNonBlank(name, "name");

        Channel<?, ?, ?> channel = channels.get(name);
        if (channel == null) {
            throw new NoSuchElementException("Channel '" + name + "' not found");
        }

        return (Channel<V, U, C>) channel;
    }

    /**
     * Gets a channel by name, or creates it if it doesn't exist.
     *
     * @param <V>      the value type
     * @param <U>      the update type
     * @param <C>      the checkpoint type
     * @param name     the channel name
     * @param supplier a supplier to create the channel if it doesn't exist
     * @return the existing or newly created channel
     */
    @SuppressWarnings("unchecked")
    public <V, U, C> Channel<V, U, C> getOrCreate(String name, Supplier<Channel<V, U, C>> supplier) {
        ValidationUtils.requireNonBlank(name, "name");
        ValidationUtils.requireNonNull(supplier, "supplier");

        Channel<?, ?, ?> channel = channels.computeIfAbsent(name, k -> {
            Channel<V, U, C> newChannel = supplier.get();
            log.debug("Auto-created channel: {} [type={}]", name, newChannel.getClass().getSimpleName());
            return newChannel;
        });

        return (Channel<V, U, C>) channel;
    }

    /**
     * Updates a channel with a list of values.
     *
     * @param <U>    the update type
     * @param name   the channel name
     * @param values the update values
     * @return true if the channel was modified
     * @throws NoSuchElementException if channel doesn't exist
     */
    @SuppressWarnings("unchecked")
    public <U> boolean update(String name, List<U> values) {
        Channel<?, U, ?> channel = get(name);

        boolean modified = channel.update(values);

        if (modified) {
            updatedChannels.add(name);
            log.trace("Channel '{}' updated with {} values", name, values.size());
        }

        return modified;
    }

    /**
     * Batch updates multiple channels.
     * <p>
     * This method updates all channels and returns the set of channels
     * that were actually modified.
     *
     * @param updates map of channel name to list of update values
     * @return set of channel names that were updated
     */
    @SuppressWarnings("unchecked")
    public Set<String> batchUpdate(Map<String, List<?>> updates) {
        ValidationUtils.requireNonNull(updates, "updates");

        Set<String> modified = new LinkedHashSet<>();

        for (Map.Entry<String, List<?>> entry : updates.entrySet()) {
            String channelName = entry.getKey();
            List<?> values = entry.getValue();

            try {
                Channel<?, ?, ?> channel = get(channelName);
                boolean updated = ((Channel<Object, Object, Object>) channel)
                    .update((List<Object>) values);

                if (updated) {
                    updatedChannels.add(channelName);
                    modified.add(channelName);
                }
            } catch (Exception e) {
                throw new InvalidUpdateException(
                    channelName,
                    "Batch update failed",
                    e
                );
            }
        }

        log.debug("Batch updated {} channels: {}", modified.size(), modified);
        return modified;
    }

    /**
     * Gets the set of channels that have been updated since the last reset.
     *
     * @return immutable set of updated channel names
     */
    public Set<String> getUpdatedChannels() {
        return Set.copyOf(updatedChannels);
    }

    /**
     * Clears the updated channels tracking set.
     * <p>
     * This should be called at the beginning of each execution step.
     */
    public void clearUpdatedFlags() {
        updatedChannels.clear();
        log.trace("Cleared updated channel flags");
    }

    /**
     * Creates a checkpoint of all channels.
     *
     * @return map of channel name to checkpoint data
     */
    public Map<String, Object> checkpoint() {
        Map<String, Object> checkpointData = new LinkedHashMap<>();

        for (Map.Entry<String, Channel<?, ?, ?>> entry : channels.entrySet()) {
            String name = entry.getKey();
            Channel<?, ?, ?> channel = entry.getValue();

            try {
                Object checkpoint = channel.checkpoint();
                checkpointData.put(name, checkpoint);
            } catch (Exception e) {
                log.warn("Failed to checkpoint channel '{}': {}", name, e.getMessage());
                // Continue checkpointing other channels
            }
        }

        log.debug("Created checkpoint for {} channels", checkpointData.size());
        return checkpointData;
    }

    /**
     * Restores channels from a checkpoint.
     *
     * @param checkpointData map of channel name to checkpoint data
     */
    @SuppressWarnings("unchecked")
    public void restore(Map<String, Object> checkpointData) {
        ValidationUtils.requireNonNull(checkpointData, "checkpointData");

        for (Map.Entry<String, Object> entry : checkpointData.entrySet()) {
            String name = entry.getKey();
            Object checkpoint = entry.getValue();

            try {
                Channel<?, ?, ?> channel = get(name);
                Channel<?, ?, ?> restored = ((Channel<Object, Object, Object>) channel)
                    .fromCheckpoint(checkpoint);

                channels.put(name, restored);
            } catch (Exception e) {
                log.warn("Failed to restore channel '{}': {}", name, e.getMessage());
                // Continue restoring other channels
            }
        }

        // Clear updated flags after restoration
        updatedChannels.clear();

        log.debug("Restored {} channels from checkpoint", checkpointData.size());
    }

    /**
     * Gets all registered channels.
     *
     * @return immutable map of channel name to channel instance
     */
    public Map<String, Channel<?, ?, ?>> getAll() {
        return Map.copyOf(channels);
    }

    /**
     * Checks if a channel is registered.
     *
     * @param name the channel name
     * @return true if registered
     */
    public boolean contains(String name) {
        return channels.containsKey(name);
    }

    /**
     * Removes a channel from this manager.
     *
     * @param name the channel name
     * @return the removed channel, or null if not found
     */
    public Channel<?, ?, ?> remove(String name) {
        Channel<?, ?, ?> removed = channels.remove(name);
        if (removed != null) {
            updatedChannels.remove(name);
            log.debug("Removed channel: {}", name);
        }
        return removed;
    }

    /**
     * Gets the number of registered channels.
     *
     * @return the channel count
     */
    public int size() {
        return channels.size();
    }

    /**
     * Clears all channels and resets state.
     */
    public void clear() {
        int count = channels.size();
        channels.clear();
        updatedChannels.clear();
        log.debug("Cleared {} channels", count);
    }
}
