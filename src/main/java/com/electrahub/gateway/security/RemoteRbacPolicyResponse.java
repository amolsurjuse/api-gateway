package com.electrahub.gateway.security;

import java.util.List;

public record RemoteRbacPolicyResponse(
        String policyKey,
        String roleHierarchy,
        String defaultDecision,
        long version,
        List<RemoteRbacRuleResponse> rules
) {

    public record RemoteRbacRuleResponse(
            String name,
            List<String> methods,
            String pathPattern,
            String effect,
            boolean allowAnonymous,
            List<String> requiredRoles
    ) {
    }
}
