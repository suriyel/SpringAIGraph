package com.aigraph.pregel;

import com.aigraph.channels.ChannelManager;
import com.aigraph.core.exceptions.ExecutionException;
import com.aigraph.nodes.Node;
import com.aigraph.pregel.internal.ExecutionService;
import com.aigraph.pregel.internal.InputPreparationService;
import com.aigraph.pregel.internal.PlanningService;
import com.aigraph.pregel.internal.UpdateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Reactive Pregel executor using Project Reactor.
 * <p>
 * Provides true reactive streaming of execution steps with:
 * <ul>
 *   <li>Non-blocking execution</li>
 *   <li>Backpressure support</li>
 *   <li>Cancellation support</li>
 *   <li>Hot and cold streams</li>
 * </ul>
 *
 * @author AIGraph Team
 * @since 0.0.9
 */
public class ReactivePregelExecutor {

    private static final Logger log = LoggerFactory.getLogger(ReactivePregelExecutor.class);

    private final PlanningService planningService;
    private final ExecutionService executionService;
    private final UpdateService updateService;
    private final InputPreparationService inputPreparationService;
    private final ExecutorService threadPool;

    public ReactivePregelExecutor(PregelConfig config) {
        this.planningService = new PlanningService();
        this.updateService = new UpdateService();
        this.inputPreparationService = new InputPreparationService();

        // Create thread pool
        int poolSize = config.threadPoolSize();
        ThreadFactory threadFactory = new DaemonThreadFactory("reactive-pregel-executor");
        this.threadPool = Executors.newFixedThreadPool(poolSize, threadFactory);
        this.executionService = new ExecutionService(threadPool, config.timeout());

        log.info("Reactive Pregel executor initialized with {} threads", poolSize);
    }

    /**
     * Daemon thread factory for executor threads.
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
            t.setDaemon(true);
            return t;
        }
    }

    /**
     * Reactively executes the graph and streams execution steps.
     * <p>
     * Returns a Flux that emits ExecutionStep objects as they complete.
     * The Flux supports cancellation and backpressure.
     *
     * @param context the execution context
     * @return Flux of execution steps
     */
    public Flux<ExecutionStep> executeReactive(ExecutionContext context) {
        log.debug("Starting reactive Pregel execution for thread {}", context.getThreadId());

        return Flux.create(sink -> {
            ExecutionContext currentContext = context;

            try {
                while (!currentContext.isTerminated()) {
                    // Check if subscriber cancelled
                    if (sink.isCancelled()) {
                        log.debug("Execution cancelled by subscriber");
                        break;
                    }

                    // Check timeout
                    if (currentContext.isTimedOut()) {
                        sink.error(new ExecutionException(
                            "Execution timed out after step " + currentContext.getStepNumber()));
                        return;
                    }

                    // Execute one step
                    ExecutionStep step = executeStep(currentContext);

                    // Record step
                    currentContext.recordStep(step);

                    // Emit step to subscriber
                    sink.next(step);

                    // Check if we should terminate
                    if (step.updatedChannels().isEmpty()) {
                        log.debug("No updates in step {}, terminating", currentContext.getStepNumber());
                        break;
                    }

                    // Move to next step
                    currentContext = currentContext.nextStep(step.updatedChannels());
                }

                log.info("Reactive execution completed after {} steps",
                    currentContext.getStepHistory().size());
                sink.complete();

            } catch (Exception e) {
                log.error("Reactive execution failed at step {}", currentContext.getStepNumber(), e);
                sink.error(e);
            }
        });
    }

    /**
     * Reactively executes the graph and returns the final result.
     * <p>
     * Returns a Mono that emits the output when execution completes.
     *
     * @param context the execution context
     * @return Mono containing the execution result
     */
    public Mono<ExecutionResult> executeReactiveMono(ExecutionContext context) {
        return Mono.fromCallable(() -> {
            log.debug("Starting reactive Pregel execution for thread {}", context.getThreadId());

            ExecutionContext currentContext = context;

            try {
                while (!currentContext.isTerminated()) {
                    if (currentContext.isTimedOut()) {
                        throw new ExecutionException(
                            "Execution timed out after step " + currentContext.getStepNumber());
                    }

                    ExecutionStep step = executeStep(currentContext);
                    currentContext.recordStep(step);

                    if (step.updatedChannels().isEmpty()) {
                        log.debug("No updates in step {}, terminating", currentContext.getStepNumber());
                        break;
                    }

                    currentContext = currentContext.nextStep(step.updatedChannels());
                }

                log.info("Execution completed after {} steps", currentContext.getStepHistory().size());
                return ExecutionResult.success(null, currentContext.getStepHistory());

            } catch (Exception e) {
                log.error("Execution failed at step {}", currentContext.getStepNumber(), e);
                return ExecutionResult.failure(e, currentContext.getStepHistory());
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Executes a single step.
     */
    private ExecutionStep executeStep(ExecutionContext context) {
        Instant stepStart = Instant.now();
        int stepNum = context.getStepNumber();
        ChannelManager channelManager = context.getChannelManager();

        // Phase 1: Plan
        log.debug("Step {}: Planning", stepNum);
        List<Node<?, ?>> nodesToRun = planningService.plan(context);

        if (nodesToRun.isEmpty()) {
            log.debug("Step {}: No nodes to execute", stepNum);
            return new ExecutionStep(stepNum, List.of(), Set.of(), Map.of(), stepStart, Instant.now());
        }

        // Phase 2: Read inputs
        log.debug("Step {}: Reading inputs for {} nodes", stepNum, nodesToRun.size());
        Map<String, Object> nodeInputs = inputPreparationService.prepareNodeInputs(nodesToRun, channelManager);

        // Phase 3: Execute nodes
        log.debug("Step {}: Executing {} nodes", stepNum, nodesToRun.size());
        Map<String, NodeResult> results = executionService.executeNodes(nodesToRun, nodeInputs, context);

        // Phase 4: Update channels
        log.debug("Step {}: Updating channels", stepNum);
        Map<String, List<Object>> writes = updateService.collectWrites(results);
        Set<String> updatedChannels = updateService.applyUpdates(channelManager, writes);

        // Collect executed node names
        List<String> executedNodes = nodesToRun.stream()
            .map(Node::getName)
            .toList();

        // Create channel snapshots
        Map<String, Object> snapshots = new HashMap<>();
        for (String channel : updatedChannels) {
            try {
                Object value = channelManager.get(channel).get();
                snapshots.put(channel, value);
            } catch (Exception e) {
                log.trace("Could not snapshot channel {}: {}", channel, e.getMessage());
            }
        }

        return new ExecutionStep(stepNum, executedNodes, updatedChannels,
            snapshots, stepStart, Instant.now());
    }


    /**
     * Shuts down the executor and releases resources.
     * Waits for existing tasks to complete within a timeout period.
     * Forces shutdown if graceful shutdown times out.
     */
    public void shutdown() {
        log.info("Shutting down reactive Pregel executor");
        threadPool.shutdown();
        try {
            if (!threadPool.awaitTermination(30, java.util.concurrent.TimeUnit.SECONDS)) {
                log.warn("Reactive thread pool did not terminate gracefully, forcing shutdown");
                threadPool.shutdownNow();
                // Wait a bit for tasks to respond to being cancelled
                if (!threadPool.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS)) {
                    log.error("Reactive thread pool did not terminate after forced shutdown");
                }
            }
        } catch (InterruptedException e) {
            log.warn("Shutdown interrupted, forcing immediate shutdown");
            threadPool.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
