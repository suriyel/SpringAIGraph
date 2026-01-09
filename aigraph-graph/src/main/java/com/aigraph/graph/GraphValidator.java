package com.aigraph.graph;

import com.aigraph.nodes.Node;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Validates graph structure and configuration.
 */
public class GraphValidator {

    public ValidationResult validate(Graph<?, ?> graph) {
        List<ValidationIssue> issues = new ArrayList<>();

        issues.addAll(checkOrphanNodes(graph));
        issues.addAll(checkUnreachableOutputs(graph));
        issues.addAll(checkMissingChannels(graph));
        issues.addAll(checkCircularDependencies(graph));

        return issues.isEmpty() ?
            ValidationResult.valid() :
            ValidationResult.invalid(issues);
    }

    private List<ValidationIssue> checkOrphanNodes(Graph<?, ?> graph) {
        List<ValidationIssue> issues = new ArrayList<>();

        for (Node<?, ?> node : graph.getNodes()) {
            if (node.getSubscribedChannels().isEmpty()) {
                issues.add(new ValidationIssue(
                    ValidationIssue.Level.WARNING,
                    "ORPHAN_NODE",
                    "Node has no subscribed channels and will never execute",
                    node.getName()
                ));
            }
        }

        return issues;
    }

    private List<ValidationIssue> checkUnreachableOutputs(Graph<?, ?> graph) {
        List<ValidationIssue> issues = new ArrayList<>();

        Set<String> writtenChannels = graph.getNodes().stream()
            .flatMap(node -> node.getWriteTargets().keySet().stream())
            .collect(Collectors.toSet());

        for (String outputChannel : graph.getOutputChannels()) {
            if (!graph.getChannels().containsKey(outputChannel)) {
                issues.add(new ValidationIssue(
                    ValidationIssue.Level.ERROR,
                    "MISSING_OUTPUT_CHANNEL",
                    "Output channel not registered",
                    outputChannel
                ));
            }
        }

        return issues;
    }

    private List<ValidationIssue> checkMissingChannels(Graph<?, ?> graph) {
        List<ValidationIssue> issues = new ArrayList<>();

        for (Node<?, ?> node : graph.getNodes()) {
            for (String channel : node.getSubscribedChannels()) {
                if (!graph.getChannels().containsKey(channel)) {
                    issues.add(new ValidationIssue(
                        ValidationIssue.Level.ERROR,
                        "MISSING_CHANNEL",
                        "Subscribed channel not found",
                        node.getName() + " -> " + channel
                    ));
                }
            }
        }

        return issues;
    }

    /**
     * Checks for circular dependencies in the graph.
     * <p>
     * A circular dependency exists when a node's output eventually feeds back
     * to itself through a chain of channel->node relationships, creating a cycle.
     * While Pregel/BSP can handle cycles with proper termination conditions,
     * unintended cycles without termination logic can cause infinite loops.
     * <p>
     * This method uses DFS to detect cycles in the node dependency graph.
     *
     * @param graph the graph to validate
     * @return list of validation issues for detected cycles
     */
    private List<ValidationIssue> checkCircularDependencies(Graph<?, ?> graph) {
        List<ValidationIssue> issues = new ArrayList<>();

        // Build adjacency list: node -> list of dependent nodes
        Map<String, Set<String>> adjacency = new HashMap<>();

        for (Node<?, ?> node : graph.getNodes()) {
            String nodeName = node.getName();
            adjacency.putIfAbsent(nodeName, new HashSet<>());

            // For each channel this node writes to, find nodes that subscribe to it
            for (String writeChannel : node.getWriteTargets().keySet()) {
                for (Node<?, ?> subscriberNode : graph.getNodes()) {
                    if (subscriberNode.getSubscribedChannels().contains(writeChannel)) {
                        adjacency.get(nodeName).add(subscriberNode.getName());
                    }
                }
            }
        }

        // Detect cycles using DFS
        Set<String> visited = new HashSet<>();
        Set<String> recursionStack = new HashSet<>();
        List<String> currentPath = new ArrayList<>();

        for (String nodeName : adjacency.keySet()) {
            if (!visited.contains(nodeName)) {
                List<String> cycle = detectCycleDFS(nodeName, adjacency, visited, recursionStack, currentPath);
                if (cycle != null) {
                    String cycleDescription = String.join(" -> ", cycle);
                    issues.add(new ValidationIssue(
                        ValidationIssue.Level.WARNING,
                        "CIRCULAR_DEPENDENCY",
                        "Circular dependency detected in node chain: " + cycleDescription +
                        ". Ensure proper termination conditions to avoid infinite loops.",
                        cycleDescription
                    ));
                    // Only report first cycle to avoid overwhelming output
                    break;
                }
            }
        }

        return issues;
    }

    /**
     * DFS helper for cycle detection.
     *
     * @param node the current node
     * @param adjacency the adjacency list
     * @param visited set of visited nodes
     * @param recursionStack set of nodes in current DFS path
     * @param currentPath list tracking current path for cycle reporting
     * @return list of nodes forming a cycle, or null if no cycle found
     */
    private List<String> detectCycleDFS(String node, Map<String, Set<String>> adjacency,
                                         Set<String> visited, Set<String> recursionStack,
                                         List<String> currentPath) {
        visited.add(node);
        recursionStack.add(node);
        currentPath.add(node);

        Set<String> neighbors = adjacency.getOrDefault(node, Set.of());
        for (String neighbor : neighbors) {
            if (!visited.contains(neighbor)) {
                List<String> cycle = detectCycleDFS(neighbor, adjacency, visited, recursionStack, currentPath);
                if (cycle != null) {
                    return cycle;
                }
            } else if (recursionStack.contains(neighbor)) {
                // Cycle detected! Build cycle path
                List<String> cycle = new ArrayList<>();
                int cycleStart = currentPath.indexOf(neighbor);
                for (int i = cycleStart; i < currentPath.size(); i++) {
                    cycle.add(currentPath.get(i));
                }
                cycle.add(neighbor); // Complete the cycle
                return cycle;
            }
        }

        recursionStack.remove(node);
        currentPath.remove(currentPath.size() - 1);
        return null;
    }
}
