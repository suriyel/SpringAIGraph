package com.aigraph.pregel;

import java.time.Duration;
import java.util.Map;

/**
 * Result of a single node execution.
 *
 * @param nodeName the node name
 * @param output   the output value
 * @param writes   map of channel name to value to be written
 * @param success  whether execution succeeded
 * @param error    exception if execution failed (optional)
 * @param duration execution duration
 * @author AIGraph Team
 * @since 0.0.8
 */
public record NodeResult(
    String nodeName,
    Object output,
    Map<String, Object> writes,
    boolean success,
    Throwable error,
    Duration duration
) {
    public NodeResult {
        writes = writes != null ? Map.copyOf(writes) : Map.of();
    }

    public static NodeResult success(String nodeName, Object output,
                                      Map<String, Object> writes, Duration duration) {
        return new NodeResult(nodeName, output, writes, true, null, duration);
    }

    public static NodeResult failure(String nodeName, Throwable error, Duration duration) {
        return new NodeResult(nodeName, null, Map.of(), false, error, duration);
    }
}
