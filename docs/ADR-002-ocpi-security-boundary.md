# ADR-002: OCPI Security Boundary at the API Gateway

## Status

Accepted

## Context

OCPI 2.2.1 uses an opaque `Authorization: Token <token>` credential rather
than the platform's bearer JWT. The gateway's remotely supplied RBAC policy
replaces its local YAML rules and therefore cannot authorize an OCPI token.
Applying normal gateway RBAC to `/ocpi/**` blocks both public discovery and
authenticated partner module calls before they reach the OCPI service.

## Decision

- Treat `/ocpi/**` as protocol pass-through traffic in the gateway security
  filter chain.
- Preserve the `Authorization` header unchanged.
- Keep all OCPI authentication and authorization in `ocpi-service`:
  discovery endpoints are public, initial credential registration requires
  the bootstrap token, and module endpoints require an active partner token.
- Keep the gateway route scoped to the in-cluster `ocpi-service` destination.
- Verify end to end that discovery returns 200 while tokenless module and
  credential requests return 401 from the OCPI service.

## Consequences

The gateway does not interpret partner credentials, avoiding two conflicting
authorization systems. The OCPI service is the single fail-closed security
boundary for this protocol, so its authentication regression tests and live
deployment probes are mandatory before gateway promotion.
