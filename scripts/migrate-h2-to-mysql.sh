#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
H2_DATABASE_FILE="${H2_DATABASE_FILE:-$PROJECT_DIR/tmp/data/liu-ai-agent.mv.db}"

if [[ ! -f "$H2_DATABASE_FILE" ]]; then
  echo "H2 database not found: $H2_DATABASE_FILE" >&2
  exit 1
fi

if command -v lsof >/dev/null 2>&1 && lsof "$H2_DATABASE_FILE" >/dev/null 2>&1; then
  echo "H2 database is still open. Stop the backend before migration: $H2_DATABASE_FILE" >&2
  exit 1
fi

BACKUP_FILE="$H2_DATABASE_FILE.backup-$(date -u +%Y%m%dT%H%M%SZ)"
cp -p "$H2_DATABASE_FILE" "$BACKUP_FILE"
echo "Created rollback backup: $BACKUP_FILE"

cd "$PROJECT_DIR"
./mvnw -q -DskipTests compile dependency:build-classpath \
  -Dmdep.outputFile=target/h2-to-mysql-classpath.txt

RUNTIME_CLASSPATH="target/classes:$(<target/h2-to-mysql-classpath.txt)"
java -cp "$RUNTIME_CLASSPATH" com.lyh.liuaiagent.migration.H2ToMySqlMigrator
