package com.aigraph.channels;

import com.aigraph.core.exceptions.EmptyChannelException;
import com.aigraph.core.exceptions.InvalidUpdateException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

class LastValueChannelTest {

    @Test
    void shouldStoreLastValue() {
        var channel = new LastValueChannel<>("test", String.class);

        channel.update(List.of("first"));
        assertThat(channel.get()).isEqualTo("first");

        channel.update(List.of("second"));
        assertThat(channel.get()).isEqualTo("second");
    }

    @Test
    void shouldRejectMultipleUpdates() {
        var channel = new LastValueChannel<>("test", String.class);

        assertThatThrownBy(() -> channel.update(List.of("a", "b")))
            .isInstanceOf(InvalidUpdateException.class)
            .hasMessageContaining("multiple updates");
    }

    @Test
    void shouldThrowOnEmptyRead() {
        var channel = new LastValueChannel<>("test", String.class);

        assertThatThrownBy(channel::get)
            .isInstanceOf(EmptyChannelException.class);
    }

    @Test
    void shouldSupportCheckpoint() {
        var channel = new LastValueChannel<>("test", String.class);
        channel.update(List.of("value"));

        String checkpoint = channel.checkpoint();
        assertThat(checkpoint).isEqualTo("value");

        var restored = (LastValueChannel<String>) channel.fromCheckpoint("restored");
        assertThat(restored.get()).isEqualTo("restored");
    }

    @Test
    void shouldCopy() {
        var channel = new LastValueChannel<>("test", String.class);
        channel.update(List.of("original"));

        var copy = (LastValueChannel<String>) channel.copy();
        assertThat(copy.get()).isEqualTo("original");
        assertThat(copy).isNotSameAs(channel);
    }

    @Test
    void shouldSkipNullUpdates() {
        var channel = new LastValueChannel<>("test", String.class);
        channel.update(List.of("value"));

        boolean updated = channel.update(List.of((String) null));
        assertThat(updated).isFalse();
        assertThat(channel.get()).isEqualTo("value");
    }
}
