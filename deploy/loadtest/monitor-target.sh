#!/usr/bin/env sh
set -eu

result_directory=${1:?result directory is required}
health_url=${LOADTEST_HEALTH_URL:?LOADTEST_HEALTH_URL is required}
interval_seconds=${LOADTEST_CANARY_INTERVAL_SECONDS:-30}
output_file="$result_directory/target-canary.jsonl"

trap 'exit 0' HUP INT TERM
: >"$output_file"

while :
do
  sampled_at=$(date -u '+%Y-%m-%dT%H:%M:%SZ')
  result=$(curl --silent --show-error --output /dev/null \
    --write-out '%{http_code} %{time_total}' --max-time 10 "$health_url" 2>/dev/null \
    || printf '000 10')
  status=$(printf '%s' "$result" | awk '{print $1}')
  duration_ms=$(printf '%s' "$result" | awk '{printf "%.0f", $2 * 1000}')
  case "$status" in
    2??) available=true ;;
    *) available=false ;;
  esac
  printf '{"sampled_at":"%s","available":%s,"status":%s,"duration_ms":%s}\n' \
    "$sampled_at" "$available" "${status:-000}" "${duration_ms:-10000}" >>"$output_file"
  sleep "$interval_seconds"
done
