package com.aigraph.pregel.internal;

import com.aigraph.nodes.ContextAwareNode;
import com.aigraph.nodes.Node;
import com.aigraph.pregel.NodeResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.function.Function;

/**
 * Service for executing nodes in parallel.
 * <p>
 * Handles:
 * <ul>
 *   <li>Parallel node execution using thread pool</li>
 *   <li>Input passing to nodes</li>
 *   <li>Output collection and write target mapping</li>
 *   <li>Error handling and result aggregation</li>
 * </ul>
 *
 * @author AIGraph Team
 * @since 0.0.8
 */
public class ExecutionService {
    private static final Logger log = LoggerFactory.getLogger(ExecutionService.class);
    private final ExecutorService threadPool;

    public ExecutionService(ExecutorService threadPool) {
        this.threadPool = threadPool;
    }

    /**
     * Executes nodes in parallel.
     *
     * @param nodes   the nodes to execute
     * @param inputs  map of node name to input value
     * @param context the execution context (for context-aware nodes)
     * @return map of node name to execution result
     */
    @SuppressWarnings("unchecked")
    public Map<String, NodeResult> executeNodes(List<Node<?, ?>> nodes, Map<String, Object> inputs, Object context) {
        if (nodes.isEmpty()) {
            return Map.of();
        }

        Map<String, CompletableFuture<NodeResult>> futures = new HashMap<>();

        for (Node<?, ?> node : nodes) {
            String nodeName = node.getName();
            Object input = inputs.get(nodeName);

            CompletableFuture<NodeResult> future = CompletableFuture.supplyAsync(() ->
                    executeNode((Node<Object, Object>) node, input, context), threadPool);

            futures.put(nodeName, future);
        }

        // Wait for all nodes to complete
        try {
            CompletableFuture.allOf(futures.values().toArray(new CompletableFuture[0]))
                    .get(5, TimeUnit.MINUTES); // Default timeout
        } catch (TimeoutException e) {
            log.error("Node execution timed out");
            // Cancel remaining futures
            futures.values().forEach(f -> f.cancel(true));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Node execution interrupted");
        } catch (ExecutionException e) {
            log.error("Node execution failed", e.getCause());
        }

        // Collect results
        Map<String, NodeResult> results = new HashMap<>();
        futures.forEach((name, future) -> {
            try {
                if (future.isDone() && !future.isCancelled()) {
                    results.put(name, future.get());
                } else {
                    results.put(name, NodeResult.failure(name,
                            new RuntimeException("Node execution did not complete"), Duration.ZERO));
                }
            } catch (Exception e) {
                results.put(name, NodeResult.failure(name, e, Duration.ZERO));
            }
        });

        return results;
    }

    /**
     * Executes a single node and collects its writes.
     *
     * @param node    the node to execute
     * @param input   the input value
     * @param context the execution context (for context-aware nodes)
     * @return the execution result
     */
    @SuppressWarnings("unchecked")
    private NodeResult executeNode(Node<Object, Object> node, Object input, Object context) {
        String nodeName = node.getName();
        Instant start = Instant.now();

        try {
            log.debug("Executing node '{}' with input: {}", nodeName,
                    input != null ? input.getClass().getSimpleName() : "null");

            // Execute the node (context-aware if applicable)
            Object output;
            if (node instanceof ContextAwareNode) {
                log.debug("Node '{}' is context-aware, passing execution context", nodeName);
                output = ((ContextAwareNode<Object, Object>) node).invokeWithContext(input, context);
            } else {
                output = node.invoke(input);
            }

            Duration duration = Duration.between(start, Instant.now());
            log.debug("Node '{}' completed in {}ms", nodeName, duration.toMillis());

            // Collect writes based on write targets
            Map<String, Object> writes = collectWrites(node, output);

            return NodeResult.success(nodeName, output, writes, duration);

        } catch (Exception e) {
            Duration duration = Duration.between(start, Instant.now());
            log.error("Node '{}' execution failed after {}ms: {}",
                    nodeName, duration.toMillis(), e.getMessage(), e);
            return NodeResult.failure(nodeName, e, duration);
        }
    }

    /**
     * Collects write operations from node output based on write targets.
     * <p>
     * For each write target:
     * <ul>
     *   <li>If mapper is null, write output directly</li>
     *   <li>If mapper returns null, skip the write (conditional write)</li>
     *   <li>Otherwise, write the mapped value</li>
     * </ul>
     *
     * @param node   the node
     * @param output the node output
     * @return map of channel name to value to write
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> collectWrites(Node<Object, Object> node, Object output) {
        Map<String, Object> writes = new HashMap<>();

        Map<String, Function<Object, ?>> writeTargets = node.getWriteTargets();

        for (Map.Entry<String, Function<Object, ?>> entry : writeTargets.entrySet()) {
            String channel = entry.getKey();
            Function<Object, ?> mapper = entry.getValue();

            try {
                Object value;
                if (mapper != null) {
                    // Apply mapper function
                    value = mapper.apply(output);
                } else {
                    // No mapper - write output directly
                    value = output;
                }

                // Only write non-null values (null means skip/conditional write)
                if (value != null) {
                    writes.put(channel, value);
                    log.trace("Node '{}' writes to channel '{}': {}",
                            node.getName(), channel, value);
                } else {
                    log.trace("Node '{}' skips write to channel '{}' (null value)",
                            node.getName(), channel);
                }
            } catch (Exception e) {
                log.warn("Failed to map output for channel '{}' in node '{}': {}",
                        channel, node.getName(), e.getMessage());
            }
        }

        return writes;
    }
}