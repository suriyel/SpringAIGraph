package com.aigraph.pregel;

import com.aigraph.channels.Channel;
import com.aigraph.channels.ChannelManager;
import com.aigraph.core.exceptions.ExecutionException;
import com.aigraph.nodes.Node;
import com.aigraph.pregel.internal.ExecutionService;
import com.aigraph.pregel.internal.InputPreparationService;
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
    private final InputPreparationService inputPreparationService;
    private final ExecutorService threadPool;

    public PregelExecutor(PregelConfig config) {
        this.planningService = new PlanningService();
        this.inputPreparationService = new InputPreparationService();
        this.threadPool = Executors.newFixedThreadPool(
                config.threadPoolSize(),
                new DaemonThreadFactory("pregel-worker")
        );
        this.executionService = new ExecutionService(threadPool, config.timeout());
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
        Map<String, Object> nodeInputs = inputPreparationService.prepareNodeInputs(nodesToRun, channelManager);

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

    /**
     * Shuts down the thread pool gracefully.
     * Waits for existing tasks to complete within a timeout period.
     * Forces shutdown if graceful shutdown times out.
     */
    public void shutdown() {
        threadPool.shutdown();
        try {
            if (!threadPool.awaitTermination(30, java.util.concurrent.TimeUnit.SECONDS)) {
                log.warn("Thread pool did not terminate gracefully, forcing shutdown");
                threadPool.shutdownNow();
                // Wait a bit for tasks to respond to being cancelled
                if (!threadPool.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS)) {
                    log.error("Thread pool did not terminate after forced shutdown");
                }
            }
        } catch (InterruptedException e) {
            log.warn("Shutdown interrupted, forcing immediate shutdown");
            threadPool.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}