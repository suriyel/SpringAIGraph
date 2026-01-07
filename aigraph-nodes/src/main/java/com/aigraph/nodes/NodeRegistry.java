package com.aigraph.nodes;

import com.aigraph.core.utils.ValidationUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Thread-safe registry for managing nodes in a graph.
 * <p>
 * The registry maintains:
 * <ul>
 *   <li>All registered nodes by name</li>
 *   <li>Subscription index for efficient node lookup by channel</li>
 * </ul>
 * <p>
 * Thread Safety:
 * <ul>
 *   <li>Uses {@link ConcurrentHashMap} for node storage</li>
 *   <li>Subscription index is rebuilt on each registration for consistency</li>
 *   <li>All public methods are thread-safe</li>
 * </ul>
 * <p>
 * Example usage:
 * <pre>{@code
 * var registry = new NodeRegistry();
 * registry.register(node1);
 * registry.register(node2);
 *
 * // Find nodes subscribed to a channel
 * Set<Node<?, ?>> subscribers = registry.getBySubscription("input");
 * }</pre>
 *
 * @author AIGraph Team
 * @since 0.0.8
 */
public class NodeRegistry {

    private static final Logger log = LoggerFactory.getLogger(NodeRegistry.class);

    private final ConcurrentHashMap<String, Node<?, ?>> nodes;
    private final Map<String, Set<String>> subscriptionIndex;

    /**
     * Creates a new empty registry.
     */
    public NodeRegistry() {
        this.nodes = new ConcurrentHashMap<>();
        this.subscriptionIndex = new ConcurrentHashMap<>();
    }

    /**
     * Registers a node using its metadata name.
     *
     * @param node the node to register
     * @throws IllegalArgumentException if a node with this name already exists
     */
    public void register(Node<?, ?> node) {
        ValidationUtils.requireNonNull(node, "node");
        register(node.getName(), node);
    }

    /**
     * Registers a node with a specific name.
     *
     * @param name the registration name
     * @param node the node instance
     * @throws IllegalArgumentException if a node with this name already exists
     */
    public void register(String name, Node<?, ?> node) {
        ValidationUtils.requireNonBlank(name, "name");
        ValidationUtils.requireNonNull(node, "node");

        Node<?, ?> existing = nodes.putIfAbsent(name, node);
        if (existing != null) {
            throw new IllegalArgumentException(
                "Node '" + name + "' is already registered"
            );
        }

        // Update subscription index
        updateIndex(node);

        log.debug("Registered node: {} [subscribes to: {}]",
            name, node.getSubscribedChannels());
    }

    /**
     * Gets a node by name.
     *
     * @param name the node name
     * @return optional containing the node, or empty if not found
     */
    public Optional<Node<?, ?>> get(String name) {
        ValidationUtils.requireNonBlank(name, "name");
        return Optional.ofNullable(nodes.get(name));
    }

    /**
     * Gets all nodes subscribed to a specific channel.
     *
     * @param channel the channel name
     * @return set of nodes subscribed to the channel
     */
    public Set<Node<?, ?>> getBySubscription(String channel) {
        ValidationUtils.requireNonBlank(channel, "channel");

        Set<String> nodeNames = subscriptionIndex.get(channel);
        if (nodeNames == null || nodeNames.isEmpty()) {
            return Set.of();
        }

        return nodeNames.stream()
            .map(nodes::get)
            .filter(Objects::nonNull)
            .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * Gets all nodes subscribed to any of the given channels.
     *
     * @param channels the channel names
     * @return set of nodes subscribed to any of the channels
     */
    public Set<Node<?, ?>> getBySubscriptions(Set<String> channels) {
        ValidationUtils.requireNonNull(channels, "channels");

        if (channels.isEmpty()) {
            return Set.of();
        }

        return channels.stream()
            .flatMap(channel -> getBySubscription(channel).stream())
            .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * Gets all registered nodes.
     *
     * @return immutable collection of all nodes
     */
    public Collection<Node<?, ?>> getAll() {
        return Collections.unmodifiableCollection(nodes.values());
    }

    /**
     * Removes a node from the registry.
     *
     * @param name the node name
     * @return true if the node was removed
     */
    public boolean remove(String name) {
        ValidationUtils.requireNonBlank(name, "name");

        Node<?, ?> removed = nodes.remove(name);
        if (removed != null) {
            // Remove from subscription index
            removeFromIndex(removed);
            log.debug("Removed node: {}", name);
            return true;
        }
        return false;
    }

    /**
     * Checks if a node is registered.
     *
     * @param name the node name
     * @return true if registered
     */
    public boolean contains(String name) {
        return nodes.containsKey(name);
    }

    /**
     * Gets the number of registered nodes.
     *
     * @return the node count
     */
    public int size() {
        return nodes.size();
    }

    /**
     * Removes all nodes from the registry.
     */
    public void clear() {
        int count = nodes.size();
        nodes.clear();
        subscriptionIndex.clear();
        log.debug("Cleared {} nodes", count);
    }

    /**
     * Updates the subscription index for a node.
     */
    private synchronized void updateIndex(Node<?, ?> node) {
        for (String channel : node.getSubscribedChannels()) {
            subscriptionIndex.computeIfAbsent(channel, k -> ConcurrentHashMap.newKeySet())
                .add(node.getName());
        }
    }

    /**
     * Removes a node from the subscription index.
     */
    private synchronized void removeFromIndex(Node<?, ?> node) {
        for (String channel : node.getSubscribedChannels()) {
            Set<String> nodeNames = subscriptionIndex.get(channel);
            if (nodeNames != null) {
                nodeNames.remove(node.getName());
                if (nodeNames.isEmpty()) {
                    subscriptionIndex.remove(channel);
                }
            }
        }
    }

    /**
     * Gets all channel names that have subscribers.
     *
     * @return set of channel names
     */
    public Set<String> getAllSubscribedChannels() {
        return Set.copyOf(subscriptionIndex.keySet());
    }
}
