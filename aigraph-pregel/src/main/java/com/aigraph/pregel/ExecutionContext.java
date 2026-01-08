package com.aigraph.pregel;

import com.aigraph.channels.ChannelManager;
import com.aigraph.nodes.NodeRegistry;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Immutable execution context for a Pregel run.
 * <p>
 * Tracks the state of execution including:
 * <ul>
 *   <li>Current step number</li>
 *   <li>Which channels were updated (triggers node execution)</li>
 *   <li>Execution history</li>
 *   <li>Timing information</li>
 * </ul>
 *
 * @author AIGraph Team
 * @since 0.0.8
 */
public class ExecutionContext {
    private final String threadId;
    private final int stepNumber;
    private final ChannelManager channelManager;
    private final NodeRegistry nodeRegistry;
    private final Set<String> updatedChannels;
    private final PregelConfig config;
    private final Instant startTime;
    private final List<ExecutionStep> stepHistory;

    /**
     * Creates a new execution context with initial updated channels from ChannelManager.
     *
     * @param threadId       the thread/session identifier
     * @param channelManager the channel manager
     * @param nodeRegistry   the node registry
     * @param config         the pregel configuration
     */
    public ExecutionContext(String threadId, ChannelManager channelManager,
                            NodeRegistry nodeRegistry, PregelConfig config) {
        this(threadId, 0, channelManager, nodeRegistry,
                channelManager.getUpdatedChannels(),
                config, Instant.now(), new ArrayList<>());
    }

    /**
     * Creates a new execution context with explicit initial updated channels.
     * <p>
     * This constructor allows specifying which channels should be considered
     * "updated" at the start of execution, enabling support for Void/null inputs.
     *
     * @param threadId               the thread/session identifier
     * @param channelManager         the channel manager
     * @param nodeRegistry           the node registry
     * @param initialUpdatedChannels the channels to consider as updated initially
     * @param config                 the pregel configuration
     */
    public ExecutionContext(String threadId, ChannelManager channelManager,
                            NodeRegistry nodeRegistry, Set<String> initialUpdatedChannels,
                            PregelConfig config) {
        this(threadId, 0, channelManager, nodeRegistry,
                initialUpdatedChannels,
                config, Instant.now(), new ArrayList<>());
    }

    /**
     * Private constructor for creating context with specific values.
     */
    private ExecutionContext(String threadId, int stepNumber,
                             ChannelManager channelManager, NodeRegistry nodeRegistry,
                             Set<String> updatedChannels, PregelConfig config,
                             Instant startTime, List<ExecutionStep> stepHistory) {
        this.threadId = threadId;
        this.stepNumber = stepNumber;
        this.channelManager = channelManager;
        this.nodeRegistry = nodeRegistry;
        this.updatedChannels = updatedChannels != null ? Set.copyOf(updatedChannels) : Set.of();
        this.config = config;
        this.startTime = startTime;
        this.stepHistory = stepHistory;
    }

    /**
     * Creates a new context for the next step with updated channels.
     *
     * @param newUpdatedChannels the channels updated in the current step
     * @return a new context for the next step
     */
    public ExecutionContext nextStep(Set<String> newUpdatedChannels) {
        return new ExecutionContext(threadId, stepNumber + 1, channelManager,
                nodeRegistry, newUpdatedChannels, config, startTime, stepHistory);
    }

    /**
     * Records an execution step in the history.
     *
     * @param step the step to record
     */
    public void recordStep(ExecutionStep step) {
        stepHistory.add(step);
    }

    /**
     * Checks if execution should terminate.
     * <p>
     * Termination occurs when:
     * <ul>
     *   <li>Maximum steps reached</li>
     *   <li>No channels were updated (fixed point reached)</li>
     * </ul>
     *
     * @return true if execution should terminate
     */
    public boolean isTerminated() {
        return stepNumber >= config.maxSteps() || updatedChannels.isEmpty();
    }

    /**
     * Checks if execution has timed out.
     *
     * @return true if timed out
     */
    public boolean isTimedOut() {
        if (config.timeout() == null) return false;
        return Duration.between(startTime, Instant.now())
                .compareTo(config.timeout()) > 0;
    }

    /**
     * Gets the elapsed time since execution started.
     *
     * @return the elapsed duration
     */
    public Duration getElapsedTime() {
        return Duration.between(startTime, Instant.now());
    }

    // Getters
    public String getThreadId() { return threadId; }
    public int getStepNumber() { return stepNumber; }
    public ChannelManager getChannelManager() { return channelManager; }
    public NodeRegistry getNodeRegistry() { return nodeRegistry; }
    public Set<String> getUpdatedChannels() { return updatedChannels; }
    public PregelConfig getConfig() { return config; }
    public List<ExecutionStep> getStepHistory() { return List.copyOf(stepHistory); }
}