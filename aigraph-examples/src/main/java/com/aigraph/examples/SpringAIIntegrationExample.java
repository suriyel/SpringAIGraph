package com.aigraph.examples;

import com.aigraph.channels.LastValueChannel;
import com.aigraph.graph.Graph;
import com.aigraph.graph.GraphBuilder;
import com.aigraph.nodes.Node;
import com.aigraph.nodes.NodeBuilder;
import com.aigraph.pregel.ExecutionContext;
import com.aigraph.pregel.MessageContext;
import com.aigraph.pregel.Pregel;
import com.aigraph.pregel.PregelConfig;

import java.util.List;
import java.util.Map;

/**
 * Example: Spring AI Integration with Message Context
 * <p>
 * This example demonstrates:
 * <ul>
 *   <li>Using MessageContext to store conversation history</li>
 *   <li>Creating context-aware nodes that access message history</li>
 *   <li>Passing execution context through the graph</li>
 *   <li>Building AI agent workflows with stateful conversations</li>
 * </ul>
 */
public class SpringAIIntegrationExample {

    public static void main(String[] args) {
        System.out.println("=== Spring AI Integration Example ===\n");

        // Example 1: Basic Message Context Usage
        basicMessageContextExample();

        System.out.println("\n" + "=".repeat(60) + "\n");

        // Example 2: Context-Aware Node with Message History
        contextAwareNodeExample();

        System.out.println("\n" + "=".repeat(60) + "\n");

        // Example 3: Multi-Step AI Agent Workflow
        multiStepAIAgentExample();
    }

    /**
     * Example 1: Basic Message Context Usage
     * Shows how to create and manipulate message context
     */
    private static void basicMessageContextExample() {
        System.out.println("Example 1: Basic Message Context Usage");
        System.out.println("-".repeat(50));

        // Create a message context
        MessageContext context = new MessageContext("conversation-001");

        // Add messages to the context
        context = context.addMessage("user", "Hello, how are you?");
        context = context.addMessage("assistant", "I'm doing well, thank you for asking!");
        context = context.addMessage("user", "What's the weather like?");

        // Add metadata
        context = context.withMetadata("userId", "user-123")
                .withMetadata("sessionStart", System.currentTimeMillis());

        // Display context information
        System.out.println("Conversation ID: " + context.getConversationId());
        System.out.println("Message Count: " + context.size());
        System.out.println("\nMessages:");
        context.getMessages().forEach(msg ->
                System.out.printf("  [%s]: %s%n", msg.getRole(), msg.getContent())
        );

        System.out.println("\nMetadata:");
        context.getAllMetadata().forEach((key, value) ->
                System.out.printf("  %s: %s%n", key, value)
        );

        // Get last message
        MessageContext.Message lastMessage = context.getLastMessage();
        System.out.println("\nLast Message: " + lastMessage);

        // Filter messages by role
        System.out.println("\nUser Messages:");
        context.getMessagesByRole("user").forEach(msg ->
                System.out.println("  - " + msg.getContent())
        );
    }

    /**
     * Example 2: Context-Aware Node with Message History
     * Shows how nodes can access execution context and message history
     */
    private static void contextAwareNodeExample() {
        System.out.println("Example 2: Context-Aware Node");
        System.out.println("-".repeat(50));

        // Create a context-aware node that accesses message history
        Node<String, String> contextAwareNode = NodeBuilder.<String, String>create("chat-processor")
                .subscribeOnly("input")
                .processWithContext((input, ctx) -> {
                    // Cast context to ExecutionContext
                    ExecutionContext execCtx = (ExecutionContext) ctx;
                    MessageContext msgCtx = execCtx.getMessageContext();

                    // Access message history
                    System.out.println("\n  Processing with context:");
                    System.out.println("  - Thread ID: " + execCtx.getThreadId());
                    System.out.println("  - Step Number: " + execCtx.getStepNumber());
                    System.out.println("  - Message Count: " + msgCtx.size());

                    // Build response based on conversation history
                    StringBuilder response = new StringBuilder("Response to: " + input);
                    if (!msgCtx.isEmpty()) {
                        response.append(" (considering ").append(msgCtx.size())
                                .append(" previous messages)");
                    }

                    return response.toString();
                })
                .writeTo("output")
                .build();

        // Build graph
        Graph<String, String> graph = GraphBuilder.<String, String>create()
                .name("context-aware-graph")
                .addNode("chat-processor", contextAwareNode)
                .addChannel("input", new LastValueChannel<>("input", String.class))
                .addChannel("output", new LastValueChannel<>("output", String.class))
                .setInput("input")
                .setOutput("output")
                .build();

        // Execute
        Pregel<String, String> pregel = graph.compile();
        String result = pregel.invoke("How does machine learning work?");

        System.out.println("\nResult: " + result);
    }

    /**
     * Example 3: Multi-Step AI Agent Workflow
     * Shows a more complex scenario with multiple context-aware nodes
     *
     * Key fix: When a node subscribes to multiple channels, the input becomes
     * a Map<String, Object>. We need to declare the node with the correct type.
     */
    private static void multiStepAIAgentExample() {
        System.out.println("Example 3: Multi-Step AI Agent Workflow");
        System.out.println("-".repeat(50));

        // Node 1: Intent classifier (context-aware)
        // Single subscription - input is String directly
        Node<String, String> intentNode = NodeBuilder.<String, String>create("intent-classifier")
                .subscribeOnly("input")
                .processWithContext((input, ctx) -> {
                    ExecutionContext execCtx = (ExecutionContext) ctx;
                    MessageContext msgCtx = execCtx.getMessageContext();

                    System.out.println("\n[Intent Classifier]");
                    System.out.println("  Input: " + input);
                    System.out.println("  History size: " + msgCtx.size());

                    // Simple intent classification
                    String intent;
                    if (input.toLowerCase().contains("weather")) {
                        intent = "WEATHER_QUERY";
                    } else if (input.toLowerCase().contains("hello") || input.toLowerCase().contains("hi")) {
                        intent = "GREETING";
                    } else {
                        intent = "GENERAL_QUERY";
                    }

                    System.out.println("  Detected intent: " + intent);
                    return intent;
                })
                .writeTo("intent")
                .build();

        // Node 2: Response generator (context-aware)
        // FIX: This node subscribes to multiple channels ("intent", "input")
        // When subscribing to multiple channels, the input is Map<String, Object>
        // We need to use the correct generic type: Map<String, Object> as input
        Node<Map<String, Object>, String> responseNode = NodeBuilder
                .<Map<String, Object>, String>create("response-generator")
                .subscribeTo("intent")
                .alsoRead("input")  // Use alsoRead for additional context without triggering
                .processWithContext((inputs, ctx) -> {
                    ExecutionContext execCtx = (ExecutionContext) ctx;
                    MessageContext msgCtx = execCtx.getMessageContext();

                    System.out.println("\n[Response Generator]");

                    // Extract values from the input map
                    String intent = (String) inputs.get("intent");
                    String originalInput = (String) inputs.get("input");

                    System.out.println("  Intent: " + intent);
                    System.out.println("  Original input: " + originalInput);
                    System.out.println("  Generating response based on conversation history...");

                    // Generate context-aware response
                    String response;
                    if (intent == null) {
                        intent = "GENERAL_QUERY";
                    }

                    switch (intent) {
                        case "WEATHER_QUERY":
                            response = "Based on our conversation, let me help with the weather...";
                            break;
                        case "GREETING":
                            response = "Hello! Nice to meet you.";
                            break;
                        default:
                            response = "I understand your question. Let me think about it...";
                    }

                    // Update message context with new exchange
                    MessageContext newContext = msgCtx
                            .addMessage("user", originalInput != null ? originalInput : "User query")
                            .addMessage("assistant", response);

                    System.out.println("  Response: " + response);
                    System.out.println("  Updated context size: " + newContext.size());

                    return response;
                })
                .writeTo("output")
                .build();

        // Build graph with explicit channel types
        Graph<String, String> graph = GraphBuilder.<String, String>create()
                .name("ai-agent-workflow")
                .addNode("intent-classifier", intentNode)
                .addNode("response-generator", responseNode)
                .addChannel("input", new LastValueChannel<>("input", String.class))
                .addChannel("intent", new LastValueChannel<>("intent", String.class))
                .addChannel("output", new LastValueChannel<>("output", String.class))
                .setInput("input")
                .setOutput("output")
                .build();

        PregelConfig config = PregelConfig.builder()
                .maxSteps(10)
                .debug(true)
                .build();

        Pregel<String, String> pregel = graph.compile(config);

        // Test with different inputs
        String[] testInputs = {
                "Hello there!",
                "What's the weather like today?",
                "Can you explain quantum computing?"
        };

        for (String input : testInputs) {
            System.out.println("\n" + "=".repeat(50));
            System.out.println("Processing: \"" + input + "\"");
            String result = pregel.invoke(input);
            System.out.println("\nFinal Output: " + result);
        }
    }
}