package com.aigraph.nodes;

import com.aigraph.core.utils.ValidationUtils;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * A node that chains multiple nodes together sequentially.
 * <p>
 * Each stage's output becomes the next stage's input.
 * Useful for creating processing pipelines.
 * <p>
 * Example:
 * <pre>{@code
 * CompositeNode<String, Integer> pipeline = new CompositeNode<>("pipeline")
 *     .addStage(trimNode)
 *     .addStage(parseNode)
 *     .addStage(validateNode);
 * }</pre>
 *
 * @param <I> initial input type
 * @param <O> final output type
 * @author AIGraph Team
 * @since 0.0.8
 */
public class CompositeNode<I, O> implements Node<I, O> {

    private final String name;
    private final List<Node<?, ?>> stages;
    private final Set<String> subscribedChannels;
    private final Map<String, Function<O, ?>> writeTargets;
    private final NodeMetadata metadata;

    public CompositeNode(String name) {
        this.name = ValidationUtils.requireNonBlank(name, "name");
        this.stages = new ArrayList<>();
        this.subscribedChannels = new LinkedHashSet<>();
        this.writeTargets = new LinkedHashMap<>();
        this.metadata = NodeMetadata.of(name);
    }

    /**
     * Adds a stage to the pipeline.
     *
     * @param stage the node to add
     * @return this composite node
     */
    public CompositeNode<I, O> addStage(Node<?, ?> stage) {
        ValidationUtils.requireNonNull(stage, "stage");
        this.stages.add(stage);
        return this;
    }

    /**
     * Subscribes to channels (applied to first stage).
     */
    public CompositeNode<I, O> subscribeTo(String... channels) {
        this.subscribedChannels.addAll(Arrays.asList(channels));
        return this;
    }

    /**
     * Writes output to channels.
     */
    public CompositeNode<I, O> writeTo(String channel) {
        this.writeTargets.put(channel, null);
        return this;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Set<String> getSubscribedChannels() {
        return Collections.unmodifiableSet(subscribedChannels);
    }

    @Override
    public Map<String, Function<O, ?>> getWriteTargets() {
        return Collections.unmodifiableMap(writeTargets);
    }

    @Override
    public NodeMetadata getMetadata() {
        return metadata;
    }

    @Override
    @SuppressWarnings("unchecked")
    public O invoke(I input) throws Exception {
        if (stages.isEmpty()) {
            throw new IllegalStateException("CompositeNode has no stages");
        }

        Object current = input;

        for (Node<?, ?> stage : stages) {
            current = ((Node<Object, Object>) stage).invoke(current);
        }

        return (O) current;
    }

    @Override
    @SuppressWarnings("unchecked")
    public CompletableFuture<O> invokeAsync(I input) {
        if (stages.isEmpty()) {
            return CompletableFuture.failedFuture(
                new IllegalStateException("CompositeNode has no stages")
            );
        }

        CompletableFuture<Object> future = CompletableFuture.completedFuture((Object) input);

        for (Node<?, ?> stage : stages) {
            Node<Object, Object> typedStage = (Node<Object, Object>) stage;
            future = future.thenCompose(typedStage::invokeAsync);
        }

        return future.thenApply(result -> (O) result);
    }

    /**
     * Gets all stages in this composite.
     *
     * @return immutable list of stages
     */
    public List<Node<?, ?>> getStages() {
        return Collections.unmodifiableList(stages);
    }

    /**
     * Gets the number of stages.
     *
     * @return stage count
     */
    public int getStageCount() {
        return stages.size();
    }
}
