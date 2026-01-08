package com.aigraph.examples;

import com.aigraph.channels.LastValueChannel;
import com.aigraph.channels.TopicChannel;
import com.aigraph.graph.Graph;
import com.aigraph.graph.GraphBuilder;
import com.aigraph.nodes.Node;
import com.aigraph.nodes.NodeBuilder;
import com.aigraph.pregel.ExecutionStep;
import com.aigraph.pregel.Pregel;
import com.aigraph.pregel.PregelConfig;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Example: Advanced stream monitoring with analytics
 * <p>
 * This example demonstrates practical use cases for stream monitoring:
 * - Real-time progress tracking
 * - Performance analysis
 * - Execution statistics
 * - Custom stream processing
 */
public class StreamMonitoringExample {

    public static void main(String[] args) {
        System.out.println("=== Stream Monitoring Example ===\n");

        // Example 1: Progress tracking
        progressTrackingExample();

        System.out.println("\n" + "=".repeat(60) + "\n");

        // Example 2: Performance analysis
        performanceAnalysisExample();

        System.out.println("\n" + "=".repeat(60) + "\n");

        // Example 3: Custom stream analytics
        customAnalyticsExample();
    }

    /**
     * Example 1: Progress Tracking
     * Shows how to create a progress bar using stream
     */
    private static void progressTrackingExample() {
        System.out.println("Example 1: Real-Time Progress Tracking");
        System.out.println("-".repeat(50));

        // Create a multi-step processing pipeline
        Node<Integer, Integer> step1 = NodeBuilder.<Integer, Integer>create("validate")
            .subscribeOnly("input")
            .process(n -> {
                simulateWork(100); // Simulate processing time
                return n;
            })
            .writeTo("validated")
            .build();

        Node<Integer, Integer> step2 = NodeBuilder.<Integer, Integer>create("process")
            .subscribeOnly("validated")
            .process(n -> {
                simulateWork(150);
                return n * 2;
            })
            .writeTo("processed")
            .build();

        Node<Integer, Integer> step3 = NodeBuilder.<Integer, Integer>create("finalize")
            .subscribeOnly("processed")
            .process(n -> {
                simulateWork(100);
                return n + 100;
            })
            .writeTo("output")
            .build();

        Graph<Integer, Integer> graph = GraphBuilder.<Integer, Integer>create()
            .name("progress-tracking")
            .addNode("validate", step1)
            .addNode("process", step2)
            .addNode("finalize", step3)
            .setInput("input")
            .setOutput("output")
            .build();

        PregelConfig config = PregelConfig.builder()
            .maxSteps(10)
            .build();

        Pregel<Integer, Integer> pregel = graph.compile(config);

        // Track progress using stream
        System.out.println("Processing input: 42\n");

        AtomicInteger completedSteps = new AtomicInteger(0);
        int totalSteps = 3; // Known number of steps in this pipeline

        pregel.stream(42).forEach(step -> {
            completedSteps.incrementAndGet();
            int progress = (completedSteps.get() * 100) / totalSteps;

            // Print progress bar
            String bar = "█".repeat(progress / 5) + "░".repeat(20 - progress / 5);
            System.out.printf("\r[%s] %d%% - Step %d: %s completed in %dms",
                bar, progress, step.stepNumber(),
                step.executedNodes(), step.duration().toMillis());
        });

        System.out.println("\n\n✓ Processing complete!");
        System.out.println("Result: " + pregel.invoke(42));
    }

    /**
     * Example 2: Performance Analysis
     * Analyzes execution performance metrics from stream
     */
    private static void performanceAnalysisExample() {
        System.out.println("Example 2: Performance Analysis");
        System.out.println("-".repeat(50));

        // Create a graph with varying execution times
        Node<Integer, Integer> fast = NodeBuilder.<Integer, Integer>create("fast-node")
            .subscribeOnly("input")
            .process(n -> {
                simulateWork(50);
                return n + 1;
            })
            .writeTo("fast-result")
            .build();

        Node<Integer, Integer> medium = NodeBuilder.<Integer, Integer>create("medium-node")
            .subscribeOnly("input")
            .process(n -> {
                simulateWork(150);
                return n + 2;
            })
            .writeTo("medium-result")
            .build();

        Node<Integer, Integer> slow = NodeBuilder.<Integer, Integer>create("slow-node")
            .subscribeOnly("input")
            .process(n -> {
                simulateWork(300);
                return n + 3;
            })
            .writeTo("slow-result")
            .build();

        Graph<Integer, Integer> graph = GraphBuilder.<Integer, Integer>create()
            .name("performance-analysis")
            .addNode("fast", fast)
            .addNode("medium", medium)
            .addNode("slow", slow)
            .setInput("input")
            .setOutput("fast-result")
            .build();

        Pregel<Integer, Integer> pregel = graph.compile();

        // Collect performance metrics
        System.out.println("Analyzing parallel execution performance...\n");

        List<ExecutionStep> steps = pregel.stream(10).collect(Collectors.toList());

        // Analyze the results
        System.out.println("Performance Report:");
        System.out.println("-".repeat(50));

        Duration totalDuration = steps.stream()
            .map(ExecutionStep::duration)
            .reduce(Duration.ZERO, Duration::plus);

        Duration avgDuration = Duration.ofMillis(
            totalDuration.toMillis() / Math.max(1, steps.size())
        );

        Duration maxDuration = steps.stream()
            .map(ExecutionStep::duration)
            .max(Duration::compareTo)
            .orElse(Duration.ZERO);

        Duration minDuration = steps.stream()
            .map(ExecutionStep::duration)
            .min(Duration::compareTo)
            .orElse(Duration.ZERO);

        System.out.println("Total Steps: " + steps.size());
        System.out.println("Total Execution Time: " + totalDuration.toMillis() + "ms");
        System.out.println("Average Step Duration: " + avgDuration.toMillis() + "ms");
        System.out.println("Min Step Duration: " + minDuration.toMillis() + "ms");
        System.out.println("Max Step Duration: " + maxDuration.toMillis() + "ms");

        System.out.println("\nStep-by-Step Breakdown:");
        steps.forEach(step -> {
            System.out.printf("  Step %d: %s (%dms)%n",
                step.stepNumber(),
                step.executedNodes(),
                step.duration().toMillis());
        });

        // Note about parallel execution
        if (!steps.isEmpty() && steps.get(0).executedNodes().size() > 1) {
            System.out.println("\n💡 Note: " + steps.get(0).executedNodes().size() +
                " nodes executed in parallel in step 0");
            System.out.println("   (Step duration reflects parallel execution, not sequential sum)");
        }
    }

    /**
     * Example 3: Custom Stream Analytics
     * Demonstrates custom processing and filtering of execution steps
     */
    private static void customAnalyticsExample() {
        System.out.println("Example 3: Custom Stream Analytics");
        System.out.println("-".repeat(50));

        // Create a loop with interesting behavior
        Node<Integer, Integer> multiply = NodeBuilder.<Integer, Integer>create("multiplier")
            .subscribeOnly("value")
            .process(n -> n * 3)
            .writeTo("value", n -> n < 1000 ? n : null)
            .build();

        Graph<Integer, Integer> graph = GraphBuilder.<Integer, Integer>create()
            .name("custom-analytics")
            .addNode("multiply", multiply)
            .setInput("value")
            .setOutput("value")
            .build();

        PregelConfig config = PregelConfig.builder()
            .maxSteps(20)
            .debug(true)
            .build();

        Pregel<Integer, Integer> pregel = graph.compile(config);

        System.out.println("Analyzing loop execution (multiply by 3 until >= 1000)...\n");

        // Custom analytics: Track value growth
        class Analytics {
            int totalSteps = 0;
            int totalNodeExecutions = 0;
            int channelUpdates = 0;
            Integer previousValue = null;

            void process(ExecutionStep step) {
                totalSteps++;
                totalNodeExecutions += step.executedNodes().size();
                channelUpdates += step.updatedChannels().size();

                Integer currentValue = (Integer) step.channelSnapshots().get("value");
                if (currentValue != null) {
                    if (previousValue != null) {
                        double growthRate = ((double) currentValue - previousValue) / previousValue * 100;
                        System.out.printf("Step %d: %d → %d (%.1f%% growth)%n",
                            step.stepNumber(), previousValue, currentValue, growthRate);
                    } else {
                        System.out.printf("Step %d: Initial value = %d%n",
                            step.stepNumber(), currentValue);
                    }
                    previousValue = currentValue;
                }
            }

            void printSummary() {
                System.out.println("\nExecution Summary:");
                System.out.println("-".repeat(50));
                System.out.println("Total Steps: " + totalSteps);
                System.out.println("Total Node Executions: " + totalNodeExecutions);
                System.out.println("Total Channel Updates: " + channelUpdates);
                System.out.println("Final Value: " + previousValue);
                if (previousValue != null && totalSteps > 0) {
                    double avgGrowth = Math.pow((double) previousValue / 2, 1.0 / (totalSteps - 1)) - 1;
                    System.out.printf("Average Growth Per Step: %.1f%%%n", avgGrowth * 100);
                }
            }
        }

        Analytics analytics = new Analytics();
        pregel.stream(2).forEach(analytics::process);
        analytics.printSummary();

        // Show filtering example
        System.out.println("\n" + "-".repeat(50));
        System.out.println("Filtering Example: Steps with channel updates only");
        System.out.println("-".repeat(50));

        long stepsWithUpdates = pregel.stream(2)
            .filter(step -> !step.updatedChannels().isEmpty())
            .peek(step -> System.out.println("  Step " + step.stepNumber() +
                " updated: " + step.updatedChannels()))
            .count();

        System.out.println("Total steps with updates: " + stepsWithUpdates);
    }

    /**
     * Simulates work by sleeping for the specified milliseconds
     */
    private static void simulateWork(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
