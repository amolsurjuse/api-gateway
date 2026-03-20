package com.electrahub.gateway.security;

public interface RbacPolicySnapshotProvider {
    RbacPolicySnapshot currentPolicy();
    void invalidate();
}
