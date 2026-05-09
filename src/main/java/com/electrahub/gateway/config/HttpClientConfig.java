package com.electrahub.gateway.config;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class HttpClientConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger(HttpClientConfig.class);


    /**
     * Executes rest client builder for `HttpClientConfig`.
     *
     * <p>Detailed behavior: follows the current implementation path and
     * enforces component-specific rules in `com.electrahub.gateway.config`.
     * @return result produced by restClientBuilder.
     */
    @Bean
    RestClient.Builder restClientBuilder() {
        LOGGER.debug("Creating shared RestClient.Builder for gateway outbound calls");
        return RestClient.builder();
    }
}
