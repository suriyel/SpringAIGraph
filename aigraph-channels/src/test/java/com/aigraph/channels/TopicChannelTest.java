package com.aigraph.channels;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

class TopicChannelTest {

    @Test
    void shouldAccumulateValues() {
        var channel = new TopicChannel<>("test", String.class, true, false);

        channel.update(List.of("a", "b"));
        channel.update(List.of("c"));

        assertThat(channel.get()).containsExactly("a", "b", "c");
    }

    @Test
    void shouldReplaceInNonAccumulateMode() {
        var channel = new TopicChannel<>("test", String.class, false, false);

        channel.update(List.of("a", "b"));
        channel.update(List.of("c"));

        assertThat(channel.get()).containsExactly("c");
    }

    @Test
    void shouldDeduplicateInUniqueMode() {
        var channel = new TopicChannel<>("test", String.class, true, true);

        channel.update(List.of("a", "b", "a"));
        channel.update(List.of("b", "c"));

        assertThat(channel.get()).containsExactly("a", "b", "c");
    }

    @Test
    void shouldClear() {
        var channel = new TopicChannel<>("test", String.class);
        channel.update(List.of("a", "b"));

        channel.clear();

        assertThat(channel.isEmpty()).isTrue();
    }
}
