package com.electrahub.gateway.config;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConfigurationProperties(prefix = "app.cors")
public class CorsProperties {
    private static final Logger LOGGER = LoggerFactory.getLogger(CorsProperties.class);


    private List<String> allowedOriginPatterns = List.of();

    /**
     * Retrieves get allowed origin patterns for `CorsProperties`.
     *
     * <p>Detailed behavior: follows the current implementation path and
     * enforces component-specific rules in `com.electrahub.gateway.config`.
     * @return result produced by getAllowedOriginPatterns.
     */
    public List<String> getAllowedOriginPatterns() {
        LOGGER.info("CODEx_ENTRY_LOG: Entering CorsProperties#getAllowedOriginPatterns");
        LOGGER.debug("CODEx_ENTRY_LOG: Entering CorsProperties#getAllowedOriginPatterns with debug context");
        return allowedOriginPatterns;
    }

    /**
     * Updates set allowed origin patterns for `CorsProperties`.
     *
     * <p>Detailed behavior: follows the current implementation path and
     * enforces component-specific rules in `com.electrahub.gateway.config`.
     * @param allowedOriginPatterns input consumed by setAllowedOriginPatterns.
     */
    public void setAllowedOriginPatterns(List<String> allowedOriginPatterns) {
        this.allowedOriginPatterns = allowedOriginPatterns;
    }
}
