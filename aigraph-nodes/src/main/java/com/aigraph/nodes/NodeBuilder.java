package com.aigraph.nodes;

import com.aigraph.core.functional.ContextAwareNodeFunction;
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

public final class NodeBuilder<I, O> {

    private final String name;
    private final LinkedHashSet<String> subscribedChannels;
    private final LinkedHashSet<String> readChannels;
    private final LinkedHashMap<String, Function<O, ?>> writeTargets;
    private NodeFunction<I, O> processor;
    private ContextAwareNodeFunction<I, O> contextAwareProcessor;
    private NodeMetadata.Builder metadataBuilder;
    private ExecutorService executor;

    private NodeBuilder(String name) {
        this.name = ValidationUtils.requireNonBlank(name, "name");
        this.subscribedChannels = new LinkedHashSet<>();
        this.readChannels = new LinkedHashSet<>();
        this.writeTargets = new LinkedHashMap<>();
        this.metadataBuilder = NodeMetadata.builder(name);
    }

    public static <I, O> NodeBuilder<I, O> create(String name) {
        return new NodeBuilder<>(name);
    }

    public NodeBuilder<I, O> subscribeOnly(String channel) {
        ValidationUtils.requireNonBlank(channel, "channel");
        this.subscribedChannels.add(channel);
        return this;
    }

    public NodeBuilder<I, O> subscribeTo(String... channels) {
        ValidationUtils.requireNonEmpty(Set.of(channels), "channels");
        for (String channel : channels) {
            ValidationUtils.requireNonBlank(channel, "channel");
            this.subscribedChannels.add(channel);
        }
        return this;
    }

    public NodeBuilder<I, O> alsoRead(String... channels) {
        for (String channel : channels) {
            ValidationUtils.requireNonBlank(channel, "channel");
            this.readChannels.add(channel);
        }
        return this;
    }

    /**
     * Sets the processing function.
     * <p>
     * Accepts {@link NodeFunction} which can throw checked exceptions.
     * Regular lambdas and method references are automatically compatible.
     * <p>
     * Example:
     * <pre>{@code
     * // Lambda without exception
     * .process(s -> s.toUpperCase())
     *
     * // Lambda with exception
     * .process(s -> {
     *     if (s == null) throw new IllegalArgumentException();
     *     return s.toUpperCase();
     * })
     *
     * // Method reference
     * .process(String::toUpperCase)
     * }</pre>
     *
     * @param function the processing function
     * @return this builder
     */
    public NodeBuilder<I, O> process(NodeFunction<I, O> function) {
        this.processor = ValidationUtils.requireNonNull(function, "function");
        this.contextAwareProcessor = null; // Clear context-aware processor
        return this;
    }

    /**
     * Sets a context-aware processing function.
     * <p>
     * This function receives both input and execution context, allowing
     * access to message history, metadata, and other contextual information.
     * <p>
     * Example:
     * <pre>{@code
     * .processWithContext((input, ctx) -> {
     *     ExecutionContext context = (ExecutionContext) ctx;
     *     MessageContext msgCtx = context.getMessageContext();
     *     // Access message history
     *     List<Message> messages = msgCtx.getMessages();
     *     // Process with context
     *     return doSomething(input, messages);
     * })
     * }</pre>
     *
     * @param function the context-aware processing function
     * @return this builder
     */
    public NodeBuilder<I, O> processWithContext(ContextAwareNodeFunction<I, O> function) {
        this.contextAwareProcessor = ValidationUtils.requireNonNull(function, "function");
        this.processor = null; // Clear regular processor
        return this;
    }

    // ⚠️ 已移除: process(Function<I, O> function) 方法
    // 该重载与 NodeFunction 版本冲突，导致 lambda 表达式引用不明确
    // NodeFunction 已经可以接受所有不抛异常的 lambda

    public NodeBuilder<I, O> writeTo(String channel) {
        ValidationUtils.requireNonBlank(channel, "channel");
        this.writeTargets.put(channel, null);
        return this;
    }

    public <V> NodeBuilder<I, O> writeTo(String channel, Function<O, V> mapper) {
        ValidationUtils.requireNonBlank(channel, "channel");
        ValidationUtils.requireNonNull(mapper, "mapper");
        this.writeTargets.put(channel, mapper);
        return this;
    }

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

    public NodeBuilder<I, O> withRetry(RetryPolicy policy) {
        this.metadataBuilder.retryPolicy(policy);
        return this;
    }

    public NodeBuilder<I, O> withTimeout(Duration timeout) {
        this.metadataBuilder.timeout(timeout);
        return this;
    }

    public NodeBuilder<I, O> withDescription(String description) {
        this.metadataBuilder.description(description);
        return this;
    }

    public NodeBuilder<I, O> withTags(String... tags) {
        this.metadataBuilder.tags(tags);
        return this;
    }

    public NodeBuilder<I, O> withExecutor(ExecutorService executor) {
        this.executor = executor;
        return this;
    }

    public Node<I, O> build() {
        ValidationUtils.requireState(!subscribedChannels.isEmpty(),
                "Node must subscribe to at least one channel");
        ValidationUtils.requireState(processor != null || contextAwareProcessor != null,
                "Node must have a processing function (use process() or processWithContext())");

        NodeMetadata metadata = metadataBuilder.build();

        // Build context-aware node if context processor is provided
        if (contextAwareProcessor != null) {
            return new ContextAwareFunctionalNode<>(
                    name,
                    subscribedChannels,
                    readChannels,
                    writeTargets,
                    contextAwareProcessor,
                    metadata,
                    executor
            );
        }

        // Build regular node
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