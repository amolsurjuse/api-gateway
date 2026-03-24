package com.electrahub.gateway.config;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "app.rbac")
public class RbacProperties {
    private static final Logger LOGGER = LoggerFactory.getLogger(RbacProperties.class);


    private String roleHierarchy = "ROLE_SYSTEM_ADMIN > ROLE_USER";
    private Decision defaultDecision = Decision.DENY;
    private String internalApiKey = "dev-rbac-internal-key";
    private RemotePolicy remotePolicy = new RemotePolicy();
    private List<Rule> rules = new ArrayList<>();

    /**
     * Retrieves get role hierarchy for `RbacProperties`.
     *
     * <p>Detailed behavior: follows the current implementation path and
     * enforces component-specific rules in `com.electrahub.gateway.config`.
     * @return result produced by getRoleHierarchy.
     */
    public String getRoleHierarchy() {
        LOGGER.info("CODEx_ENTRY_LOG: Entering RbacProperties#getRoleHierarchy");
        LOGGER.debug("CODEx_ENTRY_LOG: Entering RbacProperties#getRoleHierarchy with debug context");
        return roleHierarchy;
    }

    /**
     * Updates set role hierarchy for `RbacProperties`.
     *
     * <p>Detailed behavior: follows the current implementation path and
     * enforces component-specific rules in `com.electrahub.gateway.config`.
     * @param roleHierarchy input consumed by setRoleHierarchy.
     */
    public void setRoleHierarchy(String roleHierarchy) {
        this.roleHierarchy = roleHierarchy;
    }

    /**
     * Retrieves get default decision for `RbacProperties`.
     *
     * <p>Detailed behavior: follows the current implementation path and
     * enforces component-specific rules in `com.electrahub.gateway.config`.
     * @return result produced by getDefaultDecision.
     */
    public Decision getDefaultDecision() {
        return defaultDecision;
    }

    /**
     * Updates set default decision for `RbacProperties`.
     *
     * <p>Detailed behavior: follows the current implementation path and
     * enforces component-specific rules in `com.electrahub.gateway.config`.
     * @param defaultDecision input consumed by setDefaultDecision.
     */
    public void setDefaultDecision(Decision defaultDecision) {
        this.defaultDecision = defaultDecision;
    }

    /**
     * Retrieves get internal api key for `RbacProperties`.
     *
     * <p>Detailed behavior: follows the current implementation path and
     * enforces component-specific rules in `com.electrahub.gateway.config`.
     * @return result produced by getInternalApiKey.
     */
    public String getInternalApiKey() {
        return internalApiKey;
    }

    /**
     * Updates set internal api key for `RbacProperties`.
     *
     * <p>Detailed behavior: follows the current implementation path and
     * enforces component-specific rules in `com.electrahub.gateway.config`.
     * @param internalApiKey input consumed by setInternalApiKey.
     */
    public void setInternalApiKey(String internalApiKey) {
        this.internalApiKey = internalApiKey;
    }

    /**
     * Retrieves get remote policy for `RbacProperties`.
     *
     * <p>Detailed behavior: follows the current implementation path and
     * enforces component-specific rules in `com.electrahub.gateway.config`.
     * @return result produced by getRemotePolicy.
     */
    public RemotePolicy getRemotePolicy() {
        return remotePolicy;
    }

    /**
     * Updates set remote policy for `RbacProperties`.
     *
     * <p>Detailed behavior: follows the current implementation path and
     * enforces component-specific rules in `com.electrahub.gateway.config`.
     * @param remotePolicy input consumed by setRemotePolicy.
     */
    public void setRemotePolicy(RemotePolicy remotePolicy) {
        this.remotePolicy = remotePolicy;
    }

    /**
     * Retrieves get rules for `RbacProperties`.
     *
     * <p>Detailed behavior: follows the current implementation path and
     * enforces component-specific rules in `com.electrahub.gateway.config`.
     * @return result produced by getRules.
     */
    public List<Rule> getRules() {
        return rules;
    }

    /**
     * Updates set rules for `RbacProperties`.
     *
     * <p>Detailed behavior: follows the current implementation path and
     * enforces component-specific rules in `com.electrahub.gateway.config`.
     * @param rules input consumed by setRules.
     */
    public void setRules(List<Rule> rules) {
        this.rules = rules;
    }

    public enum Decision {
        ALLOW,
        DENY
    }

    public static class Rule {
        private String name;
        private List<String> methods = List.of("*");
        private String pathPattern = "/**";
        private Decision effect = Decision.ALLOW;
        private boolean allowAnonymous = false;
        private List<String> requiredRoles = List.of();

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public List<String> getMethods() {
            return methods;
        }

        public void setMethods(List<String> methods) {
            this.methods = methods;
        }

        public String getPathPattern() {
            return pathPattern;
        }

        public void setPathPattern(String pathPattern) {
            this.pathPattern = pathPattern;
        }

        public Decision getEffect() {
            return effect;
        }

        public void setEffect(Decision effect) {
            this.effect = effect;
        }

        public boolean isAllowAnonymous() {
            return allowAnonymous;
        }

        public void setAllowAnonymous(boolean allowAnonymous) {
            this.allowAnonymous = allowAnonymous;
        }

        public List<String> getRequiredRoles() {
            return requiredRoles;
        }

        public void setRequiredRoles(List<String> requiredRoles) {
            this.requiredRoles = requiredRoles;
        }
    }

    public static class RemotePolicy {
        private boolean enabled = true;
        private String sourceUrl = "http://user-service:8082/api/internal/rbac/policy";
        private Duration refreshInterval = Duration.ofSeconds(30);

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getSourceUrl() {
            return sourceUrl;
        }

        public void setSourceUrl(String sourceUrl) {
            this.sourceUrl = sourceUrl;
        }

        public Duration getRefreshInterval() {
            return refreshInterval;
        }

        public void setRefreshInterval(Duration refreshInterval) {
            this.refreshInterval = refreshInterval;
        }
    }
}
