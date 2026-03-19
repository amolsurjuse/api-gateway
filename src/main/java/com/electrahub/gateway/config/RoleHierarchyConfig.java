package com.electrahub.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;

@Configuration
public class RoleHierarchyConfig {

    @Bean
    public RoleHierarchy roleHierarchy(RbacProperties rbacProperties) {
        return RoleHierarchyImpl.fromHierarchy(rbacProperties.getRoleHierarchy());
    }
}
