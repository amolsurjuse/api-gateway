#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

export SERVICE_NAME="${SERVICE_NAME:-api-gateway}"

export TEAMCITY_PARENT_PROJECT_ID="${TEAMCITY_PARENT_PROJECT_ID:-Amy}"
export TEAMCITY_PROJECT_ID="${TEAMCITY_PROJECT_ID:-Amy_ApiGateway}"
export TEAMCITY_PROJECT_NAME="${TEAMCITY_PROJECT_NAME:-api-gateway}"
export TEAMCITY_BUILD_TYPE_ID="${TEAMCITY_BUILD_TYPE_ID:-Amy_ApiGateway_Build}"
export TEAMCITY_VCS_ROOT_ID="${TEAMCITY_VCS_ROOT_ID:-Amy_HttpsGithubComAmolsurjuseApiGatewayRefsHeadsMain}"

export GIT_URL="${GIT_URL:-${API_GATEWAY_GIT_URL:-https://github.com/amolsurjuse/api-gateway.git}}"
export GIT_BRANCH="${GIT_BRANCH:-${API_GATEWAY_GIT_BRANCH:-main}}"
export DOCKER_IMAGE="${DOCKER_IMAGE:-${API_GATEWAY_DOCKER_IMAGE:-amolsurjuse/api-gateway}}"
export DOCKERFILE_PATH="${DOCKERFILE_PATH:-Dockerfile}"
export POM_PATH="${POM_PATH:-pom.xml}"
export MAVEN_GOALS="${MAVEN_GOALS:-clean package}"
export MAVEN_RUNNER_ARGS="${MAVEN_RUNNER_ARGS:-}"
export K8S_BRANCH="${K8S_BRANCH:-develop}"
export DEPLOY_VERSION_FILE="${DEPLOY_VERSION_FILE:-charts/config/services/api-gateway/us/version/dev-version.yaml}"
export DOCKER_USERNAME="${DOCKER_USERNAME:-amolsurjuse}"

exec "${SCRIPT_DIR}/setup_spring_boot_pipeline_common.sh"
