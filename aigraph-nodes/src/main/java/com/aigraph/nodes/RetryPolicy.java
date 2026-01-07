package com.aigraph.nodes;

import java.time.Duration;
import java.util.Set;

/**
 * Configuration for node retry behavior.
 * <p>
 * Defines how a node should retry failed executions.
 * <p>
 * Example usage:
 * <pre>{@code
 * // No retry
 * var noRetry = RetryPolicy.none();
 *
 * // Fixed backoff: 3 attempts with 100ms between retries
 * var fixed = RetryPolicy.fixed(3, Duration.ofMillis(100));
 *
 * // Exponential backoff: 5 attempts starting at 50ms
 * var exponential = RetryPolicy.exponential(5, Duration.ofMillis(50));
 *
 * // Custom retryable exceptions
 * var custom = new RetryPolicy(
 *     3,
 *     Duration.ofMillis(100),
 *     BackoffStrategy.FIXED,
 *     Set.of(IOException.class, TimeoutException.class)
 * );
 * }</pre>
 *
 * @param maxAttempts        maximum number of execution attempts (including the first)
 * @param initialBackoff     initial backoff duration between retries
 * @param backoffStrategy    the backoff calculation strategy
 * @param retryableExceptions set of exception types that should trigger retry
 * @author AIGraph Team
 * @since 0.0.8
 */
public record RetryPolicy(
    int maxAttempts,
    Duration initialBackoff,
    BackoffStrategy backoffStrategy,
    Set<Class<? extends Throwable>> retryableExceptions
) {
    /**
     * Backoff strategy for retry delays.
     */
    public enum BackoffStrategy {
        /**
         * Fixed delay between retries.
         */
        FIXED,

        /**
         * Exponentially increasing delay (backoff * 2^attempt).
         */
        EXPONENTIAL,

        /**
         * Linear increasing delay (backoff * attempt).
         */
        LINEAR
    }

    /**
     * Compact constructor with validation.
     */
    public RetryPolicy {
        if (maxAttempts < 0) {
            throw new IllegalArgumentException("maxAttempts must be non-negative, got: " + maxAttempts);
        }
        if (initialBackoff != null && initialBackoff.isNegative()) {
            throw new IllegalArgumentException("initialBackoff must be non-negative");
        }
        if (backoffStrategy == null) {
            backoffStrategy = BackoffStrategy.FIXED;
        }
        if (retryableExceptions == null) {
            retryableExceptions = Set.of(Exception.class);
        } else {
            retryableExceptions = Set.copyOf(retryableExceptions);
        }
    }

    /**
     * Creates a policy with no retries.
     *
     * @return a no-retry policy
     */
    public static RetryPolicy none() {
        return new RetryPolicy(0, Duration.ZERO, BackoffStrategy.FIXED, Set.of());
    }

    /**
     * Creates a policy with fixed backoff.
     *
     * @param maxAttempts     the maximum number of attempts
     * @param backoffDuration the fixed backoff duration
     * @return a fixed backoff policy
     */
    public static RetryPolicy fixed(int maxAttempts, Duration backoffDuration) {
        return new RetryPolicy(maxAttempts, backoffDuration, BackoffStrategy.FIXED, Set.of(Exception.class));
    }

    /**
     * Creates a policy with exponential backoff.
     *
     * @param maxAttempts  the maximum number of attempts
     * @param initialDelay the initial backoff duration
     * @return an exponential backoff policy
     */
    public static RetryPolicy exponential(int maxAttempts, Duration initialDelay) {
        return new RetryPolicy(maxAttempts, initialDelay, BackoffStrategy.EXPONENTIAL, Set.of(Exception.class));
    }

    /**
     * Checks if an exception is retryable according to this policy.
     *
     * @param throwable the exception to check
     * @return true if retryable
     */
    public boolean isRetryable(Throwable throwable) {
        if (maxAttempts == 0) {
            return false;
        }
        return retryableExceptions.stream()
            .anyMatch(exType -> exType.isInstance(throwable));
    }

    /**
     * Calculates the backoff duration for a given attempt.
     *
     * @param attemptNumber the attempt number (starting from 1)
     * @return the backoff duration
     */
    public Duration calculateBackoff(int attemptNumber) {
        if (initialBackoff == null || initialBackoff.isZero()) {
            return Duration.ZERO;
        }

        return switch (backoffStrategy) {
            case FIXED -> initialBackoff;
            case LINEAR -> initialBackoff.multipliedBy(attemptNumber);
            case EXPONENTIAL -> initialBackoff.multipliedBy((long) Math.pow(2, attemptNumber - 1));
        };
    }

    /**
     * Checks if retry is enabled.
     *
     * @return true if retries are enabled
     */
    public boolean isEnabled() {
        return maxAttempts > 0;
    }
}
