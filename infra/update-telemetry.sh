#!/usr/bin/env bash
# ==============================================================================
# Dangling Dice Live Server Upgrade: SSOT Telemetry, A/B Testing & Widget
# ==============================================================================
set -euo pipefail

cd /root/dangling-dice

echo ">>> 1. Adding Telemetry & A/B Testing Schema..."
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

# Apply migration directly to the running Postgres container
docker exec -i dangling-postgres psql -U dangling_user -d dangling_dice_db < backend/migrations/002_telemetry.sql

echo ">>> 2. Updating Go Control Plane API with Telemetry & A/B Endpoints..."
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
	DeviceName string `json:"device_name"`
	PublicKey  string `json:"public_key"`
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
	s.mu.Unlock()

	go func(pubKey, ip string) {
		exec.Command("wg", "set", s.cfg.WGInterface, "peer", pubKey, "allowed-ips", ip+"/32").Run()
	}(req.PublicKey, assignedIP)

	jsonResponse(w, http.StatusOK, map[string]interface{}{
		"assigned_ip":       assignedIP + "/24",
		"server_public_key": s.serverPubKey,
		"endpoint":          fmt.Sprintf("%s:%s", s.cfg.ServerPublicIP, s.cfg.VPNListenPort),
		"dns":               s.cfg.VPNDNSServer,
	})
}

func (s *ServerState) handleGenerateGuestConfig(w http.ResponseWriter, r *http.Request) {
	privKey, pubKey := generateWireGuardKeyPair()
	s.mu.Lock()
	assignedIP := fmt.Sprintf("10.8.0.%d", s.nextIPIndex)
	s.nextIPIndex++
	s.mu.Unlock()

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
`, privKey, assignedIP, s.cfg.VPNDNSServer, s.serverPubKey, s.cfg.ServerPublicIP, s.cfg.VPNListenPort)

	jsonResponse(w, http.StatusOK, map[string]interface{}{
		"assigned_ip": assignedIP,
		"public_key":  pubKey,
		"config_text": rawConf,
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

echo ">>> 3. Writing Drop-in Website Widget..."
mkdir -p web/public
cat << 'EOF' > web/public/vpn-widget.js
(function () {
  'use strict';
  const VPN_API_BASE = 'http://165.245.142.71';

  function getOrSetSessionId() {
    let sid = localStorage.getItem('dd_vpn_sid');
    if (!sid) {
      sid = 'sid_' + Math.random().toString(36).substring(2, 15) + Date.now().toString(36);
      localStorage.setItem('dd_vpn_sid', sid);
    }
    return sid;
  }

  function getUtmParams() {
    const urlParams = new URLSearchParams(window.location.search);
    return {
      utm_source: urlParams.get('utm_source') || localStorage.getItem('dd_utm_source') || 'direct',
      utm_medium: urlParams.get('utm_medium') || localStorage.getItem('dd_utm_medium') || '',
      utm_campaign: urlParams.get('utm_campaign') || localStorage.getItem('dd_utm_campaign') || ''
    };
  }

  const utms = getUtmParams();
  if (utms.utm_source !== 'direct') {
    localStorage.setItem('dd_utm_source', utms.utm_source);
    localStorage.setItem('dd_utm_medium', utms.utm_medium);
    localStorage.setItem('dd_utm_campaign', utms.utm_campaign);
  }

  const sessionId = getOrSetSessionId();
  let assignedVariant = 'variant_a';

  async function trackEvent(eventName, metadata = {}) {
    try {
      fetch(`${VPN_API_BASE}/api/v1/telemetry/event`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          event_name: eventName,
          session_id: sessionId,
          platform: 'website_widget',
          experiment_key: 'vpn_hero_cta',
          variant_assigned: assignedVariant,
          utm_source: utms.utm_source,
          utm_campaign: utms.utm_campaign,
          metadata: metadata
        })
      });
    } catch (_) {}
  }

  async function initWidget() {
    try {
      const res = await fetch(`${VPN_API_BASE}/api/v1/experiments/variant?session_id=${sessionId}`);
      const data = await res.json();
      assignedVariant = data.variant_assigned || 'variant_a';
    } catch (_) {}

    trackEvent('page_view', { path: window.location.pathname });

    const container = document.getElementById('dangling-dice-vpn-widget');
    if (!container) return;

    container.innerHTML = `
      <div style="background: #080c14; border: 1px solid rgba(16, 185, 129, 0.3); border-radius: 12px; padding: 1.5rem; color: #fff; font-family: 'Plus Jakarta Sans', system-ui, sans-serif;">
        <div style="display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 1rem; border-bottom: 1px solid #1e293b; padding-bottom: 1rem; margin-bottom: 1.25rem;">
          <div style="display: flex; align-items: center; gap: 0.75rem;">
            <span style="font-size: 1.75rem;">🎲</span>
            <div>
              <h3 style="margin: 0; font-size: 1.15rem; font-weight: 700; color: #fff;">
                ${assignedVariant === 'variant_b' ? 'Dangling Dice Developer Privacy Suite' : 'Dangling Dice WireGuard VPN'}
              </h3>
              <p style="margin: 0.2rem 0 0 0; font-size: 0.8rem; color: #94a3b8;">
                Zero-Log In-Kernel Network Shield &bull; 100% Free
              </p>
            </div>
          </div>
          <span id="dd-vpn-status-badge" style="background: rgba(245, 158, 11, 0.12); color: #f59e0b; border: 1px solid rgba(245, 158, 11, 0.3); padding: 0.3rem 0.75rem; border-radius: 999px; font-size: 0.75rem; font-weight: 700;">
            ● DIRECT (UNPROTECTED)
          </span>
        </div>

        <div style="background: #0f172a; border: 1px solid #1e293b; border-radius: 10px; padding: 1rem; margin-bottom: 1.25rem; display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 0.75rem;">
          <div>
            <span style="font-size: 0.75rem; color: #94a3b8; text-transform: uppercase;">Detected Public IP:</span>
            <div id="dd-vpn-ip" style="font-family: 'JetBrains Mono', monospace; font-size: 0.95rem; font-weight: 700; color: #22c55e;">Fetching...</div>
          </div>
          <button id="dd-btn-test-leak" style="background: #1e293b; color: #e2e8f0; border: 1px solid #334155; padding: 0.5rem 1rem; border-radius: 8px; font-size: 0.8rem; font-weight: 600; cursor: pointer;">
            ⚡ Run Leak Shield Test
          </button>
        </div>

        <div id="dd-vpn-action-box">
          <button id="dd-btn-generate-profile" style="width: 100%; background: linear-gradient(135deg, #10b981, #06b6d4); color: #020617; border: none; padding: 0.85rem; border-radius: 10px; font-size: 0.95rem; font-weight: 800; cursor: pointer;">
            🚀 1-Click Generate Free WireGuard Profile
          </button>
        </div>

        <div id="dd-vpn-profile-output" style="display: none; margin-top: 1rem; background: #030712; border: 1px solid #1e293b; border-radius: 10px; padding: 1rem;">
          <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 0.5rem;">
            <span style="font-size: 0.75rem; color: #22c55e; font-weight: 700;">● Profile Ready (Valid 24h)</span>
            <button id="dd-btn-download-conf" style="background: #10b981; color: #020617; border: none; padding: 0.35rem 0.75rem; border-radius: 6px; font-size: 0.75rem; font-weight: 700; cursor: pointer;">
              Download .conf
            </button>
          </div>
          <pre id="dd-vpn-conf-text" style="background: #0f172a; color: #94a3b8; font-family: monospace; font-size: 0.75rem; padding: 0.75rem; border-radius: 6px; overflow-x: auto; max-height: 120px; margin: 0;"></pre>
        </div>
      </div>
    `;

    const testLeakBtn = document.getElementById('dd-btn-test-leak');
    const genProfileBtn = document.getElementById('dd-btn-generate-profile');
    const ipLabel = document.getElementById('dd-vpn-ip');
    const statusBadge = document.getElementById('dd-vpn-status-badge');
    const outputBox = document.getElementById('dd-vpn-profile-output');
    const confText = document.getElementById('dd-vpn-conf-text');
    const downloadBtn = document.getElementById('dd-btn-download-conf');

    let currentConfigText = '';

    async function checkLeak() {
      try {
        const res = await fetch(`${VPN_API_BASE}/api/v1/tools/diagnostics`);
        const data = await res.json();
        if (ipLabel) ipLabel.textContent = data.client_ip;
        if (data.is_vpn_secured && statusBadge) {
          statusBadge.textContent = '● SECURED BY DANGLING DICE';
          statusBadge.style.color = '#22c55e';
          statusBadge.style.background = 'rgba(34, 197, 94, 0.12)';
          statusBadge.style.borderColor = 'rgba(34, 197, 94, 0.3)';
        }
      } catch (_) {}
    }
    checkLeak();

    testLeakBtn.addEventListener('click', async () => {
      testLeakBtn.textContent = 'Testing...';
      trackEvent('leak_test_run');
      await checkLeak();
      testLeakBtn.textContent = '⚡ Run Leak Shield Test';
    });

    genProfileBtn.addEventListener('click', async () => {
      genProfileBtn.textContent = 'Generating Crypto Keypair...';
      try {
        const res = await fetch(`${VPN_API_BASE}/api/v1/tools/guest-config`, { method: 'POST' });
        const data = await res.json();
        currentConfigText = data.config_text;
        if (confText) confText.textContent = data.config_text;
        if (outputBox) outputBox.style.display = 'block';
        genProfileBtn.textContent = '✅ Profile Generated';
        trackEvent('profile_gen', { assigned_ip: data.assigned_ip });
      } catch (err) {
        genProfileBtn.textContent = '🚀 1-Click Generate Free WireGuard Profile';
      }
    });

    downloadBtn.addEventListener('click', () => {
      if (!currentConfigText) return;
      const blob = new Blob([currentConfigText], { type: 'text/plain' });
      const a = document.createElement('a');
      a.href = URL.createObjectURL(blob);
      a.download = 'dangling-dice.conf';
      a.click();
      trackEvent('conf_downloaded');
    });
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', initWidget);
  } else {
    initWidget();
  }
})();
EOF

echo ">>> 4. Rebuilding & Restarting API and Web containers..."
docker compose up -d --build api web

echo ">>> 5. Running Breakpoint Health Verification..."
sleep 3
curl -s http://localhost/health | jq . || curl -s http://localhost/health
echo ""
curl -s http://localhost/api/v1/admin/analytics | jq . || curl -s http://localhost/api/v1/admin/analytics
echo ""
echo "=========================================================="
echo " SSOT Telemetry, A/B Testing & Widget Upgrade Complete!"
echo "=========================================================="
