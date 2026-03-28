package com.electrahub.gateway.security;

import com.electrahub.gateway.config.RbacProperties;
import com.electrahub.proto.user.v1.RbacServiceGrpc;
import com.electrahub.proto.user.v1.RbacServiceOuterClass;
import io.grpc.StatusRuntimeException;
import jakarta.annotation.PostConstruct;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

/**
 * gRPC-based RBAC Policy Snapshot Provider.
 * Fetches RBAC policies from the user-service via gRPC instead of REST.
 * Replaces CachedRbacPolicySnapshotProvider when app.rbac.transport=grpc
 */
@Component
@ConditionalOnProperty(name = "app.rbac.transport", havingValue = "grpc")
public class GrpcRbacPolicySnapshotProvider implements RbacPolicySnapshotProvider {

    private static final Logger log = LoggerFactory.getLogger(GrpcRbacPolicySnapshotProvider.class);

    private final RbacProperties rbacProperties;
    private final RbacPolicySnapshot fallbackSnapshot;
    private final AtomicReference<PolicyState> state;
    private volatile boolean invalidated = true;

    @GrpcClient("user-service")
    private RbacServiceGrpc.RbacServiceBlockingStub rbacServiceStub;

    /**
     * Initialize GrpcRbacPolicySnapshotProvider with fallback policy.
     *
     * @param rbacProperties RBAC configuration properties
     */
    public GrpcRbacPolicySnapshotProvider(RbacProperties rbacProperties) {
        log.info("CODEx_ENTRY_LOG: Entering GrpcRbacPolicySnapshotProvider#GrpcRbacPolicySnapshotProvider");
        log.debug("CODEx_ENTRY_LOG: Entering GrpcRbacPolicySnapshotProvider#GrpcRbacPolicySnapshotProvider with debug context");
        this.rbacProperties = rbacProperties;
        this.fallbackSnapshot = RbacPolicySnapshot.fromProperties(rbacProperties);
        this.state = new AtomicReference<>(new PolicyState(fallbackSnapshot, Instant.EPOCH));
    }

    /**
     * Perform initial policy fetch at startup.
     */
    @PostConstruct
    void initialFetch() {
        refreshIfNeeded(true);
    }

    /**
     * Get the current RBAC policy snapshot.
     *
     * @return the current policy snapshot
     */
    @Override
    public RbacPolicySnapshot currentPolicy() {
        refreshIfNeeded(true);
        return state.get().snapshot();
    }

    /**
     * Invalidate the cached policy to force a refresh on next access.
     */
    @Override
    public void invalidate() {
        invalidated = true;
    }

    /**
     * Scheduled periodic refresh of the policy.
     */
    @Scheduled(fixedDelayString = "30000")
    void scheduledRefresh() {
        refreshIfNeeded(false);
    }

    /**
     * Refresh the policy if needed based on cache expiration or invalidation.
     *
     * @param forceOnDemand whether this is an on-demand (vs scheduled) refresh
     */
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
                // Call gRPC service to get RBAC policy
                RbacServiceOuterClass.RbacPolicyResponse response = rbacServiceStub.getRbacPolicy(
                        RbacServiceOuterClass.GetRbacPolicyRequest.getDefaultInstance()
                );

                if (response == null) {
                    throw new IllegalStateException("Empty RBAC policy response from gRPC service");
                }

                RbacPolicySnapshot snapshot = toSnapshot(response);
                state.set(new PolicyState(snapshot, now));
                invalidated = false;
                log.debug("RBAC policy refreshed from gRPC service: version={} rules={}",
                        snapshot.version(), snapshot.rules().size());
            } catch (StatusRuntimeException ex) {
                state.set(new PolicyState(state.get().snapshot(), now));
                invalidated = false;
                if (forceOnDemand) {
                    log.warn("RBAC gRPC policy fetch failed; using cached/fallback policy: {} - {}",
                            ex.getStatus().getCode(), ex.getStatus().getDescription());
                } else {
                    log.debug("RBAC gRPC policy refresh failed; keeping cached policy: {} - {}",
                            ex.getStatus().getCode(), ex.getStatus().getDescription());
                }
            } catch (Exception ex) {
                state.set(new PolicyState(state.get().snapshot(), now));
                invalidated = false;
                if (forceOnDemand) {
                    log.warn("RBAC gRPC policy fetch failed; using cached/fallback policy: {}", ex.getMessage());
                } else {
                    log.debug("RBAC gRPC policy refresh failed; keeping cached policy: {}", ex.getMessage());
                }
            }
        }
    }

    /**
     * Check if the policy should be refreshed.
     *
     * @return true if refresh is needed
     */
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

    /**
     * Convert gRPC RbacPolicyResponse to domain RbacPolicySnapshot.
     *
     * @param response the gRPC response
     * @return the domain snapshot
     */
    private RbacPolicySnapshot toSnapshot(RbacServiceOuterClass.RbacPolicyResponse response) {
        String roleHierarchy = normalizeText(response.getRoleHierarchy());
        if (roleHierarchy.isBlank()) {
            roleHierarchy = fallbackSnapshot.roleHierarchy();
        }

        RbacProperties.Decision defaultDecision = parseDecision(response.getDefaultDecision(),
                fallbackSnapshot.defaultDecision());

        List<RbacPolicySnapshot.RbacRuleSnapshot> rules = response.getRulesList() == null
                ? List.of()
                : response.getRulesList().stream().map(this::toRuleSnapshot).toList();

        return new RbacPolicySnapshot(
                response.getVersion(),
                roleHierarchy,
                defaultDecision,
                rules
        );
    }

    /**
     * Convert gRPC RbacRule to domain RbacRuleSnapshot.
     *
     * @param rule the gRPC rule
     * @return the domain rule snapshot
     */
    private RbacPolicySnapshot.RbacRuleSnapshot toRuleSnapshot(RbacServiceOuterClass.RbacRule rule) {
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
        String name = normalizeText(rule.getName());
        String pathPattern = normalizeText(rule.getPathPattern());
        List<String> methods = rule.getMethodsList() == null || rule.getMethodsList().isEmpty()
                ? List.of("*")
                : rule.getMethodsList().stream()
                    .map(this::normalizeText)
                    .map(value -> value.toUpperCase(Locale.ROOT))
                    .filter(value -> !value.isBlank())
                    .toList();
        if (methods.isEmpty()) {
            methods = List.of("*");
        }
        List<String> requiredRoles = rule.getRequiredRolesList() == null
                ? List.of()
                : rule.getRequiredRolesList().stream()
                    .map(this::normalizeText)
                    .map(value -> value.toUpperCase(Locale.ROOT))
                    .filter(value -> !value.isBlank())
                    .toList();

        return new RbacPolicySnapshot.RbacRuleSnapshot(
                name.isBlank() ? "<unnamed>" : name,
                methods,
                pathPattern.isBlank() ? "/**" : pathPattern,
                parseDecision(rule.getEffect(), RbacProperties.Decision.ALLOW),
                rule.getAllowAnonymous(),
                requiredRoles
        );
    }

    /**
     * Parse a decision string to RbacProperties.Decision enum.
     *
     * @param value the decision string
     * @param fallback the fallback decision if parsing fails
     * @return the parsed decision or fallback
     */
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

    /**
     * Normalize text by trimming and handling nulls.
     *
     * @param value the text to normalize
     * @return the normalized text
     */
    private String normalizeText(String value) {
        return value == null ? "" : value.trim();
    }

    /**
     * Internal state record for policy and fetch timestamp.
     */
    private record PolicyState(
            RbacPolicySnapshot snapshot,
            Instant fetchedAt
    ) {
    }
}
