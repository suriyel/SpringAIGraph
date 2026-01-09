package com.aigraph.graph;

import com.aigraph.channels.Channel;
import com.aigraph.channels.ChannelManager;
import com.aigraph.core.exceptions.GraphValidationException;
import com.aigraph.nodes.Node;
import com.aigraph.nodes.NodeRegistry;
import com.aigraph.pregel.Pregel;
import com.aigraph.pregel.PregelConfig;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Represents a complete graph definition.
 */
public class Graph<I, O> {
    private final String name;
    private final NodeRegistry nodeRegistry;
    private final ChannelManager channelManager;
    private final List<String> inputChannels;
    private final List<String> outputChannels;
    private final GraphMetadata metadata;

    public Graph(String name, NodeRegistry nodeRegistry, ChannelManager channelManager,
                 List<String> inputChannels, List<String> outputChannels, GraphMetadata metadata) {
        this.name = name;
        this.nodeRegistry = nodeRegistry;
        this.channelManager = channelManager;
        this.inputChannels = List.copyOf(inputChannels);
        this.outputChannels = List.copyOf(outputChannels);
        this.metadata = metadata != null ? metadata : GraphMetadata.of(name);
    }

    public String getName() {
        return name;
    }

    public Collection<Node<?, ?>> getNodes() {
        return nodeRegistry.getAll();
    }

    public Map<String, Channel<?, ?, ?>> getChannels() {
        return channelManager.getAll();
    }

    public List<String> getInputChannels() {
        return inputChannels;
    }

    public List<String> getOutputChannels() {
        return outputChannels;
    }

    public GraphMetadata getMetadata() {
        return metadata;
    }

    public Pregel<I, O> compile() {
        return compile(PregelConfig.defaults());
    }

    /**
     * Compiles the graph into an executable Pregel instance.
     * <p>
     * Automatically validates the graph structure before compilation.
     * If validation errors are found, a {@link GraphValidationException} is thrown.
     * Warnings are logged but do not prevent compilation.
     *
     * @param config the Pregel configuration
     * @return the compiled Pregel instance
     * @throws GraphValidationException if validation errors are found
     */
    public Pregel<I, O> compile(PregelConfig config) {
        // Automatic validation
        ValidationResult validationResult = validate();

        if (!validationResult.isValid()) {
            // Separate errors from warnings
            List<ValidationIssue> errors = validationResult.getErrors();
            List<ValidationIssue> warnings = validationResult.getWarnings();

            // Log warnings
            if (!warnings.isEmpty()) {
                org.slf4j.LoggerFactory.getLogger(Graph.class)
                    .warn("Graph '{}' has {} validation warnings:", name, warnings.size());
                for (ValidationIssue warning : warnings) {
                    org.slf4j.LoggerFactory.getLogger(Graph.class)
                        .warn("  [{}] {} at {}", warning.code(), warning.message(), warning.location());
                }
            }

            // Throw exception if errors exist
            if (!errors.isEmpty()) {
                String errorMessages = errors.stream()
                    .map(issue -> String.format("[%s] %s at %s",
                        issue.code(), issue.message(), issue.location()))
                    .collect(Collectors.joining("\n  "));

                throw new GraphValidationException(
                    String.format("Graph '%s' failed validation with %d error(s):\n  %s",
                        name, errors.size(), errorMessages)
                );
            }
        }

        // Merge config with graph settings
        PregelConfig finalConfig = PregelConfig.builder()
            .inputChannels(inputChannels.toArray(new String[0]))
            .outputChannels(outputChannels.toArray(new String[0]))
            .maxSteps(config.maxSteps())
            .timeout(config.timeout())
            .debug(config.debug())
            .threadPoolSize(config.threadPoolSize())
            .enableCheckpoint(config.checkpointEnabled())
            .build();

        return new Pregel<>(channelManager, nodeRegistry, finalConfig);
    }

    public ValidationResult validate() {
        return new GraphValidator().validate(this);
    }
}
