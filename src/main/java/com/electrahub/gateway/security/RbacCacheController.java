package com.electrahub.gateway.security;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import com.electrahub.gateway.config.RbacProperties;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/internal/rbac/cache")
public class RbacCacheController {
    private static final Logger LOGGER = LoggerFactory.getLogger(RbacCacheController.class);


    private final RbacProperties rbacProperties;
    private final RbacPolicySnapshotProvider rbacPolicySnapshotProvider;

    /**
     * Executes rbac cache controller for `RbacCacheController`.
     *
     * <p>Detailed behavior: follows the current implementation path and
     * enforces component-specific rules in `com.electrahub.gateway.security`.
     * @param rbacProperties input consumed by RbacCacheController.
     * @param rbacPolicySnapshotProvider input consumed by RbacCacheController.
     */
    public RbacCacheController(RbacProperties rbacProperties, RbacPolicySnapshotProvider rbacPolicySnapshotProvider) {
        LOGGER.debug("Initializing RBAC cache controller");
        this.rbacProperties = rbacProperties;
        this.rbacPolicySnapshotProvider = rbacPolicySnapshotProvider;
    }

    @PostMapping("/invalidate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void invalidate(
            @RequestHeader(value = "X-Internal-Api-Key", required = false) String internalApiKey
    ) {
        LOGGER.info("RBAC cache invalidation requested");
        if (internalApiKey == null || !internalApiKey.equals(rbacProperties.getInternalApiKey())) {
            LOGGER.warn("RBAC cache invalidation rejected due to invalid internal API key");
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid internal API key");
        }
        rbacPolicySnapshotProvider.invalidate();
        rbacPolicySnapshotProvider.currentPolicy();
        LOGGER.info("RBAC cache invalidated successfully");
    }
}
