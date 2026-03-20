package com.electrahub.gateway.security;

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

    private final RbacProperties rbacProperties;
    private final RbacPolicySnapshotProvider rbacPolicySnapshotProvider;

    public RbacCacheController(RbacProperties rbacProperties, RbacPolicySnapshotProvider rbacPolicySnapshotProvider) {
        this.rbacProperties = rbacProperties;
        this.rbacPolicySnapshotProvider = rbacPolicySnapshotProvider;
    }

    @PostMapping("/invalidate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void invalidate(
            @RequestHeader(value = "X-Internal-Api-Key", required = false) String internalApiKey
    ) {
        if (internalApiKey == null || !internalApiKey.equals(rbacProperties.getInternalApiKey())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid internal API key");
        }
        rbacPolicySnapshotProvider.invalidate();
        rbacPolicySnapshotProvider.currentPolicy();
    }
}
