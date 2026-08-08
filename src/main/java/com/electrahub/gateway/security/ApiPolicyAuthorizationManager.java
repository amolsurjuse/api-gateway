package com.electrahub.gateway.security;

import com.electrahub.gateway.config.RbacProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Component
public class ApiPolicyAuthorizationManager implements AuthorizationManager<RequestAuthorizationContext> {

    private static final Logger log = LoggerFactory.getLogger(ApiPolicyAuthorizationManager.class);
    private static final String ROLE_PREFIX = "ROLE_";
    private static final Set<String> SAFE_READ_ONLY_METHODS = Set.of("GET", "HEAD", "OPTIONS");

    private final RbacPolicySnapshotProvider policySnapshotProvider;
    private final AntPathMatcher antPathMatcher = new AntPathMatcher();
    private volatile CompiledPolicy compiledPolicy;

    /**
     * Executes api policy authorization manager for `ApiPolicyAuthorizationManager`.
     *
     * <p>Detailed behavior: follows the current implementation path and
     * enforces component-specific rules in `com.electrahub.gateway.security`.
     * @param policySnapshotProvider input consumed by ApiPolicyAuthorizationManager.
     */
    public ApiPolicyAuthorizationManager(RbacPolicySnapshotProvider policySnapshotProvider) {
        log.info(" Entering ApiPolicyAuthorizationManager#ApiPolicyAuthorizationManager");
        log.debug(" Entering ApiPolicyAuthorizationManager#ApiPolicyAuthorizationManager with debug context");
        this.policySnapshotProvider = policySnapshotProvider;
    }

    @Override
    public AuthorizationResult authorize(
            Supplier<? extends Authentication> authenticationSupplier,
            RequestAuthorizationContext requestAuthorizationContext
    ) {
        CompiledPolicy currentPolicy = resolveCompiledPolicy();
        HttpServletRequest request = requestAuthorizationContext.getRequest();
        String method = request.getMethod().toUpperCase(Locale.ROOT);
        String path = request.getRequestURI();
        Authentication authentication = null;

        Authentication candidate = authenticationSupplier.get();
        if (isReadOnlyAdmin(candidate, currentPolicy.roleHierarchy())) {
            if (!SAFE_READ_ONLY_METHODS.contains(method)) {
                log.debug("RBAC decision: method={} path={} principal={} granted=false reason=read-only-method",
                        method, path, principal(candidate));
                return new AuthorizationDecision(false);
            }
            if (isRestrictedReadOnlyPath(path)) {
                log.debug("RBAC decision: method={} path={} principal={} granted=false reason=read-only-sensitive-data",
                        method, path, principal(candidate));
                return new AuthorizationDecision(false);
            }
            log.debug("RBAC decision: method={} path={} principal={} granted=true reason=read-only-safe-method",
                    method, path, principal(candidate));
            return new AuthorizationDecision(true);
        }

        boolean matchedAnyRule = false;
        boolean grantedByAllowRule = false;

        for (CompiledRule rule : currentPolicy.rules()) {
            if (!rule.matches(method, path, antPathMatcher)) {
                continue;
            }

            matchedAnyRule = true;
            if (rule.effect() == RbacProperties.Decision.DENY) {
                if (authentication == null) {
                    authentication = authenticationSupplier.get();
                }
                if (!denyApplies(rule, authentication, currentPolicy.roleHierarchy())) {
                    continue;
                }
                if (log.isDebugEnabled()) {
                    log.debug("RBAC decision: method={} path={} principal={} rule={} granted=false reason=explicit-deny",
                            method, path, principal(authentication), rule.name());
                }
                return new AuthorizationDecision(false);
            }

            if (authentication == null) {
                authentication = authenticationSupplier.get();
            }

            if (evaluateAllowRule(rule, authentication, currentPolicy.roleHierarchy())) {
                grantedByAllowRule = true;
            }
        }

        if (matchedAnyRule) {
            if (log.isDebugEnabled()) {
                log.debug("RBAC decision: method={} path={} principal={} roles={} rule=<matched> granted={}",
                        method, path, principal(authentication), extractRolesSafe(authentication), grantedByAllowRule);
            }
            return new AuthorizationDecision(grantedByAllowRule);
        }

        boolean granted = currentPolicy.defaultDecision() == RbacProperties.Decision.ALLOW;
        if (!granted && authentication == null) {
            authentication = authenticationSupplier.get();
        }
        if (log.isDebugEnabled()) {
            log.debug("RBAC decision: method={} path={} principal={} rule=<default> granted={}",
                    method, path, principal(authentication), granted);
        }
        return new AuthorizationDecision(granted);
    }

    private boolean denyApplies(CompiledRule rule, Authentication authentication, RoleHierarchy roleHierarchy) {
        if (rule.requiredRoles().isEmpty()) {
            return true;
        }
        if (isAnonymous(authentication)) {
            return false;
        }
        Set<String> effectiveRoles = extractRoles(authentication, roleHierarchy);
        return rule.requiredRoles().stream().anyMatch(effectiveRoles::contains);
    }

    /**
     * Executes evaluate allow rule for `ApiPolicyAuthorizationManager`.
     *
     * <p>Detailed behavior: follows the current implementation path and
     * enforces component-specific rules in `com.electrahub.gateway.security`.
     * @param rule input consumed by evaluateAllowRule.
     * @param authentication input consumed by evaluateAllowRule.
     * @param roleHierarchy input consumed by evaluateAllowRule.
     * @return result produced by evaluateAllowRule.
     */
    private boolean evaluateAllowRule(CompiledRule rule, Authentication authentication, RoleHierarchy roleHierarchy) {
        if (rule.allowAnonymous()) {
            return true;
        }

        if (isAnonymous(authentication)) {
            return false;
        }

        if (rule.requiredRoles().isEmpty()) {
            return true;
        }

        Set<String> effectiveRoles = extractRoles(authentication, roleHierarchy);
        return rule.requiredRoles().stream().anyMatch(effectiveRoles::contains);
    }

    /**
     * Executes is anonymous for `ApiPolicyAuthorizationManager`.
     *
     * <p>Detailed behavior: follows the current implementation path and
     * enforces component-specific rules in `com.electrahub.gateway.security`.
     * @param authentication input consumed by isAnonymous.
     * @return result produced by isAnonymous.
     */
    private boolean isAnonymous(Authentication authentication) {
        return authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken;
    }

    private boolean isReadOnlyAdmin(Authentication authentication, RoleHierarchy roleHierarchy) {
        if (isAnonymous(authentication)) {
            return false;
        }
        Set<String> roles = extractRoles(authentication, roleHierarchy);
        return roles.contains("ADMIN_READ_ONLY") && !roles.contains("SYSTEM_ADMIN");
    }

    private boolean isRestrictedReadOnlyPath(String path) {
        return path.equals("/user/api/v1/users")
                || path.startsWith("/user/api/v1/users/")
                || path.equals("/user/api/v1/admin/users")
                || path.startsWith("/user/api/v1/admin/users/")
                || path.equals("/billing/api/v1/admin/analytics/users")
                || path.startsWith("/billing/api/v1/admin/analytics/users/")
                || path.equals("/billing/api/v1/admin/analytics/reports")
                || path.startsWith("/billing/api/v1/admin/analytics/reports/");
    }

    /**
     * Executes extract roles for `ApiPolicyAuthorizationManager`.
     *
     * <p>Detailed behavior: follows the current implementation path and
     * enforces component-specific rules in `com.electrahub.gateway.security`.
     * @param authentication input consumed by extractRoles.
     * @param roleHierarchy input consumed by extractRoles.
     * @return result produced by extractRoles.
     */
    private Set<String> extractRoles(Authentication authentication, RoleHierarchy roleHierarchy) {
        Collection<? extends GrantedAuthority> grantedAuthorities =
                roleHierarchy.getReachableGrantedAuthorities(authentication.getAuthorities());

        return grantedAuthorities.stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> authority.startsWith(ROLE_PREFIX))
                .map(authority -> authority.substring(ROLE_PREFIX.length()))
                .map(role -> role.toUpperCase(Locale.ROOT))
                .collect(Collectors.toSet());
    }

    /**
     * Executes extract roles safe for `ApiPolicyAuthorizationManager`.
     *
     * <p>Detailed behavior: follows the current implementation path and
     * enforces component-specific rules in `com.electrahub.gateway.security`.
     * @param authentication input consumed by extractRolesSafe.
     * @return result produced by extractRolesSafe.
     */
    private Set<String> extractRolesSafe(Authentication authentication) {
        if (isAnonymous(authentication)) {
            return Set.of();
        }
        return extractRoles(authentication, resolveCompiledPolicy().roleHierarchy());
    }

    /**
     * Executes principal for `ApiPolicyAuthorizationManager`.
     *
     * <p>Detailed behavior: follows the current implementation path and
     * enforces component-specific rules in `com.electrahub.gateway.security`.
     * @param authentication input consumed by principal.
     * @return result produced by principal.
     */
    private String principal(Authentication authentication) {
        if (isAnonymous(authentication)) {
            return "anonymous";
        }
        return Objects.toString(authentication.getPrincipal(), "unknown");
    }

    /**
     * Executes resolve compiled policy for `ApiPolicyAuthorizationManager`.
     *
     * <p>Detailed behavior: follows the current implementation path and
     * enforces component-specific rules in `com.electrahub.gateway.security`.
     * @return result produced by resolveCompiledPolicy.
     */
    private CompiledPolicy resolveCompiledPolicy() {
        RbacPolicySnapshot snapshot = policySnapshotProvider.currentPolicy();
        CompiledPolicy current = compiledPolicy;
        if (current != null && current.version() == snapshot.version()) {
            return current;
        }

        synchronized (this) {
            current = compiledPolicy;
            if (current != null && current.version() == snapshot.version()) {
                return current;
            }

            RoleHierarchy hierarchy;
            try {
                hierarchy = RoleHierarchyImpl.fromHierarchy(snapshot.roleHierarchy());
            } catch (Exception ex) {
                log.warn("Invalid RBAC role hierarchy received; defaulting to ROLE_SYSTEM_ADMIN > ROLE_USER. cause={}", ex.getMessage());
                hierarchy = RoleHierarchyImpl.fromHierarchy("ROLE_SYSTEM_ADMIN > ROLE_USER");
            }

            RbacProperties.Decision defaultDecision = snapshot.defaultDecision() == null
                    ? RbacProperties.Decision.DENY
                    : snapshot.defaultDecision();

            List<CompiledRule> compiledRules = snapshot.rules() == null
                    ? List.of()
                    : snapshot.rules().stream()
                    .map(this::compileRule)
                    .toList();

            CompiledPolicy resolved = new CompiledPolicy(snapshot.version(), hierarchy, defaultDecision, compiledRules);
            compiledPolicy = resolved;
            return resolved;
        }
    }

    /**
     * Executes compile rule for `ApiPolicyAuthorizationManager`.
     *
     * <p>Detailed behavior: follows the current implementation path and
     * enforces component-specific rules in `com.electrahub.gateway.security`.
     * @param rule input consumed by compileRule.
     * @return result produced by compileRule.
     */
    private CompiledRule compileRule(RbacPolicySnapshot.RbacRuleSnapshot rule) {
        String compiledName = rule.name() == null ? "<unnamed>" : rule.name().trim();
        String pathPattern = normalizePathPattern(rule.pathPattern());
        Set<String> methods = normalizeMethods(rule.methods());
        Set<String> requiredRoles = normalizeRoles(rule.requiredRoles());
        RbacProperties.Decision effect = rule.effect() == null ? RbacProperties.Decision.ALLOW : rule.effect();

        return new CompiledRule(
                compiledName,
                pathPattern,
                methods,
                effect,
                rule.allowAnonymous(),
                requiredRoles
        );
    }

    /**
     * Executes normalize path pattern for `ApiPolicyAuthorizationManager`.
     *
     * <p>Detailed behavior: follows the current implementation path and
     * enforces component-specific rules in `com.electrahub.gateway.security`.
     * @param pathPattern input consumed by normalizePathPattern.
     * @return result produced by normalizePathPattern.
     */
    private String normalizePathPattern(String pathPattern) {
        if (pathPattern == null || pathPattern.isBlank()) {
            return "/**";
        }
        return pathPattern.trim();
    }

    /**
     * Executes normalize methods for `ApiPolicyAuthorizationManager`.
     *
     * <p>Detailed behavior: follows the current implementation path and
     * enforces component-specific rules in `com.electrahub.gateway.security`.
     * @param methods input consumed by normalizeMethods.
     * @return result produced by normalizeMethods.
     */
    private Set<String> normalizeMethods(List<String> methods) {
        if (methods == null || methods.isEmpty()) {
            return Set.of("*");
        }

        Set<String> normalized = methods.stream()
                .map(method -> method == null ? "" : method.trim().toUpperCase(Locale.ROOT))
                .filter(method -> !method.isBlank())
                .collect(Collectors.toSet());
        if (normalized.isEmpty()) {
            return Set.of("*");
        }
        return normalized;
    }

    /**
     * Executes normalize roles for `ApiPolicyAuthorizationManager`.
     *
     * <p>Detailed behavior: follows the current implementation path and
     * enforces component-specific rules in `com.electrahub.gateway.security`.
     * @param requiredRoles input consumed by normalizeRoles.
     * @return result produced by normalizeRoles.
     */
    private Set<String> normalizeRoles(List<String> requiredRoles) {
        if (requiredRoles == null || requiredRoles.isEmpty()) {
            return Set.of();
        }

        return requiredRoles.stream()
                .map(role -> role == null ? "" : role.trim().toUpperCase(Locale.ROOT))
                .filter(role -> !role.isBlank())
                .collect(Collectors.toSet());
    }

    private record CompiledRule(
            String name,
            String pathPattern,
            Set<String> methods,
            RbacProperties.Decision effect,
            boolean allowAnonymous,
            Set<String> requiredRoles
    ) {
        private boolean matches(String requestMethod, String requestPath, AntPathMatcher antPathMatcher) {
            boolean methodMatches = methods.contains("*") || methods.contains(requestMethod);
            return methodMatches && antPathMatcher.match(pathPattern, requestPath);
        }
    }

    private record CompiledPolicy(
            long version,
            RoleHierarchy roleHierarchy,
            RbacProperties.Decision defaultDecision,
            List<CompiledRule> rules
    ) {
    }
}
