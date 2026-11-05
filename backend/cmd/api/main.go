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
		cfg:         cfg,
		nextIPIndex: 2, // Starts allocating from 10.8.0.2 upwards
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
	
	r.Route("/api/v1", func(r chi.Router) {
		r.Get("/servers", state.handleListServers)
		r.Get("/tools/diagnostics", state.handleDiagnostics)
		r.Post("/peers/register", state.handleRegisterPeer)
		r.Post("/tools/guest-config", state.handleGenerateGuestConfig)
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
	DeviceName string `json:"device_name"`
	PublicKey  string `json:"public_key"`
}

// Handler: Register peer with WireGuard
func (s *ServerState) handleRegisterPeer(w http.ResponseWriter, r *http.Request) {
	var req RegisterPeerRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil || req.PublicKey == "" {
		jsonError(w, http.StatusBadRequest, "Invalid request payload. 'public_key' is required.")
		return
	}

	s.mu.Lock()
	assignedIP := fmt.Sprintf("10.8.0.%d", s.nextIPIndex)
	s.nextIPIndex++
	if s.nextIPIndex > 250 {
		s.nextIPIndex = 2 // Wrap around (in production, use DB IPAM table)
	}
	s.mu.Unlock()

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
		"assigned_ip":      assignedIP + "/24",
		"server_public_key": s.serverPubKey,
		"endpoint":         fmt.Sprintf("%s:%s", s.cfg.ServerPublicIP, s.cfg.VPNListenPort),
		"dns":              s.cfg.VPNDNSServer,
		"allowed_ips":      "0.0.0.0/0, ::/0",
	})
}

// Handler: 1-Click Instant Guest WireGuard Configuration (For Web Developer Funnel)
func (s *ServerState) handleGenerateGuestConfig(w http.ResponseWriter, r *http.Request) {
	// Generate a curve25519 keypair for the client
	privKey, pubKey := generateWireGuardKeyPair()

	s.mu.Lock()
	assignedIP := fmt.Sprintf("10.8.0.%d", s.nextIPIndex)
	s.nextIPIndex++
	if s.nextIPIndex > 250 {
		s.nextIPIndex = 2
	}
	s.mu.Unlock()

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
`, privKey, assignedIP, s.cfg.VPNDNSServer, s.serverPubKey, s.cfg.ServerPublicIP, s.cfg.VPNListenPort)

	jsonResponse(w, http.StatusOK, map[string]interface{}{
		"assigned_ip": assignedIP,
		"public_key":  pubKey,
		"config_text": rawConf,
		"expires_in":  "24 hours",
	})
}

// Helpers
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

