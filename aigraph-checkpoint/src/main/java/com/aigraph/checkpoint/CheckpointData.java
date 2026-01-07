package com.aigraph.checkpoint;

import java.time.Instant;
import java.util.Map;

/**
 * Checkpoint data record.
 */
public record CheckpointData(
    String checkpointId,
    String threadId,
    int stepNumber,
    Map<String, byte[]> channelStates,
    Map<String, byte[]> nodeStates,
    CheckpointMetadata metadata,
    Instant createdAt
) {
    public CheckpointData {
        channelStates = channelStates != null ? Map.copyOf(channelStates) : Map.of();
        nodeStates = nodeStates != null ? Map.copyOf(nodeStates) : Map.of();
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
