package com.aigraph.pregel;

import java.time.Duration;
import java.util.List;

/**
 * Configuration for Pregel execution engine.
 * <p>
 * Defines execution parameters such as input/output channels,
 * termination conditions, and threading behavior.
 * <p>
 * Example:
 * <pre>{@code
 * var config = PregelConfig.builder()
 *     .inputChannels("input")
 *     .outputChannels("output")
 *     .maxSteps(100)
 *     .timeout(Duration.ofMinutes(5))
 *     .build();
 * }</pre>
 *
 * @param inputChannels      list of input channel names
 * @param outputChannels     list of output channel names
 * @param maxSteps           maximum number of execution steps (default 100)
 * @param timeout            overall execution timeout (optional)
 * @param debug              enable debug logging (default false)
 * @param threadPoolSize     size of execution thread pool (default: CPU cores)
 * @param checkpointEnabled  enable checkpointing (default false)
 * @param checkpointInterval interval between checkpoints (optional)
 * @author AIGraph Team
 * @since 0.0.8
 */
public record PregelConfig(
    List<String> inputChannels,
    List<String> outputChannels,
    int maxSteps,
    Duration timeout,
    boolean debug,
    int threadPoolSize,
    boolean checkpointEnabled,
    Duration checkpointInterval
) {
    public static final int DEFAULT_MAX_STEPS = 100;
    public static final Duration DEFAULT_TIMEOUT = Duration.ofMinutes(5);
    public static final int DEFAULT_THREAD_POOL_SIZE = Runtime.getRuntime().availableProcessors();

    public PregelConfig {
        if (inputChannels == null) inputChannels = List.of();
        if (outputChannels == null) outputChannels = List.of();
        inputChannels = List.copyOf(inputChannels);
        outputChannels = List.copyOf(outputChannels);
        if (maxSteps <= 0) maxSteps = DEFAULT_MAX_STEPS;
        if (threadPoolSize <= 0) threadPoolSize = DEFAULT_THREAD_POOL_SIZE;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static PregelConfig defaults() {
        return new PregelConfig(List.of(), List.of(), DEFAULT_MAX_STEPS, DEFAULT_TIMEOUT,
            false, DEFAULT_THREAD_POOL_SIZE, false, null);
    }

    public static class Builder {
        private List<String> inputChannels = List.of();
        private List<String> outputChannels = List.of();
        private int maxSteps = DEFAULT_MAX_STEPS;
        private Duration timeout = DEFAULT_TIMEOUT;
        private boolean debug = false;
        private int threadPoolSize = DEFAULT_THREAD_POOL_SIZE;
        private boolean checkpointEnabled = false;
        private Duration checkpointInterval = Duration.ofSeconds(30);

        public Builder inputChannels(String... channels) {
            this.inputChannels = List.of(channels);
            return this;
        }

        public Builder outputChannels(String... channels) {
            this.outputChannels = List.of(channels);
            return this;
        }

        public Builder maxSteps(int maxSteps) {
            this.maxSteps = maxSteps;
            return this;
        }

        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public Builder debug(boolean debug) {
            this.debug = debug;
            return this;
        }

        public Builder threadPoolSize(int size) {
            this.threadPoolSize = size;
            return this;
        }

        public Builder enableCheckpoint(boolean enabled) {
            this.checkpointEnabled = enabled;
            return this;
        }

        public Builder checkpointInterval(Duration interval) {
            this.checkpointInterval = interval;
            return this;
        }

        public PregelConfig build() {
            return new PregelConfig(inputChannels, outputChannels, maxSteps, timeout,
                debug, threadPoolSize, checkpointEnabled, checkpointInterval);
        }
    }
}
