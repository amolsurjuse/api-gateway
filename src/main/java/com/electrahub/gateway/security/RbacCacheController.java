package com.electrahub.gateway.security;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import com.electrahub.gateway.config.RbacProperties;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/internal/rbac/cache")
public class RbacCacheController {
    private static final Logger LOGGER = LoggerFactory.getLogger(RbacCacheController.class);


    private final RbacProperties rbacProperties;
    private final RbacPolicySnapshotProvider rbacPolicySnapshotProvider;
    private final GatewayAccessScopeCache gatewayAccessScopeCache;

    /**
     * Executes rbac cache controller for `RbacCacheController`.
     *
     * <p>Detailed behavior: follows the current implementation path and
     * enforces component-specific rules in `com.electrahub.gateway.security`.
     * @param rbacProperties input consumed by RbacCacheController.
     * @param rbacPolicySnapshotProvider input consumed by RbacCacheController.
     */
    public RbacCacheController(
            RbacProperties rbacProperties,
            RbacPolicySnapshotProvider rbacPolicySnapshotProvider,
            GatewayAccessScopeCache gatewayAccessScopeCache
    ) {
        LOGGER.info(" Entering RbacCacheController#RbacCacheController");
        LOGGER.debug(" Entering RbacCacheController#RbacCacheController with debug context");
        this.rbacProperties = rbacProperties;
        this.rbacPolicySnapshotProvider = rbacPolicySnapshotProvider;
        this.gatewayAccessScopeCache = gatewayAccessScopeCache;
    }

    @PostMapping("/invalidate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void invalidate(
            @RequestHeader(value = "X-Internal-Api-Key", required = false) String internalApiKey,
            @RequestParam(value = "userId", required = false) java.util.UUID userId
    ) {
        if (internalApiKey == null || !internalApiKey.equals(rbacProperties.getInternalApiKey())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid internal API key");
        }
        if (userId != null) {
            gatewayAccessScopeCache.invalidateUser(userId);
            return;
        }
        rbacPolicySnapshotProvider.invalidate();
        rbacPolicySnapshotProvider.currentPolicy();
    }
}
