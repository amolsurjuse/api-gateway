package com.electrahub.gateway.security;

import com.electrahub.gateway.config.RbacProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiPolicyAuthorizationManagerTest {

    private static final RoleHierarchy ROLE_HIERARCHY =
            RoleHierarchyImpl.fromHierarchy("ROLE_SYSTEM_ADMIN > ROLE_USER");

    @Test
    void allowsAnonymousForPublicRule() {
        var properties = new RbacProperties();
        properties.setRules(List.of(
                rule("public-health", List.of("GET"), "/actuator/health/**", true, List.of())
        ));

        var manager = new ApiPolicyAuthorizationManager(properties, ROLE_HIERARCHY);
        var decision = authorize(manager, "GET", "/actuator/health/readiness", () -> null);

        assertTrue(decision.isGranted());
    }

    @Test
    void deniesWhenNoRuleMatchesAndDefaultIsDeny() {
        var properties = new RbacProperties();
        properties.setDefaultDecision(RbacProperties.Decision.DENY);
        properties.setRules(List.of(
                rule("known", List.of("GET"), "/known/**", false, List.of("USER"))
        ));

        var manager = new ApiPolicyAuthorizationManager(properties, ROLE_HIERARCHY);
        var decision = authorize(manager, "GET", "/unknown/path", () -> authenticationWithRoles("USER"));

        assertFalse(decision.isGranted());
    }

    @Test
    void allowsSystemAdminThroughRoleHierarchy() {
        var properties = new RbacProperties();
        properties.setRules(List.of(
                rule("user-protected", List.of("*"), "/user/**", false, List.of("USER"))
        ));

        var manager = new ApiPolicyAuthorizationManager(properties, ROLE_HIERARCHY);
        var decision = authorize(manager, "GET", "/user/api/v1/profile", () -> authenticationWithRoles("SYSTEM_ADMIN"));

        assertTrue(decision.isGranted());
    }

    @Test
    void explicitDenyOverridesAllow() {
        var properties = new RbacProperties();
        properties.setRules(List.of(
                rule("user-allow", List.of("*"), "/user/**", false, List.of("USER")),
                denyRule("user-sensitive-deny", List.of("*"), "/user/api/v1/admin/**")
        ));

        var manager = new ApiPolicyAuthorizationManager(properties, ROLE_HIERARCHY);
        var decision = authorize(manager, "GET", "/user/api/v1/admin/users", () -> authenticationWithRoles("SYSTEM_ADMIN"));

        assertFalse(decision.isGranted());
    }

    private static AuthorizationDecision authorize(
            ApiPolicyAuthorizationManager manager,
            String method,
            String path,
            Supplier<Authentication> authenticationSupplier
    ) {
        var request = new MockHttpServletRequest(method, path);
        var context = new RequestAuthorizationContext(request);
        AuthorizationResult result = manager.authorize(authenticationSupplier, context);
        return new AuthorizationDecision(result != null && result.isGranted());
    }

    private static Authentication authenticationWithRoles(String... roles) {
        var authorities = Arrays.stream(roles)
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .toList();

        return new UsernamePasswordAuthenticationToken("user@example.com", "n/a", authorities);
    }

    private static RbacProperties.Rule rule(
            String name,
            List<String> methods,
            String pathPattern,
            boolean allowAnonymous,
            List<String> roles
    ) {
        var rule = new RbacProperties.Rule();
        rule.setName(name);
        rule.setMethods(methods);
        rule.setPathPattern(pathPattern);
        rule.setEffect(RbacProperties.Decision.ALLOW);
        rule.setAllowAnonymous(allowAnonymous);
        rule.setRequiredRoles(roles);
        return rule;
    }

    private static RbacProperties.Rule denyRule(String name, List<String> methods, String pathPattern) {
        var rule = new RbacProperties.Rule();
        rule.setName(name);
        rule.setMethods(methods);
        rule.setPathPattern(pathPattern);
        rule.setEffect(RbacProperties.Decision.DENY);
        rule.setAllowAnonymous(false);
        rule.setRequiredRoles(List.of());
        return rule;
    }
}
