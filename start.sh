#!/usr/bin/env bash

set -Eeuo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SERVER_PID_FILE="$ROOT_DIR/workflow-server/server.pid"
SCHEMA_WORKER_PID_FILE="$ROOT_DIR/workflow-server/schema-worker.pid"
WEB_PID_FILE="$ROOT_DIR/workflow-web/web.pid"
SERVER_LOG="$ROOT_DIR/workflow-server/server.log"
SCHEMA_WORKER_LOG="$ROOT_DIR/workflow-server/schema-worker.log"
WEB_LOG="$ROOT_DIR/workflow-web/web.log"
SERVER_JAR="$ROOT_DIR/workflow-server/workflow-app/target/workflow-server-1.0.0.jar"
MIGRATOR_JAR="$ROOT_DIR/workflow-server/workflow-db-migrator/target/workflow-db-migrator-1.0.0-exec.jar"
WEB_EXECUTABLE="$ROOT_DIR/workflow-web/node_modules/.bin/vite"
WEB_PROCESS_PATTERN="node_modules/.bin/vite"

action="${1:-start}"
environment_file="${FLOW_ENV_FILE:-$ROOT_DIR/.env}"

usage() {
    cat <<'EOF'
Usage: ./start.sh [start|stop|status]

  start   Build, migrate, and restart the local application (default)
  stop    Stop application processes started by this script
  status  Show local process and endpoint status

Environment:
  START_LOCAL_MYSQL=auto|true|false
      auto starts the Compose MySQL service when DB_HOST is localhost.
EOF
}

log() {
    printf '%s\n' "$*"
}

fail() {
    printf 'ERROR: %s\n' "$*" >&2
    exit 1
}

require_command() {
    command -v "$1" >/dev/null 2>&1 || fail "Required command not found: $1"
}

load_environment() {
    if [[ -f "$environment_file" ]]; then
        set -a
        # shellcheck disable=SC1090
        source "$environment_file"
        set +a
    elif [[ "$action" == "start" ]]; then
        fail "Missing $environment_file. Copy .env.example and replace every placeholder."
    fi

    if [[ -n "${JAVA_HOME:-}" ]]; then
        export JAVA_HOME
    fi

    export SERVER_PORT="${SERVER_PORT:-8080}"
    export WEB_PORT="${WEB_PORT:-3000}"
    export CORS_ALLOWED_ORIGINS="${CORS_ALLOWED_ORIGINS:-http://localhost:${WEB_PORT},http://127.0.0.1:${WEB_PORT}}"
    export DB_HOST="${DB_HOST:-localhost}"
    export DB_PORT="${DB_PORT:-3306}"
    export DB_NAME="${DB_NAME:-workflow}"
    export SPRING_DATASOURCE_URL="${SPRING_DATASOURCE_URL:-jdbc:mysql://${DB_HOST}:${DB_PORT}/${DB_NAME}?serverTimezone=UTC&useUnicode=true&characterEncoding=utf-8&connectionCollation=utf8mb4_unicode_ci&nullCatalogMeansCurrent=true}"
    export SCHEMA_DATASOURCE_URL="${SCHEMA_DATASOURCE_URL:-$SPRING_DATASOURCE_URL}"
}

validate_port() {
    local name="$1"
    local value="$2"
    [[ "$value" =~ ^[0-9]+$ ]] || fail "$name must be an integer"
    ((value >= 1 && value <= 65535)) || fail "$name must be between 1 and 65535"
}

validate_start_environment() {
    local name value bootstrap_password normalized_password
    for name in \
        DB_USERNAME DB_PASSWORD \
        SCHEMA_DB_USERNAME SCHEMA_DB_PASSWORD \
        JWT_SECRET CONFIG_MIGRATION_SIGNING_KEY \
        WORKFLOW_BOOTSTRAP_ADMIN_PASSWORD; do
        value="${!name:-}"
        [[ -n "$value" ]] || fail "$name is required in .env"
        [[ "$value" != *"replace-with"* ]] || fail "$name still contains a template placeholder"
    done

    [[ "$DB_USERNAME" != "$SCHEMA_DB_USERNAME" ]] \
        || fail "DB_USERNAME and SCHEMA_DB_USERNAME must be different"

    bootstrap_password="$WORKFLOW_BOOTSTRAP_ADMIN_PASSWORD"
    if ((${#bootstrap_password} < 14 || ${#bootstrap_password} > 72)) \
        || [[ ! "$bootstrap_password" =~ [[:lower:]] ]] \
        || [[ ! "$bootstrap_password" =~ [[:upper:]] ]] \
        || [[ ! "$bootstrap_password" =~ [[:digit:]] ]]; then
        fail "WORKFLOW_BOOTSTRAP_ADMIN_PASSWORD must be 14-72 characters and contain uppercase, lowercase, and numeric characters"
    fi
    normalized_password="$(
        printf '%s' "$bootstrap_password" |
            tr '[:upper:]' '[:lower:]'
    )"
    if [[ "$normalized_password" == *admin* \
        || "$normalized_password" == *password* \
        || "$normalized_password" == *replace-with* ]]; then
        fail "WORKFLOW_BOOTSTRAP_ADMIN_PASSWORD contains a forbidden public pattern"
    fi

    validate_port SERVER_PORT "$SERVER_PORT"
    validate_port WEB_PORT "$WEB_PORT"
    validate_port DB_PORT "$DB_PORT"
}

validate_toolchain() {
    local java_major node_major
    for command in java mvn node npm curl lsof ps; do
        require_command "$command"
    done

    java_major="$(
        java -version 2>&1 |
            awk -F'"' '/version/ { split($2, parts, "."); print parts[1]; exit }'
    )"
    [[ "$java_major" =~ ^[0-9]+$ ]] || fail "Unable to determine the Java version"
    ((java_major >= 21)) || fail "JDK 21 or newer is required; found Java $java_major"

    node_major="$(node -p 'Number(process.versions.node.split(".")[0])')"
    [[ "$node_major" =~ ^[0-9]+$ ]] || fail "Unable to determine the Node.js version"
    ((node_major >= 22)) || fail "Node.js 22 or newer is required; found Node.js $node_major"
}

pid_command() {
    ps -p "$1" -o command= 2>/dev/null || true
}

pid_working_directory() {
    lsof -a -p "$1" -d cwd -Fn 2>/dev/null |
        sed -n 's/^n//p' |
        head -n 1
}

pid_is_running() {
    local state
    kill -0 "$1" 2>/dev/null || return 1
    state="$(ps -p "$1" -o state= 2>/dev/null | tr -d '[:space:]')"
    [[ -n "$state" && "$state" != Z* ]]
}

pid_matches_service() {
    local pid="$1"
    local expected_pattern="$2"
    local expected_working_directory="${3:-}"
    local command working_directory

    command="$(pid_command "$pid")"
    [[ "$command" == *"$expected_pattern"* ]] || return 1
    if [[ -n "$expected_working_directory" ]]; then
        working_directory="$(pid_working_directory "$pid")"
        [[ "$working_directory" == "$expected_working_directory" ]] || return 1
    fi
}

stop_pid_file() {
    local pid_file="$1"
    local service_name="$2"
    local expected_pattern="$3"
    local expected_working_directory="${4:-}"
    local pid command working_directory

    [[ -f "$pid_file" ]] || return 0
    pid="$(tr -d '[:space:]' <"$pid_file")"
    if [[ ! "$pid" =~ ^[0-9]+$ ]] || ! pid_is_running "$pid"; then
        rm -f "$pid_file"
        return 0
    fi

    command="$(pid_command "$pid")"
    if ! pid_matches_service \
        "$pid" "$expected_pattern" "$expected_working_directory"; then
        working_directory="$(pid_working_directory "$pid")"
        fail "$service_name PID file points to an unrelated process ($pid): $command (cwd: ${working_directory:-unknown})"
    fi

    log "Stopping $service_name (PID $pid)"
    kill "$pid"
    for _ in {1..20}; do
        if ! pid_is_running "$pid"; then
            rm -f "$pid_file"
            return 0
        fi
        sleep 0.5
    done
    fail "$service_name did not stop within 10 seconds (PID $pid)"
}

stop_owned_listener() {
    local port="$1"
    local service_name="$2"
    local expected_pattern="$3"
    local expected_working_directory="${4:-}"
    local pid command working_directory

    while IFS= read -r pid; do
        [[ -n "$pid" ]] || continue
        command="$(pid_command "$pid")"
        if ! pid_matches_service \
            "$pid" "$expected_pattern" "$expected_working_directory"; then
            working_directory="$(pid_working_directory "$pid")"
            fail "$service_name port $port is used by an unrelated process ($pid): $command (cwd: ${working_directory:-unknown})"
        fi
        log "Stopping untracked $service_name listener (PID $pid)"
        kill "$pid"
    done < <(lsof -tiTCP:"$port" -sTCP:LISTEN 2>/dev/null || true)
}

wait_for_port_release() {
    local port="$1"
    local service_name="$2"
    for _ in {1..20}; do
        if ! lsof -tiTCP:"$port" -sTCP:LISTEN >/dev/null 2>&1; then
            return 0
        fi
        sleep 0.5
    done
    fail "$service_name port $port was not released within 10 seconds"
}

stop_application() {
    stop_pid_file \
        "$WEB_PID_FILE" "web" \
        "$WEB_PROCESS_PATTERN" "$ROOT_DIR/workflow-web"
    stop_pid_file \
        "$SERVER_PID_FILE" "server" \
        "workflow-server-1.0.0.jar" "$ROOT_DIR"
    stop_pid_file \
        "$SCHEMA_WORKER_PID_FILE" "schema worker" \
        "workflow-db-migrator-1.0.0-exec.jar" "$ROOT_DIR"
    stop_owned_listener \
        "$WEB_PORT" "web" \
        "$WEB_PROCESS_PATTERN" "$ROOT_DIR/workflow-web"
    stop_owned_listener \
        "$SERVER_PORT" "server" \
        "workflow-server-1.0.0.jar" "$ROOT_DIR"
    wait_for_port_release "$WEB_PORT" "web"
    wait_for_port_release "$SERVER_PORT" "server"
}

process_status() {
    local name="$1"
    local pid_file="$2"
    local pid
    if [[ ! -f "$pid_file" ]]; then
        printf '%-16s %s\n' "$name" "stopped"
        return
    fi
    pid="$(tr -d '[:space:]' <"$pid_file")"
    if [[ "$pid" =~ ^[0-9]+$ ]] && pid_is_running "$pid"; then
        printf '%-16s running (PID %s)\n' "$name" "$pid"
    else
        printf '%-16s %s\n' "$name" "stale PID file"
    fi
}

show_status() {
    process_status "server" "$SERVER_PID_FILE"
    process_status "schema-worker" "$SCHEMA_WORKER_PID_FILE"
    process_status "web" "$WEB_PID_FILE"

    if curl --fail --silent --max-time 2 \
        "http://127.0.0.1:${SERVER_PORT}/healthz" >/dev/null 2>&1; then
        printf '%-16s %s\n' "server health" "ready"
    else
        printf '%-16s %s\n' "server health" "unavailable"
    fi
    if curl --fail --silent --max-time 2 \
        "http://127.0.0.1:${WEB_PORT}/" >/dev/null 2>&1; then
        printf '%-16s %s\n' "web health" "ready"
    else
        printf '%-16s %s\n' "web health" "unavailable"
    fi
}

should_start_local_mysql() {
    case "${START_LOCAL_MYSQL:-auto}" in
        true) return 0 ;;
        false) return 1 ;;
        auto)
            [[ "$DB_HOST" == "localhost" || "$DB_HOST" == "127.0.0.1" ]]
            ;;
        *) fail "START_LOCAL_MYSQL must be auto, true, or false" ;;
    esac
}

start_mysql() {
    should_start_local_mysql || return 0
    require_command docker
    [[ -n "${DB_ROOT_PASSWORD:-}" ]] || fail "DB_ROOT_PASSWORD is required for local MySQL"
    [[ "$DB_ROOT_PASSWORD" != *"replace-with"* ]] \
        || fail "DB_ROOT_PASSWORD still contains a template placeholder"

    log "Starting local MySQL"
    (
        cd "$ROOT_DIR"
        docker compose up -d mysql --wait --wait-timeout 180
        docker compose exec -T mysql \
            bash /docker-entrypoint-initdb.d/10-database-users.sh
    )
}

build_application() {
    log "Building backend"
    (
        cd "$ROOT_DIR/workflow-server"
        mvn -B -ntp -pl workflow-app -am clean package -DskipTests
    )

    log "Installing and building frontend"
    (
        cd "$ROOT_DIR/workflow-web"
        npm ci
        npm run build
    )
}

run_migrations() {
    [[ -f "$MIGRATOR_JAR" ]] || fail "Database migrator artifact was not built"
    log "Applying database migrations"
    java -jar "$MIGRATOR_JAR"
}

start_detached() {
    local pid_file="$1"
    local log_file="$2"
    local working_directory="$3"
    shift 3

    node -e '
const { closeSync, openSync, writeFileSync } = require("node:fs");
const { spawn } = require("node:child_process");
const [pidFile, logFile, cwd, command, ...args] = process.argv.slice(1);
const output = openSync(logFile, "w");
const child = spawn(command, args, {
    cwd,
    env: process.env,
    detached: true,
    stdio: ["ignore", output, output]
});
closeSync(output);
if (!child.pid) {
    throw new Error(`Unable to start ${command}`);
}
writeFileSync(pidFile, `${child.pid}\n`);
child.unref();
' "$pid_file" "$log_file" "$working_directory" "$@"
}

start_schema_worker() {
    log "Starting schema worker"
    start_detached \
        "$SCHEMA_WORKER_PID_FILE" \
        "$SCHEMA_WORKER_LOG" \
        "$ROOT_DIR" \
        env \
        MIGRATION_COMMAND=schema-worker \
        SCHEMA_WORKER_ID="local-$(hostname)-$$" \
        java -jar "$MIGRATOR_JAR"
}

start_server() {
    log "Starting server"
    (
        unset SCHEMA_DATASOURCE_URL SCHEMA_DB_USERNAME SCHEMA_DB_PASSWORD
        export SPRING_FLYWAY_ENABLED=false
        export FLOWABLE_SCHEMA_UPDATE=false
        export WORKFLOW_SCHEMA_PUBLISHER_MODE=queue
        start_detached \
            "$SERVER_PID_FILE" \
            "$SERVER_LOG" \
            "$ROOT_DIR" \
            java -jar "$SERVER_JAR"
    )
}

start_web() {
    [[ -x "$WEB_EXECUTABLE" ]] || fail "Vite executable was not installed"
    log "Starting web development server"
    start_detached \
        "$WEB_PID_FILE" \
        "$WEB_LOG" \
        "$ROOT_DIR/workflow-web" \
        env \
        VITE_API_PROXY_TARGET="${VITE_API_PROXY_TARGET:-http://127.0.0.1:${SERVER_PORT}}" \
        "$WEB_EXECUTABLE" \
        --host 0.0.0.0 \
        --port "$WEB_PORT" \
        --strictPort
}

cleanup_failed_start() {
    local startup_status=$?
    trap - EXIT
    set +e
    log "Startup failed; stopping application processes started during this run." >&2
    stop_application
    exit "$startup_status"
}

wait_for_process() {
    local service_name="$1"
    local pid_file="$2"
    local url="$3"
    local log_file="$4"
    local pid
    pid="$(tr -d '[:space:]' <"$pid_file")"

    for _ in {1..90}; do
        if ! pid_is_running "$pid"; then
            tail -n 100 "$log_file" >&2 || true
            fail "$service_name exited before becoming ready"
        fi
        if curl --fail --silent --max-time 2 "$url" >/dev/null 2>&1; then
            log "$service_name is ready: $url"
            return 0
        fi
        sleep 1
    done

    tail -n 100 "$log_file" >&2 || true
    fail "$service_name did not become ready within 90 seconds"
}

start_application() {
    validate_start_environment
    validate_toolchain
    stop_application
    trap cleanup_failed_start EXIT
    start_mysql
    build_application
    run_migrations
    start_schema_worker
    start_server
    wait_for_process \
        "server" "$SERVER_PID_FILE" \
        "http://127.0.0.1:${SERVER_PORT}/healthz" "$SERVER_LOG"
    start_web
    wait_for_process \
        "web" "$WEB_PID_FILE" \
        "http://127.0.0.1:${WEB_PORT}/" "$WEB_LOG"

    log ""
    log "Flow is running."
    log "Web:    http://127.0.0.1:${WEB_PORT}"
    log "API:    http://127.0.0.1:${SERVER_PORT}/api"
    log "Logs:   $SERVER_LOG"
    log "        $SCHEMA_WORKER_LOG"
    log "        $WEB_LOG"
    trap - EXIT
}

load_environment

case "$action" in
    start)
        start_application
        ;;
    stop)
        require_command lsof
        require_command ps
        stop_application
        log "Flow application processes are stopped."
        ;;
    status)
        require_command curl
        show_status
        ;;
    -h|--help|help)
        usage
        ;;
    *)
        usage >&2
        exit 2
        ;;
esac
