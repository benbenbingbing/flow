#!/usr/bin/env sh
set -eu

script_dir=$(CDPATH='' cd -- "$(dirname -- "$0")" && pwd)
config_file=${1:-"$script_dir/config.env"}

if [ -f "$config_file" ]; then
  set -a
  . "$config_file"
  set +a
fi

api_base_url=${LOADTEST_API_BASE_URL:?LOADTEST_API_BASE_URL is required}
run_id=${LOADTEST_RUN_ID:?LOADTEST_RUN_ID is required}
prefix="load_${run_id}_"
file_idempotency_prefix="load-file-${run_id}-"

case "$run_id" in
  ''|*[!a-zA-Z0-9_-]*)
    printf 'refusing unsafe cleanup run id: %s\n' "$run_id" >&2
    exit 1
    ;;
  *)
    ;;
esac

if [ "${LOADTEST_CONFIRM_CLEANUP:-}" != "$run_id" ]; then
  printf 'set LOADTEST_CONFIRM_CLEANUP=%s to remove only resources from this run\n' "$run_id" >&2
  exit 1
fi

if [ -n "${LOADTEST_CREDENTIALS_FILE:-}" ]; then
  username=$(jq -er '.[0].username' "$LOADTEST_CREDENTIALS_FILE")
  password=$(jq -er '.[0].password' "$LOADTEST_CREDENTIALS_FILE")
else
  username=${LOADTEST_USERNAME:?LOADTEST_USERNAME is required}
  password=${LOADTEST_PASSWORD:?LOADTEST_PASSWORD is required}
fi

login_payload=$(jq -nc --arg username "$username" --arg password "$password" \
  '{username:$username,password:$password}')
token=$(curl --fail --silent --show-error --max-time 15 \
  -H 'Content-Type: application/json' --data "$login_payload" \
  "$api_base_url/auth/login" | jq -er '.data.token')

authorized_get() {
  curl --fail --silent --show-error --max-time 30 \
    -H "Authorization: Bearer $token" "$1"
}

authorized_post() {
  response=$(curl --fail --silent --show-error --max-time 30 \
    -X POST -H "Authorization: Bearer $token" \
    -H 'Content-Type: application/json' --data '{}' "$1")
  printf '%s' "$response" | jq -e '
    .code == 0 or .code == 200 or .code == "0" or .code == "200"
  ' >/dev/null
}

cleanup_kubernetes_files() {
  [ "${LOADTEST_CLEANUP_K8S_FILES:-false}" = "true" ] || return 0

  for command_name in kubectl jq
  do
    if ! command -v "$command_name" >/dev/null 2>&1; then
      printf 'missing required command for file cleanup: %s\n' \
        "$command_name" >&2
      exit 1
    fi
  done

  flow_namespace=${FLOW_NAMESPACE:-flow}
  mysql_statefulset=${FLOW_MYSQL_STATEFULSET:-local-mysql}
  mysql_database=${FLOW_MYSQL_DATABASE:-workflow}
  query="SELECT storage_url FROM storage_file_object WHERE deleted = 0 AND LEFT(idempotency_key, CHAR_LENGTH('${file_idempotency_prefix}')) = '${file_idempotency_prefix}';"
  file_urls=$(kubectl -n "$flow_namespace" exec \
    "statefulset/$mysql_statefulset" -- sh -c '
      MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql \
        --batch --skip-column-names -uroot "$1" --execute="$2"
    ' sh "$mysql_database" "$query")

  deleted_files=0
  printf '%s\n' "$file_urls" | while IFS= read -r file_url
  do
    [ -n "$file_url" ] || continue
    encoded_url=$(printf '%s' "$file_url" | jq -sRr @uri)
    authorized_post "$api_base_url/file?url=$encoded_url" >/dev/null
    deleted_files=$((deleted_files + 1))
    printf 'deleted file %s\n' "$deleted_files"
  done
}

groups=$(authorized_get "$api_base_url/system/group/list")
printf '%s' "$groups" | jq -r --arg prefix "$prefix" \
  '.data[]? | select(.groupCode | startswith($prefix)) | .id' \
  | while IFS= read -r id
    do
      [ -n "$id" ] || continue
      authorized_post "$api_base_url/system/group/$id/delete" >/dev/null
      printf 'deleted group %s\n' "$id"
    done

dicts=$(authorized_get "$api_base_url/system/dict/page?pageNum=1&pageSize=10000&dictCode=$(printf '%s' "$prefix" | jq -sRr @uri)")
printf '%s' "$dicts" | jq -r --arg prefix "$prefix" '
  (.data.list // .data.records // .data.rows // [])[]?
  | select(.dictCode | startswith($prefix)) | .id
' | while IFS= read -r id
    do
      [ -n "$id" ] || continue
      authorized_post "$api_base_url/system/dict/$id/delete" >/dev/null
      printf 'deleted dictionary %s\n' "$id"
    done

cleanup_kubernetes_files

printf 'cleanup completed for run %s\n' "$run_id"
