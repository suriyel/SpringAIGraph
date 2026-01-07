package com.aigraph.pregel;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Record representing a single execution step in the BSP cycle.
 *
 * @param stepNumber        the step number (starting from 0)
 * @param executedNodes     list of node names executed in this step
 * @param updatedChannels   set of channels updated in this step
 * @param channelSnapshots  snapshots of channel values (for debugging)
 * @param startTime         when this step started
 * @param endTime           when this step completed
 * @author AIGraph Team
 * @since 0.0.8
 */
public record ExecutionStep(
    int stepNumber,
    List<String> executedNodes,
    Set<String> updatedChannels,
    Map<String, Object> channelSnapshots,
    Instant startTime,
    Instant endTime
) {
    public ExecutionStep {
        executedNodes = executedNodes != null ? List.copyOf(executedNodes) : List.of();
        updatedChannels = updatedChannels != null ? Set.copyOf(updatedChannels) : Set.of();
        channelSnapshots = channelSnapshots != null ? Map.copyOf(channelSnapshots) : Map.of();
    }

    public Duration duration() {
        if (startTime == null || endTime == null) {
            return Duration.ZERO;
        }
        return Duration.between(startTime, endTime);
    }
}
