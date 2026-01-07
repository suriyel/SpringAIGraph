package com.aigraph.spring.boot.autoconfigure;

import com.aigraph.checkpoint.Checkpointer;
import com.aigraph.checkpoint.memory.MemoryCheckpointer;
import com.aigraph.pregel.PregelConfig;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Auto-configuration for AIGraph.
 */
@Configuration
@ConditionalOnProperty(prefix = "spring.ai.graph", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(AiGraphProperties.class)
public class AiGraphAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public PregelConfig defaultPregelConfig(AiGraphProperties properties) {
        return PregelConfig.builder()
            .maxSteps(properties.getDefaultMaxSteps())
            .timeout(properties.getDefaultTimeout())
            .threadPoolSize(properties.getThreadPoolSize())
            .enableCheckpoint(properties.getCheckpoint().isEnabled())
            .build();
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "spring.ai.graph.checkpoint", name = "enabled", havingValue = "true")
    public Checkpointer checkpointer(AiGraphProperties properties) {
        String type = properties.getCheckpoint().getType();
        if ("memory".equalsIgnoreCase(type)) {
            return new MemoryCheckpointer();
        }
        throw new IllegalArgumentException("Unsupported checkpoint type: " + type);
    }
}
