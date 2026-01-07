package com.aigraph.pregel;

import java.time.Duration;
import java.util.List;

/**
 * Result of a complete graph execution.
 *
 * @param success       whether execution completed successfully
 * @param output        the output value (from output channels)
 * @param steps         list of execution steps
 * @param totalSteps    total number of steps executed
 * @param totalDuration total execution time
 * @param error         exception if execution failed (optional)
 * @author AIGraph Team
 * @since 0.0.8
 */
public record ExecutionResult(
    boolean success,
    Object output,
    List<ExecutionStep> steps,
    int totalSteps,
    Duration totalDuration,
    Throwable error
) {
    public ExecutionResult {
        steps = steps != null ? List.copyOf(steps) : List.of();
    }

    public static ExecutionResult success(Object output, List<ExecutionStep> steps) {
        Duration totalDuration = steps.stream()
            .map(ExecutionStep::duration)
            .reduce(Duration.ZERO, Duration::plus);
        return new ExecutionResult(true, output, steps, steps.size(), totalDuration, null);
    }

    public static ExecutionResult failure(Throwable error, List<ExecutionStep> steps) {
        Duration totalDuration = steps.stream()
            .map(ExecutionStep::duration)
            .reduce(Duration.ZERO, Duration::plus);
        return new ExecutionResult(false, null, steps, steps.size(), totalDuration, error);
    }
}
