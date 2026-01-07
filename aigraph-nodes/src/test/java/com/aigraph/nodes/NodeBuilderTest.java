package com.aigraph.nodes;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.*;

class NodeBuilderTest {

    @Test
    void shouldBuildBasicNode() {
        Node<String, String> node = NodeBuilder.<String, String>create("test")
            .subscribeOnly("input")
            .process(String::toUpperCase)
            .writeTo("output")
            .build();

        assertThat(node.getName()).isEqualTo("test");
        assertThat(node.getSubscribedChannels()).containsExactly("input");
        assertThat(node.getWriteTargets()).containsKey("output");
    }

    @Test
    void shouldSupportMultipleSubscriptions() {
        Node<String, String> node = NodeBuilder.<String, String>create("test")
            .subscribeTo("ch1", "ch2", "ch3")
            .process(s -> s)
            .writeTo("output")
            .build();

        assertThat(node.getSubscribedChannels()).containsExactly("ch1", "ch2", "ch3");
    }

    @Test
    void shouldSupportMetadata() {
        Node<String, String> node = NodeBuilder.<String, String>create("test")
            .subscribeOnly("input")
            .process(String::toUpperCase)
            .writeTo("output")
            .withDescription("Test node")
            .withTags("test", "example")
            .withTimeout(Duration.ofSeconds(5))
            .build();

        NodeMetadata metadata = node.getMetadata();
        assertThat(metadata.name()).isEqualTo("test");
        assertThat(metadata.description()).isEqualTo("Test node");
        assertThat(metadata.tags()).containsExactly("test", "example");
        assertThat(metadata.timeout()).isEqualTo(Duration.ofSeconds(5));
    }

    @Test
    void shouldFailWithoutSubscriptions() {
        assertThatThrownBy(() ->
            NodeBuilder.<String, String>create("test")
                .process(String::toUpperCase)
                .build()
        ).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldFailWithoutProcessor() {
        assertThatThrownBy(() ->
            NodeBuilder.<String, String>create("test")
                .subscribeOnly("input")
                .build()
        ).isInstanceOf(NullPointerException.class);
    }
}
