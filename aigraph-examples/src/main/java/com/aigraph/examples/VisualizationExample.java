package com.aigraph.examples;

import com.aigraph.channels.LastValueChannel;
import com.aigraph.graph.Graph;
import com.aigraph.graph.GraphBuilder;
import com.aigraph.graph.GraphVisualizer;
import com.aigraph.nodes.Node;
import com.aigraph.nodes.NodeBuilder;

/**
 * Example: Graph visualization
 * <p>
 * Demonstrates how to generate Mermaid, DOT, and ASCII visualizations.
 */
public class VisualizationExample {

    public static void main(String[] args) {
        // Create a simple pipeline
        Node<String, String> upperNode = NodeBuilder.<String, String>create("uppercase")
            .subscribeOnly("input")
            .process(String::toUpperCase)
            .writeTo("middle")
            .build();

        Node<String, String> reverseNode = NodeBuilder.<String, String>create("reverse")
            .subscribeOnly("middle")
            .process(s -> new StringBuilder(s).reverse().toString())
            .writeTo("output")
            .build();

        Graph<String, String> graph = GraphBuilder.<String, String>create()
            .name("visualization-demo")
            .addNode("uppercase", upperNode)
            .addNode("reverse", reverseNode)
            .addChannel("input", new LastValueChannel<>("input", String.class))
            .addChannel("middle", new LastValueChannel<>("middle", String.class))
            .addChannel("output", new LastValueChannel<>("output", String.class))
            .setInput("input")
            .setOutput("output")
            .build();

        GraphVisualizer visualizer = new GraphVisualizer();

        // Mermaid diagram
        System.out.println("=== Mermaid Diagram ===");
        System.out.println(visualizer.toMermaid(graph));

        // ASCII representation
        System.out.println("\n=== ASCII Representation ===");
        System.out.println(visualizer.toAscii(graph));

        // DOT format
        System.out.println("\n=== DOT Format ===");
        System.out.println(visualizer.toDot(graph));
    }
}
