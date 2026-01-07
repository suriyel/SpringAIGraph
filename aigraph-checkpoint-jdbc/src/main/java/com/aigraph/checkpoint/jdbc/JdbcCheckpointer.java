package com.aigraph.checkpoint.jdbc;

import com.aigraph.checkpoint.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

/**
 * JDBC-based checkpoint implementation.
 */
public class JdbcCheckpointer implements Checkpointer {

    private static final Logger logger = LoggerFactory.getLogger(JdbcCheckpointer.class);

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final String tableName;

    private static final String INSERT_SQL =
        "INSERT INTO %s (checkpoint_id, thread_id, step_number, channel_states, node_states, " +
        "metadata_source, metadata_step_number, metadata_executed_nodes, metadata_parent_checkpoint_id, " +
        "metadata_tags, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    private static final String SELECT_BY_ID_SQL =
        "SELECT * FROM %s WHERE checkpoint_id = ?";

    private static final String SELECT_BY_THREAD_SQL =
        "SELECT * FROM %s WHERE thread_id = ? ORDER BY step_number DESC";

    private static final String SELECT_LATEST_SQL =
        "SELECT * FROM %s WHERE thread_id = ? ORDER BY created_at DESC LIMIT 1";

    private static final String SELECT_METADATA_SQL =
        "SELECT checkpoint_id, thread_id, step_number, metadata_source, metadata_step_number, " +
        "metadata_executed_nodes, metadata_parent_checkpoint_id, metadata_tags, created_at " +
        "FROM %s WHERE thread_id = ? ORDER BY created_at DESC LIMIT ?";

    private static final String DELETE_BY_ID_SQL =
        "DELETE FROM %s WHERE checkpoint_id = ?";

    private static final String DELETE_BY_THREAD_SQL =
        "DELETE FROM %s WHERE thread_id = ?";

    private static final String EXISTS_SQL =
        "SELECT COUNT(*) FROM %s WHERE checkpoint_id = ?";

    /**
     * Constructor with default table name.
     */
    public JdbcCheckpointer(DataSource dataSource) {
        this(dataSource, "aigraph_checkpoints");
    }

    /**
     * Constructor with custom table name.
     */
    public JdbcCheckpointer(DataSource dataSource, String tableName) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
        this.objectMapper = new ObjectMapper();
        this.tableName = tableName;
        logger.info("JdbcCheckpointer initialized with table: {}", tableName);
    }

    /**
     * Constructor with custom ObjectMapper.
     */
    public JdbcCheckpointer(DataSource dataSource, String tableName, ObjectMapper objectMapper) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
        this.objectMapper = objectMapper;
        this.tableName = tableName;
        logger.info("JdbcCheckpointer initialized with table: {}", tableName);
    }

    @Override
    public String save(String threadId, CheckpointData data) {
        try {
            String channelStatesJson = serializeMap(data.channelStates());
            String nodeStatesJson = serializeMap(data.nodeStates());
            String executedNodesJson = serializeList(data.metadata().executedNodes());
            String tagsJson = serializeStringMap(data.metadata().tags());

            jdbcTemplate.update(
                String.format(INSERT_SQL, tableName),
                data.checkpointId(),
                threadId,
                data.stepNumber(),
                channelStatesJson,
                nodeStatesJson,
                data.metadata().source(),
                data.metadata().stepNumber(),
                executedNodesJson,
                data.metadata().parentCheckpointId(),
                tagsJson,
                Timestamp.from(data.createdAt())
            );

            logger.debug("Saved checkpoint {} for thread {}", data.checkpointId(), threadId);
            return data.checkpointId();
        } catch (Exception e) {
            logger.error("Failed to save checkpoint {} for thread {}", data.checkpointId(), threadId, e);
            throw new CheckpointException("Failed to save checkpoint", e);
        }
    }

    @Override
    public Optional<CheckpointData> load(String checkpointId) {
        try {
            List<CheckpointData> results = jdbcTemplate.query(
                String.format(SELECT_BY_ID_SQL, tableName),
                new CheckpointRowMapper(),
                checkpointId
            );

            if (results.isEmpty()) {
                logger.debug("Checkpoint {} not found", checkpointId);
                return Optional.empty();
            }

            logger.debug("Loaded checkpoint {}", checkpointId);
            return Optional.of(results.get(0));
        } catch (Exception e) {
            logger.error("Failed to load checkpoint {}", checkpointId, e);
            throw new CheckpointException("Failed to load checkpoint", e);
        }
    }

    @Override
    public Optional<CheckpointData> loadByThread(String threadId) {
        try {
            List<CheckpointData> results = jdbcTemplate.query(
                String.format(SELECT_BY_THREAD_SQL, tableName),
                new CheckpointRowMapper(),
                threadId
            );

            if (results.isEmpty()) {
                logger.debug("No checkpoints found for thread {}", threadId);
                return Optional.empty();
            }

            logger.debug("Loaded checkpoint for thread {}", threadId);
            return Optional.of(results.get(0));
        } catch (Exception e) {
            logger.error("Failed to load checkpoint for thread {}", threadId, e);
            throw new CheckpointException("Failed to load checkpoint by thread", e);
        }
    }

    @Override
    public Optional<CheckpointData> loadLatest(String threadId) {
        try {
            List<CheckpointData> results = jdbcTemplate.query(
                String.format(SELECT_LATEST_SQL, tableName),
                new CheckpointRowMapper(),
                threadId
            );

            if (results.isEmpty()) {
                logger.debug("No checkpoints found for thread {}", threadId);
                return Optional.empty();
            }

            logger.debug("Loaded latest checkpoint for thread {}", threadId);
            return Optional.of(results.get(0));
        } catch (Exception e) {
            logger.error("Failed to load latest checkpoint for thread {}", threadId, e);
            throw new CheckpointException("Failed to load latest checkpoint", e);
        }
    }

    @Override
    public List<CheckpointMetadata> list(String threadId) {
        return list(threadId, Integer.MAX_VALUE);
    }

    @Override
    public List<CheckpointMetadata> list(String threadId, int limit) {
        try {
            List<CheckpointMetadata> results = jdbcTemplate.query(
                String.format(SELECT_METADATA_SQL, tableName),
                new MetadataRowMapper(),
                threadId,
                limit
            );

            logger.debug("Listed {} checkpoints for thread {}", results.size(), threadId);
            return results;
        } catch (Exception e) {
            logger.error("Failed to list checkpoints for thread {}", threadId, e);
            throw new CheckpointException("Failed to list checkpoints", e);
        }
    }

    @Override
    public boolean delete(String checkpointId) {
        try {
            int deleted = jdbcTemplate.update(
                String.format(DELETE_BY_ID_SQL, tableName),
                checkpointId
            );

            boolean result = deleted > 0;
            logger.debug("Deleted checkpoint {}: {}", checkpointId, result);
            return result;
        } catch (Exception e) {
            logger.error("Failed to delete checkpoint {}", checkpointId, e);
            throw new CheckpointException("Failed to delete checkpoint", e);
        }
    }

    @Override
    public int deleteByThread(String threadId) {
        try {
            int deleted = jdbcTemplate.update(
                String.format(DELETE_BY_THREAD_SQL, tableName),
                threadId
            );

            logger.debug("Deleted {} checkpoints for thread {}", deleted, threadId);
            return deleted;
        } catch (Exception e) {
            logger.error("Failed to delete checkpoints for thread {}", threadId, e);
            throw new CheckpointException("Failed to delete checkpoints by thread", e);
        }
    }

    @Override
    public boolean exists(String checkpointId) {
        try {
            Integer count = jdbcTemplate.queryForObject(
                String.format(EXISTS_SQL, tableName),
                Integer.class,
                checkpointId
            );

            return count != null && count > 0;
        } catch (Exception e) {
            logger.error("Failed to check existence of checkpoint {}", checkpointId, e);
            throw new CheckpointException("Failed to check checkpoint existence", e);
        }
    }

    /**
     * Row mapper for CheckpointData.
     */
    private class CheckpointRowMapper implements RowMapper<CheckpointData> {
        @Override
        public CheckpointData mapRow(ResultSet rs, int rowNum) throws SQLException {
            try {
                String checkpointId = rs.getString("checkpoint_id");
                String threadId = rs.getString("thread_id");
                int stepNumber = rs.getInt("step_number");

                Map<String, byte[]> channelStates = deserializeMap(rs.getString("channel_states"));
                Map<String, byte[]> nodeStates = deserializeMap(rs.getString("node_states"));

                CheckpointMetadata metadata = new CheckpointMetadata(
                    rs.getString("metadata_source"),
                    rs.getInt("metadata_step_number"),
                    deserializeList(rs.getString("metadata_executed_nodes")),
                    rs.getString("metadata_parent_checkpoint_id"),
                    deserializeStringMap(rs.getString("metadata_tags"))
                );

                Instant createdAt = rs.getTimestamp("created_at").toInstant();

                return new CheckpointData(
                    checkpointId,
                    threadId,
                    stepNumber,
                    channelStates,
                    nodeStates,
                    metadata,
                    createdAt
                );
            } catch (Exception e) {
                throw new SQLException("Failed to map checkpoint row", e);
            }
        }
    }

    /**
     * Row mapper for CheckpointMetadata (lightweight).
     */
    private class MetadataRowMapper implements RowMapper<CheckpointMetadata> {
        @Override
        public CheckpointMetadata mapRow(ResultSet rs, int rowNum) throws SQLException {
            try {
                return new CheckpointMetadata(
                    rs.getString("metadata_source"),
                    rs.getInt("metadata_step_number"),
                    deserializeList(rs.getString("metadata_executed_nodes")),
                    rs.getString("metadata_parent_checkpoint_id"),
                    deserializeStringMap(rs.getString("metadata_tags"))
                );
            } catch (Exception e) {
                throw new SQLException("Failed to map metadata row", e);
            }
        }
    }

    // Serialization helpers

    private String serializeMap(Map<String, byte[]> map) throws Exception {
        if (map == null || map.isEmpty()) {
            return "{}";
        }
        // Convert byte[] to Base64 strings for JSON serialization
        Map<String, String> base64Map = new HashMap<>();
        for (Map.Entry<String, byte[]> entry : map.entrySet()) {
            base64Map.put(entry.getKey(), Base64.getEncoder().encodeToString(entry.getValue()));
        }
        return objectMapper.writeValueAsString(base64Map);
    }

    private Map<String, byte[]> deserializeMap(String json) throws Exception {
        if (json == null || json.isEmpty() || json.equals("{}")) {
            return Map.of();
        }
        Map<String, String> base64Map = objectMapper.readValue(json,
            new TypeReference<Map<String, String>>() {});
        Map<String, byte[]> result = new HashMap<>();
        for (Map.Entry<String, String> entry : base64Map.entrySet()) {
            result.put(entry.getKey(), Base64.getDecoder().decode(entry.getValue()));
        }
        return result;
    }

    private String serializeList(List<String> list) throws Exception {
        if (list == null || list.isEmpty()) {
            return "[]";
        }
        return objectMapper.writeValueAsString(list);
    }

    private List<String> deserializeList(String json) throws Exception {
        if (json == null || json.isEmpty() || json.equals("[]")) {
            return List.of();
        }
        return objectMapper.readValue(json, new TypeReference<List<String>>() {});
    }

    private String serializeStringMap(Map<String, String> map) throws Exception {
        if (map == null || map.isEmpty()) {
            return "{}";
        }
        return objectMapper.writeValueAsString(map);
    }

    private Map<String, String> deserializeStringMap(String json) throws Exception {
        if (json == null || json.isEmpty() || json.equals("{}")) {
            return Map.of();
        }
        return objectMapper.readValue(json, new TypeReference<Map<String, String>>() {});
    }
}
