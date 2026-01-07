package com.aigraph.checkpoint;

import java.util.List;
import java.util.Map;

/**
 * Checkpoint metadata.
 */
public record CheckpointMetadata(
    String source,
    int stepNumber,
    List<String> executedNodes,
    String parentCheckpointId,
    Map<String, String> tags
) {
    public CheckpointMetadata {
        executedNodes = executedNodes != null ? List.copyOf(executedNodes) : List.of();
        tags = tags != null ? Map.copyOf(tags) : Map.of();
    }
}
