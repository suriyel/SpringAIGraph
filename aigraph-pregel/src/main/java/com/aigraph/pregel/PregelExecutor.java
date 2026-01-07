package com.aigraph.pregel;

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

/**
 * Core executor that runs the BSP execution loop.
 */
public class PregelExecutor {
    private static final Logger log = LoggerFactory.getLogger(PregelExecutor.class);

    private final PlanningService planningService;
    private final ExecutionService executionService;
    private final UpdateService updateService;
    private final ExecutorService threadPool;

    public PregelExecutor(PregelConfig config) {
        this.planningService = new PlanningService();
        this.threadPool = Executors.newFixedThreadPool(config.threadPoolSize());
        this.executionService = new ExecutionService(threadPool);
        this.updateService = new UpdateService();
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

        log.debug("Step {}: Planning", stepNum);
        List<Node<?, ?>> nodesToRun = planningService.plan(context);

        if (nodesToRun.isEmpty()) {
            return new ExecutionStep(stepNum, List.of(), Set.of(), Map.of(), stepStart, Instant.now());
        }

        log.debug("Step {}: Executing {} nodes", stepNum, nodesToRun.size());
        Map<String, Object> inputs = Map.of(); // Simplified: would read from channels
        Map<String, NodeResult> results = executionService.executeNodes(nodesToRun, inputs);

        log.debug("Step {}: Updating channels", stepNum);
        Map<String, List<Object>> writes = updateService.collectWrites(results);
        Set<String> updatedChannels = updateService.applyUpdates(context.getChannelManager(), writes);

        List<String> executedNodes = nodesToRun.stream()
            .map(Node::getName)
            .toList();

        return new ExecutionStep(
            stepNum,
            executedNodes,
            updatedChannels,
            Map.of(),
            stepStart,
            Instant.now()
        );
    }

    public void shutdown() {
        threadPool.shutdown();
    }
}
