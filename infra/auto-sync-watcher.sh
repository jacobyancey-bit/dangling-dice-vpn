#!/usr/bin/env bash
# ==============================================================================
# Dangling Dice Automated GitOps Watcher (Runs every 60s via systemd/cron)
# Automatically pulls from GitHub main and rebuilds containers ONLY when changed
# ==============================================================================
set -euo pipefail

cd /root/dangling-dice

git fetch origin main --quiet
LOCAL_SHA=$(git rev-parse HEAD)
REMOTE_SHA=$(git rev-parse origin/main)

if [ "$LOCAL_SHA" != "$REMOTE_SHA" ]; then
    echo "[$(date -u)] New commit detected on GitHub ($LOCAL_SHA -> $REMOTE_SHA). Auto-deploying..."
    CHANGED_FILES=$(git diff --name-only "$LOCAL_SHA" "$REMOTE_SHA")
    git reset --hard origin/main

    # Only rebuild Docker containers if server/web files changed (ignore Android-only commits)
    if echo "$CHANGED_FILES" | grep -qE "^(backend/|web/|studio/|Caddyfile|docker-compose\.yml)"; then
        docker exec -i dangling-postgres psql -U dangling_user -d dangling_dice_db < backend/migrations/002_telemetry.sql || true
        docker compose up -d --build api web studio
        docker compose restart caddy
        echo "[$(date -u)] Server containers rebuilt and restarted."
    else
        echo "[$(date -u)] Only Android/client files changed; skipping Docker rebuild."
    fi
fi
