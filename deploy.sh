#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

DEPLOY_USER="${DEPLOY_USER:-root}"
DEPLOY_HOST="${DEPLOY_HOST:-80.66.89.226}"
DEPLOY_TARGET="${DEPLOY_TARGET:-/opt/tajwid}"
DEPLOY_COMPOSE_FILE="${DEPLOY_COMPOSE_FILE:-docker-compose.server.yml}"
DEPLOY_HEALTH_URL="${DEPLOY_HEALTH_URL:-https://bot.tartilschool.online/actuator/health}"
DEPLOY_HEALTH_RETRIES="${DEPLOY_HEALTH_RETRIES:-30}"
DEPLOY_HEALTH_DELAY_SECONDS="${DEPLOY_HEALTH_DELAY_SECONDS:-2}"
DEPLOY_SKIP_HEALTH="${DEPLOY_SKIP_HEALTH:-false}"
DEPLOY_SSH_KEY="${DEPLOY_SSH_KEY:-}"

DRY_RUN=false
if [[ "${1:-}" == "--dry-run" ]]; then
  DRY_RUN=true
fi

require_cmd() {
  local cmd="$1"
  if ! command -v "$cmd" >/dev/null 2>&1; then
    echo "Missing required command: $cmd" >&2
    exit 1
  fi
}

require_cmd rsync
require_cmd ssh
require_cmd curl

RSYNC_ARGS=(
  -az
  --delete
  --exclude .git
  --exclude .gradle
  --exclude build
  --exclude .idea
  --exclude .env
  --exclude docker-compose.server.yml
)

if [[ "$DRY_RUN" == "true" ]]; then
  RSYNC_ARGS+=(--dry-run)
fi

REMOTE="${DEPLOY_USER}@${DEPLOY_HOST}"
SSH_ARGS=(-o StrictHostKeyChecking=accept-new)
if [[ -z "$DEPLOY_SSH_KEY" && -f "$HOME/.ssh/id_ed25519_tajwid_deploy" ]]; then
  DEPLOY_SSH_KEY="$HOME/.ssh/id_ed25519_tajwid_deploy"
fi
if [[ -n "$DEPLOY_SSH_KEY" ]]; then
  SSH_ARGS+=(-i "$DEPLOY_SSH_KEY")
fi

RSYNC_SSH_CMD="ssh -o StrictHostKeyChecking=accept-new"
if [[ -n "$DEPLOY_SSH_KEY" ]]; then
  RSYNC_SSH_CMD="${RSYNC_SSH_CMD} -i ${DEPLOY_SSH_KEY}"
fi

echo "[1/4] Syncing files to ${REMOTE}:${DEPLOY_TARGET}"
rsync -e "${RSYNC_SSH_CMD}" "${RSYNC_ARGS[@]}" "${SCRIPT_DIR}/" "${REMOTE}:${DEPLOY_TARGET}/"

if [[ "$DRY_RUN" == "true" ]]; then
  echo "[DRY RUN] Sync complete. Skipping remote restart and health checks."
  exit 0
fi

echo "[2/4] Rebuilding and restarting app container on remote host"
ssh "${SSH_ARGS[@]}" "$REMOTE" "
  set -euo pipefail
  cd '${DEPLOY_TARGET}'
  docker-compose -f '${DEPLOY_COMPOSE_FILE}' up -d --build app
"

echo "[3/4] Checking local health endpoint on remote host"
ssh "${SSH_ARGS[@]}" "$REMOTE" "
  set -euo pipefail
  attempt=1
  while [ \"\$attempt\" -le '${DEPLOY_HEALTH_RETRIES}' ]; do
    if curl -fsS http://127.0.0.1:8080/actuator/health >/dev/null; then
      exit 0
    fi
    sleep '${DEPLOY_HEALTH_DELAY_SECONDS}'
    attempt=\$((attempt + 1))
  done
  echo 'Local health check failed: http://127.0.0.1:8080/actuator/health' >&2
  exit 1
"

if [[ "$DEPLOY_SKIP_HEALTH" == "true" ]]; then
  echo "[4/4] Public health check skipped (DEPLOY_SKIP_HEALTH=true)"
  exit 0
fi

echo "[4/4] Checking public health endpoint: ${DEPLOY_HEALTH_URL}"
attempt=1
while (( attempt <= DEPLOY_HEALTH_RETRIES )); do
  if curl -fsS "${DEPLOY_HEALTH_URL}" >/dev/null; then
    echo "Deployment completed successfully."
    exit 0
  fi
  sleep "${DEPLOY_HEALTH_DELAY_SECONDS}"
  ((attempt++))
done

echo "Public health check failed: ${DEPLOY_HEALTH_URL}" >&2
exit 1
