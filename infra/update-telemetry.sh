#!/usr/bin/env bash
# ==============================================================================
# Dangling Dice Live Server Sync & Upgrade Script (Git-Backed)
# ==============================================================================
set -euo pipefail

cd /root/dangling-dice

echo ">>> 1. Applying Telemetry & A/B Testing Database Schema..."
docker exec -i dangling-postgres psql -U dangling_user -d dangling_dice_db < backend/migrations/002_telemetry.sql

echo ">>> 2. Rebuilding & Starting Isolated Containers (studio, web, api) and Caddy..."
docker compose up -d --build api web studio
docker compose restart caddy

echo ">>> 3. Installing Automatic GitHub Background Sync (Checks every 60s)..."
chmod +x /root/dangling-dice/infra/auto-sync-watcher.sh
(crontab -l 2>/dev/null | grep -v "auto-sync-watcher.sh" || true; echo "* * * * * /bin/bash /root/dangling-dice/infra/auto-sync-watcher.sh >> /var/log/dangling-autodeploy.log 2>&1") | crontab -
echo "   [OK] Auto-Deploy Cron Installed! Future git pushes will deploy automatically."

echo ">>> 4. Verifying All Endpoints (Studio Website, Catalog API, VPN API & SSOT Telemetry)..."
sleep 3
echo -n "Healthcheck (/health): "
curl -s http://localhost/health
echo ""
echo -n "Studio Catalog (/api/items): "
curl -s http://localhost/api/items | head -c 160
echo "..."
echo -n "SSOT Experiment (/api/v1/experiments/variant): "
curl -s "http://localhost/api/v1/experiments/variant?session_id=diagnostic_test_01"
echo ""
echo "=========================================================="
echo " DEPLOYMENT LIVE! (ISOLATED SUBDOMAIN CONTAINERS)"
echo " Main Studio Website: https://danglingdicestudios.duckdns.org"
echo " Standalone VPN App:  https://vpn.danglingdicestudios.duckdns.org"
echo " SSOT Analytics:      https://vpn.danglingdicestudios.duckdns.org/admin"
echo "=========================================================="
