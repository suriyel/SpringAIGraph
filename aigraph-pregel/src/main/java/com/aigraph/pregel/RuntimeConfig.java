package com.aigraph.pregel;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

/**
 * Runtime configuration for a specific execution.
 * <p>
 * Provides execution-specific settings that override defaults,
 * such as thread ID for checkpoint continuity.
 *
 * @param threadId      unique identifier for this execution thread
 * @param checkpointId  specific checkpoint to resume from (optional)
 * @param tags          custom tags for this execution
 * @param timeout       execution timeout override (optional)
 * @author AIGraph Team
 * @since 0.0.8
 */
public record RuntimeConfig(
    String threadId,
    String checkpointId,
    Map<String, String> tags,
    Duration timeout
) {
    public RuntimeConfig {
        if (threadId == null || threadId.isBlank()) {
            threadId = UUID.randomUUID().toString();
        }
        if (tags == null) {
            tags = Map.of();
        } else {
            tags = Map.copyOf(tags);
        }
    }

    public static RuntimeConfig defaults() {
        return new RuntimeConfig(null, null, Map.of(), null);
    }

    public static RuntimeConfig withThreadId(String threadId) {
        return new RuntimeConfig(threadId, null, Map.of(), null);
    }
}
