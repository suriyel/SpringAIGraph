package com.aigraph.examples;

import com.aigraph.channels.LastValueChannel;
import com.aigraph.graph.Graph;
import com.aigraph.graph.GraphBuilder;
import com.aigraph.nodes.Node;
import com.aigraph.nodes.NodeBuilder;
import com.aigraph.pregel.Pregel;
import com.aigraph.pregel.PregelConfig;

/**
 * Example: Loop - value grows until it reaches threshold
 * <p>
 * Flow: input -> grow (doubles value) -> value (loops back if < 10)
 */
public class LoopExample {

    public static void main(String[] args) {
        // Node that doubles the value
        Node<Integer, Integer> growNode = NodeBuilder.<Integer, Integer>create("grow")
            .subscribeOnly("value")
            .process(n -> n * 2)
            .writeTo("value", n -> n < 10 ? n : null) // Conditional write
            .build();

        // Build graph with loop
        Graph<Integer, Integer> graph = GraphBuilder.<Integer, Integer>create()
            .name("loop-example")
            .addNode("grow", growNode)
            .addChannel("value", new LastValueChannel<>("value", Integer.class))
            .setInput("value")
            .setOutput("value")
            .build();

        // Compile with max steps to prevent infinite loop
        Pregel<Integer, Integer> pregel = graph.compile(
            PregelConfig.builder()
                .maxSteps(20)
                .debug(true)
                .build()
        );

        // Execute
        int input = 1;
        Integer result = pregel.invoke(input);

        System.out.println("Input: " + input);
        System.out.println("Output: " + result);
        System.out.println("Expected: 8 (1 -> 2 -> 4 -> 8, stops because 16 >= 10)");
    }
}
