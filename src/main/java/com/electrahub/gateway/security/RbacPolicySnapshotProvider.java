package com.electrahub.gateway.security;

public interface RbacPolicySnapshotProvider {
    /**
     * Executes current policy for `RbacPolicySnapshotProvider`.
     *
     * <p>Detailed behavior: follows the current implementation path and
     * enforces component-specific rules in `com.electrahub.gateway.security`.
     * @return result produced by currentPolicy.
     */
    RbacPolicySnapshot currentPolicy();
    /**
     * Executes invalidate for `RbacPolicySnapshotProvider`.
     *
     * <p>Detailed behavior: follows the current implementation path and
     * enforces component-specific rules in `com.electrahub.gateway.security`.
     */
    void invalidate();
}
