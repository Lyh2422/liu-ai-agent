#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

MYSQL_BASE_DIR="${MYSQL_BASE_DIR:-/usr/local/mysql}"
MYSQL_DATA_DIR="${MYSQL_DATA_DIR:-$PROJECT_DIR/tmp/mysql-data}"
MYSQL_PORT="${MYSQL_PORT:-3307}"
MYSQL_SOCKET="${MYSQL_SOCKET:-$PROJECT_DIR/tmp/mysql.sock}"
MYSQL_PID_FILE="${MYSQL_PID_FILE:-$PROJECT_DIR/tmp/mysql.pid}"
MYSQL_LOG_FILE="${MYSQL_LOG_FILE:-$PROJECT_DIR/tmp/mysql-error.log}"
MYSQL_APP_USERNAME="${MYSQL_APP_USERNAME:-liu_ai_agent}"
MYSQL_APP_PASSWORD="${MYSQL_APP_PASSWORD:-liu_ai_agent_dev}"

MYSQLD="$MYSQL_BASE_DIR/bin/mysqld"
MYSQLADMIN="$MYSQL_BASE_DIR/bin/mysqladmin"

if [[ ! -x "$MYSQLD" || ! -x "$MYSQLADMIN" ]]; then
  echo "MySQL installation not found under $MYSQL_BASE_DIR" >&2
  exit 1
fi

if [[ ! -d "$MYSQL_DATA_DIR/mysql" ]]; then
  echo "Initialized MySQL data directory not found: $MYSQL_DATA_DIR" >&2
  echo "Use compose.mysql.yml for a new environment, or set MYSQL_DATA_DIR." >&2
  exit 1
fi

if MYSQL_PWD="$MYSQL_APP_PASSWORD" "$MYSQLADMIN" \
  --host=127.0.0.1 --port="$MYSQL_PORT" \
  --user="$MYSQL_APP_USERNAME" --protocol=tcp ping --silent >/dev/null 2>&1; then
  echo "Local MySQL is already running on 127.0.0.1:$MYSQL_PORT"
  exit 0
fi

mkdir -p "$(dirname "$MYSQL_SOCKET")" "$(dirname "$MYSQL_LOG_FILE")"

"$MYSQLD" \
  --daemonize \
  --basedir="$MYSQL_BASE_DIR" \
  --datadir="$MYSQL_DATA_DIR" \
  --bind-address=127.0.0.1 \
  --port="$MYSQL_PORT" \
  --socket="$MYSQL_SOCKET" \
  --pid-file="$MYSQL_PID_FILE" \
  --log-error="$MYSQL_LOG_FILE" \
  --character-set-server=utf8mb4 \
  --collation-server=utf8mb4_0900_ai_ci

for _ in {1..30}; do
  if MYSQL_PWD="$MYSQL_APP_PASSWORD" "$MYSQLADMIN" \
    --host=127.0.0.1 --port="$MYSQL_PORT" \
    --user="$MYSQL_APP_USERNAME" --protocol=tcp ping --silent >/dev/null 2>&1; then
    echo "Local MySQL started on 127.0.0.1:$MYSQL_PORT"
    exit 0
  fi
  sleep 1
done

echo "MySQL did not become ready. Check $MYSQL_LOG_FILE" >&2
exit 1
