package com.aigraph.pregel;

import com.aigraph.channels.Channel;
import com.aigraph.channels.ChannelManager;
import com.aigraph.core.exceptions.ExecutionException;
import com.aigraph.nodes.Node;
import com.aigraph.pregel.internal.ExecutionService;
import com.aigraph.pregel.internal.PlanningService;
import com.aigraph.pregel.internal.UpdateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Core executor that runs the BSP execution loop.
 * <p>
 * Implements the Plan-Execute-Update cycle:
 * <ol>
 *   <li>Plan: Find nodes subscribed to updated channels</li>
 *   <li>Execute: Run nodes in parallel, reading from channels</li>
 *   <li>Update: Collect writes and batch update channels</li>
 * </ol>
 *
 * @author AIGraph Team
 * @since 0.0.8
 */
public class PregelExecutor {
    private static final Logger log = LoggerFactory.getLogger(PregelExecutor.class);

    private final PlanningService planningService;
    private final ExecutionService executionService;
    private final UpdateService updateService;
    private final ExecutorService threadPool;

    public PregelExecutor(PregelConfig config) {
        this.planningService = new PlanningService();
        this.threadPool = Executors.newFixedThreadPool(
                config.threadPoolSize(),
                new DaemonThreadFactory("pregel-worker")
        );
        this.executionService = new ExecutionService(threadPool);
        this.updateService = new UpdateService();
    }

    /**
     * 创建守护线程的工厂类
     */
    private static class DaemonThreadFactory implements ThreadFactory {
        private final AtomicInteger threadNumber = new AtomicInteger(1);
        private final String namePrefix;

        DaemonThreadFactory(String namePrefix) {
            this.namePrefix = namePrefix;
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r);
            t.setName(namePrefix + "-" + threadNumber.getAndIncrement());
            t.setDaemon(true);  // 设置为守护线程
            return t;
        }
    }

    public ExecutionResult execute(ExecutionContext context) {
        log.debug("Starting Pregel execution for thread {}", context.getThreadId());

        try {
            while (!context.isTerminated()) {
                if (context.isTimedOut()) {
                    throw new ExecutionException("Execution timed out after step " + context.getStepNumber());
                }

                ExecutionStep step = executeStep(context);
                context.recordStep(step);

                if (step.updatedChannels().isEmpty()) {
                    log.debug("No updates in step {}, terminating", context.getStepNumber());
                    break;
                }

                context = context.nextStep(step.updatedChannels());
            }

            log.info("Execution completed after {} steps", context.getStepHistory().size());
            return ExecutionResult.success(null, context.getStepHistory());

        } catch (Exception e) {
            log.error("Execution failed at step {}", context.getStepNumber(), e);
            return ExecutionResult.failure(e, context.getStepHistory());
        }
    }

    private ExecutionStep executeStep(ExecutionContext context) {
        Instant stepStart = Instant.now();
        int stepNum = context.getStepNumber();
        ChannelManager channelManager = context.getChannelManager();

        // Phase 1: Plan - find nodes to execute
        log.debug("Step {}: Planning", stepNum);
        List<Node<?, ?>> nodesToRun = planningService.plan(context);

        if (nodesToRun.isEmpty()) {
            log.debug("Step {}: No nodes to execute", stepNum);
            return new ExecutionStep(stepNum, List.of(), Set.of(), Map.of(), stepStart, Instant.now());
        }

        // Phase 2: Read inputs from channels for each node
        log.debug("Step {}: Reading inputs for {} nodes", stepNum, nodesToRun.size());
        Map<String, Object> nodeInputs = prepareNodeInputs(nodesToRun, channelManager);

        // Phase 3: Execute nodes in parallel (pass context for context-aware nodes)
        log.debug("Step {}: Executing {} nodes", stepNum, nodesToRun.size());
        Map<String, NodeResult> results = executionService.executeNodes(nodesToRun, nodeInputs, context);

        // Phase 4: Update channels with results
        log.debug("Step {}: Updating channels", stepNum);
        Map<String, List<Object>> writes = updateService.collectWrites(results);
        Set<String> updatedChannels = updateService.applyUpdates(channelManager, writes);

        // Collect executed node names
        List<String> executedNodes = nodesToRun.stream()
                .map(Node::getName)
                .toList();

        // Create channel snapshots for debugging
        Map<String, Object> channelSnapshots = context.getConfig().debug()
                ? createChannelSnapshots(channelManager)
                : Map.of();

        return new ExecutionStep(
                stepNum,
                executedNodes,
                updatedChannels,
                channelSnapshots,
                stepStart,
                Instant.now()
        );
    }

    /**
     * Prepares input values for each node by reading from their subscribed channels.
     * <p>
     * Input preparation rules:
     * <ul>
     *   <li>Single subscription: input is the channel value directly</li>
     *   <li>Multiple subscriptions: input is a Map&lt;String, Object&gt; of channel name to value</li>
     *   <li>Additional read channels are included in the map for multi-subscription nodes</li>
     * </ul>
     *
     * @param nodes          the nodes to prepare inputs for
     * @param channelManager the channel manager
     * @return map of node name to input value
     */
    private Map<String, Object> prepareNodeInputs(List<Node<?, ?>> nodes, ChannelManager channelManager) {
        Map<String, Object> nodeInputs = new HashMap<>();

        for (Node<?, ?> node : nodes) {
            String nodeName = node.getName();
            Set<String> subscribedChannels = node.getSubscribedChannels();
            Set<String> readChannels = node.getReadChannels();

            try {
                Object input;

                if (subscribedChannels.size() == 1 && readChannels.isEmpty()) {
                    // Single subscription: pass channel value directly
                    String channelName = subscribedChannels.iterator().next();
                    input = readChannelValue(channelManager, channelName);
                } else {
                    // Multiple subscriptions or has read channels: build a map
                    Map<String, Object> inputMap = new LinkedHashMap<>();

                    // Add subscribed channel values
                    for (String channelName : subscribedChannels) {
                        Object value = readChannelValue(channelManager, channelName);
                        inputMap.put(channelName, value);
                    }

                    // Add additional read channel values
                    for (String channelName : readChannels) {
                        Object value = readChannelValue(channelManager, channelName);
                        inputMap.put(channelName, value);
                    }

                    input = inputMap;
                }

                nodeInputs.put(nodeName, input);
                log.trace("Prepared input for node '{}': {}", nodeName, input);

            } catch (Exception e) {
                log.warn("Failed to prepare input for node '{}': {}", nodeName, e.getMessage());
                // Pass null if we can't read the input - node will handle it
                nodeInputs.put(nodeName, null);
            }
        }

        return nodeInputs;
    }

    /**
     * Reads a value from a channel, returning null if the channel is empty.
     *
     * @param channelManager the channel manager
     * @param channelName    the channel name
     * @return the channel value, or null if empty
     */
    private Object readChannelValue(ChannelManager channelManager, String channelName) {
        try {
            Channel<?, ?, ?> channel = channelManager.get(channelName);
            if (channel.isEmpty()) {
                return null;
            }
            return channel.get();
        } catch (Exception e) {
            log.trace("Channel '{}' is empty or unreadable: {}", channelName, e.getMessage());
            return null;
        }
    }

    /**
     * Creates snapshots of all channel values for debugging.
     */
    private Map<String, Object> createChannelSnapshots(ChannelManager channelManager) {
        Map<String, Object> snapshots = new LinkedHashMap<>();

        channelManager.getAll().forEach((name, channel) -> {
            try {
                if (!channel.isEmpty()) {
                    snapshots.put(name, channel.get());
                }
            } catch (Exception e) {
                // Ignore - channel might be empty
            }
        });

        return snapshots;
    }

    public void shutdown() {
        threadPool.shutdown();
    }
}