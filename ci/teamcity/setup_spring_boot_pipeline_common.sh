#!/usr/bin/env bash
set -euo pipefail

TEAMCITY_URL="${TEAMCITY_URL:-http://localhost:8111}"
TEAMCITY_TOKEN="${TEAMCITY_TOKEN:-}"
TEAMCITY_PARENT_PROJECT_ID="${TEAMCITY_PARENT_PROJECT_ID:-Amy}"
TEAMCITY_PROJECT_ID="${TEAMCITY_PROJECT_ID:-}"
TEAMCITY_PROJECT_NAME="${TEAMCITY_PROJECT_NAME:-}"
TEAMCITY_BUILD_TYPE_ID="${TEAMCITY_BUILD_TYPE_ID:-}"
TEAMCITY_VCS_ROOT_ID="${TEAMCITY_VCS_ROOT_ID:-}"

SERVICE_NAME="${SERVICE_NAME:-}"
GIT_URL="${GIT_URL:-}"
GIT_BRANCH="${GIT_BRANCH:-develop}"
DOCKER_IMAGE="${DOCKER_IMAGE:-}"
DOCKERFILE_PATH="${DOCKERFILE_PATH:-Dockerfile}"
POM_PATH="${POM_PATH:-pom.xml}"
MAVEN_GOALS="${MAVEN_GOALS:-clean package}"
MAVEN_RUNNER_ARGS="${MAVEN_RUNNER_ARGS:-}"
K8S_BRANCH="${K8S_BRANCH:-develop}"
DEPLOY_VERSION_FILE="${DEPLOY_VERSION_FILE:-}"
DOCKER_USERNAME="${DOCKER_USERNAME:-amolsurjuse}"

if [[ -z "${TEAMCITY_TOKEN}" ]]; then
  echo "TEAMCITY_TOKEN is required"
  exit 1
fi

for required_cmd in jq curl; do
  if ! command -v "${required_cmd}" >/dev/null 2>&1; then
    echo "${required_cmd} is required"
    exit 1
  fi
done

for required_var in SERVICE_NAME TEAMCITY_PROJECT_ID TEAMCITY_PROJECT_NAME TEAMCITY_BUILD_TYPE_ID TEAMCITY_VCS_ROOT_ID GIT_URL DOCKER_IMAGE DEPLOY_VERSION_FILE; do
  if [[ -z "${!required_var}" ]]; then
    echo "${required_var} is required"
    exit 1
  fi
done

api() {
  local method="$1"
  local path="$2"
  local data="${3:-}"

  if [[ -n "${data}" ]]; then
    curl -sS -u ":${TEAMCITY_TOKEN}" -X "${method}" \
      -H "Content-Type: application/json" \
      -H "Accept: application/json" \
      "${TEAMCITY_URL}${path}" \
      -d "${data}"
  else
    curl -sS -u ":${TEAMCITY_TOKEN}" -X "${method}" \
      -H "Accept: application/json" \
      "${TEAMCITY_URL}${path}"
  fi
}

exists() {
  local path="$1"
  local code
  code=$(curl -sS -u ":${TEAMCITY_TOKEN}" -o /dev/null -w "%{http_code}" "${TEAMCITY_URL}${path}")
  [[ "${code}" == "200" ]]
}

build_update_version_script() {
  cat <<EOS
set -eu

test -n "%github.token%"

BRANCH="%k8s.branch%"
REPO="https://%github.user%:%github.token%@github.com/amolsurjuse/k8s-platform.git"
BUILD="%build.number%"

WORKDIR="\$(pwd)/k8s"
rm -rf "\$WORKDIR"
mkdir -p "\$WORKDIR"

git clone --branch "\$BRANCH" --depth 1 "\$REPO" "\$WORKDIR"
cd "\$WORKDIR"

FILE="${DEPLOY_VERSION_FILE}"

if [ ! -f "\$FILE" ]; then
  echo "ERROR: \$FILE not found"
  exit 1
fi

sed -i.bak "s/^\\([[:space:]]*tag:\\).*/\\1 \\"\${BUILD}\\"/g" "\$FILE"
rm -f "\$FILE.bak"

git add "\$FILE"
git config user.email "ci@teamcity"
git config user.name "teamcity-ci"
git commit -m "chore(${SERVICE_NAME}): deploy dev image tag \${BUILD}" || {
  echo "Nothing to commit"
  exit 0
}

git push origin "\$BRANCH"
EOS
}

ensure_project() {
  if exists "/app/rest/projects/id:${TEAMCITY_PROJECT_ID}"; then
    echo "TeamCity project exists: ${TEAMCITY_PROJECT_ID}"
    return
  fi

  api POST "/app/rest/projects" "$(jq -cn --arg id "${TEAMCITY_PROJECT_ID}" --arg name "${TEAMCITY_PROJECT_NAME}" --arg parent "${TEAMCITY_PARENT_PROJECT_ID}" '{id:$id,name:$name,parentProject:{id:$parent}}')" >/dev/null
  echo "Created project: ${TEAMCITY_PROJECT_ID}"
}

ensure_vcs_root() {
  if exists "/app/rest/vcs-roots/id:${TEAMCITY_VCS_ROOT_ID}"; then
    echo "VCS root exists: ${TEAMCITY_VCS_ROOT_ID}"
    return
  fi

  local payload
  payload=$(jq -cn \
    --arg id "${TEAMCITY_VCS_ROOT_ID}" \
    --arg name "${GIT_URL}#refs/heads/${GIT_BRANCH}" \
    --arg project "${TEAMCITY_PARENT_PROJECT_ID}" \
    --arg branch "refs/heads/${GIT_BRANCH}" \
    --arg url "${GIT_URL}" \
    --arg user "${DOCKER_USERNAME}" \
    '{
      id:$id,
      name:$name,
      vcsName:"jetbrains.git",
      project:{id:$project},
      properties:{
        property:[
          {name:"agentCleanFilesPolicy",value:"ALL_UNTRACKED"},
          {name:"agentCleanPolicy",value:"ON_BRANCH_CHANGE"},
          {name:"authMethod",value:"PASSWORD"},
          {name:"branch",value:$branch},
          {name:"secure:password",value:"%github.token%"},
          {name:"submoduleCheckout",value:"CHECKOUT"},
          {name:"teamcity:branchSpec",value:"refs/heads/*"},
          {name:"url",value:$url},
          {name:"useAlternates",value:"AUTO"},
          {name:"username",value:$user},
          {name:"usernameStyle",value:"USERID"}
        ]
      }
    }')

  api POST "/app/rest/vcs-roots" "${payload}" >/dev/null
  echo "Created VCS root: ${TEAMCITY_VCS_ROOT_ID}"
}

ensure_build_type() {
  if exists "/app/rest/buildTypes/id:${TEAMCITY_BUILD_TYPE_ID}"; then
    echo "Build config exists: ${TEAMCITY_BUILD_TYPE_ID}"
    return
  fi

  api POST "/app/rest/projects/id:${TEAMCITY_PROJECT_ID}/buildTypes" "$(jq -cn --arg id "${TEAMCITY_BUILD_TYPE_ID}" '{id:$id,name:"Build"}')" >/dev/null
  echo "Created build config: ${TEAMCITY_BUILD_TYPE_ID}"
}

attach_vcs_root_if_missing() {
  if api GET "/app/rest/buildTypes/id:${TEAMCITY_BUILD_TYPE_ID}" | jq -e --arg id "${TEAMCITY_VCS_ROOT_ID}" '."vcs-root-entries"."vcs-root-entry"[]?.id == $id' >/dev/null; then
    echo "VCS root already attached"
    return
  fi

  api POST "/app/rest/buildTypes/id:${TEAMCITY_BUILD_TYPE_ID}/vcs-root-entries" "$(jq -cn --arg id "${TEAMCITY_VCS_ROOT_ID}" '{id:$id,"vcs-root":{id:$id},"checkout-rules":""}')" >/dev/null
  echo "Attached VCS root"
}

set_parameters() {
  api PUT "/app/rest/buildTypes/id:${TEAMCITY_BUILD_TYPE_ID}/parameters/k8s.branch" "$(jq -cn --arg branch "${K8S_BRANCH}" '{name:"k8s.branch",value:$branch}')" >/dev/null
  api PUT "/app/rest/buildTypes/id:${TEAMCITY_BUILD_TYPE_ID}/parameters/docker.username" "$(jq -cn --arg user "${DOCKER_USERNAME}" '{name:"docker.username",value:$user}')" >/dev/null
  api PUT "/app/rest/buildTypes/id:${TEAMCITY_BUILD_TYPE_ID}/parameters/deployment.version.file" "$(jq -cn --arg value "${DEPLOY_VERSION_FILE}" '{name:"deployment.version.file",value:$value}')" >/dev/null
  echo "Set build parameters"
}

ensure_agent_requirement() {
  local requirement_count
  requirement_count=$(api GET "/app/rest/buildTypes/id:${TEAMCITY_BUILD_TYPE_ID}/agent-requirements" | jq '.count')
  if [[ "${requirement_count}" -gt 0 ]]; then
    echo "Agent requirement already configured"
    return
  fi

  api POST "/app/rest/buildTypes/id:${TEAMCITY_BUILD_TYPE_ID}/agent-requirements" "$(jq -cn '{type:"equals",properties:{property:[{name:"property-name",value:"system.agent.name"},{name:"property-value",value:"teamcity-minimal-agent"}]}}')" >/dev/null
  echo "Created agent requirement"
}

create_steps_if_empty() {
  local step_count
  step_count=$(api GET "/app/rest/buildTypes/id:${TEAMCITY_BUILD_TYPE_ID}" | jq '.steps.count')
  if [[ "${step_count}" -gt 0 ]]; then
    echo "Build steps already configured"
    return
  fi

  local maven_step_payload
  maven_step_payload=$(jq -cn --arg goals "${MAVEN_GOALS}" --arg pom "${POM_PATH}" --arg runnerArgs "${MAVEN_RUNNER_ARGS}" '
    {
      name:"Maven Build",
      type:"Maven2",
      properties:{
        property:
          (
            [
              {name:"goals",value:$goals},
              {name:"localRepoScope",value:"agent"},
              {name:"maven.path",value:"%teamcity.tool.maven.DEFAULT%"},
              {name:"pomLocation",value:$pom},
              {name:"teamcity.step.mode",value:"default"},
              {name:"userSettingsSelection",value:"userSettingsSelection:default"}
            ]
            + (if ($runnerArgs|length) > 0 then [{name:"runnerArgs",value:$runnerArgs}] else [] end)
          )
      }
    }')

  api POST "/app/rest/buildTypes/id:${TEAMCITY_BUILD_TYPE_ID}/steps" "${maven_step_payload}" >/dev/null

  api POST "/app/rest/buildTypes/id:${TEAMCITY_BUILD_TYPE_ID}/steps" "$(jq -cn --arg image "${DOCKER_IMAGE}:%build.number%" --arg dockerfile "${DOCKERFILE_PATH}" '{name:"Docker Build",type:"DockerCommand",properties:{property:[{name:"docker.command.type",value:"build"},{name:"docker.image.namesAndTags",value:$image},{name:"docker.push.remove.image",value:"true"},{name:"dockerfile.path",value:$dockerfile},{name:"dockerfile.source",value:"PATH"},{name:"teamcity.step.mode",value:"default"}]}}')" >/dev/null

  api POST "/app/rest/buildTypes/id:${TEAMCITY_BUILD_TYPE_ID}/steps" "$(jq -cn --arg image "${DOCKER_IMAGE}:%build.number%" '{name:"Docker Push",type:"DockerCommand",properties:{property:[{name:"docker.command.type",value:"push"},{name:"docker.image.namesAndTags",value:$image},{name:"docker.push.remove.image",value:"true"},{name:"dockerfile.source",value:"PATH"},{name:"teamcity.step.mode",value:"default"}]}}')" >/dev/null

  local update_script
  update_script="$(build_update_version_script)"
  api POST "/app/rest/buildTypes/id:${TEAMCITY_BUILD_TYPE_ID}/steps" "$(jq -cn --arg script "${update_script}" '{name:"Update build version",type:"simpleRunner",properties:{property:[{name:"script.content",value:$script},{name:"teamcity.step.mode",value:"default"},{name:"use.custom.script",value:"true"}]}}')" >/dev/null

  echo "Created build steps"
}

ensure_trigger() {
  local trigger_count
  trigger_count=$(api GET "/app/rest/buildTypes/id:${TEAMCITY_BUILD_TYPE_ID}" | jq '.triggers.count')
  if [[ "${trigger_count}" -gt 0 ]]; then
    echo "VCS trigger already configured"
    return
  fi

  api POST "/app/rest/buildTypes/id:${TEAMCITY_BUILD_TYPE_ID}/triggers" "$(jq -cn '{type:"vcsTrigger",properties:{property:[{name:"branchFilter",value:"+:<default>"},{name:"enableQueueOptimization",value:"true"},{name:"quietPeriodMode",value:"DO_NOT_USE"}]}}')" >/dev/null
  echo "Created VCS trigger"
}

ensure_project
ensure_vcs_root
ensure_build_type
attach_vcs_root_if_missing
set_parameters
ensure_agent_requirement
create_steps_if_empty
ensure_trigger

echo "Pipeline is ready: ${TEAMCITY_URL}/buildConfiguration/${TEAMCITY_BUILD_TYPE_ID}?mode=builds"
