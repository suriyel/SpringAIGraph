package com.aigraph.graph;

import com.aigraph.nodes.Node;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Generates visual representations of graphs.
 * <p>
 * Supports:
 * <ul>
 *   <li>Mermaid diagrams</li>
 *   <li>DOT/Graphviz format</li>
 *   <li>ASCII art</li>
 * </ul>
 *
 * @author AIGraph Team
 * @since 0.0.8
 */
public class GraphVisualizer {

    /**
     * Generates a Mermaid flowchart diagram.
     * <p>
     * Example output:
     * <pre>
     * graph TD
     *     input[Input] --> node1[Process]
     *     node1 --> output[Output]
     * </pre>
     *
     * @param graph the graph to visualize
     * @return Mermaid diagram as string
     */
    public String toMermaid(Graph<?, ?> graph) {
        StringBuilder sb = new StringBuilder();
        sb.append("graph TD\n");

        // Add nodes
        sb.append("    %% Nodes\n");
        for (Node<?, ?> node : graph.getNodes()) {
            String nodeName = sanitizeMermaidId(node.getName());
            sb.append(String.format("    %s[%s]\n", nodeName, node.getName()));
        }

        // Add channels
        sb.append("\n    %% Channels\n");
        graph.getChannels().forEach((name, channel) -> {
            String channelId = sanitizeMermaidId(name);
            String channelType = channel.getClass().getSimpleName();
            sb.append(String.format("    %s{{\"%s\\n(%s)\"}}\n",
                channelId, name, channelType));
        });

        // Add edges (subscriptions)
        sb.append("\n    %% Subscriptions\n");
        for (Node<?, ?> node : graph.getNodes()) {
            String nodeName = sanitizeMermaidId(node.getName());

            for (String channel : node.getSubscribedChannels()) {
                String channelId = sanitizeMermaidId(channel);
                sb.append(String.format("    %s -->|subscribe| %s\n",
                    channelId, nodeName));
            }
        }

        // Add edges (writes)
        sb.append("\n    %% Writes\n");
        for (Node<?, ?> node : graph.getNodes()) {
            String nodeName = sanitizeMermaidId(node.getName());

            for (String channel : node.getWriteTargets().keySet()) {
                String channelId = sanitizeMermaidId(channel);
                sb.append(String.format("    %s -->|write| %s\n",
                    nodeName, channelId));
            }
        }

        // Mark input/output channels
        if (!graph.getInputChannels().isEmpty()) {
            sb.append("\n    %% Input channels\n");
            for (String input : graph.getInputChannels()) {
                String channelId = sanitizeMermaidId(input);
                sb.append(String.format("    style %s fill:#e1f5fe\n", channelId));
            }
        }

        if (!graph.getOutputChannels().isEmpty()) {
            sb.append("\n    %% Output channels\n");
            for (String output : graph.getOutputChannels()) {
                String channelId = sanitizeMermaidId(output);
                sb.append(String.format("    style %s fill:#e8f5e9\n", channelId));
            }
        }

        return sb.toString();
    }

    /**
     * Generates a DOT/Graphviz diagram.
     *
     * @param graph the graph to visualize
     * @return DOT format as string
     */
    public String toDot(Graph<?, ?> graph) {
        StringBuilder sb = new StringBuilder();
        sb.append("digraph ").append(sanitizeDotId(graph.getName())).append(" {\n");
        sb.append("    rankdir=TB;\n");
        sb.append("    node [shape=box];\n\n");

        // Nodes
        sb.append("    // Nodes\n");
        for (Node<?, ?> node : graph.getNodes()) {
            String nodeName = sanitizeDotId(node.getName());
            sb.append(String.format("    %s [label=\"%s\"];\n",
                nodeName, node.getName()));
        }

        // Channels
        sb.append("\n    // Channels\n");
        sb.append("    node [shape=ellipse, style=filled, fillcolor=lightblue];\n");
        graph.getChannels().forEach((name, channel) -> {
            String channelId = sanitizeDotId(name);
            String channelType = channel.getClass().getSimpleName();
            sb.append(String.format("    %s [label=\"%s\\n%s\"];\n",
                channelId, name, channelType));
        });

        // Edges
        sb.append("\n    // Edges\n");
        for (Node<?, ?> node : graph.getNodes()) {
            String nodeName = sanitizeDotId(node.getName());

            for (String channel : node.getSubscribedChannels()) {
                String channelId = sanitizeDotId(channel);
                sb.append(String.format("    %s -> %s [label=\"subscribe\"];\n",
                    channelId, nodeName));
            }

            for (String channel : node.getWriteTargets().keySet()) {
                String channelId = sanitizeDotId(channel);
                sb.append(String.format("    %s -> %s [label=\"write\"];\n",
                    nodeName, channelId));
            }
        }

        sb.append("}\n");
        return sb.toString();
    }

    /**
     * Generates an ASCII art representation.
     *
     * @param graph the graph to visualize
     * @return ASCII art as string
     */
    public String toAscii(Graph<?, ?> graph) {
        StringBuilder sb = new StringBuilder();
        sb.append("Graph: ").append(graph.getName()).append("\n");
        sb.append("=".repeat(50)).append("\n\n");

        sb.append("Nodes:\n");
        for (Node<?, ?> node : graph.getNodes()) {
            sb.append("  [").append(node.getName()).append("]\n");
            sb.append("    Subscribes: ").append(node.getSubscribedChannels()).append("\n");
            sb.append("    Writes to: ").append(node.getWriteTargets().keySet()).append("\n");
        }

        sb.append("\nChannels:\n");
        graph.getChannels().forEach((name, channel) -> {
            sb.append("  {").append(name).append("} ")
                .append("(").append(channel.getClass().getSimpleName()).append(")\n");
        });

        sb.append("\nFlow:\n");
        sb.append("  Input: ").append(graph.getInputChannels()).append("\n");
        sb.append("  Output: ").append(graph.getOutputChannels()).append("\n");

        return sb.toString();
    }

    /**
     * Sanitizes an identifier for Mermaid syntax.
     */
    private String sanitizeMermaidId(String id) {
        return id.replaceAll("[^a-zA-Z0-9_]", "_");
    }

    /**
     * Sanitizes an identifier for DOT syntax.
     */
    private String sanitizeDotId(String id) {
        return "\"" + id.replaceAll("\"", "\\\\\"") + "\"";
    }
}
