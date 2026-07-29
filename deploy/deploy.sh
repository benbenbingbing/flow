#!/usr/bin/env sh
set -eu

: "${IMAGE_TAG:?IMAGE_TAG is required}"
: "${SERVER_IMAGE:?SERVER_IMAGE is required}"
: "${WEB_IMAGE:?WEB_IMAGE is required}"

DEPLOY_DIR=${DEPLOY_DIR:-/opt/flow}
COMPOSE_FILE=${COMPOSE_FILE:-compose.prod.yml}
WAIT_TIMEOUT=${WAIT_TIMEOUT:-900}

cd "$DEPLOY_DIR"

if [ ! -f .env ]; then
    echo "Missing $DEPLOY_DIR/.env" >&2
    exit 1
fi

compose() {
    SERVER_IMAGE="$SERVER_IMAGE" \
    WEB_IMAGE="$WEB_IMAGE" \
    docker compose --env-file .env -f "$COMPOSE_FILE" "$@"
}

compose config --quiet

if ! compose up -d mysql --wait --wait-timeout "$WAIT_TIMEOUT"; then
    compose ps mysql
    compose logs --tail=200 mysql
    exit 1
fi

# Docker entrypoint initialization only runs for an empty data directory.
# Re-apply the idempotent grants on every deployment so upgrades of existing
# volumes receive the dedicated schema identity as well.
compose exec -T mysql sh \
    /docker-entrypoint-initdb.d/10-database-users.sh

if ! compose up -d --remove-orphans --wait --wait-timeout "$WAIT_TIMEOUT"; then
    compose ps
    compose logs --tail=200 server web
    exit 1
fi

printf '%s\n' "$IMAGE_TAG" > .deployed-image-tag
compose ps
