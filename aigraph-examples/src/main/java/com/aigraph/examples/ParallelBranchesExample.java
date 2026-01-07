package com.aigraph.examples;

import com.aigraph.channels.LastValueChannel;
import com.aigraph.graph.Graph;
import com.aigraph.graph.GraphBuilder;
import com.aigraph.nodes.Node;
import com.aigraph.nodes.NodeBuilder;
import com.aigraph.pregel.Pregel;

import java.util.Map;

/**
 * Example: Parallel branches that merge
 * <p>
 * Flow:
 * input -> branchA -> resultA --|
 *       -> branchB -> resultB --|--> merge -> output
 */
public class ParallelBranchesExample {

    public static void main(String[] args) {
        // Branch A: prefix with "A:"
        Node<String, String> branchA = NodeBuilder.<String, String>create("branchA")
            .subscribeOnly("input")
            .process(s -> "A:" + s.toUpperCase())
            .writeTo("resultA")
            .build();

        // Branch B: prefix with "B:"
        Node<String, String> branchB = NodeBuilder.<String, String>create("branchB")
            .subscribeOnly("input")
            .process(s -> "B:" + s.toLowerCase())
            .writeTo("resultB")
            .build();

        // Merge: combines both results
        Node<Map<String, String>, String> merge = NodeBuilder
            .<Map<String, String>, String>create("merge")
            .subscribeTo("resultA", "resultB")
            .process(inputs -> String.format("%s | %s",
                inputs.getOrDefault("resultA", ""),
                inputs.getOrDefault("resultB", "")))
            .writeTo("output")
            .build();

        // Build graph
        Graph<String, String> graph = GraphBuilder.<String, String>create()
            .name("parallel-branches")
            .addNode("branchA", branchA)
            .addNode("branchB", branchB)
            .addNode("merge", merge)
            .addChannel("input", new LastValueChannel<>("input", String.class))
            .addChannel("resultA", new LastValueChannel<>("resultA", String.class))
            .addChannel("resultB", new LastValueChannel<>("resultB", String.class))
            .addChannel("output", new LastValueChannel<>("output", String.class))
            .setInput("input")
            .setOutput("output")
            .build();

        // Execute
        Pregel<String, String> pregel = graph.compile();
        String result = pregel.invoke("Hello");

        System.out.println("Input: Hello");
        System.out.println("Output: " + result);
        System.out.println("Expected: A:HELLO | B:hello");
    }
}
