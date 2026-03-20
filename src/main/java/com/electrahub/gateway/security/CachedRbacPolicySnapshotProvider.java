package com.electrahub.gateway.security;

import com.electrahub.gateway.config.RbacProperties;
import com.electrahub.gateway.security.RemoteRbacPolicyResponse.RemoteRbacRuleResponse;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class CachedRbacPolicySnapshotProvider implements RbacPolicySnapshotProvider {

    private static final Logger log = LoggerFactory.getLogger(CachedRbacPolicySnapshotProvider.class);

    private final RbacProperties rbacProperties;
    private final RestClient restClient;
    private final RbacPolicySnapshot fallbackSnapshot;
    private final AtomicReference<PolicyState> state;
    private volatile boolean invalidated = true;

    public CachedRbacPolicySnapshotProvider(RbacProperties rbacProperties, RestClient.Builder restClientBuilder) {
        this.rbacProperties = rbacProperties;
        this.restClient = restClientBuilder.build();
        this.fallbackSnapshot = RbacPolicySnapshot.fromProperties(rbacProperties);
        this.state = new AtomicReference<>(new PolicyState(fallbackSnapshot, Instant.EPOCH));
    }

    @PostConstruct
    void initialFetch() {
        refreshIfNeeded(true);
    }

    @Override
    public RbacPolicySnapshot currentPolicy() {
        refreshIfNeeded(true);
        return state.get().snapshot();
    }

    @Override
    public void invalidate() {
        invalidated = true;
    }

    @Scheduled(fixedDelayString = "30000")
    void scheduledRefresh() {
        refreshIfNeeded(false);
    }

    private void refreshIfNeeded(boolean forceOnDemand) {
        if (!rbacProperties.getRemotePolicy().isEnabled()) {
            return;
        }
        if (!shouldRefresh()) {
            return;
        }

        synchronized (this) {
            if (!shouldRefresh()) {
                return;
            }
            Instant now = Instant.now();
            try {
                RemoteRbacPolicyResponse response = restClient.get()
                        .uri(rbacProperties.getRemotePolicy().getSourceUrl())
                        .header("X-Internal-Api-Key", rbacProperties.getInternalApiKey())
                        .retrieve()
                        .body(RemoteRbacPolicyResponse.class);

                if (response == null) {
                    throw new IllegalStateException("Empty RBAC policy response from remote source");
                }

                RbacPolicySnapshot snapshot = toSnapshot(response);
                state.set(new PolicyState(snapshot, now));
                invalidated = false;
                log.debug("RBAC policy refreshed from remote source: version={} rules={}", snapshot.version(), snapshot.rules().size());
            } catch (Exception ex) {
                state.set(new PolicyState(state.get().snapshot(), now));
                invalidated = false;
                if (forceOnDemand) {
                    log.warn("RBAC remote policy fetch failed; using cached/fallback policy: {}", ex.getMessage());
                } else {
                    log.debug("RBAC remote policy refresh failed; keeping cached policy: {}", ex.getMessage());
                }
            }
        }
    }

    private boolean shouldRefresh() {
        if (invalidated) {
            return true;
        }

        PolicyState current = state.get();
        Duration refreshInterval = rbacProperties.getRemotePolicy().getRefreshInterval();
        if (refreshInterval == null || refreshInterval.isNegative() || refreshInterval.isZero()) {
            refreshInterval = Duration.ofSeconds(30);
        }
        Instant nextRefreshAt = current.fetchedAt().plus(refreshInterval);
        return Instant.now().isAfter(nextRefreshAt);
    }

    private RbacPolicySnapshot toSnapshot(RemoteRbacPolicyResponse response) {
        String roleHierarchy = normalizeText(response.roleHierarchy());
        if (roleHierarchy.isBlank()) {
            roleHierarchy = fallbackSnapshot.roleHierarchy();
        }

        RbacProperties.Decision defaultDecision = parseDecision(response.defaultDecision(), fallbackSnapshot.defaultDecision());
        List<RbacPolicySnapshot.RbacRuleSnapshot> rules = response.rules() == null
                ? List.of()
                : response.rules().stream().map(this::toRuleSnapshot).toList();

        return new RbacPolicySnapshot(
                response.version(),
                roleHierarchy,
                defaultDecision,
                rules
        );
    }

    private RbacPolicySnapshot.RbacRuleSnapshot toRuleSnapshot(RemoteRbacRuleResponse rule) {
        if (rule == null) {
            return new RbacPolicySnapshot.RbacRuleSnapshot(
                    "<invalid-rule>",
                    List.of("*"),
                    "/**",
                    RbacProperties.Decision.DENY,
                    false,
                    List.of()
            );
        }
        String name = normalizeText(rule.name());
        String pathPattern = normalizeText(rule.pathPattern());
        List<String> methods = rule.methods() == null || rule.methods().isEmpty() ? List.of("*") : rule.methods().stream()
                .map(this::normalizeText)
                .map(value -> value.toUpperCase(Locale.ROOT))
                .filter(value -> !value.isBlank())
                .toList();
        if (methods.isEmpty()) {
            methods = List.of("*");
        }
        List<String> requiredRoles = rule.requiredRoles() == null ? List.of() : rule.requiredRoles().stream()
                .map(this::normalizeText)
                .map(value -> value.toUpperCase(Locale.ROOT))
                .filter(value -> !value.isBlank())
                .toList();

        return new RbacPolicySnapshot.RbacRuleSnapshot(
                name.isBlank() ? "<unnamed>" : name,
                methods,
                pathPattern.isBlank() ? "/**" : pathPattern,
                parseDecision(rule.effect(), RbacProperties.Decision.ALLOW),
                rule.allowAnonymous(),
                requiredRoles
        );
    }

    private RbacProperties.Decision parseDecision(String value, RbacProperties.Decision fallback) {
        String normalized = normalizeText(value).toUpperCase(Locale.ROOT);
        if ("ALLOW".equals(normalized)) {
            return RbacProperties.Decision.ALLOW;
        }
        if ("DENY".equals(normalized)) {
            return RbacProperties.Decision.DENY;
        }
        return fallback;
    }

    private String normalizeText(String value) {
        return value == null ? "" : value.trim();
    }

    private record PolicyState(
            RbacPolicySnapshot snapshot,
            Instant fetchedAt
    ) {
    }
}
