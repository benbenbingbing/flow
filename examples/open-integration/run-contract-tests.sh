#!/usr/bin/env bash
set -euo pipefail

: "${FLOW_BASE_URL:?FLOW_BASE_URL is required}"
: "${FLOW_CLIENT_ID:?FLOW_CLIENT_ID is required}"
: "${FLOW_CLIENT_SECRET:?FLOW_CLIENT_SECRET is required}"

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"

(
  cd "$SCRIPT_DIR/java"
  mvn -B -ntp -q compile exec:java
)

node "$SCRIPT_DIR/javascript/flow-open-api-example.mjs"
