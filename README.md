# API Gateway

Spring Boot gateway that routes traffic to ElectraHub services and enforces JWT + RBAC authorization.

## Exposed Port

- `8090`

## Main Routes

- `/auth/**` -> auth-service
- `/user/**` -> user-service
- `/payment/**` -> payment-service
- `/subscription/**` -> subscription-service
- `/charger/**` and `/charger-management/**` -> charger-management-service
- `/ws/**` -> web-socket-connector

## Local Docker Desktop

From workspace root:

```bash
./scripts/deploy-local-service.sh up api-gateway
```

Run full stack:

```bash
./scripts/deploy-local-service.sh up all
```

## Health Check

```bash
curl -i http://localhost:8090/actuator/health
```

## Updated

- README reviewed and refreshed on `2026-03-21`.
