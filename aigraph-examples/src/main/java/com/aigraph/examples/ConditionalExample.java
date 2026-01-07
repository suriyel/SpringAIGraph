package com.aigraph.examples;

import com.aigraph.channels.LastValueChannel;
import com.aigraph.graph.Graph;
import com.aigraph.graph.GraphBuilder;
import com.aigraph.nodes.ConditionalNode;
import com.aigraph.nodes.Node;
import com.aigraph.nodes.NodeBuilder;
import com.aigraph.pregel.Pregel;

/**
 * Example: Conditional routing based on input value
 * <p>
 * If input > 0: route to positive branch
 * Else: route to negative branch
 */
public class ConditionalExample {

    public static void main(String[] args) {
        // Positive branch
        Node<Integer, String> positiveNode = NodeBuilder.<Integer, String>create("positive")
            .subscribeOnly("dummy") // Will be called via conditional
            .process(n -> "Positive: " + n)
            .writeTo("output")
            .build();

        // Negative branch
        Node<Integer, String> negativeNode = NodeBuilder.<Integer, String>create("negative")
            .subscribeOnly("dummy") // Will be called via conditional
            .process(n -> "Negative or Zero: " + n)
            .writeTo("output")
            .build();

        // Conditional router
        ConditionalNode<Integer, String> router = new ConditionalNode<>(
            "router",
            n -> n > 0,
            positiveNode,
            negativeNode
        );
        router.subscribeTo("input");
        router.writeTo("output");

        // Build graph
        Graph<Integer, String> graph = GraphBuilder.<Integer, String>create()
            .name("conditional-example")
            .addNode("router", router)
            .addChannel("input", new LastValueChannel<>("input", Integer.class))
            .addChannel("output", new LastValueChannel<>("output", String.class))
            .setInput("input")
            .setOutput("output")
            .build();

        // Execute with positive value
        Pregel<Integer, String> pregel = graph.compile();
        System.out.println("Test 1: " + pregel.invoke(5));

        // Execute with negative value
        System.out.println("Test 2: " + pregel.invoke(-3));

        // Execute with zero
        System.out.println("Test 3: " + pregel.invoke(0));
    }
}
