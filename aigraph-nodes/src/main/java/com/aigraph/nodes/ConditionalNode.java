package com.aigraph.nodes;

import com.aigraph.core.utils.ValidationUtils;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * A node that routes execution based on a condition.
 * <p>
 * Evaluates a predicate and executes either the true or false branch.
 * <p>
 * Example:
 * <pre>{@code
 * ConditionalNode<Integer, String> node = new ConditionalNode<>(
 *     "check-positive",
 *     n -> n > 0,
 *     positiveNode,
 *     negativeNode
 * );
 * }</pre>
 *
 * @param <I> input type
 * @param <O> output type
 * @author AIGraph Team
 * @since 0.0.8
 */
public class ConditionalNode<I, O> implements Node<I, O> {

    private final String name;
    private final Predicate<I> condition;
    private final Node<I, O> trueNode;
    private final Node<I, O> falseNode;
    private final Set<String> subscribedChannels;
    private final Map<String, Function<O, ?>> writeTargets;
    private final NodeMetadata metadata;

    /**
     * Creates a conditional node.
     *
     * @param name      the node name
     * @param condition the condition to evaluate
     * @param trueNode  the node to execute if condition is true
     * @param falseNode the node to execute if condition is false
     */
    public ConditionalNode(String name, Predicate<I> condition,
                           Node<I, O> trueNode, Node<I, O> falseNode) {
        this.name = ValidationUtils.requireNonBlank(name, "name");
        this.condition = ValidationUtils.requireNonNull(condition, "condition");
        this.trueNode = ValidationUtils.requireNonNull(trueNode, "trueNode");
        this.falseNode = ValidationUtils.requireNonNull(falseNode, "falseNode");
        this.subscribedChannels = new LinkedHashSet<>();
        this.writeTargets = new LinkedHashMap<>();
        this.metadata = NodeMetadata.of(name);
    }

    /**
     * Subscribes to channels.
     */
    public ConditionalNode<I, O> subscribeTo(String... channels) {
        this.subscribedChannels.addAll(Arrays.asList(channels));
        return this;
    }

    /**
     * Writes output to channels.
     */
    public ConditionalNode<I, O> writeTo(String channel) {
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
    public O invoke(I input) throws Exception {
        boolean conditionResult = condition.test(input);
        Node<I, O> selectedNode = conditionResult ? trueNode : falseNode;
        return selectedNode.invoke(input);
    }

    @Override
    public CompletableFuture<O> invokeAsync(I input) {
        boolean conditionResult = condition.test(input);
        Node<I, O> selectedNode = conditionResult ? trueNode : falseNode;
        return selectedNode.invokeAsync(input);
    }

    /**
     * Gets the condition predicate.
     *
     * @return the condition
     */
    public Predicate<I> getCondition() {
        return condition;
    }

    /**
     * Gets the true branch node.
     *
     * @return the true node
     */
    public Node<I, O> getTrueNode() {
        return trueNode;
    }

    /**
     * Gets the false branch node.
     *
     * @return the false node
     */
    public Node<I, O> getFalseNode() {
        return falseNode;
    }
}
