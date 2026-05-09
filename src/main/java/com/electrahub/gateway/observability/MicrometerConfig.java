package com.electrahub.gateway.observability;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.config.MeterFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MicrometerConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger(MicrometerConfig.class);


    @Bean
    public MeterFilter commonApplicationTags(
            /**
             * Executes value for `MicrometerConfig`.
             *
             * <p>Detailed behavior: follows the current implementation path and
             * enforces component-specific rules in `com.electrahub.gateway.observability`.
             * @param applicationName input consumed by Value.
             * @return result produced by Value.
             */
            @Value("${spring.application.name:unknown-service}") String applicationName) {
                LOGGER.info(" Entering MicrometerConfig#Value");
                LOGGER.debug(" Entering MicrometerConfig#Value with debug context");
        return MeterFilter.commonTags(Tags.of("application", applicationName));
    }
}
