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

/**
 * Example: Checkpoint and Resume with Message Context
 * <p>
 * This example demonstrates:
 * <ul>
 *   <li>Using ExecutionContext.interrupt() to stop execution</li>
 *   <li>Persisting MessageContext for later resume</li>
 *   <li>Building pausable AI workflows</li>
 *   <li>Handling long-running conversations</li>
 * </ul>
 * <p>
 * Note: Full checkpoint persistence requires checkpoint module integration.
 * This example shows the API usage pattern.
 */
public class CheckpointResumeExample {

    public static void main(String[] args) {
        System.out.println("=== Checkpoint and Resume Example ===\n");

        // Example 1: Interrupt Execution
        interruptExecutionExample();

        System.out.println("\n" + "=".repeat(60) + "\n");

        // Example 2: Simulate Checkpoint and Resume
        checkpointResumeSimulationExample();
    }

    /**
     * Example 1: Interrupt Execution
     * Shows how to interrupt execution using ExecutionContext
     */
    private static void interruptExecutionExample() {
        System.out.println("Example 1: Interrupt Execution");
        System.out.println("-".repeat(50));

        // Create a node that might need to be interrupted
        Node<Integer, Integer> processingNode = NodeBuilder.<Integer, Integer>create("processor")
            .subscribeOnly("value")
            .processWithContext((input, ctx) -> {
                ExecutionContext execCtx = (ExecutionContext) ctx;

                System.out.println("Processing step " + execCtx.getStepNumber() + ": " + input);

                // Simulate condition to interrupt
                if (input > 50) {
                    System.out.println("  Value exceeds threshold, interrupting...");
                    execCtx.interrupt();
                    return input; // Return current value
                }

                return input * 2; // Continue processing
            })
            .writeTo("value", n -> n <= 100 ? n : null) // Conditional write
            .build();

        Graph<Integer, Integer> graph = GraphBuilder.<Integer, Integer>create()
            .name("interruptable-graph")
            .addNode("processor", processingNode)
            .setInput("value")
            .setOutput("value")
            .build();

        PregelConfig config = PregelConfig.builder()
            .maxSteps(20)
            .build();

        Pregel<Integer, Integer> pregel = graph.compile(config);

        System.out.println("\nStarting with value: 3");
        Integer result = pregel.invoke(3);
        System.out.println("Final result: " + result);
        System.out.println("(Execution stopped when threshold was exceeded)");
    }

    /**
     * Example 2: Simulate Checkpoint and Resume
     * Shows the pattern for checkpoint/resume (actual persistence requires checkpoint module)
     */
    private static void checkpointResumeSimulationExample() {
        System.out.println("Example 2: Checkpoint and Resume Simulation");
        System.out.println("-".repeat(50));

        // Simulated checkpoint storage
        class CheckpointStore {
            MessageContext savedContext = null;
            Integer savedState = null;

            void saveCheckpoint(MessageContext ctx, Integer state) {
                this.savedContext = ctx;
                this.savedState = state;
                System.out.println("  ✓ Checkpoint saved (state=" + state + ", messages=" + ctx.size() + ")");
            }

            MessageContext loadContext() {
                System.out.println("  ✓ Loading context from checkpoint...");
                return savedContext;
            }

            Integer loadState() {
                return savedState;
            }
        }

        CheckpointStore checkpointStore = new CheckpointStore();

        // Create a multi-step workflow node
        Node<String, String> workflowNode = NodeBuilder.<String, String>create("workflow")
            .subscribeOnly("input")
            .processWithContext((input, ctx) -> {
                ExecutionContext execCtx = (ExecutionContext) ctx;
                MessageContext msgCtx = execCtx.getMessageContext();

                System.out.println("\nStep " + execCtx.getStepNumber() + ": Processing '" + input + "'");

                // Add message to history
                MessageContext updated = msgCtx.addMessage("user", input);

                // Simulate checkpoint at certain steps
                if (execCtx.getStepNumber() == 2) {
                    System.out.println("  Creating checkpoint...");
                    checkpointStore.saveCheckpoint(updated, execCtx.getStepNumber());
                }

                // Process and generate response
                String response = "Processed: " + input + " at step " + execCtx.getStepNumber();

                // Add assistant response to context
                updated = updated.addMessage("assistant", response);

                System.out.println("  Total messages in context: " + updated.size());

                return response;
            })
            .writeTo("output")
            .build();

        Graph<String, String> graph = GraphBuilder.<String, String>create()
            .name("checkpoint-workflow")
            .addNode("workflow", workflowNode)
            .setInput("input")
            .setOutput("output")
            .build();

        Pregel<String, String> pregel = graph.compile();

        // Phase 1: Initial execution (will create checkpoint)
        System.out.println("\n=== Phase 1: Initial Execution ===");
        MessageContext initialContext = new MessageContext("session-001")
            .withMetadata("phase", "initial");

        String result1 = pregel.invoke("First message");
        System.out.println("Result: " + result1);

        // Phase 2: Resume from checkpoint
        System.out.println("\n=== Phase 2: Resume from Checkpoint ===");
        if (checkpointStore.savedContext != null) {
            MessageContext resumedContext = checkpointStore.loadContext();
            System.out.println("Resumed context has " + resumedContext.size() + " messages");

            System.out.println("\nMessage History:");
            resumedContext.getMessages().forEach(msg ->
                System.out.printf("  [%s]: %s%n", msg.getRole(), msg.getContent())
            );

            // Continue execution with restored context
            System.out.println("\nContinuing execution...");
            String result2 = pregel.invoke("Second message");
            System.out.println("Result: " + result2);
        }

        // Show how to persist/restore for production use
        System.out.println("\n" + "=".repeat(50));
        System.out.println("Production Usage Pattern:");
        System.out.println("=".repeat(50));
        System.out.println("""
            // In production, use Checkpointer interface:

            // 1. Configure checkpointer
            Checkpointer checkpointer = new JdbcCheckpointer(dataSource);

            // 2. Save checkpoint
            CheckpointData checkpoint = new CheckpointData(
                threadId,
                stepNumber,
                channelStates,
                messageContext // MessageContext is Serializable
            );
            checkpointer.save(checkpoint);

            // 3. Resume from checkpoint
            CheckpointData loaded = checkpointer.load(checkpointId);
            MessageContext restoredContext = deserialize(loaded.getMessageContext());

            ExecutionContext resumeContext = new ExecutionContext(
                threadId,
                channelManager,
                nodeRegistry,
                updatedChannels,
                config,
                restoredContext // Pass restored context
            );

            pregel.resumeFrom(threadId, checkpointId);
            """);
    }
}
