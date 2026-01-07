-- MySQL Database Schema for AiGraph Checkpoints

CREATE TABLE IF NOT EXISTS aigraph_checkpoints (
    checkpoint_id VARCHAR(255) PRIMARY KEY,
    thread_id VARCHAR(255) NOT NULL,
    step_number INT NOT NULL,
    channel_states LONGTEXT,
    node_states LONGTEXT,
    metadata_source VARCHAR(255),
    metadata_step_number INT,
    metadata_executed_nodes LONGTEXT,
    metadata_parent_checkpoint_id VARCHAR(255),
    metadata_tags LONGTEXT,
    created_at TIMESTAMP NOT NULL,
    INDEX idx_thread_id (thread_id),
    INDEX idx_thread_step (thread_id, step_number),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
