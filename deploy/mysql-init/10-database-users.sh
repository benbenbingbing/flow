#!/usr/bin/env bash
set -euo pipefail

for variable in MYSQL_DATABASE MYSQL_USER MYSQL_PASSWORD \
    SCHEMA_DB_USERNAME SCHEMA_DB_PASSWORD; do
    if [[ -z "${!variable:-}" ]]; then
        echo "$variable is required" >&2
        exit 1
    fi
done

if [[ ! "$MYSQL_DATABASE" =~ ^[a-zA-Z][a-zA-Z0-9_]{0,63}$ ]]; then
    echo "MYSQL_DATABASE is not a safe identifier" >&2
    exit 1
fi
if [[ ! "$MYSQL_USER" =~ ^[a-zA-Z][a-zA-Z0-9_]{0,31}$ ]]; then
    echo "MYSQL_USER is not a safe identifier" >&2
    exit 1
fi
if [[ ! "$SCHEMA_DB_USERNAME" =~ ^[a-zA-Z][a-zA-Z0-9_]{0,31}$ ]]; then
    echo "SCHEMA_DB_USERNAME is not a safe identifier" >&2
    exit 1
fi
if [[ "$MYSQL_USER" == "$SCHEMA_DB_USERNAME" ]]; then
    echo "runtime and schema database users must differ" >&2
    exit 1
fi

escape_sql_literal() {
    printf '%s' "$1" | sed "s/'/''/g"
}

runtime_user=$(escape_sql_literal "$MYSQL_USER")
runtime_password=$(escape_sql_literal "$MYSQL_PASSWORD")
schema_user=$(escape_sql_literal "$SCHEMA_DB_USERNAME")
schema_password=$(escape_sql_literal "$SCHEMA_DB_PASSWORD")

MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql --protocol=socket -uroot <<SQL
SET SESSION sql_mode = 'NO_BACKSLASH_ESCAPES';
CREATE USER IF NOT EXISTS '${runtime_user}'@'%' IDENTIFIED BY '${runtime_password}';
ALTER USER '${runtime_user}'@'%' IDENTIFIED BY '${runtime_password}';
REVOKE ALL PRIVILEGES, GRANT OPTION FROM '${runtime_user}'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON \`${MYSQL_DATABASE}\`.* TO '${runtime_user}'@'%';
CREATE USER IF NOT EXISTS '${schema_user}'@'%' IDENTIFIED BY '${schema_password}';
ALTER USER '${schema_user}'@'%' IDENTIFIED BY '${schema_password}';
GRANT ALL PRIVILEGES ON \`${MYSQL_DATABASE}\`.* TO '${schema_user}'@'%';
FLUSH PRIVILEGES;
SQL
