package com.aigraph.examples;

import com.aigraph.channels.LastValueChannel;
import com.aigraph.graph.Graph;
import com.aigraph.graph.GraphBuilder;
import com.aigraph.nodes.Node;
import com.aigraph.nodes.NodeBuilder;
import com.aigraph.pregel.ExecutionStep;
import com.aigraph.pregel.Pregel;
import com.aigraph.pregel.PregelConfig;

import java.util.stream.Stream;

/**
 * Example: Using stream() to monitor step-by-step execution
 * <p>
 * This example demonstrates how to use the stream API to observe
 * each execution step in the Pregel BSP cycle, including:
 * - Which nodes were executed
 * - Which channels were updated
 * - Channel value snapshots
 * - Execution timing information
 */
public class StreamExecutionExample {

    public static void main(String[] args) {
        System.out.println("=== Stream Execution Example ===\n");

        // Example 1: Simple multi-step pipeline
        simpleStreamExample();

        System.out.println("\n" + "=".repeat(50) + "\n");

        // Example 2: Loop with stream monitoring
        loopStreamExample();

        System.out.println("\n" + "=".repeat(50) + "\n");

        // Example 3: Parallel execution with stream
        parallelStreamExample();
    }

    /**
     * Example 1: Simple multi-step pipeline
     * Shows basic stream usage with sequential node execution
     */
    private static void simpleStreamExample() {
        System.out.println("Example 1: Simple Multi-Step Pipeline");
        System.out.println("-".repeat(40));

        // Create a pipeline: input -> step1 -> step2 -> output
        Node<Integer, Integer> step1 = NodeBuilder.<Integer, Integer>create("step1")
            .subscribeOnly("input")
            .process(n -> n * 2)
            .writeTo("intermediate")
            .build();

        Node<Integer, Integer> step2 = NodeBuilder.<Integer, Integer>create("step2")
            .subscribeOnly("intermediate")
            .process(n -> n + 10)
            .writeTo("output")
            .build();

        Graph<Integer, Integer> graph = GraphBuilder.<Integer, Integer>create()
            .name("simple-stream")
            .addNode("step1", step1)
            .addNode("step2", step2)
            .addChannel("input", new LastValueChannel<>("input", Integer.class))
            .addChannel("intermediate", new LastValueChannel<>("intermediate", Integer.class))
            .addChannel("output", new LastValueChannel<>("output", Integer.class))
            .setInput("input")
            .setOutput("output")
            .build();

        Pregel<Integer, Integer> pregel = graph.compile();

        // Use stream to observe execution steps
        System.out.println("Input: 5");
        Stream<ExecutionStep> steps = pregel.stream(5);

        steps.forEach(step -> {
            System.out.println("\nStep " + step.stepNumber() + ":");
            System.out.println("  Executed Nodes: " + step.executedNodes());
            System.out.println("  Updated Channels: " + step.updatedChannels());
            System.out.println("  Duration: " + step.duration().toMillis() + "ms");
            if (!step.channelSnapshots().isEmpty()) {
                System.out.println("  Channel Values: " + step.channelSnapshots());
            }
        });

        System.out.println("\nFinal Output: " + pregel.invoke(5));
    }

    /**
     * Example 2: Loop with stream monitoring
     * Shows how stream captures multiple iterations in a loop
     */
    private static void loopStreamExample() {
        System.out.println("Example 2: Loop with Stream Monitoring");
        System.out.println("-".repeat(40));

        // Create a loop that doubles a value until it exceeds 100
        Node<Integer, Integer> doubleNode = NodeBuilder.<Integer, Integer>create("doubler")
            .subscribeOnly("value")
            .process(n -> n * 2)
            .writeTo("value", n -> n <= 100 ? n : null) // Conditional write
            .build();

        Graph<Integer, Integer> graph = GraphBuilder.<Integer, Integer>create()
            .name("loop-stream")
            .addNode("doubler", doubleNode)
            .addChannel("value", new LastValueChannel<>("value", Integer.class))
            .setInput("value")
            .setOutput("value")
            .build();

        PregelConfig config = PregelConfig.builder()
            .maxSteps(10)
            .debug(true)
            .build();

        Pregel<Integer, Integer> pregel = graph.compile(config);

        // Monitor the loop execution
        System.out.println("Input: 3 (will double until > 100)");
        Stream<ExecutionStep> steps = pregel.stream(3);

        int[] stepCount = {0};
        steps.forEach(step -> {
            stepCount[0]++;
            System.out.printf("\nIteration %d (Step %d):%n",
                stepCount[0], step.stepNumber());
            System.out.println("  Executed: " + step.executedNodes());
            System.out.println("  Updated: " + step.updatedChannels());

            // Show the value at each iteration
            if (step.channelSnapshots().containsKey("value")) {
                System.out.println("  Current Value: " +
                    step.channelSnapshots().get("value"));
            }
        });

        System.out.println("\nFinal Output: " + pregel.invoke(3));
        System.out.println("Total Iterations: " + stepCount[0]);
    }

    /**
     * Example 3: Parallel execution with stream
     * Shows how stream captures parallel node execution
     */
    private static void parallelStreamExample() {
        System.out.println("Example 3: Parallel Execution with Stream");
        System.out.println("-".repeat(40));

        // Create parallel branches
        Node<String, String> upper = NodeBuilder.<String, String>create("uppercase")
            .subscribeOnly("input")
            .process(String::toUpperCase)
            .writeTo("upper")
            .build();

        Node<String, String> lower = NodeBuilder.<String, String>create("lowercase")
            .subscribeOnly("input")
            .process(String::toLowerCase)
            .writeTo("lower")
            .build();

        Node<String, Integer> length = NodeBuilder.<String, Integer>create("length")
            .subscribeOnly("input")
            .process(String::length)
            .writeTo("len")
            .build();

        Graph<String, String> graph = GraphBuilder.<String, String>create()
            .name("parallel-stream")
            .addNode("uppercase", upper)
            .addNode("lowercase", lower)
            .addNode("length", length)
            .addChannel("input", new LastValueChannel<>("input", String.class))
            .addChannel("upper", new LastValueChannel<>("upper", String.class))
            .addChannel("lower", new LastValueChannel<>("lower", String.class))
            .addChannel("len", new LastValueChannel<>("len", Integer.class))
            .setInput("input")
            .setOutput("upper")
            .build();

        Pregel<String, String> pregel = graph.compile();

        // Observe parallel execution
        System.out.println("Input: \"Hello World\"");
        Stream<ExecutionStep> steps = pregel.stream("Hello World");

        steps.forEach(step -> {
            System.out.println("\nStep " + step.stepNumber() + ":");
            System.out.println("  Executed Nodes (parallel): " + step.executedNodes());
            System.out.println("  Node Count: " + step.executedNodes().size());
            System.out.println("  Updated Channels: " + step.updatedChannels());
            System.out.println("  Duration: " + step.duration().toMillis() + "ms");

            // Show all channel values after this step
            if (!step.channelSnapshots().isEmpty()) {
                System.out.println("  Results:");
                step.channelSnapshots().forEach((channel, value) ->
                    System.out.println("    " + channel + " = " + value)
                );
            }
        });
    }
}
