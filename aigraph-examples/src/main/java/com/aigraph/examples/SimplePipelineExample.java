package com.aigraph.examples;

import com.aigraph.channels.LastValueChannel;
import com.aigraph.graph.Graph;
import com.aigraph.graph.GraphBuilder;
import com.aigraph.nodes.Node;
import com.aigraph.nodes.NodeBuilder;
import com.aigraph.pregel.Pregel;

/**
 * Simple pipeline example: input -> uppercase -> output
 */
public class SimplePipelineExample {

    public static void main(String[] args) {
        // Create a node that converts input to uppercase
        Node<String, String> uppercaseNode = NodeBuilder.<String, String>create("uppercase")
            .subscribeOnly("input")
            .process(String::toUpperCase)
            .writeTo("output")
            .build();

        // Build the graph
        Graph<String, String> graph = GraphBuilder.<String, String>create()
            .name("simple-pipeline")
            .addNode("uppercase", uppercaseNode)
            .addChannel("input", new LastValueChannel<>("input", String.class))
            .addChannel("output", new LastValueChannel<>("output", String.class))
            .setInput("input")
            .setOutput("output")
            .build();

        // Compile and execute
        Pregel<String, String> pregel = graph.compile();
        String result = pregel.invoke("hello world");

        System.out.println("Input: hello world");
        System.out.println("Output: " + result);
    }
}
