package com.aigraph.graph;

import com.aigraph.nodes.Node;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
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
}
