package com.aigraph.examples;

import com.aigraph.channels.LastValueChannel;
import com.aigraph.graph.Graph;
import com.aigraph.graph.GraphBuilder;
import com.aigraph.nodes.Node;
import com.aigraph.nodes.NodeBuilder;
import com.aigraph.pregel.ExecutionStep;
import com.aigraph.pregel.Pregel;
import com.aigraph.pregel.PregelConfig;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Example: Reactive Programming with Project Reactor
 * <p>
 * This example demonstrates:
 * <ul>
 *   <li>Using Mono for reactive single-value results</li>
 *   <li>Using Flux for reactive stream of execution steps</li>
 *   <li>Backpressure and cancellation</li>
 *   <li>Reactive operators (map, filter, subscribe)</li>
 * </ul>
 */
public class ReactiveProgrammingExample {

    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== Reactive Programming Example ===\n");

        // Example 1: Mono - Single Value Reactive Execution
        monoExample();

        System.out.println("\n" + "=".repeat(60) + "\n");

        // Example 2: Flux - Streaming Execution Steps
        fluxStreamingExample();

        System.out.println("\n" + "=".repeat(60) + "\n");

        // Example 3: Reactive Operators
        reactiveOperatorsExample();

        System.out.println("\n" + "=".repeat(60) + "\n");

        // Example 4: Backpressure and Cancellation
        backpressureExample();

        // Wait a bit for async operations to complete
        Thread.sleep(2000);
    }

    /**
     * Example 1: Using Mono for reactive single-value execution
     */
    private static void monoExample() {
        System.out.println("Example 1: Mono - Reactive Single Value");
        System.out.println("-".repeat(50));

        // Create a simple graph
        Node<Integer, Integer> multiplyNode = NodeBuilder.<Integer, Integer>create("multiply")
            .subscribeOnly("input")
            .process(n -> n * 2)
            .writeTo("output")
            .build();

        Graph<Integer, Integer> graph = GraphBuilder.<Integer, Integer>create()
            .name("reactive-mono-graph")
            .addNode("multiply", multiplyNode)
            .addChannel("input", new LastValueChannel<>("input", Integer.class))
            .addChannel("output", new LastValueChannel<>("output", Integer.class))
            .setInput("input")
            .setOutput("output")
            .build();

        Pregel<Integer, Integer> pregel = graph.compile();

        // Execute reactively with Mono
        System.out.println("Executing reactively with Mono...");

        Mono<Integer> resultMono = pregel.invokeReactive(21);

        // Subscribe and handle result
        resultMono.subscribe(
            result -> System.out.println("✓ Result: " + result),
            error -> System.err.println("✗ Error: " + error.getMessage()),
            () -> System.out.println("✓ Execution completed")
        );

        // Chain operations
        System.out.println("\nChaining Mono operations:");
        pregel.invokeReactive(10)
            .map(result -> "Result is: " + result)
            .doOnSuccess(msg -> System.out.println("  " + msg))
            .flatMap(msg -> Mono.just(msg.toUpperCase()))
            .subscribe(finalResult -> System.out.println("  Final: " + finalResult));

        // Block and get result (for demo purposes)
        Integer blockingResult = pregel.invokeReactive(5).block();
        System.out.println("\nBlocking result: " + blockingResult);
    }

    /**
     * Example 2: Using Flux to stream execution steps
     */
    private static void fluxStreamingExample() throws InterruptedException {
        System.out.println("Example 2: Flux - Streaming Execution Steps");
        System.out.println("-".repeat(50));

        // Create a multi-step graph
        Node<Integer, Integer> stepNode = NodeBuilder.<Integer, Integer>create("stepper")
            .subscribeOnly("value")
            .process(n -> {
                System.out.println("  Processing: " + n);
                return n + 1;
            })
            .writeTo("value", n -> n < 10 ? n : null) // Stop at 10
            .build();

        Graph<Integer, Integer> graph = GraphBuilder.<Integer, Integer>create()
            .name("reactive-flux-graph")
            .addNode("stepper", stepNode)
            .setInput("value")
            .setOutput("value")
            .build();

        PregelConfig config = PregelConfig.builder()
            .maxSteps(20)
            .build();

        Pregel<Integer, Integer> pregel = graph.compile(config);

        System.out.println("Streaming execution steps reactively...\n");

        CountDownLatch latch = new CountDownLatch(1);

        // Stream execution steps as Flux
        pregel.streamReactive(1)
            .doOnNext(step -> {
                System.out.printf("Step %d: nodes=%s, channels=%s, duration=%dms%n",
                    step.stepNumber(),
                    step.executedNodes(),
                    step.updatedChannels(),
                    step.duration().toMillis());
            })
            .doOnComplete(() -> {
                System.out.println("\n✓ Stream completed");
                latch.countDown();
            })
            .doOnError(error -> {
                System.err.println("✗ Stream error: " + error.getMessage());
                latch.countDown();
            })
            .subscribe();

        // Wait for completion
        latch.await();
    }

    /**
     * Example 3: Using reactive operators
     */
    private static void reactiveOperatorsExample() throws InterruptedException {
        System.out.println("Example 3: Reactive Operators");
        System.out.println("-".repeat(50));

        Node<String, String> processNode = NodeBuilder.<String, String>create("processor")
            .subscribeOnly("input")
            .process(String::toUpperCase)
            .writeTo("output")
            .build();

        Graph<String, String> graph = GraphBuilder.<String, String>create()
            .name("reactive-operators-graph")
            .addNode("processor", processNode)
            .setInput("input")
            .setOutput("output")
            .build();

        Pregel<String, String> pregel = graph.compile();

        System.out.println("Demonstrating reactive operators:\n");

        CountDownLatch latch = new CountDownLatch(1);

        // Create a Flux of inputs
        Flux.just("hello", "world", "reactive", "programming")
            .flatMap(input -> {
                System.out.println("  Processing: " + input);
                return pregel.invokeReactive(input);
            })
            .map(result -> result + "!")
            .filter(result -> result.length() > 8)
            .collectList()
            .subscribe(
                results -> {
                    System.out.println("\nFiltered results (length > 8):");
                    results.forEach(r -> System.out.println("  - " + r));
                    latch.countDown();
                },
                error -> {
                    System.err.println("Error: " + error.getMessage());
                    latch.countDown();
                }
            );

        latch.await();
    }

    /**
     * Example 4: Backpressure and cancellation
     */
    private static void backpressureExample() throws InterruptedException {
        System.out.println("Example 4: Backpressure and Cancellation");
        System.out.println("-".repeat(50));

        Node<Integer, Integer> slowNode = NodeBuilder.<Integer, Integer>create("slow-processor")
            .subscribeOnly("value")
            .process(n -> {
                // Simulate slow processing
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return n + 1;
            })
            .writeTo("value", n -> n < 100 ? n : null)
            .build();

        Graph<Integer, Integer> graph = GraphBuilder.<Integer, Integer>create()
            .name("backpressure-graph")
            .addNode("slow-processor", slowNode)
            .setInput("value")
            .setOutput("value")
            .build();

        Pregel<Integer, Integer> pregel = graph.compile();

        System.out.println("Demonstrating cancellation after 5 steps:\n");

        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger stepCount = new AtomicInteger(0);

        pregel.streamReactive(1)
            .doOnNext(step -> {
                int count = stepCount.incrementAndGet();
                System.out.println("Step " + count + ": " + step.executedNodes());
            })
            .take(5) // Take only first 5 steps (cancellation)
            .doOnComplete(() -> {
                System.out.println("\n✓ Cancelled after 5 steps");
                latch.countDown();
            })
            .subscribe();

        latch.await();

        System.out.println("\nDemonstrating timeout:");

        CountDownLatch timeoutLatch = new CountDownLatch(1);

        pregel.streamReactive(1)
            .timeout(Duration.ofMillis(500)) // Timeout after 500ms
            .doOnNext(step -> System.out.println("  Step: " + step.stepNumber()))
            .doOnError(error -> {
                System.out.println("  ✗ Timed out: " + error.getClass().getSimpleName());
                timeoutLatch.countDown();
            })
            .subscribe(
                step -> {},
                error -> {},
                timeoutLatch::countDown
            );

        timeoutLatch.await();
    }
}
