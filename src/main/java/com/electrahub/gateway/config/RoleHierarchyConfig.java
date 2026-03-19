package com.electrahub.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;

@Configuration
public class RoleHierarchyConfig {

    /**
     * Define the role hierarchy: higher roles inherit all permissions of lower roles.
     * SYSTEM_ADMIN > USER means any SYSTEM_ADMIN automatically has USER access.
     *
     * To extend later, chain roles:
     *   ROLE_SYSTEM_ADMIN > ROLE_OPERATOR \n ROLE_OPERATOR > ROLE_USER
     */
    @Bean
    public RoleHierarchy roleHierarchy() {
        return RoleHierarchyImpl.fromHierarchy("ROLE_SYSTEM_ADMIN > ROLE_USER");
    }
}
