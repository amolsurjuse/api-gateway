package com.electrahub.gateway.config;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;

@Configuration
public class RoleHierarchyConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger(RoleHierarchyConfig.class);


    /**
     * Executes role hierarchy for `RoleHierarchyConfig`.
     *
     * <p>Detailed behavior: follows the current implementation path and
     * enforces component-specific rules in `com.electrahub.gateway.config`.
     * @param rbacProperties input consumed by roleHierarchy.
     * @return result produced by roleHierarchy.
     */
    @Bean
    public RoleHierarchy roleHierarchy(RbacProperties rbacProperties) {
        LOGGER.debug("Creating RoleHierarchy bean from configured RBAC hierarchy");
        return RoleHierarchyImpl.fromHierarchy(rbacProperties.getRoleHierarchy());
    }
}
