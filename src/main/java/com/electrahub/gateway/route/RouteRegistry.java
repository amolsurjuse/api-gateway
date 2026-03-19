package com.electrahub.gateway.route;

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

    private Map<String, String> routes = new LinkedHashMap<>();

    public Map<String, String> getRoutes() {
        return routes;
    }

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
