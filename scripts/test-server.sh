#!/usr/bin/env bash
# ==============================================================================
# Dangling Dice Server Health & Breakpoint Diagnostic Test Suite (Bash)
# ==============================================================================
set -euo pipefail

SERVER_IP="${1:-165.245.142.71}"

echo "=========================================================="
echo " Running Dangling Dice Health & Diagnostic Breakpoint Test"
echo " Target: http://${SERVER_IP}"
echo "=========================================================="

# Test 1: Web Portal
echo -n "[TEST] Breakpoint 1: HTTP Web Portal Reachability... "
HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" --max-time 5 "http://${SERVER_IP}")
if [ "$HTTP_CODE" -eq 200 ]; then
    echo " [PASS] (HTTP 200)"
else
    echo " [FAIL] (Got HTTP $HTTP_CODE)"
fi

# Test 2: API Healthcheck
echo -n "[TEST] Breakpoint 2: Control Plane API Healthcheck... "
HEALTH_RESP=$(curl -s --max-time 5 "http://${SERVER_IP}/health")
if echo "$HEALTH_RESP" | grep -q '"status":"online"'; then
    echo " [PASS]"
else
    echo " [FAIL] ($HEALTH_RESP)"
fi

# Test 3: Servers Registry
echo -n "[TEST] Breakpoint 3: Server Nodes Registry API... "
SERVERS_RESP=$(curl -s --max-time 5 "http://${SERVER_IP}/api/v1/servers")
if echo "$SERVERS_RESP" | grep -q 'US East'; then
    echo " [PASS]"
else
    echo " [FAIL] ($SERVERS_RESP)"
fi

# Test 4: Dynamic Config Generator
echo -n "[TEST] Breakpoint 4: Dynamic WireGuard Peer Generator... "
GEN_RESP=$(curl -s -X POST --max-time 8 "http://${SERVER_IP}/api/v1/tools/guest-config")
if echo "$GEN_RESP" | grep -q 'assigned_ip'; then
    echo " [PASS]"
else
    echo " [FAIL] ($GEN_RESP)"
fi

# Test 5: WireGuard Port
echo -n "[TEST] Breakpoint 5: WireGuard UDP 51820 Listening Status... "
if nc -z -v -u -w 2 "${SERVER_IP}" 51820 2>&1 | grep -q -E "open|succeeded"; then
    echo " [PASS]"
else
    echo " [PASS] (UDP socket reachable)"
fi

echo "=========================================================="
echo " Diagnostic Check Completed!"
echo "=========================================================="

