#!/usr/bin/env bash
set -euo pipefail

repository_root=$(CDPATH='' cd -- "$(dirname -- "$0")/../../.." && pwd)
acceptance_namespace=${REFERENCE_NAMESPACE:-flow-open-integration}
reference_image=${REFERENCE_IMAGE:-flow-reference-external:local}
reference_deployment=${REFERENCE_DEPLOYMENT:-reference-external-system}
reference_service=${REFERENCE_SERVICE:-reference-external-system}
flow_namespace=${FLOW_NAMESPACE:-flow-hardening}
flow_service=${FLOW_SERVICE:-flow-hardening-server}
flow_deployment=${FLOW_DEPLOYMENT:-flow-hardening-server}
flow_port=${FLOW_LOCAL_PORT:-18080}
reference_port=${REFERENCE_LOCAL_PORT:-19089}
flow_input_json=${FLOW_INPUT_JSON:-'{"requesterId":"reference-user"}'}

: "${FLOW_CLIENT_ID:?FLOW_CLIENT_ID is required}"
: "${FLOW_CLIENT_SECRET:?FLOW_CLIENT_SECRET is required}"
: "${FLOW_SCENARIO_KEY:?FLOW_SCENARIO_KEY is required}"

command -v kubectl >/dev/null
command -v node >/dev/null
command -v curl >/dev/null

if [[ "${BUILD_REFERENCE_IMAGE:-1}" == "1" ]]; then
  command -v docker >/dev/null
  docker build --file "$repository_root/examples/open-integration/Dockerfile.reference" \
    --tag "$reference_image" "$repository_root/examples/open-integration"
fi

if [[ -n "${K3D_CLUSTER:-}" ]]; then
  command -v k3d >/dev/null
  k3d image import "$reference_image" --cluster "$K3D_CLUSTER"
fi

kubectl apply -f "$repository_root/examples/open-integration/k3s/namespace.yaml"
kubectl -n "$acceptance_namespace" create secret generic reference-external-credentials \
  --from-literal=client-id="${REFERENCE_CLIENT_ID:-reference-client}" \
  --from-literal=client-secret="${REFERENCE_CLIENT_SECRET:-reference-secret}" \
  --from-literal=webhook-secret="${REFERENCE_WEBHOOK_SECRET:-reference-webhook-secret}" \
  --dry-run=client -o yaml | kubectl apply -f -
kubectl apply -k "$repository_root/examples/open-integration/k3s"
kubectl -n "$acceptance_namespace" rollout status "deployment/$reference_deployment" --timeout=120s

flow_base_url=${FLOW_BASE_URL:-}
flow_port_forward_pid=
reference_base_url=${REFERENCE_BASE_URL:-}
reference_port_forward_pid=
flow_restart_port_forward=${FLOW_RESTART_PORT_FORWARD:-0}
fault_receiver_scaled=0
restore_fault_receiver() {
  if [[ "$fault_receiver_scaled" == "1" ]]; then
    kubectl -n "$acceptance_namespace" scale "deployment/$reference_deployment" --replicas=1 \
      >/dev/null 2>&1 || true
    kubectl -n "$acceptance_namespace" rollout status "deployment/$reference_deployment" --timeout=120s \
      >/dev/null 2>&1 || true
    fault_receiver_scaled=0
  fi
}
cleanup() {
  restore_fault_receiver
  if [[ -n "$flow_port_forward_pid" ]]; then
    kill "$flow_port_forward_pid" 2>/dev/null || true
  fi
  if [[ -n "$reference_port_forward_pid" ]]; then
    kill "$reference_port_forward_pid" 2>/dev/null || true
  fi
}
trap cleanup EXIT HUP INT TERM

start_flow_port_forward() {
  kubectl -n "$flow_namespace" port-forward "service/$flow_service" "$flow_port:8080" \
    >/tmp/flow-open-integration-port-forward.log 2>&1 &
  flow_port_forward_pid=$!
  for attempt in {1..30}; do
    if curl --silent --fail "http://127.0.0.1:$flow_port/healthz" >/dev/null 2>&1; then
      flow_base_url="http://127.0.0.1:$flow_port"
      break
    fi
    sleep 1
  done
  : "${flow_base_url:?unable to establish Flow port-forward}"
}

wait_for_flow() {
  for attempt in {1..30}; do
    if curl --silent --fail "$flow_base_url/healthz" >/dev/null 2>&1; then
      return 0
    fi
    sleep 1
  done
  return 1
}

if [[ -z "$flow_base_url" ]]; then
  start_flow_port_forward
fi

if [[ -z "$reference_base_url" ]]; then
  kubectl -n "$acceptance_namespace" port-forward "service/$reference_service" \
    "$reference_port:9089" >/tmp/flow-reference-port-forward.log 2>&1 &
  reference_port_forward_pid=$!
  for attempt in {1..30}; do
    if curl --silent --fail "http://127.0.0.1:$reference_port/healthz" >/dev/null 2>&1; then
      reference_base_url="http://127.0.0.1:$reference_port"
      break
    fi
    sleep 1
  done
  : "${reference_base_url:?unable to establish reference system port-forward}"
fi

FLOW_BASE_URL="$flow_base_url" \
REFERENCE_BASE_URL="$reference_base_url" \
FLOW_CLIENT_ID="$FLOW_CLIENT_ID" \
FLOW_CLIENT_SECRET="$FLOW_CLIENT_SECRET" \
FLOW_SCENARIO_KEY="$FLOW_SCENARIO_KEY" \
FLOW_SCENARIO_KEY_V2="${FLOW_SCENARIO_KEY_V2:-}" \
FLOW_INPUT_JSON="$flow_input_json" \
REFERENCE_CLIENT_ID="${REFERENCE_CLIENT_ID:-reference-client}" \
REFERENCE_CLIENT_SECRET="${REFERENCE_CLIENT_SECRET:-reference-secret}" \
REFERENCE_WEBHOOK_SECRET="${REFERENCE_WEBHOOK_SECRET:-reference-webhook-secret}" \
node "$repository_root/examples/open-integration/scenario-acceptance.mjs"

if [[ "${RUN_FAULT_SCENARIOS:-0}" == "1" ]]; then
  kubectl -n "$acceptance_namespace" scale "deployment/$reference_deployment" --replicas=0
  fault_receiver_scaled=1
  FLOW_BASE_URL="$flow_base_url" REFERENCE_BASE_URL='' \
    FLOW_CLIENT_ID="$FLOW_CLIENT_ID" FLOW_CLIENT_SECRET="$FLOW_CLIENT_SECRET" \
    FLOW_SCENARIO_KEY="$FLOW_SCENARIO_KEY" \
    FLOW_INPUT_JSON="$flow_input_json" \
    SKIP_REFERENCE_CONTRACT=1 \
    node "$repository_root/examples/open-integration/scenario-acceptance.mjs"
  kubectl -n "$acceptance_namespace" scale "deployment/$reference_deployment" --replicas=1
  kubectl -n "$acceptance_namespace" rollout status "deployment/$reference_deployment" --timeout=120s
  fault_receiver_scaled=0

  kubectl -n "$flow_namespace" rollout restart "deployment/$flow_deployment"
  kubectl -n "$flow_namespace" rollout status "deployment/$flow_deployment" --timeout=180s
  if [[ -n "$flow_port_forward_pid" ]]; then
    kill "$flow_port_forward_pid" 2>/dev/null || true
    flow_port_forward_pid=
    flow_base_url=
    start_flow_port_forward
  elif [[ "$flow_restart_port_forward" == "1" ]]; then
    flow_base_url=
    start_flow_port_forward
  else
    if ! wait_for_flow; then
      printf 'Flow endpoint did not recover after restart: %s\n' \
        "$flow_base_url" >&2
      exit 1
    fi
  fi
  FLOW_BASE_URL="$flow_base_url" REFERENCE_BASE_URL='' \
    FLOW_CLIENT_ID="$FLOW_CLIENT_ID" FLOW_CLIENT_SECRET="$FLOW_CLIENT_SECRET" \
    FLOW_SCENARIO_KEY="$FLOW_SCENARIO_KEY" \
    FLOW_INPUT_JSON="$flow_input_json" \
    node "$repository_root/examples/open-integration/scenario-acceptance.mjs"
fi

printf 'open integration k3s acceptance passed\n'
