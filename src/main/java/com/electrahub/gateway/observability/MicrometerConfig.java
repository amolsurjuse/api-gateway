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
    public MeterFilter commonApplicationTags(@Value("${spring.application.name:unknown-service}") String applicationName) {
        LOGGER.debug("Registering Micrometer common tags for application={}", applicationName);
        return MeterFilter.commonTags(Tags.of("application", applicationName));
    }
}
