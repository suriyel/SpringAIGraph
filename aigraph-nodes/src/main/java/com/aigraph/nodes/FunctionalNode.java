package com.aigraph.nodes;

import com.aigraph.core.functional.NodeFunction;
import com.aigraph.core.utils.ValidationUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;
import java.util.function.Function;

/**
 * A node implementation that wraps a functional processing logic.
 * <p>
 * This is the primary node implementation used in most graphs.
 * It supports:
 * <ul>
 *   <li>Functional transformation: {@code I -> O}</li>
 *   <li>Retry with configurable policy</li>
 *   <li>Timeout with configurable duration</li>
 *   <li>Multiple write targets with optional mappers</li>
 * </ul>
 * <p>
 * Instances are typically created using {@link NodeBuilder}.
 *
 * @param <I> the input type
 * @param <O> the output type
 * @author AIGraph Team
 * @since 0.0.8
 */
public final class FunctionalNode<I, O> implements Node<I, O> {

    private static final Logger log = LoggerFactory.getLogger(FunctionalNode.class);
    private static final ExecutorService DEFAULT_EXECUTOR =
        Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            t.setName("node-executor-" + t.getId());
            return t;
        });

    private final String name;
    private final Set<String> subscribedChannels;
    private final Set<String> readChannels;
    private final Map<String, Function<O, ?>> writeTargets;
    private final NodeFunction<I, O> processor;
    private final NodeMetadata metadata;
    private final ExecutorService executor;

    /**
     * Package-private constructor. Use {@link NodeBuilder} to create instances.
     */
    FunctionalNode(
        String name,
        Set<String> subscribedChannels,
        Set<String> readChannels,
        Map<String, Function<O, ?>> writeTargets,
        NodeFunction<I, O> processor,
        NodeMetadata metadata,
        ExecutorService executor
    ) {
        this.name = ValidationUtils.requireNonBlank(name, "name");
        this.subscribedChannels = Set.copyOf(ValidationUtils.requireNonEmpty(subscribedChannels, "subscribedChannels"));
        this.readChannels = Set.copyOf(readChannels);
        this.writeTargets = Map.copyOf(writeTargets);
        this.processor = ValidationUtils.requireNonNull(processor, "processor");
        this.metadata = metadata != null ? metadata : NodeMetadata.of(name);
        this.executor = executor != null ? executor : DEFAULT_EXECUTOR;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Set<String> getSubscribedChannels() {
        return subscribedChannels;
    }

    @Override
    public Set<String> getReadChannels() {
        return readChannels;
    }

    @Override
    public Map<String, Function<O, ?>> getWriteTargets() {
        return writeTargets;
    }

    @Override
    public NodeMetadata getMetadata() {
        return metadata;
    }

    @Override
    public O invoke(I input) throws Exception {
        log.debug("Executing node: {}", name);

        RetryPolicy retryPolicy = metadata.retryPolicy();
        int maxAttempts = Math.max(1, retryPolicy.maxAttempts());

        Exception lastException = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                // Execute with timeout if configured
                if (metadata.hasTimeout()) {
                    return invokeWithTimeout(input, metadata.timeout());
                } else {
                    return processor.apply(input);
                }
            } catch (Exception e) {
                lastException = e;

                // Check if we should retry
                if (attempt < maxAttempts && retryPolicy.isRetryable(e)) {
                    Duration backoff = retryPolicy.calculateBackoff(attempt);
                    log.warn("Node '{}' execution failed (attempt {}/{}), retrying after {}: {}",
                        name, attempt, maxAttempts, backoff, e.getMessage());

                    if (!backoff.isZero()) {
                        try {
                            Thread.sleep(backoff.toMillis());
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            throw new ExecutionException("Retry interrupted", ie);
                        }
                    }
                } else {
                    // No more retries or not retryable
                    log.error("Node '{}' execution failed after {} attempts", name, attempt, e);
                    throw e;
                }
            }
        }

        // Should not reach here, but just in case
        throw lastException;
    }

    /**
     * Executes the processor with a timeout.
     */
    private O invokeWithTimeout(I input, Duration timeout) throws Exception {
        Future<O> future = executor.submit(() -> processor.apply(input));

        try {
            return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new TimeoutException("Node '" + name + "' timed out after " + timeout);
        } catch (java.util.concurrent.ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception) {
                throw (Exception) cause;
            } else {
                throw new RuntimeException(cause);
            }
        }
    }

    @Override
    public CompletableFuture<O> invokeAsync(I input) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return invoke(input);
            } catch (Exception e) {
                throw new CompletionException("Node '" + name + "' execution failed", e);
            }
        }, executor);
    }

    @Override
    public String toString() {
        return "FunctionalNode{name='" + name + "', subscribes=" + subscribedChannels + "}";
    }
}
