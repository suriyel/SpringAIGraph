package com.aigraph.nodes;

import com.aigraph.core.functional.NodeFunction;
import com.aigraph.core.utils.ValidationUtils;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Fluent builder for creating {@link Node} instances.
 * <p>
 * The builder provides a fluent API for configuring all aspects of a node:
 * <ul>
 *   <li>Channel subscriptions (triggers)</li>
 *   <li>Additional channel reads (context)</li>
 *   <li>Processing logic</li>
 *   <li>Write targets with optional mappers</li>
 *   <li>Metadata (description, tags, retry, timeout)</li>
 * </ul>
 * <p>
 * Example:
 * <pre>{@code
 * Node<String, String> node = NodeBuilder.<String, String>create("process")
 *     .subscribeOnly("input")
 *     .process(String::toUpperCase)
 *     .writeTo("output")
 *     .withDescription("Converts input to uppercase")
 *     .withTimeout(Duration.ofSeconds(5))
 *     .build();
 * }</pre>
 *
 * @param <I> the input type
 * @param <O> the output type
 * @author AIGraph Team
 * @since 0.0.8
 */
public final class NodeBuilder<I, O> {

    private final String name;
    private final LinkedHashSet<String> subscribedChannels;
    private final LinkedHashSet<String> readChannels;
    private final LinkedHashMap<String, Function<O, ?>> writeTargets;
    private NodeFunction<I, O> processor;
    private NodeMetadata.Builder metadataBuilder;
    private ExecutorService executor;

    private NodeBuilder(String name) {
        this.name = ValidationUtils.requireNonBlank(name, "name");
        this.subscribedChannels = new LinkedHashSet<>();
        this.readChannels = new LinkedHashSet<>();
        this.writeTargets = new LinkedHashMap<>();
        this.metadataBuilder = NodeMetadata.builder(name);
    }

    /**
     * Creates a new builder with the specified name.
     *
     * @param <I>  the input type
     * @param <O>  the output type
     * @param name the node name
     * @return a new builder
     */
    public static <I, O> NodeBuilder<I, O> create(String name) {
        return new NodeBuilder<>(name);
    }

    /**
     * Subscribes to a single channel (convenience method).
     * <p>
     * The node will be triggered when this channel is updated.
     *
     * @param channel the channel name
     * @return this builder
     */
    public NodeBuilder<I, O> subscribeOnly(String channel) {
        ValidationUtils.requireNonBlank(channel, "channel");
        this.subscribedChannels.add(channel);
        return this;
    }

    /**
     * Subscribes to one or more channels.
     * <p>
     * The node will be triggered when any of these channels are updated.
     *
     * @param channels the channel names
     * @return this builder
     */
    public NodeBuilder<I, O> subscribeTo(String... channels) {
        ValidationUtils.requireNonEmpty(Set.of(channels), "channels");
        for (String channel : channels) {
            ValidationUtils.requireNonBlank(channel, "channel");
            this.subscribedChannels.add(channel);
        }
        return this;
    }

    /**
     * Adds additional channels to read from (without subscribing).
     * <p>
     * These channels provide context but don't trigger execution.
     *
     * @param channels the channel names
     * @return this builder
     */
    public NodeBuilder<I, O> alsoRead(String... channels) {
        for (String channel : channels) {
            ValidationUtils.requireNonBlank(channel, "channel");
            this.readChannels.add(channel);
        }
        return this;
    }

    /**
     * Sets the processing function.
     *
     * @param function the processing function
     * @return this builder
     */
    public NodeBuilder<I, O> process(NodeFunction<I, O> function) {
        this.processor = ValidationUtils.requireNonNull(function, "function");
        return this;
    }

    /**
     * Sets the processing function using a standard Java Function.
     *
     * @param function the processing function
     * @return this builder
     */
    public NodeBuilder<I, O> process(Function<I, O> function) {
        ValidationUtils.requireNonNull(function, "function");
        this.processor = function::apply;
        return this;
    }

    /**
     * Writes the output directly to a channel.
     *
     * @param channel the channel name
     * @return this builder
     */
    public NodeBuilder<I, O> writeTo(String channel) {
        ValidationUtils.requireNonBlank(channel, "channel");
        this.writeTargets.put(channel, null);
        return this;
    }

    /**
     * Writes the output to a channel after applying a mapper function.
     *
     * @param <V>     the mapped value type
     * @param channel the channel name
     * @param mapper  the mapper function
     * @return this builder
     */
    public <V> NodeBuilder<I, O> writeTo(String channel, Function<O, V> mapper) {
        ValidationUtils.requireNonBlank(channel, "channel");
        ValidationUtils.requireNonNull(mapper, "mapper");
        this.writeTargets.put(channel, mapper);
        return this;
    }

    /**
     * Writes to a channel conditionally.
     * <p>
     * The mapper is only applied if the predicate returns true.
     * If predicate returns false or mapper returns null, nothing is written.
     *
     * @param <V>       the mapped value type
     * @param channel   the channel name
     * @param predicate the condition to check
     * @param mapper    the mapper function
     * @return this builder
     */
    public <V> NodeBuilder<I, O> writeToConditional(
        String channel,
        Predicate<O> predicate,
        Function<O, V> mapper
    ) {
        ValidationUtils.requireNonBlank(channel, "channel");
        ValidationUtils.requireNonNull(predicate, "predicate");
        ValidationUtils.requireNonNull(mapper, "mapper");

        this.writeTargets.put(channel, output ->
            predicate.test(output) ? mapper.apply(output) : null
        );
        return this;
    }

    /**
     * Sets the retry policy.
     *
     * @param policy the retry policy
     * @return this builder
     */
    public NodeBuilder<I, O> withRetry(RetryPolicy policy) {
        this.metadataBuilder.retryPolicy(policy);
        return this;
    }

    /**
     * Sets the execution timeout.
     *
     * @param timeout the timeout duration
     * @return this builder
     */
    public NodeBuilder<I, O> withTimeout(Duration timeout) {
        this.metadataBuilder.timeout(timeout);
        return this;
    }

    /**
     * Sets the node description.
     *
     * @param description the description
     * @return this builder
     */
    public NodeBuilder<I, O> withDescription(String description) {
        this.metadataBuilder.description(description);
        return this;
    }

    /**
     * Sets the node tags.
     *
     * @param tags the tags
     * @return this builder
     */
    public NodeBuilder<I, O> withTags(String... tags) {
        this.metadataBuilder.tags(tags);
        return this;
    }

    /**
     * Sets a custom executor service for async execution.
     *
     * @param executor the executor service
     * @return this builder
     */
    public NodeBuilder<I, O> withExecutor(ExecutorService executor) {
        this.executor = executor;
        return this;
    }

    /**
     * Builds the node.
     *
     * @return a new node instance
     * @throws IllegalStateException if required configuration is missing
     */
    public Node<I, O> build() {
        // Validate required fields
        ValidationUtils.requireState(!subscribedChannels.isEmpty(),
            "Node must subscribe to at least one channel");
        ValidationUtils.requireNonNull(processor,
            "Node must have a processing function");

        NodeMetadata metadata = metadataBuilder.build();

        return new FunctionalNode<>(
            name,
            subscribedChannels,
            readChannels,
            writeTargets,
            processor,
            metadata,
            executor
        );
    }
}
