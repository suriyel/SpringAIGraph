package com.aigraph.graph;

import com.aigraph.channels.Channel;
import com.aigraph.channels.ChannelManager;
import com.aigraph.nodes.Node;
import com.aigraph.nodes.NodeRegistry;
import com.aigraph.pregel.Pregel;
import com.aigraph.pregel.PregelConfig;

import java.util.Collection;
import java.util.List;
import java.util.Map;

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

    public Pregel<I, O> compile(PregelConfig config) {
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
