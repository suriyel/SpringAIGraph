package com.aigraph.nodes;

import com.aigraph.core.functional.ContextAwareNodeFunction;
import com.aigraph.core.utils.ValidationUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;

/**
 * A context-aware node implementation that can access execution context.
 * <p>
 * This node type extends the basic functionality to include access to:
 * <ul>
 *   <li>Message history (for Spring AI integration)</li>
 *   <li>Execution metadata and state</li>
 *   <li>Custom application context</li>
 * </ul>
 * <p>
 * Instances are created using {@link NodeBuilder#processWithContext(ContextAwareNodeFunction)}.
 *
 * @param <I> the input type
 * @param <O> the output type
 * @author AIGraph Team
 * @since 0.0.9
 */
public final class ContextAwareFunctionalNode<I, O> implements ContextAwareNode<I, O> {

    private static final Logger log = LoggerFactory.getLogger(ContextAwareFunctionalNode.class);
    private static final ExecutorService DEFAULT_EXECUTOR =
        Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            t.setName("context-node-executor-" + t.getId());
            return t;
        });

    private final String name;
    private final Set<String> subscribedChannels;
    private final Set<String> readChannels;
    private final Map<String, Function<O, ?>> writeTargets;
    private final ContextAwareNodeFunction<I, O> processor;
    private final NodeMetadata metadata;
    private final ExecutorService executor;

    /**
     * Package-private constructor. Use {@link NodeBuilder} to create instances.
     */
    ContextAwareFunctionalNode(
        String name,
        Set<String> subscribedChannels,
        Set<String> readChannels,
        Map<String, Function<O, ?>> writeTargets,
        ContextAwareNodeFunction<I, O> processor,
        NodeMetadata metadata,
        ExecutorService executor
    ) {
        this.name = ValidationUtils.requireNonBlank(name, "name");
        this.subscribedChannels = Set.copyOf(subscribedChannels);
        this.readChannels = Set.copyOf(readChannels);
        this.writeTargets = Map.copyOf(writeTargets);
        this.processor = ValidationUtils.requireNonNull(processor, "processor");
        this.metadata = metadata;
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
    public O invokeWithContext(I input, Object context) {
        log.debug("Executing context-aware node: {}", name);

        RetryPolicy retryPolicy = metadata.retryPolicy();
        if (retryPolicy != null && retryPolicy.maxAttempts() > 1) {
            return processWithRetry(input, context, retryPolicy);
        }

        return processWithTimeout(input, context);
    }

    @Override
    public CompletableFuture<O> invokeWithContextAsync(I input, Object context) {
        return CompletableFuture.supplyAsync(() -> invokeWithContext(input, context), executor);
    }

    /**
     * Process with timeout if configured.
     */
    private O processWithTimeout(I input, Object context) {
        Duration timeout = metadata.timeout();

        if (timeout != null && !timeout.isZero() && !timeout.isNegative()) {
            try {
                return CompletableFuture.supplyAsync(
                    () -> {
                        try {
                            return processor.apply(input, context);
                        } catch (Exception e) {
                            throw new RuntimeException("Node processing failed: " + name, e);
                        }
                    },
                    executor
                ).get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                throw new RuntimeException("Node execution timed out after " +
                    timeout.toMillis() + "ms: " + name, e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Node execution interrupted: " + name, e);
            } catch (ExecutionException e) {
                throw new RuntimeException("Node execution failed: " + name, e.getCause());
            }
        }

        // No timeout configured, execute directly
        try {
            return processor.apply(input, context);
        } catch (Exception e) {
            throw new RuntimeException("Node processing failed: " + name, e);
        }
    }

    /**
     * Process with retry logic.
     */
    private O processWithRetry(I input, Object context, RetryPolicy policy) {
        int attempts = 0;
        Exception lastException = null;

        while (attempts < policy.maxAttempts()) {
            try {
                if (attempts > 0) {
                    log.debug("Retrying node {} (attempt {}/{})",
                        name, attempts + 1, policy.maxAttempts());

                    // Apply backoff
                    long backoff = policy.backoffMillis() * attempts;
                    if (backoff > 0) {
                        Thread.sleep(backoff);
                    }
                }

                return processWithTimeout(input, context);

            } catch (Exception e) {
                lastException = e;
                attempts++;

                // Check if exception is retryable
                if (!isRetryable(e, policy)) {
                    log.debug("Exception not retryable for node {}: {}",
                        name, e.getClass().getSimpleName());
                    break;
                }

                if (attempts >= policy.maxAttempts()) {
                    log.debug("Max retry attempts reached for node {}", name);
                    break;
                }
            }
        }

        throw new RuntimeException(
            "Node execution failed after " + attempts + " attempts: " + name,
            lastException
        );
    }

    /**
     * Checks if an exception is retryable according to the policy.
     */
    private boolean isRetryable(Exception e, RetryPolicy policy) {
        if (policy.retryableExceptions().isEmpty()) {
            return true; // Retry all exceptions if none specified
        }

        Class<?> exceptionClass = e.getClass();
        for (Class<?> retryableClass : policy.retryableExceptions()) {
            if (retryableClass.isAssignableFrom(exceptionClass)) {
                return true;
            }
        }

        return false;
    }

    @Override
    public String toString() {
        return "ContextAwareFunctionalNode{" +
            "name='" + name + '\'' +
            ", subscribedChannels=" + subscribedChannels +
            '}';
    }
}
