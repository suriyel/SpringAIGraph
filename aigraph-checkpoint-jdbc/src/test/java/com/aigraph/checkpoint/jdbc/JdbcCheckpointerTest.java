package com.aigraph.checkpoint.jdbc;

import com.aigraph.checkpoint.CheckpointData;
import com.aigraph.checkpoint.CheckpointMetadata;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class JdbcCheckpointerTest {

    private DataSource dataSource;
    private JdbcCheckpointer checkpointer;
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        // Create H2 in-memory database
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1");
        config.setUsername("sa");
        config.setPassword("");
        config.setMaximumPoolSize(5);

        dataSource = new HikariDataSource(config);
        jdbcTemplate = new JdbcTemplate(dataSource);

        // Create table
        String createTableSql = """
            CREATE TABLE aigraph_checkpoints (
                checkpoint_id VARCHAR(255) PRIMARY KEY,
                thread_id VARCHAR(255) NOT NULL,
                step_number INT NOT NULL,
                channel_states CLOB,
                node_states CLOB,
                metadata_source VARCHAR(255),
                metadata_step_number INT,
                metadata_executed_nodes CLOB,
                metadata_parent_checkpoint_id VARCHAR(255),
                metadata_tags CLOB,
                created_at TIMESTAMP NOT NULL
            )
            """;
        jdbcTemplate.execute(createTableSql);

        // Create indexes
        jdbcTemplate.execute("CREATE INDEX idx_thread_id ON aigraph_checkpoints(thread_id)");
        jdbcTemplate.execute("CREATE INDEX idx_thread_step ON aigraph_checkpoints(thread_id, step_number)");
        jdbcTemplate.execute("CREATE INDEX idx_created_at ON aigraph_checkpoints(created_at)");

        checkpointer = new JdbcCheckpointer(dataSource);
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS aigraph_checkpoints");
        if (dataSource instanceof HikariDataSource) {
            ((HikariDataSource) dataSource).close();
        }
    }

    @Test
    void shouldSaveAndLoad() {
        CheckpointData data = new CheckpointData(
            "cp1",
            "thread1",
            1,
            Map.of("ch1", new byte[]{1, 2, 3}),
            Map.of("node1", new byte[]{4, 5, 6}),
            new CheckpointMetadata("test", 1, List.of("node1"), null, Map.of("tag1", "value1")),
            Instant.now()
        );

        String id = checkpointer.save("thread1", data);
        assertThat(id).isEqualTo("cp1");

        var loaded = checkpointer.load("cp1");
        assertThat(loaded).isPresent();
        assertThat(loaded.get().checkpointId()).isEqualTo("cp1");
        assertThat(loaded.get().threadId()).isEqualTo("thread1");
        assertThat(loaded.get().stepNumber()).isEqualTo(1);
        assertThat(loaded.get().channelStates()).containsKey("ch1");
        assertThat(loaded.get().channelStates().get("ch1")).containsExactly((byte) 1, (byte) 2, (byte) 3);
        assertThat(loaded.get().nodeStates()).containsKey("node1");
        assertThat(loaded.get().metadata().source()).isEqualTo("test");
        assertThat(loaded.get().metadata().executedNodes()).containsExactly("node1");
        assertThat(loaded.get().metadata().tags()).containsEntry("tag1", "value1");
    }

    @Test
    void shouldLoadLatest() throws InterruptedException {
        CheckpointData data1 = new CheckpointData(
            "cp1", "thread1", 1, Map.of(), Map.of(),
            new CheckpointMetadata("test", 1, List.of(), null, Map.of()),
            Instant.now()
        );
        CheckpointData data2 = new CheckpointData(
            "cp2", "thread1", 2, Map.of(), Map.of(),
            new CheckpointMetadata("test", 2, List.of(), null, Map.of()),
            Instant.now().plusSeconds(1)
        );

        checkpointer.save("thread1", data1);
        Thread.sleep(10); // Small delay to ensure different timestamps
        checkpointer.save("thread1", data2);

        var latest = checkpointer.loadLatest("thread1");
        assertThat(latest).isPresent();
        assertThat(latest.get().checkpointId()).isEqualTo("cp2");
    }

    @Test
    void shouldLoadByThread() {
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

        var loaded = checkpointer.loadByThread("thread1");
        assertThat(loaded).isPresent();
        // Should return the one with highest step number
        assertThat(loaded.get().checkpointId()).isEqualTo("cp2");
    }

    @Test
    void shouldListCheckpoints() {
        CheckpointData data1 = new CheckpointData(
            "cp1", "thread1", 1, Map.of(), Map.of(),
            new CheckpointMetadata("test", 1, List.of("node1"), null, Map.of()),
            Instant.now()
        );
        CheckpointData data2 = new CheckpointData(
            "cp2", "thread1", 2, Map.of(), Map.of(),
            new CheckpointMetadata("test", 2, List.of("node2"), null, Map.of()),
            Instant.now()
        );
        CheckpointData data3 = new CheckpointData(
            "cp3", "thread2", 1, Map.of(), Map.of(),
            new CheckpointMetadata("test", 1, List.of("node3"), null, Map.of()),
            Instant.now()
        );

        checkpointer.save("thread1", data1);
        checkpointer.save("thread1", data2);
        checkpointer.save("thread2", data3);

        var list = checkpointer.list("thread1");
        assertThat(list).hasSize(2);

        var limitedList = checkpointer.list("thread1", 1);
        assertThat(limitedList).hasSize(1);
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
    void shouldDeleteByThread() {
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
        CheckpointData data3 = new CheckpointData(
            "cp3", "thread2", 1, Map.of(), Map.of(),
            new CheckpointMetadata("test", 1, List.of(), null, Map.of()),
            Instant.now()
        );

        checkpointer.save("thread1", data1);
        checkpointer.save("thread1", data2);
        checkpointer.save("thread2", data3);

        int deleted = checkpointer.deleteByThread("thread1");
        assertThat(deleted).isEqualTo(2);
        assertThat(checkpointer.exists("cp1")).isFalse();
        assertThat(checkpointer.exists("cp2")).isFalse();
        assertThat(checkpointer.exists("cp3")).isTrue();
    }

    @Test
    void shouldCheckExistence() {
        CheckpointData data = new CheckpointData(
            "cp1", "thread1", 1, Map.of(), Map.of(),
            new CheckpointMetadata("test", 1, List.of(), null, Map.of()),
            Instant.now()
        );

        assertThat(checkpointer.exists("cp1")).isFalse();

        checkpointer.save("thread1", data);
        assertThat(checkpointer.exists("cp1")).isTrue();
    }

    @Test
    void shouldHandleEmptyResults() {
        assertThat(checkpointer.load("nonexistent")).isEmpty();
        assertThat(checkpointer.loadByThread("nonexistent")).isEmpty();
        assertThat(checkpointer.loadLatest("nonexistent")).isEmpty();
        assertThat(checkpointer.list("nonexistent")).isEmpty();
        assertThat(checkpointer.delete("nonexistent")).isFalse();
        assertThat(checkpointer.deleteByThread("nonexistent")).isEqualTo(0);
    }

    @Test
    void shouldHandleEmptyCollections() {
        CheckpointData data = new CheckpointData(
            "cp1", "thread1", 1, Map.of(), Map.of(),
            new CheckpointMetadata("test", 1, List.of(), null, Map.of()),
            Instant.now()
        );

        checkpointer.save("thread1", data);

        var loaded = checkpointer.load("cp1");
        assertThat(loaded).isPresent();
        assertThat(loaded.get().channelStates()).isEmpty();
        assertThat(loaded.get().nodeStates()).isEmpty();
        assertThat(loaded.get().metadata().executedNodes()).isEmpty();
        assertThat(loaded.get().metadata().tags()).isEmpty();
    }

    @Test
    void shouldHandleLargeBinaryData() {
        byte[] largeData = new byte[10000];
        for (int i = 0; i < largeData.length; i++) {
            largeData[i] = (byte) (i % 256);
        }

        CheckpointData data = new CheckpointData(
            "cp1", "thread1", 1,
            Map.of("large", largeData),
            Map.of(),
            new CheckpointMetadata("test", 1, List.of(), null, Map.of()),
            Instant.now()
        );

        checkpointer.save("thread1", data);

        var loaded = checkpointer.load("cp1");
        assertThat(loaded).isPresent();
        assertThat(loaded.get().channelStates().get("large")).isEqualTo(largeData);
    }

    @Test
    void shouldHandleSpecialCharactersInMetadata() {
        CheckpointData data = new CheckpointData(
            "cp1", "thread1", 1, Map.of(), Map.of(),
            new CheckpointMetadata(
                "test-source",
                1,
                List.of("node-1", "node-2", "node/with/slash"),
                "parent-cp-1",
                Map.of("key-with-dash", "value with spaces", "特殊字符", "中文值")
            ),
            Instant.now()
        );

        checkpointer.save("thread1", data);

        var loaded = checkpointer.load("cp1");
        assertThat(loaded).isPresent();
        assertThat(loaded.get().metadata().executedNodes())
            .containsExactly("node-1", "node-2", "node/with/slash");
        assertThat(loaded.get().metadata().tags())
            .containsEntry("key-with-dash", "value with spaces")
            .containsEntry("特殊字符", "中文值");
    }
}
