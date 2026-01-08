package com.aigraph.graph;

import com.aigraph.channels.LastValueChannel;
import com.aigraph.nodes.Node;
import com.aigraph.nodes.NodeBuilder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class GraphBuilderTest {

    @Test
    void shouldBuildBasicGraph() {
        Node<String, String> node = NodeBuilder.<String, String>create("process")
            .subscribeOnly("input")
            .process(String::toUpperCase)
            .writeTo("output")
            .build();

        Graph<String, String> graph = GraphBuilder.<String, String>create()
            .name("test-graph")
            .addNode("process", node)
            .addChannel("input", new LastValueChannel<>("input", String.class))
            .addChannel("output", new LastValueChannel<>("output", String.class))
            .setInput("input")
            .setOutput("output")
            .build();

        assertThat(graph.getName()).isEqualTo("test-graph");
        assertThat(graph.getNodes()).hasSize(1);
        assertThat(graph.getChannels()).hasSize(2);
        assertThat(graph.getInputChannels()).containsExactly("input");
        assertThat(graph.getOutputChannels()).containsExactly("output");
    }

    @Test
    void shouldAutoCreateChannels() {
        Node<String, String> node = NodeBuilder.<String, String>create("process")
            .subscribeOnly("input")
            .process(String::toUpperCase)
            .writeTo("output")
            .build();

        Graph<String, String> graph = GraphBuilder.<String, String>create()
            .name("test-graph")
            .addNode("process", node)
            .autoCreateChannels(true)
            .setInput("input")
            .setOutput("output")
            .build();

        assertThat(graph.getChannels()).containsKeys("input", "output");
    }

    @Test
    void shouldValidateGraph() {
        Node<String, String> node = NodeBuilder.<String, String>create("process")
            .subscribeOnly("input")
            .process(String::toUpperCase)
            .writeTo("output")
            .build();

        Graph<String, String> graph = GraphBuilder.<String, String>create()
            .name("test-graph")
            .addNode("process", node)
            .addChannel("input", new LastValueChannel<>("input", String.class))
            .addChannel("output", new LastValueChannel<>("output", String.class))
            .setInput("input")
            .setOutput("output")
            .build();

        ValidationResult result = graph.validate();
        assertThat(result.isValid()).isTrue();
    }
}
