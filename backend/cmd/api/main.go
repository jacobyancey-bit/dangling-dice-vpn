package main

import (
	"crypto/rand"
	"crypto/sha256"
	"database/sql"
	"encoding/base64"
	"encoding/hex"
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
	peerIPMap    map[string]string      // Persistent Curve25519 PublicKey -> Assigned 10.8.0.x IP
	rateLimitMap map[string][]time.Time // Per-IP sliding window rate limiter for anti-bot protection
}

func main() {
	cfg := Config{
		APIPort:        getEnv("API_PORT", "8080"),
		ServerPublicIP: getEnv("SERVER_PUBLIC_IP", "127.0.0.1"),
		VPNListenPort:  getEnv("VPN_LISTEN_PORT", "51820"),
		VPNGatewayIP:   getEnv("VPN_GATEWAY_IP", "10.8.0.1"),
		VPNDNSServer:   getEnv("VPN_DNS_SERVER", "1.1.1.1,1.0.0.1"),
		WGInterface:    getEnv("WG_INTERFACE", "wg0"),
		PostgresDSN: fmt.Sprintf("host=%s port=%s user=%s password=%s dbname=%s sslmode=disable",
			getEnv("POSTGRES_HOST", "postgres"),
			getEnv("POSTGRES_PORT", "5432"),
			getEnv("POSTGRES_USER", "dangling_user"),
			getEnv("POSTGRES_PASSWORD", "change_this_password"),
			getEnv("POSTGRES_DB", "dangling_dice_db"),
		),
	}

	state := &ServerState{
		cfg:          cfg,
		nextIPIndex:  2, // Starts allocating from 10.8.0.2 upwards
		peerIPMap:    make(map[string]string),
		rateLimitMap: make(map[string][]time.Time),
	}

	// Read or load server public key from /etc/wireguard/server_public.key
	state.serverPubKey = loadServerPublicKey()

	// Initialize DB connection (retry loop for docker startup)
	var err error
	for i := 0; i < 10; i++ {
		state.db, err = sql.Open("postgres", cfg.PostgresDSN)
		if err == nil && state.db.Ping() == nil {
			log.Println("Successfully connected to PostgreSQL database")
			break
		}
		log.Println("Waiting for database to be ready...")
		time.Sleep(2 * time.Second)
	}

	r := chi.NewRouter()

	// Global Middlewares
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

	// API Routes
	r.Get("/health", state.handleHealth)
	r.Get("/api", state.handleStudioApiRoot)
	r.Get("/api/", state.handleStudioApiRoot)
	r.Get("/api/items", state.handleStudioCatalogItems)
	
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
		log.Fatalf("Server failed to start: %v", err)
	}
}

// Handler: Healthcheck
func (s *ServerState) handleHealth(w http.ResponseWriter, r *http.Request) {
	jsonResponse(w, http.StatusOK, map[string]interface{}{
		"status":      "online",
		"service":     "dangling-dice-control-api",
		"timestamp":   time.Now().UTC(),
		"public_ip":   s.cfg.ServerPublicIP,
		"wireguard":   s.cfg.WGInterface,
	})
}

// Handler: Studio Website API Healthcheck (/api/)
func (s *ServerState) handleStudioApiRoot(w http.ResponseWriter, r *http.Request) {
	jsonResponse(w, http.StatusOK, map[string]interface{}{
		"success":       true,
		"service":       "Dangling Dice Studios™ REST API & VPN Control Plane",
		"version":       "1.0.0",
		"documentation": "/api",
	})
}

// Handler: Studio Website Catalog Items (/api/items)
func (s *ServerState) handleStudioCatalogItems(w http.ResponseWriter, r *http.Request) {
	items := []map[string]interface{}{
		{
			"_id":         "item-1",
			"title":       "Dangling Dice Secure Dev VPN & Proxy",
			"description": "High-speed, encrypted WireGuard VPN built specifically for developers. Geolocation testing, static IP whitelisting, ad/malware DNS sinkholing, and privacy protection.",
			"category":    "Developer Tools",
			"mediaUrl":    "https://vpn.danglingdicestudios.duckdns.org",
			"imageUrl":    "/images/logo.png",
			"tags":        []string{"VPN", "WireGuard", "Privacy", "Android", "DevTools"},
			"price":       0,
			"isFeatured":  true,
			"isTool":      true,
			"toolType":    "standalone-app",
			"liveUrl":     "https://vpn.danglingdicestudios.duckdns.org",
		},
		{
			"_id":         "item-2",
			"title":       "MongoDB Connection URI & Query Inspector",
			"description": "Interactive client-side developer utility. Validates SRV connection strings, encodes credentials, highlights syntax errors, and generates clean .env environment variable configs.",
			"category":    "Developer Tools",
			"mediaUrl":    "#mongoInspector",
			"imageUrl":    "/images/logo.png",
			"tags":        []string{"MongoDB", "DevTools", "Database", "Utility"},
			"price":       0,
			"isFeatured":  true,
			"isTool":      true,
			"toolType":    "web-utility",
			"liveUrl":     "#mongoInspector",
		},
		{
			"_id":         "item-3",
			"title":       "AI Calculator — Smart Android Mathematical Assistant",
			"description": "Next-generation Android calculator powered by artificial intelligence. Solves complex algebra, calculus, step-by-step homework problems, and visualizes mathematical graphs. Available on Google Play Store.",
			"category":    "Mobile Apps",
			"mediaUrl":    "https://play.google.com/store/apps/details?id=com.danglingdicestudios.aicalculator",
			"imageUrl":    "/images/ai_calculator.png",
			"tags":        []string{"Android", "AI", "Google Play", "Calculator", "Mobile App"},
			"price":       0,
			"isFeatured":  true,
		},
		{
			"_id":         "item-4",
			"title":       "Building Modern Full-Stack Applications from Scratch",
			"description": "Comprehensive video course showing beginners and developers how to scaffold clean three-tier architecture with Node, Express, MongoDB, Go, and modern decoupled clients.",
			"category":    "Coding Tutorials",
			"mediaUrl":    "https://www.youtube.com/@danglingdicestudios",
			"imageUrl":    "/images/banner.jpg",
			"tags":        []string{"JavaScript", "Node.js", "MongoDB", "Architecture", "Web Dev"},
			"price":       0,
			"isFeatured":  true,
		},
		{
			"_id":         "item-5",
			"title":       "Neon D20 — Cyberpunk Lofi Instrumental EP",
			"description": "Atmospheric beat collection crafted for late-night coding sessions, studying, and focus. Produced by Dangling Beats Studio.",
			"category":    "Beats & Audio",
			"mediaUrl":    "https://www.youtube.com/@danglingdicestudios",
			"imageUrl":    "/images/banner.jpg",
			"tags":        []string{"Lofi", "Beats", "Cyberpunk", "Study Music", "Coding Beats"},
			"price":       0,
			"isFeatured":  true,
		},
		{
			"_id":               "item-6",
			"title":             "DigitalOcean — Cloud Compute & Dedicated Droplets",
			"description":       "Developer-friendly cloud computing, scalable VPS droplets, managed Kubernetes, and databases. Simple pricing with zero hidden egress fees.",
			"category":          "Developer Tools",
			"mediaUrl":          "https://www.digitalocean.com",
			"imageUrl":          "/images/logo.png",
			"tags":              []string{"Cloud", "VPS", "Hosting", "Droplets", "Recommended"},
			"price":             0,
			"isFeatured":        true,
			"isAffiliate":       true,
			"affiliateCategory": "Cloud Hosting & Databases",
			"affiliateBadge":    "Partner Deal • $200 Free 60-Day Credit",
			"affiliateUrl":      "https://www.digitalocean.com",
		},
		{
			"_id":               "item-7",
			"title":             "MongoDB Atlas — Multi-Cloud Managed Database",
			"description":       "Deploy fully managed MongoDB clusters across AWS, Google Cloud, and Azure. Free shared cluster tier with automated backups, monitoring, and seamless scaling.",
			"category":          "Developer Tools",
			"mediaUrl":          "https://www.mongodb.com/cloud/atlas/register",
			"imageUrl":          "/images/logo.png",
			"tags":              []string{"MongoDB", "Atlas", "Cloud Database", "Recommended"},
			"price":             0,
			"isFeatured":        true,
			"isAffiliate":       true,
			"affiliateCategory": "Cloud Hosting & Databases",
			"affiliateBadge":    "Official Cloud Partner • Free Tier Available",
			"affiliateUrl":      "https://www.mongodb.com/cloud/atlas/register",
		},
	}

	jsonResponse(w, http.StatusOK, map[string]interface{}{
		"success": true,
		"count":   len(items),
		"data":    items,
	})
}

// Handler: Diagnostics (for WebRTC/DNS leak tester tool)
func (s *ServerState) handleDiagnostics(w http.ResponseWriter, r *http.Request) {
	clientIP := r.Header.Get("X-Real-IP")
	if clientIP == "" {
		clientIP = r.RemoteAddr
	}

	isUsingVPN := strings.HasPrefix(clientIP, "10.8.0.") || clientIP == s.cfg.ServerPublicIP

	jsonResponse(w, http.StatusOK, map[string]interface{}{
		"client_ip":      clientIP,
		"user_agent":     r.UserAgent(),
		"is_vpn_secured": isUsingVPN,
		"timestamp":      time.Now().UTC(),
		"server_node":    "Node-US-East-1",
	})
}

// Handler: List available VPN servers
func (s *ServerState) handleListServers(w http.ResponseWriter, r *http.Request) {
	servers := []map[string]interface{}{
		{
			"id":           "us-east-primary",
			"name":         "US East (Primary Gateway)",
			"country_code": "US",
			"city":         "New York / NYC",
			"endpoint":     fmt.Sprintf("%s:%s", s.cfg.ServerPublicIP, s.cfg.VPNListenPort),
			"public_key":   s.serverPubKey,
			"load_percent": 12,
			"is_online":    true,
			"is_free_tier": true,
		},
	}
	jsonResponse(w, http.StatusOK, map[string]interface{}{
		"servers": servers,
	})
}

type RegisterPeerRequest struct {
	DeviceName     string `json:"device_name"`
	PublicKey      string `json:"public_key"`
	AdBlockEnabled bool   `json:"adblock_enabled"`
}

// Handler: Register peer with WireGuard (Zero-Login Cryptographic Device Auth)
func (s *ServerState) handleRegisterPeer(w http.ResponseWriter, r *http.Request) {
	var req RegisterPeerRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil || req.PublicKey == "" {
		jsonError(w, http.StatusBadRequest, "Invalid request payload. 'public_key' is required.")
		return
	}

	if !isValidWireGuardKey(req.PublicKey) {
		jsonError(w, http.StatusBadRequest, "Invalid Curve25519 'public_key' format (expected 32-byte base64 key).")
		return
	}

	clientIP := r.Header.Get("X-Real-IP")
	if clientIP == "" {
		clientIP = r.RemoteAddr
	}

	s.mu.Lock()
	if s.peerIPMap == nil {
		s.peerIPMap = make(map[string]string)
	}
	// 1. Idempotent Zero-Login Lookup: Re-use existing IP if this device keypair is already registered
	assignedIP, exists := s.peerIPMap[req.PublicKey]
	if !exists {
		// 2. Enforce anti-bot rate limit only when allocating a brand-new peer key
		if !s.allowRateLimitLocked(clientIP, 15, 10*time.Minute) {
			s.mu.Unlock()
			jsonError(w, http.StatusTooManyRequests, "Rate limit exceeded for new peer registrations. Please wait a few minutes.")
			return
		}
		assignedIP = fmt.Sprintf("10.8.0.%d", s.nextIPIndex)
		s.nextIPIndex++
		if s.nextIPIndex > 250 {
			s.nextIPIndex = 2 // Wrap around & recycle oldest slot
		}
		s.peerIPMap[req.PublicKey] = assignedIP
	}
	s.mu.Unlock()

	dnsServer := s.cfg.VPNDNSServer
	if req.AdBlockEnabled {
		// AdGuard DNS Sinkhole (Blocks Ads, Trackers, and Malware at DNS level)
		dnsServer = "94.140.14.14, 94.140.15.15"
	}

	// Derive deterministic Zero-Login Account Token from device's Curve25519 PublicKey
	accountToken := deriveAccountToken(req.PublicKey)

	// Apply dynamically to WireGuard interface via 'wg set'
	// Format: wg set wg0 peer <PubKey> allowed-ips <IP>/32
	go func(pubKey, ip string) {
		cmd := exec.Command("wg", "set", s.cfg.WGInterface, "peer", pubKey, "allowed-ips", ip+"/32")
		if output, err := cmd.CombinedOutput(); err != nil {
			log.Printf("Warning: wg set failed (simulated if running in dev without host wg): %v - %s", err, string(output))
		} else {
			log.Printf("Registered peer %s with IP %s", pubKey, ip)
		}
	}(req.PublicKey, assignedIP)

	jsonResponse(w, http.StatusOK, map[string]interface{}{
		"assigned_ip":       assignedIP + "/24",
		"server_public_key": s.serverPubKey,
		"endpoint":          fmt.Sprintf("%s:%s", s.cfg.ServerPublicIP, s.cfg.VPNListenPort),
		"dns":               dnsServer,
		"adblock_active":    req.AdBlockEnabled,
		"allowed_ips":       "0.0.0.0/0, ::/0",
		"account_token":     accountToken,
		"auth_mode":         "zero_login_curve25519",
	})
}

type GuestConfigRequest struct {
	AdBlockEnabled bool `json:"adblock_enabled"`
}

// Handler: 1-Click Instant Guest WireGuard Configuration (For Web Developer Funnel)
func (s *ServerState) handleGenerateGuestConfig(w http.ResponseWriter, r *http.Request) {
	var req GuestConfigRequest
	_ = json.NewDecoder(r.Body).Decode(&req)

	// Generate a curve25519 keypair for the client
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

	// Apply to WireGuard
	exec.Command("wg", "set", s.cfg.WGInterface, "peer", pubKey, "allowed-ips", assignedIP+"/32").Run()

	// Build raw .conf profile text
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
		"expires_in":     "24 hours",
	})
}

type DevTunnelRequest struct {
	Subdomain  string `json:"subdomain"`
	TargetPort int    `json:"target_port"`
	PeerIP     string `json:"peer_ip"`
}

// Handler: Developer Port Forwarding / Localhost Tunneling
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
		"status":      "active",
		"public_url":  fmt.Sprintf("http://%s.%s", req.Subdomain, s.cfg.ServerPublicIP),
		"target":      fmt.Sprintf("%s:%d", req.PeerIP, req.TargetPort),
		"description": "Traffic to this URL routes over the encrypted WireGuard mesh to your local port.",
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

// Handler: Ingest Telemetry Event (SSOT)
func (s *ServerState) handleTrackEvent(w http.ResponseWriter, r *http.Request) {
	var req TelemetryEventRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil || req.EventName == "" {
		jsonError(w, http.StatusBadRequest, "Invalid payload. 'event_name' is required.")
		return
	}

	clientIP := r.Header.Get("X-Real-IP")
	if clientIP == "" {
		clientIP = r.RemoteAddr
	}
	userAgent := r.UserAgent()

	// Persist to PostgreSQL asynchronously
	go func(event TelemetryEventRequest, ip, ua string) {
		if s.db == nil {
			return
		}
		metaJSON, _ := json.Marshal(event.Metadata)
		query := `INSERT INTO telemetry_events 
			(event_name, session_id, platform, experiment_key, variant_assigned, utm_source, utm_campaign, client_ip, user_agent, metadata)
			VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10)`
		_, err := s.db.Exec(query,
			event.EventName,
			event.SessionID,
			event.Platform,
			event.ExperimentKey,
			event.VariantAssigned,
			event.UTMSource,
			event.UTMCampaign,
			ip,
			ua,
			metaJSON,
		)
		if err != nil {
			log.Printf("Telemetry insert error: %v", err)
		}
	}(req, clientIP, userAgent)

	jsonResponse(w, http.StatusOK, map[string]interface{}{"status": "recorded"})
}

// Handler: A/B Testing Deterministic Variant Assignment
func (s *ServerState) handleGetExperimentVariant(w http.ResponseWriter, r *http.Request) {
	experiment := r.URL.Query().Get("experiment")
	sessionID := r.URL.Query().Get("session_id")

	if experiment == "" {
		experiment = "vpn_hero_cta"
	}
	if sessionID == "" {
		sessionID = r.RemoteAddr
	}

	// Deterministic variant assignment based on session hash
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

// Handler: Single Source of Truth Analytics & Funnel Dashboard
func (s *ServerState) handleGetAnalyticsSummary(w http.ResponseWriter, r *http.Request) {
	summary := map[string]interface{}{
		"total_events":      0,
		"funnel": map[string]int{
			"page_views":         0,
			"leak_tests_run":     0,
			"profiles_generated": 0,
			"android_downloads":  0,
			"vpn_handshakes":     0,
		},
		"ad_sources":   map[string]int{},
		"ab_variants":  map[string]int{},
		"last_updated": time.Now().UTC(),
	}

	if s.db != nil {
		// Aggregate funnel counts
		rows, err := s.db.Query(`
			SELECT event_name, COUNT(*) 
			FROM telemetry_events 
			GROUP BY event_name`)
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

		// Aggregate traffic by ad UTM source
		utmRows, err := s.db.Query(`
			SELECT COALESCE(NULLIF(utm_source, ''), 'direct') as src, COUNT(*) 
			FROM telemetry_events 
			GROUP BY src 
			ORDER BY COUNT(*) DESC LIMIT 10`)
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

		// Aggregate A/B test conversion
		abRows, err := s.db.Query(`
			SELECT COALESCE(NULLIF(variant_assigned, ''), 'none') as variant, COUNT(*) 
			FROM telemetry_events 
			WHERE event_name = 'profile_gen' 
			GROUP BY variant`)
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

// Helpers
func isValidWireGuardKey(pubKey string) bool {
	decoded, err := base64.StdEncoding.DecodeString(strings.TrimSpace(pubKey))
	return err == nil && len(decoded) == 32
}

func deriveAccountToken(pubKey string) string {
	sum := sha256.Sum256([]byte(strings.TrimSpace(pubKey)))
	hexStr := strings.ToUpper(hex.EncodeToString(sum[:6])) // 12 hex chars
	return fmt.Sprintf("DD-%s-%s-%s", hexStr[0:4], hexStr[4:8], hexStr[8:12])
}

func (s *ServerState) allowRateLimitLocked(clientIP string, maxRequests int, window time.Duration) bool {
	if s.rateLimitMap == nil {
		s.rateLimitMap = make(map[string][]time.Time)
	}
	now := time.Now()
	cutoff := now.Add(-window)
	history := s.rateLimitMap[clientIP]
	valid := history[:0]
	for _, ts := range history {
		if ts.After(cutoff) {
			valid = append(valid, ts)
		}
	}
	if len(valid) >= maxRequests {
		s.rateLimitMap[clientIP] = valid
		return false
	}
	s.rateLimitMap[clientIP] = append(valid, now)
	return true
}

func generateWireGuardKeyPair() (string, string) {
	var priv [32]byte
	rand.Read(priv[:])
	// Curve25519 clamping
	priv[0] &= 248
	priv[31] &= 127
	priv[31] |= 64

	var pub [32]byte
	curve25519.ScalarBaseMult(&pub, &priv)

	privB64 := base64.StdEncoding.EncodeToString(priv[:])
	pubB64 := base64.StdEncoding.EncodeToString(pub[:])
	return privB64, pubB64
}

func loadServerPublicKey() string {
	content, err := os.ReadFile("/etc/wireguard/server_public.key")
	if err == nil {
		return strings.TrimSpace(string(content))
	}
	// Fallback simulated key for local development
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

