package com.aigraph.graph;

import com.aigraph.channels.Channel;
import com.aigraph.channels.ChannelManager;
import com.aigraph.channels.LastValueChannel;
import com.aigraph.nodes.Node;
import com.aigraph.nodes.NodeRegistry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Fluent builder for creating graphs.
 */
public class GraphBuilder<I, O> {
    private String name;
    private final Map<String, Node<?, ?>> nodes;
    private final Map<String, Channel<?, ?, ?>> channels;
    private final List<String> inputChannels;
    private final List<String> outputChannels;
    private boolean autoCreateChannels;
    private GraphMetadata metadata;

    private GraphBuilder() {
        this.nodes = new LinkedHashMap<>();
        this.channels = new LinkedHashMap<>();
        this.inputChannels = new ArrayList<>();
        this.outputChannels = new ArrayList<>();
        this.autoCreateChannels = true;
    }

    public static <I, O> GraphBuilder<I, O> create() {
        return new GraphBuilder<>();
    }

    public GraphBuilder<I, O> name(String name) {
        this.name = name;
        return this;
    }

    public GraphBuilder<I, O> addNode(String name, Node<?, ?> node) {
        this.nodes.put(name, node);
        return this;
    }

    public GraphBuilder<I, O> addNode(String name, Function<?, ?> function) {
        throw new UnsupportedOperationException("Use NodeBuilder to create nodes");
    }

    public GraphBuilder<I, O> addChannel(String name, Channel<?, ?, ?> channel) {
        this.channels.put(name, channel);
        return this;
    }

    @SuppressWarnings("unchecked")
    public GraphBuilder<I, O> addLastValueChannel(String name, Class<?> type) {
        this.channels.put(name, new LastValueChannel(name, type));
        return this;
    }

    public GraphBuilder<I, O> setInput(String... channels) {
        this.inputChannels.clear();
        this.inputChannels.addAll(List.of(channels));
        return this;
    }

    public GraphBuilder<I, O> setOutput(String... channels) {
        this.outputChannels.clear();
        this.outputChannels.addAll(List.of(channels));
        return this;
    }

    public GraphBuilder<I, O> autoCreateChannels(boolean auto) {
        this.autoCreateChannels = auto;
        return this;
    }

    public GraphBuilder<I, O> metadata(GraphMetadata metadata) {
        this.metadata = metadata;
        return this;
    }

    @SuppressWarnings("unchecked")
    public Graph<I, O> build() {
        if (name == null || name.isBlank()) {
            name = "graph-" + System.currentTimeMillis();
        }

        // Create registries
        NodeRegistry nodeRegistry = new NodeRegistry();
        ChannelManager channelManager = new ChannelManager();

        // Register channels
        channels.forEach(channelManager::register);

        // Auto-create missing channels if enabled
        if (autoCreateChannels) {
            nodes.values().forEach(node -> {
                node.getSubscribedChannels().forEach(ch -> {
                    if (!channelManager.contains(ch)) {
                        channelManager.register(ch, new LastValueChannel(ch, Object.class));
                    }
                });
                node.getWriteTargets().keySet().forEach(ch -> {
                    if (!channelManager.contains(ch)) {
                        channelManager.register(ch, new LastValueChannel(ch, Object.class));
                    }
                });
            });
        }

        // Register nodes
        nodes.forEach(nodeRegistry::register);

        return new Graph<>(name, nodeRegistry, channelManager,
            inputChannels, outputChannels, metadata);
    }
}
