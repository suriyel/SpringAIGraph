package com.aigraph.pregel;

import com.aigraph.channels.ChannelManager;
import com.aigraph.nodes.NodeRegistry;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Immutable execution context for a Pregel run.
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

    public ExecutionContext(String threadId, ChannelManager channelManager,
                            NodeRegistry nodeRegistry, PregelConfig config) {
        this(threadId, 0, channelManager, nodeRegistry, Set.of(),
            config, Instant.now(), new ArrayList<>());
    }

    private ExecutionContext(String threadId, int stepNumber,
                             ChannelManager channelManager, NodeRegistry nodeRegistry,
                             Set<String> updatedChannels, PregelConfig config,
                             Instant startTime, List<ExecutionStep> stepHistory) {
        this.threadId = threadId;
        this.stepNumber = stepNumber;
        this.channelManager = channelManager;
        this.nodeRegistry = nodeRegistry;
        this.updatedChannels = updatedChannels;
        this.config = config;
        this.startTime = startTime;
        this.stepHistory = stepHistory;
    }

    public ExecutionContext nextStep(Set<String> newUpdatedChannels) {
        return new ExecutionContext(threadId, stepNumber + 1, channelManager,
            nodeRegistry, newUpdatedChannels, config, startTime, stepHistory);
    }

    public void recordStep(ExecutionStep step) {
        stepHistory.add(step);
    }

    public boolean isTerminated() {
        return stepNumber >= config.maxSteps() || updatedChannels.isEmpty();
    }

    public boolean isTimedOut() {
        if (config.timeout() == null) return false;
        return java.time.Duration.between(startTime, Instant.now())
            .compareTo(config.timeout()) > 0;
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
