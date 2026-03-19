#!/usr/bin/env bash
# =============================================================================
# deploy-dev.sh — End-to-end deployment of api-gateway to the dev environment
#
# What this script does (idempotent — safe to rerun):
#   1. Initialises the api-gateway git repo and pushes to GitHub
#   2. Creates the TeamCity pipeline via REST API
#   3. Commits & pushes k8s-platform changes (ArgoCD app + Helm configs + ingress updates)
#   4. Applies the ArgoCD Application to the cluster
#   5. Triggers the first TeamCity build
#   6. Verifies the deployment is healthy
#
# Prerequisites:
#   - git, jq, curl, kubectl, gh (GitHub CLI) installed
#   - TEAMCITY_TOKEN set (or local super-user token discoverable)
#   - kubectl context configured for the dev cluster
#   - GitHub SSH access configured for amolsurjuse
#
# Usage:
#   export TEAMCITY_TOKEN='<your-token>'   # or let it auto-discover locally
#   cd /path/to/projects/api-gateway
#   ./deploy-dev.sh
# =============================================================================
set -euo pipefail

# ─── Configuration ───────────────────────────────────────────────────────────
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECTS_DIR="$(dirname "$SCRIPT_DIR")"
K8S_PLATFORM_DIR="${PROJECTS_DIR}/k8s-platform"
TEAMCITY_SKILL_DIR="${PROJECTS_DIR}/../skills/teamcity-pipeline"

TEAMCITY_URL="${TEAMCITY_URL:-http://localhost:8111}"
GITHUB_USER="amolsurjuse"
GITHUB_REPO="api-gateway"
GIT_REMOTE="git@github.com:${GITHUB_USER}/${GITHUB_REPO}.git"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

step=0
step() {
  step=$((step + 1))
  echo ""
  echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
  echo -e "${BLUE}  Step ${step}: $1${NC}"
  echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
}

ok()   { echo -e "  ${GREEN}✔${NC} $1"; }
warn() { echo -e "  ${YELLOW}⚠${NC} $1"; }
fail() { echo -e "  ${RED}✘${NC} $1"; exit 1; }

# ─── Pre-flight checks ──────────────────────────────────────────────────────
step "Pre-flight checks"

for cmd in git jq curl kubectl; do
  command -v "$cmd" >/dev/null 2>&1 || fail "$cmd is required but not found"
  ok "$cmd found"
done

# Check kubectl connectivity
if kubectl cluster-info >/dev/null 2>&1; then
  ok "Kubernetes cluster reachable"
else
  fail "Cannot reach Kubernetes cluster. Check your kubeconfig."
fi

# Check TeamCity token
discover_teamcity_token() {
  if [[ -n "${TEAMCITY_TOKEN:-}" ]]; then
    return 0
  fi
  # Try local super-user token discovery
  local token=""
  local log_file="${HOME}/teamcity_data/server/logs/teamcity-server.log"
  if [[ -f "${log_file}" ]]; then
    token="$(grep 'Super user authentication token' "${log_file}" 2>/dev/null \
      | tail -n 1 \
      | sed -n 's/.*Super user authentication token: \([0-9][0-9]*\).*/\1/p')"
  fi
  if [[ -z "${token}" ]] && command -v docker >/dev/null 2>&1; then
    token="$(docker logs teamcity-server --tail 200 2>/dev/null \
      | sed -n 's/.*Super user authentication token: \([0-9][0-9]*\).*/\1/p' \
      | tail -n 1)"
  fi
  if [[ -n "${token}" ]]; then
    export TEAMCITY_TOKEN="${token}"
    ok "Auto-discovered TeamCity super-user token"
  else
    fail "TEAMCITY_TOKEN not set and no local super-user token found. Export TEAMCITY_TOKEN and rerun."
  fi
}

discover_teamcity_token

# Verify TeamCity is reachable
tc_code=$(curl -sS --max-time 10 -o /dev/null -w '%{http_code}' \
  -u ":${TEAMCITY_TOKEN}" -H 'Accept: application/json' \
  "${TEAMCITY_URL}/app/rest/server" 2>/dev/null || echo "000")
if [[ "$tc_code" == "200" ]]; then
  ok "TeamCity reachable and authenticated at ${TEAMCITY_URL}"
else
  fail "TeamCity not reachable or auth failed (HTTP ${tc_code}). Check TEAMCITY_URL and TEAMCITY_TOKEN."
fi

# ═════════════════════════════════════════════════════════════════════════════
# PHASE 1: Push api-gateway code to GitHub
# ═════════════════════════════════════════════════════════════════════════════
step "Initialise api-gateway Git repo and push to GitHub"

cd "$SCRIPT_DIR"

# Create GitHub repo if it doesn't exist
if gh repo view "${GITHUB_USER}/${GITHUB_REPO}" >/dev/null 2>&1; then
  ok "GitHub repo ${GITHUB_USER}/${GITHUB_REPO} already exists"
else
  warn "Creating GitHub repo ${GITHUB_USER}/${GITHUB_REPO}..."
  gh repo create "${GITHUB_USER}/${GITHUB_REPO}" --private --source=. --push 2>/dev/null || true
  ok "GitHub repo created"
fi

# Initialise local git repo if needed
if [[ ! -d .git ]]; then
  git init
  ok "Initialised local git repo"
else
  ok "Local git repo already exists"
fi

# Set remote
if git remote get-url origin >/dev/null 2>&1; then
  current_remote=$(git remote get-url origin)
  if [[ "$current_remote" != "$GIT_REMOTE" ]]; then
    git remote set-url origin "$GIT_REMOTE"
    ok "Updated remote origin to ${GIT_REMOTE}"
  else
    ok "Remote origin already set to ${GIT_REMOTE}"
  fi
else
  git remote add origin "$GIT_REMOTE"
  ok "Added remote origin: ${GIT_REMOTE}"
fi

# Add, commit, push
git add -A
if git diff --cached --quiet 2>/dev/null; then
  ok "No new changes to commit"
else
  git commit -m "feat: initial api-gateway service

Spring Boot 4.0.1 reverse proxy with:
- Centralised JWT authentication (JJWT 0.12.6)
- Redis-backed token denylist and version checks
- Role hierarchy (SYSTEM_ADMIN > USER)
- Config-driven route registry
- RestClient-based transparent proxying
- TeamCity CI/CD pipeline
- Kubernetes deployment via ArgoCD"
  ok "Committed api-gateway source"
fi

# Push to main
git push -u origin main 2>/dev/null || git push -u origin HEAD:main
ok "Pushed api-gateway to GitHub (main branch)"

# ═════════════════════════════════════════════════════════════════════════════
# PHASE 2: Create TeamCity pipeline
# ═════════════════════════════════════════════════════════════════════════════
step "Create TeamCity pipeline for api-gateway"

cd "$SCRIPT_DIR"
bash ci/teamcity/setup_pipeline.sh
ok "TeamCity pipeline created/verified: Amy_ApiGateway_Build"

# ═════════════════════════════════════════════════════════════════════════════
# PHASE 3: Push k8s-platform changes
# ═════════════════════════════════════════════════════════════════════════════
step "Commit and push k8s-platform changes (ArgoCD app + configs + ingress)"

cd "$K8S_PLATFORM_DIR"

# Ensure we're on develop branch
current_branch=$(git branch --show-current)
if [[ "$current_branch" != "develop" ]]; then
  git checkout develop
fi

# Stage api-gateway related files
git add argocd/applications/api-gateway-dev.yaml
git add charts/common/api-gateway/
git add charts/config/services/api-gateway/

# Stage ingress disabling changes for existing services
git add charts/config/services/auth-service/us/values/dev-values.yaml
git add charts/config/services/user-service/us/values/dev-values.yaml
git add charts/config/services/subscription-service/us/values/dev-values.yaml
git add charts/config/services/payment-service/us/values/dev-values.yaml
git add charts/config/services/charger-management-service/us/values/dev-values.yaml
git add charts/config/services/web-socket-connector/us/values/dev-values.yaml

if git diff --cached --quiet 2>/dev/null; then
  ok "No new k8s-platform changes to commit"
else
  git commit -m "feat(api-gateway): add gateway deployment + route all traffic through gateway

- Add ArgoCD application for api-gateway (dev)
- Add Helm values, base config, dev-values, version file
- Disable individual service ingress (auth, user, subscription,
  payment, charger-management, ws-connector) — all traffic now
  routes through the api-gateway catch-all ingress"
  ok "Committed k8s-platform changes"
fi

git push origin develop
ok "Pushed k8s-platform develop branch"

# ═════════════════════════════════════════════════════════════════════════════
# PHASE 4: Apply ArgoCD Application
# ═════════════════════════════════════════════════════════════════════════════
step "Apply ArgoCD Application to Kubernetes cluster"

# Check if application already exists
if kubectl -n argocd get application api-gateway >/dev/null 2>&1; then
  ok "ArgoCD application 'api-gateway' already exists"
  # Trigger a sync to pick up latest changes
  if command -v argocd >/dev/null 2>&1; then
    argocd app sync api-gateway --async 2>/dev/null || true
    ok "Triggered ArgoCD sync"
  else
    warn "argocd CLI not found — ArgoCD will auto-sync (syncPolicy: automated)"
  fi
else
  kubectl apply -f "${K8S_PLATFORM_DIR}/argocd/applications/api-gateway-dev.yaml"
  ok "Applied ArgoCD application 'api-gateway'"
fi

# Wait for ArgoCD to register the application
echo "  Waiting for ArgoCD to register the application..."
for i in $(seq 1 30); do
  status=$(kubectl -n argocd get application api-gateway -o jsonpath='{.status.sync.status}' 2>/dev/null || echo "Unknown")
  health=$(kubectl -n argocd get application api-gateway -o jsonpath='{.status.health.status}' 2>/dev/null || echo "Unknown")
  if [[ "$status" != "Unknown" ]]; then
    ok "ArgoCD app registered — sync: ${status}, health: ${health}"
    break
  fi
  sleep 2
done

# ═════════════════════════════════════════════════════════════════════════════
# PHASE 5: Trigger first TeamCity build
# ═════════════════════════════════════════════════════════════════════════════
step "Trigger first TeamCity build"

BUILD_TYPE_ID="Amy_ApiGateway_Build"

# Check if there's already a queued/running build
queue_count=$(curl -sS -u ":${TEAMCITY_TOKEN}" -H 'Accept: application/json' \
  "${TEAMCITY_URL}/app/rest/buildQueue?locator=buildType:(id:${BUILD_TYPE_ID})" 2>/dev/null \
  | jq '.count // 0')

if [[ "$queue_count" -gt 0 ]]; then
  warn "Build already in queue (${queue_count} items). Skipping trigger."
else
  trigger_result=$(curl -sS -u ":${TEAMCITY_TOKEN}" \
    -H 'Content-Type: application/json' \
    -H 'Accept: application/json' \
    -X POST \
    "${TEAMCITY_URL}/app/rest/buildQueue" \
    -d "{\"buildType\":{\"id\":\"${BUILD_TYPE_ID}\"}}" 2>/dev/null)

  build_id=$(echo "$trigger_result" | jq -r '.id // "unknown"')
  ok "Triggered build #${build_id}"
  echo "  Build URL: ${TEAMCITY_URL}/buildConfiguration/${BUILD_TYPE_ID}/${build_id}"
fi

# ═════════════════════════════════════════════════════════════════════════════
# PHASE 6: Monitor and verify
# ═════════════════════════════════════════════════════════════════════════════
step "Waiting for build and deployment (this may take a few minutes)"

echo ""
echo -e "${YELLOW}  The build is running in TeamCity. You can monitor progress at:${NC}"
echo -e "${YELLOW}    ${TEAMCITY_URL}/buildConfiguration/${BUILD_TYPE_ID}${NC}"
echo ""

# Wait for the build to complete (up to 10 minutes)
if [[ -n "${build_id:-}" && "$build_id" != "unknown" ]]; then
  echo "  Waiting for build #${build_id} to complete..."
  for i in $(seq 1 60); do
    build_status=$(curl -sS -u ":${TEAMCITY_TOKEN}" -H 'Accept: application/json' \
      "${TEAMCITY_URL}/app/rest/builds/id:${build_id}" 2>/dev/null \
      | jq -r '.state // "unknown"')

    if [[ "$build_status" == "finished" ]]; then
      build_result=$(curl -sS -u ":${TEAMCITY_TOKEN}" -H 'Accept: application/json' \
        "${TEAMCITY_URL}/app/rest/builds/id:${build_id}" 2>/dev/null \
        | jq -r '.status // "unknown"')
      if [[ "$build_result" == "SUCCESS" ]]; then
        ok "Build #${build_id} completed successfully!"
      else
        fail "Build #${build_id} finished with status: ${build_result}. Check TeamCity logs."
      fi
      break
    fi

    if [[ $i -eq 60 ]]; then
      warn "Build still running after 10 minutes. Check TeamCity manually."
    fi
    sleep 10
  done
fi

# Verify ArgoCD deployment health
echo ""
echo "  Checking ArgoCD application health..."
for i in $(seq 1 30); do
  sync_status=$(kubectl -n argocd get application api-gateway -o jsonpath='{.status.sync.status}' 2>/dev/null || echo "Unknown")
  health_status=$(kubectl -n argocd get application api-gateway -o jsonpath='{.status.health.status}' 2>/dev/null || echo "Unknown")

  if [[ "$sync_status" == "Synced" && "$health_status" == "Healthy" ]]; then
    ok "ArgoCD app is Synced and Healthy!"
    break
  fi

  if [[ $i -eq 30 ]]; then
    warn "ArgoCD app not yet healthy after 5 minutes (sync: ${sync_status}, health: ${health_status})"
    warn "Check: kubectl -n argocd get application api-gateway -o yaml"
  fi
  sleep 10
done

# Verify pods are running
echo ""
echo "  Checking pods in dev namespace..."
kubectl -n dev get pods -l app.kubernetes.io/name=api-gateway 2>/dev/null || warn "Could not list api-gateway pods"

# ═════════════════════════════════════════════════════════════════════════════
# DONE
# ═════════════════════════════════════════════════════════════════════════════
echo ""
echo -e "${GREEN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${GREEN}  Deployment complete!${NC}"
echo -e "${GREEN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo ""
echo "  Summary:"
echo "    GitHub:   https://github.com/${GITHUB_USER}/${GITHUB_REPO}"
echo "    TeamCity: ${TEAMCITY_URL}/buildConfiguration/${BUILD_TYPE_ID}"
echo "    ArgoCD:   kubectl -n argocd get application api-gateway"
echo "    Pods:     kubectl -n dev get pods -l app.kubernetes.io/name=api-gateway"
echo ""
echo "  Useful commands:"
echo "    # Check build status"
echo "    bash ${TEAMCITY_SKILL_DIR}/scripts/teamcity_local.sh latest ${BUILD_TYPE_ID}"
echo ""
echo "    # Check ArgoCD app"
echo "    kubectl -n argocd get application api-gateway -o jsonpath='{.status.sync.status}'"
echo ""
echo "    # Check api-gateway logs"
echo "    kubectl -n dev logs -l app.kubernetes.io/name=api-gateway --tail=50"
echo ""
echo "    # Test the gateway"
echo "    curl -k https://dev.electrahub.com/auth/api/v1/health"
echo ""
