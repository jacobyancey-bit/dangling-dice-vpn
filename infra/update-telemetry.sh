#!/usr/bin/env bash
# ==============================================================================
# Dangling Dice Live Server Sync & Upgrade Script (Git-Backed)
# ==============================================================================
set -euo pipefail

cd /root/dangling-dice

echo ">>> 1. Applying Telemetry & A/B Testing Database Schema..."
docker exec -i dangling-postgres psql -U dangling_user -d dangling_dice_db < backend/migrations/002_telemetry.sql

echo ">>> 2. Rebuilding & Restarting API, Web (Studio + VPN), and Caddy..."
docker compose up -d --build api web
docker compose restart caddy

echo ">>> 3. Verifying All Endpoints (Studio Website, Catalog API, VPN API & SSOT Telemetry)..."
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
echo " DEPLOYMENT LIVE!"
echo " Main Studio Website: https://danglingdicestudios.duckdns.org"
echo " Standalone VPN App:  https://danglingdicestudios.duckdns.org/vpn"
echo " SSOT Analytics:      https://danglingdicestudios.duckdns.org/admin"
echo "=========================================================="
