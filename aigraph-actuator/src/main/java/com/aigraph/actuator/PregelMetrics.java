package com.aigraph.actuator;

import com.aigraph.pregel.ExecutionResult;
import com.aigraph.pregel.ExecutionStep;
import com.aigraph.pregel.NodeResult;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Metrics collector for Pregel execution.
 * <p>
 * Tracks:
 * <ul>
 *   <li>Execution duration</li>
 *   <li>Step count</li>
 *   <li>Node executions</li>
 *   <li>Node errors</li>
 * </ul>
 *
 * @author AIGraph Team
 * @since 0.0.8
 */
public class PregelMetrics {

    private final MeterRegistry registry;
    private final Map<String, Timer> executionTimers;
    private final Map<String, Counter> nodeExecutionCounters;
    private final Map<String, Counter> nodeErrorCounters;
    private final Counter stepCounter;
    private final Counter successCounter;
    private final Counter failureCounter;

    public PregelMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.executionTimers = new ConcurrentHashMap<>();
        this.nodeExecutionCounters = new ConcurrentHashMap<>();
        this.nodeErrorCounters = new ConcurrentHashMap<>();

        this.stepCounter = Counter.builder("aigraph.execution.steps")
            .description("Total number of execution steps")
            .register(registry);

        this.successCounter = Counter.builder("aigraph.execution.success")
            .description("Successful executions")
            .register(registry);

        this.failureCounter = Counter.builder("aigraph.execution.failure")
            .description("Failed executions")
            .register(registry);
    }

    /**
     * Records a complete execution.
     */
    public void recordExecution(String graphName, ExecutionResult result) {
        if (result.success()) {
            successCounter.increment();
        } else {
            failureCounter.increment();
        }

        // Record execution duration
        Timer timer = executionTimers.computeIfAbsent(graphName,
            name -> Timer.builder("aigraph.execution.duration")
                .description("Graph execution duration")
                .tag("graph", name)
                .register(registry));

        timer.record(result.totalDuration());

        // Record steps
        stepCounter.increment(result.totalSteps());
    }

    /**
     * Records a single step execution.
     */
    public void recordStep(String graphName, ExecutionStep step) {
        step.executedNodes().forEach(this::recordNodeExecution);
    }

    /**
     * Records a node execution.
     */
    public void recordNodeExecution(String nodeName) {
        Counter counter = nodeExecutionCounters.computeIfAbsent(nodeName,
            name -> Counter.builder("aigraph.node.executions")
                .description("Node execution count")
                .tag("node", name)
                .register(registry));

        counter.increment();
    }

    /**
     * Records a node error.
     */
    public void recordNodeError(String nodeName, Throwable error) {
        Counter counter = nodeErrorCounters.computeIfAbsent(nodeName,
            name -> Counter.builder("aigraph.node.errors")
                .description("Node error count")
                .tag("node", name)
                .tag("error_type", error.getClass().getSimpleName())
                .register(registry));

        counter.increment();
    }

    /**
     * Records node result.
     */
    public void recordNodeResult(NodeResult result) {
        if (result.success()) {
            recordNodeExecution(result.nodeName());
        } else {
            recordNodeError(result.nodeName(), result.error());
        }
    }

    /**
     * Records channel update.
     */
    public void recordChannelUpdate(String channelName, int updateCount) {
        Counter.builder("aigraph.channel.updates")
            .description("Channel update count")
            .tag("channel", channelName)
            .register(registry)
            .increment(updateCount);
    }
}
