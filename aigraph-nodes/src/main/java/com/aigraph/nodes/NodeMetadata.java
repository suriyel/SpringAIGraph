package com.aigraph.nodes;

import java.time.Duration;
import java.util.Set;

/**
 * Metadata record for node configuration.
 * <p>
 * Contains descriptive and operational metadata about a node.
 * <p>
 * Example:
 * <pre>{@code
 * var metadata = new NodeMetadata(
 *     "data-processor",
 *     "Processes incoming data",
 *     Set.of("etl", "batch"),
 *     RetryPolicy.exponential(3, Duration.ofMillis(100)),
 *     Duration.ofSeconds(30)
 * );
 * }</pre>
 *
 * @param name        the node name
 * @param description optional description
 * @param tags        optional tags for categorization
 * @param retryPolicy the retry policy
 * @param timeout     execution timeout
 * @author AIGraph Team
 * @since 0.0.8
 */
public record NodeMetadata(
    String name,
    String description,
    Set<String> tags,
    RetryPolicy retryPolicy,
    Duration timeout
) {
    /**
     * Compact constructor with validation and defaults.
     */
    public NodeMetadata {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Node name must not be blank");
        }
        if (tags == null) {
            tags = Set.of();
        } else {
            tags = Set.copyOf(tags);
        }
        if (retryPolicy == null) {
            retryPolicy = RetryPolicy.none();
        }
        // timeout can be null (no timeout)
    }

    /**
     * Creates metadata with minimal configuration.
     *
     * @param name the node name
     * @return metadata with defaults
     */
    public static NodeMetadata of(String name) {
        return new NodeMetadata(name, null, Set.of(), RetryPolicy.none(), null);
    }

    /**
     * Creates a builder for fluent construction.
     *
     * @param name the node name
     * @return a new builder
     */
    public static Builder builder(String name) {
        return new Builder(name);
    }

    /**
     * Builder for NodeMetadata.
     */
    public static class Builder {
        private final String name;
        private String description;
        private Set<String> tags = Set.of();
        private RetryPolicy retryPolicy = RetryPolicy.none();
        private Duration timeout;

        private Builder(String name) {
            this.name = name;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder tags(String... tags) {
            this.tags = Set.of(tags);
            return this;
        }

        public Builder retryPolicy(RetryPolicy retryPolicy) {
            this.retryPolicy = retryPolicy;
            return this;
        }

        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public NodeMetadata build() {
            return new NodeMetadata(name, description, tags, retryPolicy, timeout);
        }
    }

    /**
     * Checks if timeout is configured.
     *
     * @return true if timeout is set
     */
    public boolean hasTimeout() {
        return timeout != null;
    }

    /**
     * Checks if retry is enabled.
     *
     * @return true if retry policy is enabled
     */
    public boolean hasRetry() {
        return retryPolicy != null && retryPolicy.isEnabled();
    }
}
