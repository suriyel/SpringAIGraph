-- PostgreSQL Database Schema for AiGraph Checkpoints

CREATE TABLE IF NOT EXISTS aigraph_checkpoints (
    checkpoint_id VARCHAR(255) PRIMARY KEY,
    thread_id VARCHAR(255) NOT NULL,
    step_number INT NOT NULL,
    channel_states TEXT,
    node_states TEXT,
    metadata_source VARCHAR(255),
    metadata_step_number INT,
    metadata_executed_nodes TEXT,
    metadata_parent_checkpoint_id VARCHAR(255),
    metadata_tags TEXT,
    created_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_thread_id ON aigraph_checkpoints(thread_id);
CREATE INDEX IF NOT EXISTS idx_thread_step ON aigraph_checkpoints(thread_id, step_number);
CREATE INDEX IF NOT EXISTS idx_created_at ON aigraph_checkpoints(created_at);
