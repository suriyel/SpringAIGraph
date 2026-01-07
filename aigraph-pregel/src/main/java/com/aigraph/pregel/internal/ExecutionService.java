package com.aigraph.pregel.internal;

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
 */
public class ExecutionService {
    private static final Logger log = LoggerFactory.getLogger(ExecutionService.class);
    private final ExecutorService threadPool;

    public ExecutionService(ExecutorService threadPool) {
        this.threadPool = threadPool;
    }

    @SuppressWarnings("unchecked")
    public Map<String, NodeResult> executeNodes(List<Node<?, ?>> nodes, Map<String, Object> inputs) {
        if (nodes.isEmpty()) {
            return Map.of();
        }

        Map<String, CompletableFuture<NodeResult>> futures = new HashMap<>();

        for (Node<?, ?> node : nodes) {
            String nodeName = node.getName();
            Object input = inputs.get(nodeName);

            CompletableFuture<NodeResult> future = CompletableFuture.supplyAsync(() -> {
                Instant start = Instant.now();
                try {
                    Object output = ((Node<Object, Object>) node).invoke(input);
                    Duration duration = Duration.between(start, Instant.now());

                    // Collect writes
                    Map<String, Object> writes = new HashMap<>();
                    for (Map.Entry<String, Function<Object, ?>> entry :
                        ((Node<Object, Object>) node).getWriteTargets().entrySet()) {
                        String channel = entry.getKey();
                        Function<Object, ?> mapper = entry.getValue();
                        Object value = mapper != null ? mapper.apply(output) : output;
                        if (value != null) {
                            writes.put(channel, value);
                        }
                    }

                    return NodeResult.success(nodeName, output, writes, duration);
                } catch (Exception e) {
                    Duration duration = Duration.between(start, Instant.now());
                    log.error("Node {} execution failed", nodeName, e);
                    return NodeResult.failure(nodeName, e, duration);
                }
            }, threadPool);

            futures.put(nodeName, future);
        }

        // Wait for all to complete
        CompletableFuture.allOf(futures.values().toArray(new CompletableFuture[0])).join();

        Map<String, NodeResult> results = new HashMap<>();
        futures.forEach((name, future) -> results.put(name, future.join()));

        return results;
    }
}
