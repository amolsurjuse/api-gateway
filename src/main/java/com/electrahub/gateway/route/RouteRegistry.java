package com.electrahub.gateway.route;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Configuration-driven route registry that maps path prefixes to backend service URLs.
 *
 * Loaded from application.yaml:
 *   gateway.routes.auth=http://auth-service:8080
 *   gateway.routes.user=http://user-service:8082
 *   ...
 */
@Component
@ConfigurationProperties(prefix = "gateway")
public class RouteRegistry {
    private static final Logger LOGGER = LoggerFactory.getLogger(RouteRegistry.class);


    private Map<String, String> routes = new LinkedHashMap<>();

    /**
     * Retrieves get routes for `RouteRegistry`.
     *
     * <p>Detailed behavior: follows the current implementation path and
     * enforces component-specific rules in `com.electrahub.gateway.route`.
     * @return result produced by getRoutes.
     */
    public Map<String, String> getRoutes() {
        LOGGER.info("CODEx_ENTRY_LOG: Entering RouteRegistry#getRoutes");
        LOGGER.debug("CODEx_ENTRY_LOG: Entering RouteRegistry#getRoutes with debug context");
        return routes;
    }

    /**
     * Updates set routes for `RouteRegistry`.
     *
     * <p>Detailed behavior: follows the current implementation path and
     * enforces component-specific rules in `com.electrahub.gateway.route`.
     * @param Map<String input consumed by setRoutes.
     * @param routes input consumed by setRoutes.
     */
    public void setRoutes(Map<String, String> routes) {
        this.routes = routes;
    }

    /**
     * Resolve a path prefix to a backend service base URL.
     *
     * @param prefix the first path segment (e.g. "user", "auth", "subscription")
     * @return the backend URL or null if no route matches
     */
    public String resolve(String prefix) {
        return routes.get(prefix);
    }
}
