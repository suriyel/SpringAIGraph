package com.aigraph.checkpoint.memory;

import com.aigraph.checkpoint.CheckpointData;
import com.aigraph.checkpoint.CheckpointMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class MemoryCheckpointerTest {

    private MemoryCheckpointer checkpointer;

    @BeforeEach
    void setUp() {
        checkpointer = new MemoryCheckpointer();
    }

    @Test
    void shouldSaveAndLoad() {
        CheckpointData data = new CheckpointData(
            "cp1",
            "thread1",
            1,
            Map.of("ch1", new byte[]{1, 2, 3}),
            Map.of(),
            new CheckpointMetadata("test", 1, List.of(), null, Map.of()),
            Instant.now()
        );

        String id = checkpointer.save("thread1", data);
        assertThat(id).isEqualTo("cp1");

        var loaded = checkpointer.load("cp1");
        assertThat(loaded).isPresent();
        assertThat(loaded.get().checkpointId()).isEqualTo("cp1");
    }

    @Test
    void shouldLoadLatest() {
        CheckpointData data1 = new CheckpointData(
            "cp1", "thread1", 1, Map.of(), Map.of(),
            new CheckpointMetadata("test", 1, List.of(), null, Map.of()),
            Instant.now()
        );
        CheckpointData data2 = new CheckpointData(
            "cp2", "thread1", 2, Map.of(), Map.of(),
            new CheckpointMetadata("test", 2, List.of(), null, Map.of()),
            Instant.now()
        );

        checkpointer.save("thread1", data1);
        checkpointer.save("thread1", data2);

        var latest = checkpointer.loadLatest("thread1");
        assertThat(latest).isPresent();
        assertThat(latest.get().checkpointId()).isEqualTo("cp2");
    }

    @Test
    void shouldDelete() {
        CheckpointData data = new CheckpointData(
            "cp1", "thread1", 1, Map.of(), Map.of(),
            new CheckpointMetadata("test", 1, List.of(), null, Map.of()),
            Instant.now()
        );

        checkpointer.save("thread1", data);
        assertThat(checkpointer.exists("cp1")).isTrue();

        boolean deleted = checkpointer.delete("cp1");
        assertThat(deleted).isTrue();
        assertThat(checkpointer.exists("cp1")).isFalse();
    }

    @Test
    void shouldClear() {
        CheckpointData data = new CheckpointData(
            "cp1", "thread1", 1, Map.of(), Map.of(),
            new CheckpointMetadata("test", 1, List.of(), null, Map.of()),
            Instant.now()
        );

        checkpointer.save("thread1", data);
        assertThat(checkpointer.size()).isEqualTo(1);

        checkpointer.clear();
        assertThat(checkpointer.size()).isEqualTo(0);
    }
}
