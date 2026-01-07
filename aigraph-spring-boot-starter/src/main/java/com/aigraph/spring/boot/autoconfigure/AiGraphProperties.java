package com.aigraph.spring.boot.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Configuration properties for AIGraph.
 */
@ConfigurationProperties(prefix = "spring.ai.graph")
public class AiGraphProperties {

    private boolean enabled = true;
    private int defaultMaxSteps = 100;
    private Duration defaultTimeout = Duration.ofMinutes(5);
    private int threadPoolSize = Runtime.getRuntime().availableProcessors();
    private Checkpoint checkpoint = new Checkpoint();

    public static class Checkpoint {
        private boolean enabled = false;
        private String type = "memory";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }
    }

    // Getters and Setters
    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getDefaultMaxSteps() {
        return defaultMaxSteps;
    }

    public void setDefaultMaxSteps(int defaultMaxSteps) {
        this.defaultMaxSteps = defaultMaxSteps;
    }

    public Duration getDefaultTimeout() {
        return defaultTimeout;
    }

    public void setDefaultTimeout(Duration defaultTimeout) {
        this.defaultTimeout = defaultTimeout;
    }

    public int getThreadPoolSize() {
        return threadPoolSize;
    }

    public void setThreadPoolSize(int threadPoolSize) {
        this.threadPoolSize = threadPoolSize;
    }

    public Checkpoint getCheckpoint() {
        return checkpoint;
    }

    public void setCheckpoint(Checkpoint checkpoint) {
        this.checkpoint = checkpoint;
    }
}
