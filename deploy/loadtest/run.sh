#!/usr/bin/env sh
set -eu

script_dir=$(CDPATH='' cd -- "$(dirname -- "$0")" && pwd)
repository_root=$(CDPATH='' cd -- "$script_dir/../.." && pwd)
config_file=${1:-"$script_dir/config.env"}

if [ -f "$config_file" ]; then
  set -a
  # The config file is an operator-owned shell environment file and must not be committed.
  . "$config_file"
  set +a
fi

profile=${LOADTEST_PROFILE:-smoke}
api_base_url=${LOADTEST_API_BASE_URL:-}
health_url=${LOADTEST_HEALTH_URL:-}
result_root=${LOADTEST_RESULT_ROOT:-"$script_dir/results"}
k6_image=${K6_IMAGE:-grafana/k6:2.0.0}
observe_k8s=${LOADTEST_OBSERVE_K8S:-false}
allow_writes=${LOADTEST_ALLOW_WRITES:-false}
run_id=${LOADTEST_RUN_ID:-"$(date -u '+%Y%m%dT%H%M%SZ')"}

require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    printf 'missing required command: %s\n' "$1" >&2
    exit 1
  fi
}

require_value() {
  name=$1
  eval "value=\${$name:-}"
  if [ -z "$value" ]; then
    printf 'missing required setting: %s\n' "$name" >&2
    exit 1
  fi
}

absolute_path() {
  path=$1
  directory=$(dirname "$path")
  filename=$(basename "$path")
  (CDPATH='' cd -- "$directory" && printf '%s/%s\n' "$(pwd)" "$filename")
}

profile_seconds() {
  if [ -n "${LOADTEST_DURATION_SECONDS:-}" ]; then
    case "$LOADTEST_DURATION_SECONDS" in
      *[!0-9]*|'')
        printf 'LOADTEST_DURATION_SECONDS must be a positive integer\n' >&2
        exit 1
        ;;
    esac
    if [ "$LOADTEST_DURATION_SECONDS" -le 0 ]; then
      printf 'LOADTEST_DURATION_SECONDS must be greater than zero\n' >&2
      exit 1
    fi
    printf '%s' "$LOADTEST_DURATION_SECONDS"
    return
  fi
  case "$profile" in
    smoke) printf '120' ;;
    baseline) printf '2100' ;;
    soak) printf '23400' ;;
    stress) printf '3600' ;;
    spike) printf '1200' ;;
    *)
      printf 'unsupported LOADTEST_PROFILE: %s\n' "$profile" >&2
      exit 1
      ;;
  esac
}

cleanup_runtime() {
  if [ -n "${observer_pid:-}" ]; then
    kill "$observer_pid" >/dev/null 2>&1 || true
    wait "$observer_pid" >/dev/null 2>&1 || true
  fi
  if [ -n "${monitor_pid:-}" ]; then
    kill "$monitor_pid" >/dev/null 2>&1 || true
    wait "$monitor_pid" >/dev/null 2>&1 || true
  fi
}

capture_cluster_snapshot() {
  label=$1
  [ "$observe_k8s" = "true" ] || return 0
  kubectl get nodes -o wide >"$result_directory/k8s-nodes-$label.txt" 2>&1 || true
  kubectl get pods -A -o wide >"$result_directory/k8s-pods-$label.txt" 2>&1 || true
  kubectl top nodes >"$result_directory/k8s-node-usage-$label.txt" 2>&1 || true
  kubectl top pods -A >"$result_directory/k8s-pod-usage-$label.txt" 2>&1 || true
  kubectl get events -A --sort-by=.lastTimestamp >"$result_directory/k8s-events-$label.txt" 2>&1 || true
}

require_command curl
require_command jq
require_value LOADTEST_API_BASE_URL
require_value LOADTEST_HEALTH_URL

case "$result_root" in
  /*) ;;
  *) result_root="$repository_root/$result_root" ;;
esac

case "$api_base_url" in
  *://*@*)
    printf 'refusing API URL with embedded credentials; use a credentials file or environment variables\n' >&2
    exit 1
    ;;
esac

case "$profile" in
  smoke|baseline|soak|stress|spike) ;;
  *)
    printf 'unsupported LOADTEST_PROFILE: %s\n' "$profile" >&2
    exit 1
    ;;
esac

if [ "$profile" != "smoke" ] \
    && [ "${LOADTEST_CONFIRM_TARGET:-}" != "$api_base_url" ]; then
  printf 'refusing non-smoke test: LOADTEST_CONFIRM_TARGET must exactly match LOADTEST_API_BASE_URL\n' >&2
  exit 1
fi

if [ "$allow_writes" = "true" ] \
    && [ "${LOADTEST_CONFIRM_WRITES:-}" != "isolated-test-environment" ]; then
  printf 'refusing writes: set LOADTEST_CONFIRM_WRITES=isolated-test-environment after verifying isolation and backups\n' >&2
  exit 1
fi

credentials_file=${LOADTEST_CREDENTIALS_FILE:-}
if [ -n "$credentials_file" ]; then
  if [ ! -r "$credentials_file" ]; then
    printf 'credentials file is not readable: %s\n' "$credentials_file" >&2
    exit 1
  fi
  credentials_file=$(absolute_path "$credentials_file")
  username=$(jq -er '.[0].username | select(type == "string" and length > 0)' "$credentials_file")
  password=$(jq -er '.[0].password | select(type == "string" and length > 0)' "$credentials_file")
else
  require_value LOADTEST_USERNAME
  require_value LOADTEST_PASSWORD
  username=$LOADTEST_USERNAME
  password=$LOADTEST_PASSWORD
fi

entity_create_file=${LOADTEST_ENTITY_CREATE_BODY_FILE:-}
entity_update_file=${LOADTEST_ENTITY_UPDATE_BODY_FILE:-}
for fixture in "$entity_create_file" "$entity_update_file"
do
  [ -n "$fixture" ] || continue
  if [ ! -r "$fixture" ]; then
    printf 'entity fixture is not readable: %s\n' "$fixture" >&2
    exit 1
  fi
  jq -e 'type == "object"' "$fixture" >/dev/null
done

mkdir -p "$result_root"
result_root=$(CDPATH='' cd -- "$result_root" && pwd)
result_directory="$result_root/$run_id"
if [ -e "$result_directory" ]; then
  printf 'result directory already exists: %s\n' "$result_directory" >&2
  exit 1
fi
mkdir -p "$result_directory"

export LOADTEST_RUN_ID=$run_id
export LOADTEST_PROFILE=$profile
export LOADTEST_API_BASE_URL=$api_base_url
export LOADTEST_HEALTH_URL=$health_url

trap cleanup_runtime EXIT HUP INT TERM

printf 'checking target health...\n'
curl --fail --silent --show-error --max-time 10 "$health_url" \
  >"$result_directory/preflight-health.txt"

login_payload=$(jq -nc --arg username "$username" --arg password "$password" \
  '{username:$username,password:$password}')
login_response=$(curl --fail --silent --show-error --max-time 15 \
  -H 'Content-Type: application/json' \
  --data "$login_payload" \
  "$api_base_url/auth/login")
printf '%s' "$login_response" | jq -e '
  (.code == 0 or .code == 200 or .code == "0" or .code == "200")
  and (.data.token | type == "string" and length > 0)
' >/dev/null
jq -n \
  --arg runId "$run_id" \
  --arg profile "$profile" \
  --arg apiBaseUrl "$api_base_url" \
  --arg healthUrl "$health_url" \
  --arg startedAt "$(date -u '+%Y-%m-%dT%H:%M:%SZ')" \
  --arg writePercent "${LOADTEST_WRITE_PERCENT:-3}" \
  --arg baseRate "${LOADTEST_BASE_RATE:-8}" \
  --arg peakRate "${LOADTEST_PEAK_RATE:-24}" \
  --arg spikeRate "${LOADTEST_SPIKE_RATE:-48}" \
  --arg maxVus "${LOADTEST_MAX_VUS:-120}" \
  --arg totalDuration "${LOADTEST_TOTAL_DURATION:-}" \
  --arg durationSeconds "${LOADTEST_DURATION_SECONDS:-}" \
  --arg businessPhases "${LOADTEST_BUSINESS_PHASES_JSON:-}" \
  --argjson writes "$([ "$allow_writes" = "true" ] && printf true || printf false)" \
  --argjson fileLifecycle \
    "$([ "${LOADTEST_ENABLE_FILE_LIFECYCLE:-false}" = "true" ] && printf true || printf false)" \
  '{
    run_id: $runId,
    profile: $profile,
    api_base_url: $apiBaseUrl,
    health_url: $healthUrl,
    started_at: $startedAt,
    writes_enabled: $writes,
    workload: {
      writes_enabled: $writes,
      write_percent: ($writePercent | tonumber),
      file_lifecycle_enabled: $fileLifecycle,
      base_rate: ($baseRate | tonumber),
      peak_rate: ($peakRate | tonumber),
      spike_rate: ($spikeRate | tonumber),
      max_vus: ($maxVus | tonumber),
      total_duration: (if $totalDuration == "" then null else $totalDuration end),
      duration_seconds: (if $durationSeconds == "" then null else ($durationSeconds | tonumber) end),
      business_phases: (if $businessPhases == "" then null else ($businessPhases | fromjson) end)
    }
  }' \
  >"$result_directory/run.json"

capture_cluster_snapshot before

if [ "$observe_k8s" = "true" ]; then
  require_command kubectl
  observation_seconds=$(( $(profile_seconds) + 120 ))
  OBSERVABILITY_OBSERVE_SECONDS=$observation_seconds \
  OBSERVABILITY_OBSERVE_RESULT_FILE="$result_directory/observability.jsonl" \
  FLOW_NAMESPACE=${FLOW_NAMESPACE:-flow} \
  FLOW_RELEASE=${FLOW_RELEASE:-flow} \
  OBSERVABILITY_NAMESPACE=${OBSERVABILITY_NAMESPACE:-flow-observability} \
  OBSERVABILITY_MAX_MEMORY_LIMIT_RATIO=${OBSERVABILITY_MAX_MEMORY_LIMIT_RATIO:-0.90} \
    "$repository_root/deploy/observability/observe-lite-observability.sh" \
      >"$result_directory/observer.log" 2>&1 &
  observer_pid=$!
fi

if [ "$observe_k8s" = "true" ]; then
  sleep 6
fi

"$script_dir/monitor-target.sh" "$result_directory" &
monitor_pid=$!

set +e
if command -v k6 >/dev/null 2>&1; then
  export LOADTEST_SUMMARY_DIR=$result_directory
  if [ -n "$credentials_file" ]; then
    export LOADTEST_CREDENTIALS_FILE=$credentials_file
  fi
  k6 run "$script_dir/k6/flow-business.js" \
    >"$result_directory/k6.log" 2>&1
  k6_status=$?
  sed -n '1,200p' "$result_directory/k6.log"
else
  require_command docker
  set -- run --rm --add-host=host.docker.internal:host-gateway \
    --user "$(id -u):$(id -g)" \
    -v "$script_dir:/work:ro" \
    -v "$result_directory:/results" \
    -e LOADTEST_API_BASE_URL -e LOADTEST_HEALTH_URL -e LOADTEST_PROFILE \
    -e LOADTEST_RUN_ID -e LOADTEST_BASE_RATE -e LOADTEST_PEAK_RATE \
    -e LOADTEST_SPIKE_RATE -e LOADTEST_MAX_VUS -e LOADTEST_WRITE_PERCENT \
    -e LOADTEST_AUTH_RATE_PER_MINUTE -e LOADTEST_ALLOW_WRITES \
    -e LOADTEST_ENABLE_FILE_LIFECYCLE -e LOADTEST_ENTITY_CODE \
    -e LOADTEST_ENTITY_LIST_KEY -e LOADTEST_HTTP_ERROR_RATE \
    -e LOADTEST_BUSINESS_ERROR_RATE -e LOADTEST_READ_P95_MS \
    -e LOADTEST_WRITE_P95_MS -e LOADTEST_AUTH_P95_MS \
    -e LOADTEST_P99_MS \
    -e LOADTEST_TOKEN_REFRESH_SECONDS -e LOADTEST_INSECURE_SKIP_TLS_VERIFY \
    -e K6_OUT -e K6_PROMETHEUS_RW_SERVER_URL \
    -e LOADTEST_BUSINESS_PHASES_JSON -e LOADTEST_TOTAL_DURATION \
    -e LOADTEST_DURATION_SECONDS \
    -e LOADTEST_SUMMARY_DIR=/results

  if [ -n "$credentials_file" ]; then
    set -- "$@" -v "$credentials_file:/run/secrets/loadtest-credentials.json:ro" \
      -e LOADTEST_CREDENTIALS_FILE=/run/secrets/loadtest-credentials.json
  else
    export LOADTEST_USERNAME LOADTEST_PASSWORD
    set -- "$@" -e LOADTEST_USERNAME -e LOADTEST_PASSWORD
  fi
  if [ -n "$entity_create_file" ]; then
    entity_create_file=$(absolute_path "$entity_create_file")
    set -- "$@" -v "$entity_create_file:/run/fixtures/entity-create.json:ro" \
      -e LOADTEST_ENTITY_CREATE_BODY_FILE=/run/fixtures/entity-create.json
  fi
  if [ -n "$entity_update_file" ]; then
    entity_update_file=$(absolute_path "$entity_update_file")
    set -- "$@" -v "$entity_update_file:/run/fixtures/entity-update.json:ro" \
      -e LOADTEST_ENTITY_UPDATE_BODY_FILE=/run/fixtures/entity-update.json
  fi
  docker "$@" "$k6_image" run /work/k6/flow-business.js \
    >"$result_directory/k6.log" 2>&1
  k6_status=$?
  sed -n '1,200p' "$result_directory/k6.log"
fi
set -e

cleanup_runtime
observer_pid=
monitor_pid=
capture_cluster_snapshot after

set +e
"$script_dir/analyze.sh" "$result_directory"
analysis_status=$?
set -e

jq --arg endedAt "$(date -u '+%Y-%m-%dT%H:%M:%SZ')" \
  --argjson k6Status "${k6_status:-0}" \
  --argjson analysisStatus "$analysis_status" \
  '. + {ended_at:$endedAt,k6_status:$k6Status,analysis_status:$analysisStatus}' \
  "$result_directory/run.json" >"$result_directory/run.json.tmp"
mv "$result_directory/run.json.tmp" "$result_directory/run.json"

if [ "${k6_status:-0}" -ne 0 ] || [ "$analysis_status" -ne 0 ]; then
  printf 'load test failed: %s\n' "$result_directory" >&2
  exit 1
fi

printf 'load test passed: %s\n' "$result_directory"
