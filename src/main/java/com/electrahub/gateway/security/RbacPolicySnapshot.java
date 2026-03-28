package com.electrahub.gateway.security;

import com.electrahub.gateway.config.RbacProperties;

import java.util.List;

public record RbacPolicySnapshot(
        long version,
        String roleHierarchy,
        RbacProperties.Decision defaultDecision,
        List<RbacRuleSnapshot> rules
) {
    /**
     * Executes from properties for `RbacPolicySnapshot`.
     *
     * <p>Detailed behavior: follows the current implementation path and
     * enforces component-specific rules in `com.electrahub.gateway.security`.
     * @param properties input consumed by fromProperties.
     * @return result produced by fromProperties.
     */
    public static RbacPolicySnapshot fromProperties(RbacProperties properties) {
        List<RbacRuleSnapshot> ruleSnapshots = properties.getRules().stream()
                .map(rule -> new RbacRuleSnapshot(
                        rule.getName(),
                        rule.getMethods(),
                        rule.getPathPattern(),
                        rule.getEffect() == null ? RbacProperties.Decision.ALLOW : rule.getEffect(),
                        rule.isAllowAnonymous(),
                        rule.getRequiredRoles()
                ))
                .toList();

        return new RbacPolicySnapshot(
                0L,
                properties.getRoleHierarchy(),
                properties.getDefaultDecision() == null ? RbacProperties.Decision.DENY : properties.getDefaultDecision(),
                ruleSnapshots
        );
    }

    public record RbacRuleSnapshot(
            String name,
            List<String> methods,
            String pathPattern,
            RbacProperties.Decision effect,
            boolean allowAnonymous,
            List<String> requiredRoles
    ) {
    }
}
