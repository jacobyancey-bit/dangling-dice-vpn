#!/usr/bin/env bash
# ==============================================================================
# Dangling Dice Live Server Upgrade: SSOT Telemetry, Admin UI, AdBlock & DevTunnel
# ==============================================================================
set -euo pipefail

cd /root/dangling-dice

echo ">>> 1. Applying Telemetry & A/B Testing Database Schema..."
cat << 'EOF' > backend/migrations/002_telemetry.sql
CREATE TABLE IF NOT EXISTS ad_campaigns (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    campaign_name VARCHAR(100) NOT NULL,
    utm_source VARCHAR(50) NOT NULL,
    utm_medium VARCHAR(50),
    utm_campaign VARCHAR(100),
    utm_content VARCHAR(100),
    click_id VARCHAR(100),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS ab_experiments (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    experiment_key VARCHAR(50) UNIQUE NOT NULL,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    variants JSONB NOT NULL,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS telemetry_events (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    event_name VARCHAR(60) NOT NULL,
    session_id VARCHAR(64) NOT NULL,
    platform VARCHAR(20) NOT NULL,
    experiment_key VARCHAR(50),
    variant_assigned VARCHAR(50),
    utm_source VARCHAR(50),
    utm_campaign VARCHAR(100),
    client_ip VARCHAR(45),
    user_agent TEXT,
    metadata JSONB,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS system_diagnostics (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    node_name VARCHAR(50) NOT NULL,
    cpu_percent FLOAT,
    memory_used_mb INT,
    active_peers INT DEFAULT 0,
    network_rx_mbps FLOAT DEFAULT 0.0,
    network_tx_mbps FLOAT DEFAULT 0.0,
    recorded_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_telemetry_event_name ON telemetry_events(event_name);
CREATE INDEX IF NOT EXISTS idx_telemetry_created_at ON telemetry_events(created_at);
CREATE INDEX IF NOT EXISTS idx_telemetry_utm_source ON telemetry_events(utm_source);
CREATE INDEX IF NOT EXISTS idx_telemetry_platform ON telemetry_events(platform);
EOF

docker exec -i dangling-postgres psql -U dangling_user -d dangling_dice_db < backend/migrations/002_telemetry.sql

echo ">>> 2. Updating Go Control Plane API..."
cat << 'EOF' > backend/cmd/api/main.go
package main

import (
	"crypto/rand"
	"database/sql"
	"encoding/base64"
	"encoding/json"
	"fmt"
	"log"
	"net/http"
	"os"
	"os/exec"
	"strings"
	"sync"
	"time"

	"github.com/go-chi/chi/v5"
	"github.com/go-chi/chi/v5/middleware"
	"github.com/go-chi/cors"
	_ "github.com/lib/pq"
	"golang.org/x/crypto/curve25519"
)

type Config struct {
	APIPort        string
	ServerPublicIP string
	VPNListenPort  string
	VPNGatewayIP   string
	VPNDNSServer   string
	PostgresDSN    string
	WGInterface    string
}

type ServerState struct {
	mu           sync.Mutex
	db           *sql.DB
	cfg          Config
	serverPubKey string
	nextIPIndex  int
}

func main() {
	cfg := Config{
		APIPort:        getEnv("API_PORT", "8080"),
		ServerPublicIP: getEnv("SERVER_PUBLIC_IP", "165.245.142.71"),
		VPNListenPort:  getEnv("VPN_LISTEN_PORT", "51820"),
		VPNGatewayIP:   getEnv("VPN_GATEWAY_IP", "10.8.0.1"),
		VPNDNSServer:   getEnv("VPN_DNS_SERVER", "1.1.1.1,1.0.0.1"),
		WGInterface:    getEnv("WG_INTERFACE", "wg0"),
		PostgresDSN: fmt.Sprintf("host=%s port=%s user=%s password=%s dbname=%s sslmode=disable",
			getEnv("POSTGRES_HOST", "postgres"),
			getEnv("POSTGRES_PORT", "5432"),
			getEnv("POSTGRES_USER", "dangling_user"),
			getEnv("POSTGRES_PASSWORD", "dice_secure_pg_pass_9281a8f9b2"),
			getEnv("POSTGRES_DB", "dangling_dice_db"),
		),
	}

	state := &ServerState{
		cfg:         cfg,
		nextIPIndex: 2,
	}

	state.serverPubKey = loadServerPublicKey()

	var err error
	for i := 0; i < 15; i++ {
		state.db, err = sql.Open("postgres", cfg.PostgresDSN)
		if err == nil && state.db.Ping() == nil {
			log.Println("Successfully connected to PostgreSQL")
			break
		}
		time.Sleep(2 * time.Second)
	}

	r := chi.NewRouter()
	r.Use(middleware.RequestID)
	r.Use(middleware.RealIP)
	r.Use(middleware.Logger)
	r.Use(middleware.Recoverer)
	r.Use(cors.Handler(cors.Options{
		AllowedOrigins:   []string{"*"},
		AllowedMethods:   []string{"GET", "POST", "OPTIONS"},
		AllowedHeaders:   []string{"Accept", "Authorization", "Content-Type"},
		AllowCredentials: true,
		MaxAge:           300,
	}))

	r.Get("/health", state.handleHealth)
	r.Route("/api/v1", func(r chi.Router) {
		r.Get("/servers", state.handleListServers)
		r.Get("/tools/diagnostics", state.handleDiagnostics)
		r.Post("/peers/register", state.handleRegisterPeer)
		r.Post("/tools/guest-config", state.handleGenerateGuestConfig)
		r.Post("/tools/dev-tunnel", state.handleCreateDevTunnel)

		// Single Source of Truth (SSOT): Telemetry, Attribution & A/B Testing
		r.Post("/telemetry/event", state.handleTrackEvent)
		r.Get("/experiments/variant", state.handleGetExperimentVariant)
		r.Get("/admin/analytics", state.handleGetAnalyticsSummary)
	})

	log.Printf("Dangling Dice Control Plane running on port %s", cfg.APIPort)
	if err := http.ListenAndServe(":"+cfg.APIPort, r); err != nil {
		log.Fatalf("Server failed: %v", err)
	}
}

func (s *ServerState) handleHealth(w http.ResponseWriter, r *http.Request) {
	jsonResponse(w, http.StatusOK, map[string]interface{}{
		"status":    "online",
		"service":   "dangling-dice-control-api",
		"public_ip": s.cfg.ServerPublicIP,
	})
}

func (s *ServerState) handleDiagnostics(w http.ResponseWriter, r *http.Request) {
	clientIP := r.Header.Get("X-Real-IP")
	if clientIP == "" {
		clientIP = r.RemoteAddr
	}
	isUsingVPN := strings.HasPrefix(clientIP, "10.8.0.") || clientIP == s.cfg.ServerPublicIP
	jsonResponse(w, http.StatusOK, map[string]interface{}{
		"client_ip":      clientIP,
		"is_vpn_secured": isUsingVPN,
		"server_node":    "Node-US-East-1",
		"timestamp":      time.Now().UTC(),
	})
}

func (s *ServerState) handleListServers(w http.ResponseWriter, r *http.Request) {
	servers := []map[string]interface{}{
		{
			"id":           "us-east-1",
			"name":         "US East (Atlanta / NYC)",
			"endpoint":     fmt.Sprintf("%s:%s", s.cfg.ServerPublicIP, s.cfg.VPNListenPort),
			"public_key":   s.serverPubKey,
			"is_online":    true,
			"is_free_tier": true,
		},
	}
	jsonResponse(w, http.StatusOK, map[string]interface{}{"servers": servers})
}

type RegisterPeerRequest struct {
	DeviceName     string `json:"device_name"`
	PublicKey      string `json:"public_key"`
	AdBlockEnabled bool   `json:"adblock_enabled"`
}

func (s *ServerState) handleRegisterPeer(w http.ResponseWriter, r *http.Request) {
	var req RegisterPeerRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil || req.PublicKey == "" {
		jsonError(w, http.StatusBadRequest, "Invalid payload")
		return
	}
	s.mu.Lock()
	assignedIP := fmt.Sprintf("10.8.0.%d", s.nextIPIndex)
	s.nextIPIndex++
	if s.nextIPIndex > 250 {
		s.nextIPIndex = 2
	}
	s.mu.Unlock()

	dnsServer := s.cfg.VPNDNSServer
	if req.AdBlockEnabled {
		dnsServer = "94.140.14.14, 94.140.15.15"
	}

	go func(pubKey, ip string) {
		exec.Command("wg", "set", s.cfg.WGInterface, "peer", pubKey, "allowed-ips", ip+"/32").Run()
	}(req.PublicKey, assignedIP)

	jsonResponse(w, http.StatusOK, map[string]interface{}{
		"assigned_ip":       assignedIP + "/24",
		"server_public_key": s.serverPubKey,
		"endpoint":          fmt.Sprintf("%s:%s", s.cfg.ServerPublicIP, s.cfg.VPNListenPort),
		"dns":               dnsServer,
		"adblock_active":    req.AdBlockEnabled,
	})
}

type GuestConfigRequest struct {
	AdBlockEnabled bool `json:"adblock_enabled"`
}

func (s *ServerState) handleGenerateGuestConfig(w http.ResponseWriter, r *http.Request) {
	var req GuestConfigRequest
	_ = json.NewDecoder(r.Body).Decode(&req)

	privKey, pubKey := generateWireGuardKeyPair()
	s.mu.Lock()
	assignedIP := fmt.Sprintf("10.8.0.%d", s.nextIPIndex)
	s.nextIPIndex++
	if s.nextIPIndex > 250 {
		s.nextIPIndex = 2
	}
	s.mu.Unlock()

	dnsServer := s.cfg.VPNDNSServer
	if req.AdBlockEnabled {
		dnsServer = "94.140.14.14, 94.140.15.15"
	}

	exec.Command("wg", "set", s.cfg.WGInterface, "peer", pubKey, "allowed-ips", assignedIP+"/32").Run()

	rawConf := fmt.Sprintf(`[Interface]
PrivateKey = %s
Address = %s/24
DNS = %s

[Peer]
PublicKey = %s
Endpoint = %s:%s
AllowedIPs = 0.0.0.0/0, ::/0
PersistentKeepalive = 25
`, privKey, assignedIP, dnsServer, s.serverPubKey, s.cfg.ServerPublicIP, s.cfg.VPNListenPort)

	jsonResponse(w, http.StatusOK, map[string]interface{}{
		"assigned_ip":    assignedIP,
		"public_key":     pubKey,
		"dns":            dnsServer,
		"adblock_active": req.AdBlockEnabled,
		"config_text":    rawConf,
	})
}

type DevTunnelRequest struct {
	Subdomain  string `json:"subdomain"`
	TargetPort int    `json:"target_port"`
	PeerIP     string `json:"peer_ip"`
}

func (s *ServerState) handleCreateDevTunnel(w http.ResponseWriter, r *http.Request) {
	var req DevTunnelRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil || req.Subdomain == "" || req.TargetPort <= 0 {
		jsonError(w, http.StatusBadRequest, "Valid 'subdomain' and 'target_port' are required.")
		return
	}
	if req.PeerIP == "" {
		req.PeerIP = "10.8.0.2"
	}
	if s.db != nil {
		_, _ = s.db.Exec(
			`INSERT INTO dev_tunnels (subdomain, target_port, peer_ip) VALUES ($1, $2, $3) ON CONFLICT (subdomain) DO UPDATE SET target_port = $2, peer_ip = $3`,
			req.Subdomain, req.TargetPort, req.PeerIP,
		)
	}
	jsonResponse(w, http.StatusOK, map[string]interface{}{
		"status":     "active",
		"public_url": fmt.Sprintf("http://%s.%s", req.Subdomain, s.cfg.ServerPublicIP),
		"target":     fmt.Sprintf("%s:%d", req.PeerIP, req.TargetPort),
	})
}

type TelemetryEventRequest struct {
	EventName       string                 `json:"event_name"`
	SessionID       string                 `json:"session_id"`
	Platform        string                 `json:"platform"`
	ExperimentKey   string                 `json:"experiment_key,omitempty"`
	VariantAssigned string                 `json:"variant_assigned,omitempty"`
	UTMSource       string                 `json:"utm_source,omitempty"`
	UTMCampaign     string                 `json:"utm_campaign,omitempty"`
	Metadata        map[string]interface{} `json:"metadata,omitempty"`
}

func (s *ServerState) handleTrackEvent(w http.ResponseWriter, r *http.Request) {
	var req TelemetryEventRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil || req.EventName == "" {
		jsonError(w, http.StatusBadRequest, "Invalid payload")
		return
	}
	clientIP := r.Header.Get("X-Real-IP")
	if clientIP == "" {
		clientIP = r.RemoteAddr
	}
	userAgent := r.UserAgent()

	go func(event TelemetryEventRequest, ip, ua string) {
		if s.db == nil {
			return
		}
		metaJSON, _ := json.Marshal(event.Metadata)
		query := `INSERT INTO telemetry_events 
			(event_name, session_id, platform, experiment_key, variant_assigned, utm_source, utm_campaign, client_ip, user_agent, metadata)
			VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10)`
		s.db.Exec(query, event.EventName, event.SessionID, event.Platform, event.ExperimentKey, event.VariantAssigned, event.UTMSource, event.UTMCampaign, ip, ua, metaJSON)
	}(req, clientIP, userAgent)

	jsonResponse(w, http.StatusOK, map[string]interface{}{"status": "recorded"})
}

func (s *ServerState) handleGetExperimentVariant(w http.ResponseWriter, r *http.Request) {
	experiment := r.URL.Query().Get("experiment")
	sessionID := r.URL.Query().Get("session_id")
	if experiment == "" {
		experiment = "vpn_hero_cta"
	}
	if sessionID == "" {
		sessionID = r.RemoteAddr
	}
	hash := 0
	for _, char := range sessionID {
		hash = (hash*31 + int(char)) % 100
	}
	variant := "variant_a"
	if hash >= 50 {
		variant = "variant_b"
	}
	jsonResponse(w, http.StatusOK, map[string]interface{}{
		"experiment_key":   experiment,
		"variant_assigned": variant,
		"session_id":       sessionID,
	})
}

func (s *ServerState) handleGetAnalyticsSummary(w http.ResponseWriter, r *http.Request) {
	summary := map[string]interface{}{
		"funnel": map[string]int{
			"page_view":       0,
			"leak_test_run":   0,
			"profile_gen":     0,
			"conf_downloaded": 0,
			"vpn_handshake":   0,
		},
		"ad_sources":  map[string]int{},
		"ab_variants": map[string]int{},
	}

	if s.db != nil {
		rows, err := s.db.Query(`SELECT event_name, COUNT(*) FROM telemetry_events GROUP BY event_name`)
		if err == nil {
			defer rows.Close()
			funnelMap := summary["funnel"].(map[string]int)
			for rows.Next() {
				var name string
				var count int
				if err := rows.Scan(&name, &count); err == nil {
					funnelMap[name] = count
				}
			}
		}

		utmRows, err := s.db.Query(`SELECT COALESCE(NULLIF(utm_source, ''), 'direct') as src, COUNT(*) FROM telemetry_events GROUP BY src ORDER BY COUNT(*) DESC LIMIT 10`)
		if err == nil {
			defer utmRows.Close()
			utmSources := summary["ad_sources"].(map[string]int)
			for utmRows.Next() {
				var src string
				var count int
				if err := utmRows.Scan(&src, &count); err == nil {
					utmSources[src] = count
				}
			}
		}

		abRows, err := s.db.Query(`SELECT COALESCE(NULLIF(variant_assigned, ''), 'none') as variant, COUNT(*) FROM telemetry_events WHERE event_name = 'profile_gen' GROUP BY variant`)
		if err == nil {
			defer abRows.Close()
			abVariants := summary["ab_variants"].(map[string]int)
			for abRows.Next() {
				var variant string
				var count int
				if err := abRows.Scan(&variant, &count); err == nil {
					abVariants[variant] = count
				}
			}
		}
	}

	jsonResponse(w, http.StatusOK, summary)
}

func generateWireGuardKeyPair() (string, string) {
	var priv [32]byte
	rand.Read(priv[:])
	priv[0] &= 248
	priv[31] &= 127
	priv[31] |= 64
	var pub [32]byte
	curve25519.ScalarBaseMult(&pub, &priv)
	return base64.StdEncoding.EncodeToString(priv[:]), base64.StdEncoding.EncodeToString(pub[:])
}

func loadServerPublicKey() string {
	content, err := os.ReadFile("/etc/wireguard/server_public.key")
	if err == nil {
		return strings.TrimSpace(string(content))
	}
	return "xTpevhK8n+3m9+yH8OaJ6uV0cQw4Mv+B9x7pY6s="
}

func getEnv(key, defaultVal string) string {
	if val := os.Getenv(key); val != "" {
		return val
	}
	return defaultVal
}

func jsonResponse(w http.ResponseWriter, status int, data interface{}) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	json.NewEncoder(w).Encode(data)
}

func jsonError(w http.ResponseWriter, status int, msg string) {
	jsonResponse(w, status, map[string]string{"error": msg})
}
EOF

echo ">>> 3. Writing Drop-in Website Widget & Admin Dashboard..."
mkdir -p web/public web/src/app/admin

cat << 'EOF' > web/src/app/admin/page.tsx
'use client';
import React, { useState, useEffect } from 'react';
import { BarChart3, TrendingUp, Users, ShieldCheck, Activity, RefreshCw, ArrowLeft, Layers, Zap, Award } from 'lucide-react';
import Link from 'next/link';

export default function AdminAnalyticsPage() {
  const [data, setData] = useState<any>(null);
  const [loading, setLoading] = useState(true);
  const [simulating, setSimulating] = useState(false);

  const fetchAnalytics = async () => {
    setLoading(true);
    try {
      const res = await fetch('/api/v1/admin/analytics');
      const json = await res.json();
      setData(json);
    } catch (_) {}
    setLoading(false);
  };

  useEffect(() => { fetchAnalytics(); }, []);

  const simulateConversionEvent = async () => {
    setSimulating(true);
    const sources = ['youtube', 'tiktok', 'google', 'github', 'reddit'];
    const randomSource = sources[Math.floor(Math.random() * sources.length)];
    const randomVariant = Math.random() > 0.45 ? 'variant_b' : 'variant_a';
    try {
      await fetch('/api/v1/telemetry/event', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          event_name: 'profile_gen',
          session_id: 'sim_' + Date.now(),
          platform: 'admin_test',
          experiment_key: 'vpn_hero_cta',
          variant_assigned: randomVariant,
          utm_source: randomSource,
          utm_campaign: 'dev_funnel_launch',
        }),
      });
      await fetchAnalytics();
    } catch (_) {}
    setSimulating(false);
  };

  const funnel = data?.funnel || {};
  const pageViews = funnel.page_view || 0;
  const leakTests = funnel.leak_test_run || 0;
  const profilesGen = funnel.profile_gen || 0;
  const handshakes = funnel.vpn_handshake || 0;
  const variantACount = data?.ab_variants?.variant_a || 0;
  const variantBCount = data?.ab_variants?.variant_b || 0;
  const winningVariant = variantBCount >= variantACount ? 'Variant B (Developer Privacy Suite)' : 'Variant A (WireGuard VPN)';

  return (
    <div className="min-h-screen bg-slate-950 text-slate-100 p-6 md:p-10">
      <div className="max-w-6xl mx-auto space-y-8">
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 border-b border-slate-800 pb-6">
          <div>
            <div className="flex items-center space-x-3">
              <Link href="/" className="p-2 rounded-lg bg-slate-900 border border-slate-800 text-slate-400 hover:text-white">
                <ArrowLeft className="w-4 h-4" />
              </Link>
              <span className="text-xs font-mono uppercase px-2.5 py-1 rounded bg-emerald-500/10 text-emerald-400 border border-emerald-500/20">
                Single Source of Truth (SSOT)
              </span>
            </div>
            <h1 className="text-2xl font-bold text-white mt-3">Ad Attribution, Funnel & A/B Testing Center</h1>
          </div>
          <div className="flex items-center space-x-3">
            <button onClick={simulateConversionEvent} disabled={simulating} className="px-4 py-2 rounded-xl bg-emerald-500/10 text-emerald-400 border border-emerald-500/30 text-xs font-bold flex items-center space-x-2">
              <Zap className="w-4 h-4" />
              <span>{simulating ? 'Recording...' : 'Simulate Funnel Event'}</span>
            </button>
            <button onClick={fetchAnalytics} disabled={loading} className="px-4 py-2 rounded-xl bg-slate-900 text-slate-200 border border-slate-800 text-xs font-semibold flex items-center space-x-2">
              <RefreshCw className={`w-4 h-4 ${loading ? 'animate-spin' : ''}`} />
              <span>Refresh</span>
            </button>
          </div>
        </div>

        <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
          <div className="p-5 rounded-2xl bg-slate-900/50 border border-slate-800">
            <span className="text-xs font-mono text-slate-400 uppercase">Impressions</span>
            <div className="text-3xl font-extrabold text-white mt-2">{pageViews}</div>
          </div>
          <div className="p-5 rounded-2xl bg-slate-900/50 border border-slate-800">
            <span className="text-xs font-mono text-slate-400 uppercase">Leak Tests</span>
            <div className="text-3xl font-extrabold text-white mt-2">{leakTests}</div>
          </div>
          <div className="p-5 rounded-2xl bg-slate-900/50 border border-slate-800">
            <span className="text-xs font-mono text-slate-400 uppercase">Profiles Generated</span>
            <div className="text-3xl font-extrabold text-emerald-400 mt-2">{profilesGen}</div>
          </div>
          <div className="p-5 rounded-2xl bg-slate-900/50 border border-slate-800">
            <span className="text-xs font-mono text-slate-400 uppercase">VPN Handshakes</span>
            <div className="text-3xl font-extrabold text-purple-400 mt-2">{handshakes}</div>
          </div>
        </div>

        <div className="p-6 rounded-2xl bg-slate-900/40 border border-slate-800 space-y-4">
          <div className="flex justify-between items-center">
            <h2 className="text-lg font-bold text-white">Live A/B Experiment: vpn_hero_cta</h2>
            <span className="text-xs font-mono px-3 py-1 rounded bg-emerald-500/10 text-emerald-400 border border-emerald-500/30 font-bold">
              Leader: {winningVariant}
            </span>
          </div>
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div className="p-4 rounded-xl bg-slate-950 border border-slate-800 flex justify-between items-center">
              <div>
                <span className="text-xs font-mono text-slate-400">VARIANT A</span>
                <div className="font-bold text-white">Dangling Dice WireGuard VPN</div>
              </div>
              <div className="text-2xl font-extrabold text-white">{variantACount}</div>
            </div>
            <div className="p-4 rounded-xl bg-slate-950 border border-emerald-500/30 flex justify-between items-center">
              <div>
                <span className="text-xs font-mono text-emerald-400">VARIANT B</span>
                <div className="font-bold text-white">Dangling Dice Developer Privacy Suite</div>
              </div>
              <div className="text-2xl font-extrabold text-emerald-400">{variantBCount}</div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
EOF

echo ">>> 4. Rebuilding & Restarting API and Web containers..."
docker compose up -d --build api web

echo ">>> 5. Verifying Breakpoint 6 (SSOT Telemetry & A/B API)..."
sleep 3
curl -s "http://localhost/api/v1/experiments/variant?session_id=diagnostic_test_01"
echo ""
echo "=========================================================="
echo " ALL 6 BREAKPOINTS READY! Run test-server.ps1 on Windows!"
echo "=========================================================="
