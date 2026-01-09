package com.aigraph.examples;

import com.aigraph.channels.LastValueChannel;
import com.aigraph.checkpoint.memory.MemoryCheckpointer;
import com.aigraph.graph.Graph;
import com.aigraph.graph.GraphBuilder;
import com.aigraph.nodes.Node;
import com.aigraph.nodes.NodeBuilder;
import com.aigraph.pregel.*;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

/**
 * Complete Spring Boot Integration Example
 * <p>
 * This example demonstrates a complete AI agent workflow using all v0.0.9 features:
 * <ul>
 *   <li>Context-aware nodes with message history</li>
 *   <li>Reactive execution with Mono/Flux</li>
 *   <li>Checkpoint and resume</li>
 *   <li>Multi-step AI processing</li>
 * </ul>
 * <p>
 * This simulates a real-world conversational AI agent that:
 * - Maintains conversation state
 * - Processes user input with context
 * - Supports interruption and resume
 * - Provides reactive streaming
 */
public class SpringBootIntegrationExample {

    public static void main(String[] args) throws Exception {
        System.out.println("=== Spring Boot Complete Integration Example ===\n");

        // Simulate a complete AI agent workflow
        completeAIAgentWorkflow();
    }

    private static void completeAIAgentWorkflow() throws Exception {
        System.out.println("Building AI Agent with Full Feature Integration");
        System.out.println("=".repeat(60));

        // Step 1: Build the AI Agent Graph
        Graph<String, String> aiAgent = buildAIAgentGraph();

        // Step 2: Configure with checkpoint support
        PregelConfig config = PregelConfig.builder()
                .maxSteps(50)
                .timeout(Duration.ofMinutes(5))
                .debug(true)
                .build();

        Pregel<String, String> pregel = aiAgent.compile(config);
        pregel.setCheckpointer(new MemoryCheckpointer());

        // Step 3: Simulate conversation with reactive streaming
        System.out.println("\n" + "=".repeat(60));
        System.out.println("Phase 1: Reactive Conversation Streaming");
        System.out.println("=".repeat(60));

        reactiveConversation(pregel);

        // Step 4: Demonstrate checkpoint and resume
        System.out.println("\n" + "=".repeat(60));
        System.out.println("Phase 2: Checkpoint and Resume");
        System.out.println("=".repeat(60));

        checkpointAndResume(pregel);

        // Step 5: Parallel processing with reactive
        System.out.println("\n" + "=".repeat(60));
        System.out.println("Phase 3: Parallel Reactive Processing");
        System.out.println("=".repeat(60));

        parallelReactiveProcessing(pregel);

        System.out.println("\n" + "=".repeat(60));
        System.out.println("✅ Complete Integration Demo Finished!");
        System.out.println("=".repeat(60));
    }

    /**
     * Builds a complete AI agent graph with multiple context-aware nodes.
     *
     * Key design decisions:
     * - Each node subscribes to only ONE channel to receive direct values
     * - Use alsoRead() for additional context without changing input type
     * - This keeps the code simple and type-safe
     */
    private static Graph<String, String> buildAIAgentGraph() {
        System.out.println("\n1. Building AI Agent Graph...");

        // Node 1: Intent Classifier (context-aware)
        // Subscribes to user-input only - receives String directly
        Node<String, String> intentClassifier = NodeBuilder.<String, String>create("intent-classifier")
                .subscribeOnly("user-input")
                .processWithContext((input, ctx) -> {
                    ExecutionContext execCtx = (ExecutionContext) ctx;
                    MessageContext msgCtx = execCtx.getMessageContext();

                    System.out.println("  [Intent Classifier] Processing: " + input);
                    System.out.println("    History: " + msgCtx.size() + " messages");

                    // Simple intent classification
                    String intent;
                    if (input.toLowerCase().contains("weather")) {
                        intent = "WEATHER_QUERY";
                    } else if (input.toLowerCase().contains("hello") || input.toLowerCase().contains("hi")) {
                        intent = "GREETING";
                    } else if (input.toLowerCase().contains("help")) {
                        intent = "HELP_REQUEST";
                    } else {
                        intent = "GENERAL_QUERY";
                    }

                    System.out.println("    Detected: " + intent);
                    return intent;
                })
                .writeTo("intent")
                .build();

        // Node 2: Context Enricher (context-aware)
        // Subscribes to intent channel, reads user-input for context
        Node<String, String> contextEnricher = NodeBuilder.<String, String>create("context-enricher")
                .subscribeOnly("intent")
                .alsoRead("user-input")
                .processWithContext((intent, ctx) -> {
                    ExecutionContext execCtx = (ExecutionContext) ctx;
                    MessageContext msgCtx = execCtx.getMessageContext();

                    System.out.println("  [Context Enricher] Enriching context for intent: " + intent);

                    // Note: When using alsoRead, the input is still a single value (intent)
                    // The read channels are available through ChannelManager if needed

                    // Add current interaction to message context
                    MessageContext enriched = msgCtx
                            .addMessage("system", "Intent classified: " + intent)
                            .withMetadata("lastIntent", intent)
                            .withMetadata("timestamp", System.currentTimeMillis());

                    System.out.println("    Enriched context: " + enriched.size() + " messages");

                    return "Enriched[" + intent + "]";
                })
                .writeTo("enriched-context")
                .build();

        // Node 3: Response Generator (context-aware)
        // Subscribes to enriched-context, reads user-input for original message
        Node<String, String> responseGenerator = NodeBuilder.<String, String>create("response-generator")
                .subscribeOnly("enriched-context")
                .alsoRead("user-input", "intent")
                .processWithContext((enrichedData, ctx) -> {
                    ExecutionContext execCtx = (ExecutionContext) ctx;
                    MessageContext msgCtx = execCtx.getMessageContext();

                    System.out.println("  [Response Generator] Generating response");
                    System.out.println("    Enriched data: " + enrichedData);
                    System.out.println("    Context size: " + msgCtx.size());

                    // Extract intent from enriched data (format: "Enriched[INTENT]")
                    String intent = "GENERAL_QUERY";
                    if (enrichedData != null && enrichedData.startsWith("Enriched[")) {
                        intent = enrichedData.substring(9, enrichedData.length() - 1);
                    }

                    // Generate context-aware response
                    String response;
                    switch (intent) {
                        case "WEATHER_QUERY":
                            response = "Based on our conversation, the weather is sunny!";
                            break;
                        case "GREETING":
                            if (msgCtx.size() > 5) {
                                response = "Hello again! We've been chatting for a while.";
                            } else {
                                response = "Hello! Nice to meet you.";
                            }
                            break;
                        case "HELP_REQUEST":
                            response = "I can help you with weather, greetings, and general questions.";
                            break;
                        default:
                            response = "That's an interesting question. Let me think about it...";
                    }

                    System.out.println("    Generated: " + response);
                    return response;
                })
                .writeTo("response")
                .build();

        // Build the graph with explicit channel definitions
        Graph<String, String> graph = GraphBuilder.<String, String>create()
                .name("ai-agent-complete")
                .addNode("intent-classifier", intentClassifier)
                .addNode("context-enricher", contextEnricher)
                .addNode("response-generator", responseGenerator)
                .addChannel("user-input", new LastValueChannel<>("user-input", String.class))
                .addChannel("intent", new LastValueChannel<>("intent", String.class))
                .addChannel("enriched-context", new LastValueChannel<>("enriched-context", String.class))
                .addChannel("response", new LastValueChannel<>("response", String.class))
                .setInput("user-input")
                .setOutput("response")
                .build();

        System.out.println("✓ AI Agent Graph Built Successfully");
        return graph;
    }

    /**
     * Demonstrates reactive conversation streaming
     */
    private static void reactiveConversation(Pregel<String, String> pregel) throws Exception {
        String[] userInputs = {
                "Hello there!",
                "What's the weather like?",
                "I need some help",
                "Tell me something interesting"
        };

        CountDownLatch latch = new CountDownLatch(userInputs.length);

        System.out.println("\nStarting reactive conversation...\n");

        // Process each input reactively
        for (int i = 0; i < userInputs.length; i++) {
            final int index = i;
            final String input = userInputs[i];

            System.out.println("User [" + (index + 1) + "]: " + input);

            pregel.invokeReactive(input)
                    .doOnSuccess(response -> {
                        System.out.println("Agent [" + (index + 1) + "]: " + response);
                        System.out.println();
                    })
                    .doOnError(error -> {
                        System.err.println("Error: " + error.getMessage());
                    })
                    .doFinally(signal -> latch.countDown())
                    .subscribe();

            // Small delay between messages
            Thread.sleep(100);
        }

        latch.await();
        System.out.println("✓ Reactive conversation completed");
    }

    /**
     * Demonstrates checkpoint and resume functionality
     */
    private static void checkpointAndResume(Pregel<String, String> pregel) {
        System.out.println("\nDemonstrating checkpoint and resume...\n");

        // Initial execution
        System.out.println("1. Initial execution:");
        String result1 = pregel.invoke("Start conversation");
        System.out.println("   Result: " + result1);

        // Checkpoint would be automatically saved here
        System.out.println("\n2. Checkpoint saved automatically");

        // Continue execution
        System.out.println("\n3. Continue execution:");
        String result2 = pregel.invoke("Continue the conversation");
        System.out.println("   Result: " + result2);

        System.out.println("\n✓ Checkpoint functionality demonstrated");
        System.out.println("   (In production, use pregel.resumeFrom(threadId, checkpointId))");
    }

    /**
     * Demonstrates parallel reactive processing
     */
    private static void parallelReactiveProcessing(Pregel<String, String> pregel) throws Exception {
        System.out.println("\nProcessing multiple requests in parallel...\n");

        String[] parallelInputs = {
                "Hello",
                "What's the weather?",
                "Help me please",
                "General question 1",
                "General question 2"
        };

        CountDownLatch latch = new CountDownLatch(1);

        Flux.fromArray(parallelInputs)
                .flatMap(input -> {
                    System.out.println("→ Processing: " + input);
                    return pregel.invokeReactive(input)
                            .doOnSuccess(result ->
                                    System.out.println("← Completed: " + input + " → " + result)
                            );
                })
                .collectList()
                .doOnSuccess(results -> {
                    System.out.println("\n✓ Parallel processing completed");
                    System.out.println("  Total results: " + results.size());
                })
                .doFinally(signal -> latch.countDown())
                .subscribe();

        latch.await();
    }
}