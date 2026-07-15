package com.electrahub.gateway.security;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import com.electrahub.gateway.config.RbacProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiPolicyAuthorizationManagerTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(ApiPolicyAuthorizationManagerTest.class);


    /**
     * Executes allows anonymous for public rule for `ApiPolicyAuthorizationManagerTest`.
     *
     * <p>Detailed behavior: follows the current implementation path and
     * enforces component-specific rules in `com.electrahub.gateway.security`.
     */
    @Test
    void allowsAnonymousForPublicRule() {
        LOGGER.info(" Entering ApiPolicyAuthorizationManagerTest#allowsAnonymousForPublicRule");
        LOGGER.debug(" Entering ApiPolicyAuthorizationManagerTest#allowsAnonymousForPublicRule with debug context");
        var properties = new RbacProperties();
        properties.setRules(List.of(
                rule("public-health", List.of("GET"), "/actuator/health/**", true, List.of())
        ));

        var manager = manager(properties);
        var decision = authorize(manager, "GET", "/actuator/health/readiness", () -> null);

        assertTrue(decision.isGranted());
    }

    /**
     * Executes denies when no rule matches and default is deny for `ApiPolicyAuthorizationManagerTest`.
     *
     * <p>Detailed behavior: follows the current implementation path and
     * enforces component-specific rules in `com.electrahub.gateway.security`.
     */
    @Test
    void deniesWhenNoRuleMatchesAndDefaultIsDeny() {
        var properties = new RbacProperties();
        properties.setDefaultDecision(RbacProperties.Decision.DENY);
        properties.setRules(List.of(
                rule("known", List.of("GET"), "/known/**", false, List.of("USER"))
        ));

        var manager = manager(properties);
        var authenticationResolved = new AtomicBoolean(false);
        var decision = authorize(manager, "GET", "/unknown/path", () -> {
            authenticationResolved.set(true);
            return authenticationWithRoles("USER");
        });

        assertFalse(decision.isGranted());
        assertTrue(authenticationResolved.get());
    }

    /**
     * Executes allows system admin through role hierarchy for `ApiPolicyAuthorizationManagerTest`.
     *
     * <p>Detailed behavior: follows the current implementation path and
     * enforces component-specific rules in `com.electrahub.gateway.security`.
     */
    @Test
    void allowsSystemAdminThroughRoleHierarchy() {
        var properties = new RbacProperties();
        properties.setRules(List.of(
                rule("user-protected", List.of("*"), "/user/**", false, List.of("USER"))
        ));

        var manager = manager(properties);
        var decision = authorize(manager, "GET", "/user/api/v1/profile", () -> authenticationWithRoles("SYSTEM_ADMIN"));

        assertTrue(decision.isGranted());
    }

    /**
     * Executes explicit deny overrides allow for `ApiPolicyAuthorizationManagerTest`.
     *
     * <p>Detailed behavior: follows the current implementation path and
     * enforces component-specific rules in `com.electrahub.gateway.security`.
     */
    @Test
    void explicitDenyOverridesAllow() {
        var properties = new RbacProperties();
        properties.setRules(List.of(
                rule("user-allow", List.of("*"), "/user/**", false, List.of("USER")),
                denyRule("user-sensitive-deny", List.of("*"), "/user/api/v1/admin/**")
        ));

        var manager = manager(properties);
        var authenticationResolved = new AtomicBoolean(false);
        var decision = authorize(manager, "GET", "/user/api/v1/admin/users", () -> {
            authenticationResolved.set(true);
            return authenticationWithRoles("SYSTEM_ADMIN");
        });

        assertFalse(decision.isGranted());
        assertTrue(authenticationResolved.get());
    }

    @Test
    void roleScopedDenyBlocksReadOnlyAdminWrites() {
        var properties = new RbacProperties();
        properties.setRules(List.of(
                denyRule("readonly-admin-write-deny", List.of("POST", "PUT", "PATCH", "DELETE"), "/charger/api/v1/admin/**", List.of("ADMIN_READ_ONLY")),
                rule("charger-admin-readonly", List.of("GET"), "/charger/api/v1/admin/**", false, List.of("ADMIN_READ_ONLY")),
                rule("charger-admin-service", List.of("*"), "/charger/api/v1/admin/**", false, List.of("SYSTEM_ADMIN"))
        ));

        var manager = manager(properties);

        assertFalse(authorize(manager, "POST", "/charger/api/v1/admin/enterprises",
                () -> authenticationWithRoles("ADMIN_READ_ONLY", "USER")).isGranted());
        assertTrue(authorize(manager, "GET", "/charger/api/v1/admin/enterprises",
                () -> authenticationWithRoles("ADMIN_READ_ONLY", "USER")).isGranted());
    }

    @Test
    void roleScopedDenyDoesNotBlockSystemAdminWrites() {
        var properties = new RbacProperties();
        properties.setRules(List.of(
                denyRule("readonly-admin-write-deny", List.of("POST", "PUT", "PATCH", "DELETE"), "/charger/api/v1/admin/**", List.of("ADMIN_READ_ONLY")),
                rule("charger-admin-service", List.of("*"), "/charger/api/v1/admin/**", false, List.of("SYSTEM_ADMIN"))
        ));

        var manager = manager(properties);
        var decision = authorize(manager, "POST", "/charger/api/v1/admin/enterprises",
                () -> authenticationWithRoles("SYSTEM_ADMIN", "USER"));

        assertTrue(decision.isGranted());
    }

    @Test
    void readOnlyAdminCannotReadUserDirectoriesButSystemAdminCan() {
        var properties = new RbacProperties();
        properties.setRules(List.of(
                denyRule("readonly-admin-users-root-deny", List.of("*"), "/user/api/v1/users", List.of("ADMIN_READ_ONLY")),
                denyRule("readonly-admin-users-deny", List.of("*"), "/user/api/v1/users/**", List.of("ADMIN_READ_ONLY")),
                denyRule("readonly-admin-admin-users-root-deny", List.of("*"), "/user/api/v1/admin/users", List.of("ADMIN_READ_ONLY")),
                denyRule("readonly-admin-admin-users-deny", List.of("*"), "/user/api/v1/admin/users/**", List.of("ADMIN_READ_ONLY")),
                rule("user-service", List.of("*"), "/user/**", false, List.of("USER"))
        ));

        var manager = manager(properties);

        assertFalse(authorize(manager, "GET", "/user/api/v1/users",
                () -> authenticationWithRoles("ADMIN_READ_ONLY", "USER")).isGranted());
        assertFalse(authorize(manager, "GET", "/user/api/v1/users/7/profile",
                () -> authenticationWithRoles("ADMIN_READ_ONLY", "USER")).isGranted());
        assertFalse(authorize(manager, "GET", "/user/api/v1/admin/users",
                () -> authenticationWithRoles("ADMIN_READ_ONLY", "USER")).isGranted());
        assertFalse(authorize(manager, "GET", "/user/api/v1/admin/users/7",
                () -> authenticationWithRoles("ADMIN_READ_ONLY", "USER")).isGranted());

        assertTrue(authorize(manager, "GET", "/user/api/v1/users",
                () -> authenticationWithRoles("SYSTEM_ADMIN", "USER")).isGranted());
        assertTrue(authorize(manager, "GET", "/user/api/v1/admin/users",
                () -> authenticationWithRoles("SYSTEM_ADMIN", "USER")).isGranted());
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

    /**
     * Executes authentication with roles for `ApiPolicyAuthorizationManagerTest`.
     *
     * <p>Detailed behavior: follows the current implementation path and
     * enforces component-specific rules in `com.electrahub.gateway.security`.
     * @param roles input consumed by authenticationWithRoles.
     * @return result produced by authenticationWithRoles.
     */
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

    /**
     * Executes deny rule for `ApiPolicyAuthorizationManagerTest`.
     *
     * <p>Detailed behavior: follows the current implementation path and
     * enforces component-specific rules in `com.electrahub.gateway.security`.
     * @param name input consumed by denyRule.
     * @param methods input consumed by denyRule.
     * @param pathPattern input consumed by denyRule.
     * @return result produced by denyRule.
     */
    private static RbacProperties.Rule denyRule(String name, List<String> methods, String pathPattern) {
        return denyRule(name, methods, pathPattern, List.of());
    }

    private static RbacProperties.Rule denyRule(String name, List<String> methods, String pathPattern, List<String> roles) {
        var rule = new RbacProperties.Rule();
        rule.setName(name);
        rule.setMethods(methods);
        rule.setPathPattern(pathPattern);
        rule.setEffect(RbacProperties.Decision.DENY);
        rule.setAllowAnonymous(false);
        rule.setRequiredRoles(roles);
        return rule;
    }

    /**
     * Executes manager for `ApiPolicyAuthorizationManagerTest`.
     *
     * <p>Detailed behavior: follows the current implementation path and
     * enforces component-specific rules in `com.electrahub.gateway.security`.
     * @param properties input consumed by manager.
     * @return result produced by manager.
     */
    private static ApiPolicyAuthorizationManager manager(RbacProperties properties) {
        return new ApiPolicyAuthorizationManager(new FixedSnapshotProvider(RbacPolicySnapshot.fromProperties(properties)));
    }

    private static final class FixedSnapshotProvider implements RbacPolicySnapshotProvider {
        private final RbacPolicySnapshot snapshot;

        private FixedSnapshotProvider(RbacPolicySnapshot snapshot) {
            this.snapshot = snapshot;
        }

        @Override
        public RbacPolicySnapshot currentPolicy() {
            return snapshot;
        }

        @Override
        public void invalidate() {
            // no-op for tests
        }
    }
}
