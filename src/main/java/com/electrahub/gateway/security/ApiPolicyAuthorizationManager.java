package com.electrahub.gateway.security;

import com.electrahub.gateway.config.RbacProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
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

    private final RoleHierarchy roleHierarchy;
    private final AntPathMatcher antPathMatcher = new AntPathMatcher();
    private final RbacProperties.Decision defaultDecision;
    private final List<CompiledRule> rules;

    public ApiPolicyAuthorizationManager(RbacProperties rbacProperties, RoleHierarchy roleHierarchy) {
        this.roleHierarchy = roleHierarchy;
        this.defaultDecision = rbacProperties.getDefaultDecision() == null
                ? RbacProperties.Decision.DENY
                : rbacProperties.getDefaultDecision();
        this.rules = rbacProperties.getRules().stream()
                .map(this::compileRule)
                .toList();
    }

    @Override
    public AuthorizationResult authorize(
            Supplier<? extends Authentication> authenticationSupplier,
            RequestAuthorizationContext requestAuthorizationContext
    ) {
        HttpServletRequest request = requestAuthorizationContext.getRequest();
        String method = request.getMethod().toUpperCase(Locale.ROOT);
        String path = request.getRequestURI();
        Authentication authentication = null;
        boolean matchedAnyRule = false;
        boolean grantedByAllowRule = false;

        for (CompiledRule rule : rules) {
            if (!rule.matches(method, path, antPathMatcher)) {
                continue;
            }

            matchedAnyRule = true;
            if (rule.effect() == RbacProperties.Decision.DENY) {
                if (log.isDebugEnabled()) {
                    log.debug("RBAC decision: method={} path={} principal={} rule={} granted=false reason=explicit-deny",
                            method, path, principal(authentication), rule.name());
                }
                return new AuthorizationDecision(false);
            }

            if (authentication == null) {
                authentication = authenticationSupplier.get();
            }

            if (evaluateAllowRule(rule, authentication)) {
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

        boolean granted = defaultDecision == RbacProperties.Decision.ALLOW;
        if (log.isDebugEnabled()) {
            log.debug("RBAC decision: method={} path={} principal={} rule=<default> granted={}",
                    method, path, principal(authentication), granted);
        }
        return new AuthorizationDecision(granted);
    }

    private boolean evaluateAllowRule(CompiledRule rule, Authentication authentication) {
        if (rule.allowAnonymous()) {
            return true;
        }

        if (isAnonymous(authentication)) {
            return false;
        }

        if (rule.requiredRoles().isEmpty()) {
            return true;
        }

        Set<String> effectiveRoles = extractRoles(authentication);
        return rule.requiredRoles().stream().anyMatch(effectiveRoles::contains);
    }

    private boolean isAnonymous(Authentication authentication) {
        return authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken;
    }

    private Set<String> extractRoles(Authentication authentication) {
        Collection<? extends GrantedAuthority> grantedAuthorities =
                roleHierarchy.getReachableGrantedAuthorities(authentication.getAuthorities());

        return grantedAuthorities.stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> authority.startsWith(ROLE_PREFIX))
                .map(authority -> authority.substring(ROLE_PREFIX.length()))
                .map(role -> role.toUpperCase(Locale.ROOT))
                .collect(Collectors.toSet());
    }

    private Set<String> extractRolesSafe(Authentication authentication) {
        if (isAnonymous(authentication)) {
            return Set.of();
        }
        return extractRoles(authentication);
    }

    private String principal(Authentication authentication) {
        if (isAnonymous(authentication)) {
            return "anonymous";
        }
        return Objects.toString(authentication.getPrincipal(), "unknown");
    }

    private CompiledRule compileRule(RbacProperties.Rule rule) {
        String compiledName = rule.getName() == null ? "<unnamed>" : rule.getName().trim();
        String pathPattern = normalizePathPattern(rule.getPathPattern());
        Set<String> methods = normalizeMethods(rule.getMethods());
        Set<String> requiredRoles = normalizeRoles(rule.getRequiredRoles());
        RbacProperties.Decision effect = rule.getEffect() == null
                ? RbacProperties.Decision.ALLOW
                : rule.getEffect();

        return new CompiledRule(
                compiledName,
                pathPattern,
                methods,
                effect,
                rule.isAllowAnonymous(),
                requiredRoles
        );
    }

    private String normalizePathPattern(String pathPattern) {
        if (pathPattern == null || pathPattern.isBlank()) {
            return "/**";
        }
        return pathPattern.trim();
    }

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
}
