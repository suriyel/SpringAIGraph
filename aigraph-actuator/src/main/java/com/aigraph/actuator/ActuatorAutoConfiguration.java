package com.aigraph.actuator;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Auto-configuration for AIGraph Actuator integration.
 *
 * @author AIGraph Team
 * @since 0.0.8
 */
@Configuration
@ConditionalOnClass({MeterRegistry.class})
public class ActuatorAutoConfiguration {

    @Bean
    @ConditionalOnBean(MeterRegistry.class)
    public PregelMetrics pregelMetrics(MeterRegistry registry) {
        return new PregelMetrics(registry);
    }

    @Bean
    public MeterRegistryCustomizer<MeterRegistry> aiGraphMetricsCommonTags() {
        return registry -> registry.config().commonTags(
            "application", "aigraph"
        );
    }
}
