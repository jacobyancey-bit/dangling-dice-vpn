#!/usr/bin/env bash
# ==============================================================================
# Dangling Dice All-in-One Direct Console Installer
# ==============================================================================
set -euo pipefail

echo "=========================================================="
echo " Deploying Dangling Dice VPN & Developer Platform"
echo "=========================================================="

# 1. Authorize your local PC's SSH key so future SCP/SSH commands connect without passwords
mkdir -p /root/.ssh
chmod 700 /root/.ssh
if ! grep -q "jacobyancey-bit" /root/.ssh/authorized_keys 2>/dev/null; then
    echo "ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAILmAqtfT9FsNZGHMpY7QmKcHVL8pCf67t6wECquGQgAe jacobyancey-bit" >> /root/.ssh/authorized_keys
    chmod 600 /root/.ssh/authorized_keys
    echo ">>> Local SSH key authorized."
fi

# 2. Setup project directory
PROJECT_DIR="/root/dangling-dice"
mkdir -p "${PROJECT_DIR}"/{infra,backend/{cmd/api,migrations},web/src/app,web/public}
cd "${PROJECT_DIR}"

# 3. Write infra/droplet-setup.sh
cat << 'EOF' > infra/droplet-setup.sh
#!/usr/bin/env bash
set -euo pipefail
export DEBIAN_FRONTEND=noninteractive
apt-get update && apt-get upgrade -y
apt-get install -y ca-certificates curl gnupg lsb-release git ufw fail2ban wireguard wireguard-tools iptables nftables net-tools htop jq

cat << 'SYSCTL_EOF' > /etc/sysctl.d/99-dangling-dice.conf
net.ipv4.ip_forward=1
net.ipv6.conf.all.forwarding=1
net.core.default_qdisc=fq
net.ipv4.tcp_congestion_control=bbr
net.core.rmem_max=16777216
net.core.wmem_max=16777216
net.ipv4.tcp_rmem=4096 87380 16777216
net.ipv4.tcp_wmem=4096 65536 16777216
net.ipv4.tcp_syncookies=1
net.ipv4.tcp_max_syn_backlog=4096
fs.file-max=2097152
SYSCTL_EOF
sysctl --system

if [ ! -f /swapfile ]; then
    fallocate -l 2G /swapfile
    chmod 600 /swapfile
    mkswap /swapfile
    swapon /swapfile
    echo '/swapfile none swap sw 0 0' >> /etc/fstab
fi

if ! command -v docker &> /dev/null; then
    install -m 0755 -d /etc/apt/keyrings
    curl -fsSL https://download.docker.com/linux/ubuntu/gpg | gpg --dearmor -o /etc/apt/keyrings/docker.gpg
    chmod a+r /etc/apt/keyrings/docker.gpg
    echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo "$VERSION_CODENAME") stable" | tee /etc/apt/sources.list.d/docker.list > /dev/null
    apt-get update
    apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
    systemctl enable docker
    systemctl start docker
fi

ufw --force reset
ufw default deny incoming
ufw default allow outgoing
ufw allow 22/tcp comment 'SSH'
ufw allow 80/tcp comment 'HTTP'
ufw allow 443/tcp comment 'HTTPS'
ufw allow 443/udp comment 'HTTP/3 QUIC'
ufw allow 51820/udp comment 'WireGuard VPN'
iptables -A OUTPUT -p tcp --dport 25 -j REJECT --reject-with icmp-port-unreachable
echo "y" | ufw enable

WG_DIR="/etc/wireguard"
mkdir -p "${WG_DIR}"
chmod 700 "${WG_DIR}"
if [ ! -f "${WG_DIR}/server_private.key" ]; then
    wg genkey | tee "${WG_DIR}/server_private.key" | wg pubkey > "${WG_DIR}/server_public.key"
    chmod 600 "${WG_DIR}/server_private.key"
fi

SERVER_PRIVKEY=$(cat "${WG_DIR}/server_private.key")
PUB_IFACE=$(ip route get 8.8.8.8 | awk '{print $5; exit}')

cat << WG_EOF > "${WG_DIR}/wg0.conf"
[Interface]
Address = 10.8.0.1/24
ListenPort = 51820
PrivateKey = ${SERVER_PRIVKEY}
SaveConfig = false
PostUp = iptables -A FORWARD -i wg0 -j ACCEPT; iptables -t nat -A POSTROUTING -o ${PUB_IFACE} -j MASQUERADE
PostDown = iptables -D FORWARD -i wg0 -j ACCEPT; iptables -t nat -D POSTROUTING -o ${PUB_IFACE} -j MASQUERADE
WG_EOF
chmod 600 "${WG_DIR}/wg0.conf"
systemctl enable wg-quick@wg0
systemctl restart wg-quick@wg0
EOF
chmod +x infra/droplet-setup.sh

# 4. Write Caddyfile
cat << 'EOF' > Caddyfile
{
    admin off
    auto_https disable_redirects
}

:80 {
    handle /api/* {
        reverse_proxy api:8080 {
            header_up Host {host}
            header_up X-Real-IP {remote_host}
            header_up X-Forwarded-For {remote_host}
            header_up X-Forwarded-Proto {scheme}
        }
    }

    handle /health {
        reverse_proxy api:8080 {
            header_up Host {host}
            header_up X-Real-IP {remote_host}
        }
    }

    handle {
        reverse_proxy web:3000 {
            header_up Host {host}
            header_up X-Real-IP {remote_host}
        }
    }
}
EOF

# 5. Write .env
cat << 'EOF' > .env
DOMAIN=165.245.142.71
API_DOMAIN=api.165.245.142.71
TOOLS_DOMAIN=tools.165.245.142.71
ACME_EMAIL=admin@danglingdice.com
SERVER_PUBLIC_IP=165.245.142.71
VPN_LISTEN_PORT=51820
VPN_SUBNET=10.8.0.0/24
VPN_GATEWAY_IP=10.8.0.1
VPN_DNS_SERVER=1.1.1.1,1.0.0.1
POSTGRES_USER=dangling_user
POSTGRES_PASSWORD=dice_secure_pg_pass_9281a8f9b2
POSTGRES_DB=dangling_dice_db
POSTGRES_PORT=5432
REDIS_PASSWORD=dice_redis_secure_pass_4481c0e3a1
JWT_SECRET=f981a742c3d5e6b7012948571029384756102938475610293847561029384756
API_PORT=8080
APP_ENV=production
DEBUG=false
ALLOW_GUEST_PROFILES=true
DEFAULT_GUEST_EXPIRY_HOURS=24
FREE_TIER_MAX_DEVICES=3
EOF

# 6. Write docker-compose.yml
cat << 'EOF' > docker-compose.yml
name: dangling-dice-platform

services:
  caddy:
    image: caddy:2.8-alpine
    container_name: dangling-caddy
    restart: unless-stopped
    ports:
      - "80:80"
      - "443:443"
      - "443:443/udp"
    volumes:
      - ./Caddyfile:/etc/caddy/Caddyfile:ro
      - caddy_data:/data
      - caddy_config:/config
    networks:
      - public-net
    depends_on:
      api:
        condition: service_started
      web:
        condition: service_started

  api:
    build:
      context: ./backend
      dockerfile: Dockerfile
    container_name: dangling-api
    restart: unless-stopped
    cap_add:
      - NET_ADMIN
    environment:
      - APP_ENV=production
      - API_PORT=8080
      - SERVER_PUBLIC_IP=165.245.142.71
      - VPN_LISTEN_PORT=51820
      - VPN_SUBNET=10.8.0.0/24
      - VPN_GATEWAY_IP=10.8.0.1
      - VPN_DNS_SERVER=1.1.1.1
      - POSTGRES_HOST=postgres
      - POSTGRES_PORT=5432
      - POSTGRES_USER=dangling_user
      - POSTGRES_PASSWORD=dice_secure_pg_pass_9281a8f9b2
      - POSTGRES_DB=dangling_dice_db
      - REDIS_HOST=redis:6379
      - REDIS_PASSWORD=dice_redis_secure_pass_4481c0e3a1
      - JWT_SECRET=f981a742c3d5e6b7012948571029384756102938475610293847561029384756
    volumes:
      - /etc/wireguard:/etc/wireguard
    networks:
      - public-net
      - backend-net
    depends_on:
      postgres:
        condition: service_healthy
      redis:
        condition: service_healthy

  web:
    build:
      context: ./web
      dockerfile: Dockerfile
    container_name: dangling-web
    restart: unless-stopped
    environment:
      - NODE_ENV=production
      - NEXT_PUBLIC_API_URL=http://165.245.142.71
      - NEXT_PUBLIC_SERVER_IP=165.245.142.71
    networks:
      - public-net
    depends_on:
      - api

  postgres:
    image: postgres:16-alpine
    container_name: dangling-postgres
    restart: unless-stopped
    environment:
      POSTGRES_USER: dangling_user
      POSTGRES_PASSWORD: dice_secure_pg_pass_9281a8f9b2
      POSTGRES_DB: dangling_dice_db
    volumes:
      - postgres_data:/var/lib/postgresql/data
      - ./backend/migrations:/docker-entrypoint-initdb.d:ro
    networks:
      - backend-net
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U dangling_user -d dangling_dice_db"]
      interval: 5s
      timeout: 5s
      retries: 5

  redis:
    image: redis:7-alpine
    container_name: dangling-redis
    restart: unless-stopped
    command: redis-server --requirepass "dice_redis_secure_pass_4481c0e3a1" --loglevel warning
    volumes:
      - redis_data:/data
    networks:
      - backend-net
    healthcheck:
      test: ["CMD", "redis-cli", "-a", "dice_redis_secure_pass_4481c0e3a1", "ping"]
      interval: 5s
      timeout: 5s
      retries: 5

networks:
  public-net:
    driver: bridge
  backend-net:
    internal: true

volumes:
  caddy_data:
  caddy_config:
  postgres_data:
  redis_data:
EOF

# 7. Write backend files
cat << 'EOF' > backend/go.mod
module github.com/dangling-dice/vpn-control

go 1.22

require (
	github.com/go-chi/chi/v5 v5.0.12
	github.com/go-chi/cors v1.2.1
	github.com/google/uuid v1.6.0
	github.com/lib/pq v1.10.9
	golang.org/x/crypto v0.24.0
)
EOF

cat << 'EOF' > backend/Dockerfile
FROM golang:1.22-alpine AS builder
WORKDIR /app
RUN apk add --no-cache git
COPY go.mod go.sum* ./
COPY . .
RUN go mod tidy && CGO_ENABLED=0 GOOS=linux go build -ldflags="-w -s" -o /app/dangling-api ./cmd/api

FROM alpine:3.20
WORKDIR /app
RUN apk add --no-cache ca-certificates wireguard-tools iptables iproute2 curl
COPY --from=builder /app/dangling-api /app/dangling-api
EXPOSE 8080
CMD ["/app/dangling-api"]
EOF

cat << 'EOF' > backend/migrations/001_init.sql
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255),
    tier VARCHAR(50) DEFAULT 'free' CHECK (tier IN ('free', 'pro', 'enterprise')),
    api_key VARCHAR(64) UNIQUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS peers (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID REFERENCES users(id) ON DELETE CASCADE,
    device_name VARCHAR(100) NOT NULL,
    public_key VARCHAR(64) UNIQUE NOT NULL,
    preshared_key VARCHAR(64),
    assigned_ip VARCHAR(45) UNIQUE NOT NULL,
    bytes_tx BIGINT DEFAULT 0,
    bytes_rx BIGINT DEFAULT 0,
    is_active BOOLEAN DEFAULT TRUE,
    expires_at TIMESTAMP WITH TIME ZONE,
    last_handshake_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS dev_tunnels (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID REFERENCES users(id) ON DELETE CASCADE,
    subdomain VARCHAR(63) UNIQUE NOT NULL,
    target_port INT NOT NULL,
    peer_ip VARCHAR(45) NOT NULL,
    is_public BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS server_nodes (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name VARCHAR(100) NOT NULL,
    country_code VARCHAR(3) NOT NULL,
    city VARCHAR(100) NOT NULL,
    public_ip VARCHAR(45) NOT NULL,
    public_key VARCHAR(64) NOT NULL,
    listen_port INT DEFAULT 51820,
    max_clients INT DEFAULT 1000,
    current_clients INT DEFAULT 0,
    is_healthy BOOLEAN DEFAULT TRUE,
    last_heartbeat TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
EOF

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

# 8. Write web frontend files
cat << 'EOF' > web/package.json
{
  "name": "dangling-dice-web",
  "version": "1.0.0",
  "private": true,
  "scripts": {
    "dev": "next dev",
    "build": "next build",
    "start": "next start"
  },
  "dependencies": {
    "clsx": "^2.1.1",
    "lucide-react": "^0.395.0",
    "next": "^14.2.4",
    "qrcode": "^1.5.3",
    "react": "^18.3.1",
    "react-dom": "^18.3.1",
    "tailwind-merge": "^2.3.0"
  },
  "devDependencies": {
    "@types/node": "^20.14.2",
    "@types/qrcode": "^1.5.5",
    "@types/react": "^18.3.3",
    "@types/react-dom": "^18.3.0",
    "autoprefixer": "^10.4.19",
    "postcss": "^8.4.38",
    "tailwindcss": "^3.4.4",
    "typescript": "^5.4.5"
  }
}
EOF

cat << 'EOF' > web/next.config.js
/** @type {import('next').NextConfig} */
const nextConfig = {
  output: 'standalone',
  reactStrictMode: true,
};
module.exports = nextConfig;
EOF

cat << 'EOF' > web/tsconfig.json
{
  "compilerOptions": {
    "lib": ["dom", "dom.iterable", "esnext"],
    "allowJs": true,
    "skipLibCheck": true,
    "strict": true,
    "noEmit": true,
    "esModuleInterop": true,
    "module": "esnext",
    "moduleResolution": "bundler",
    "resolveJsonModule": true,
    "isolatedModules": true,
    "jsx": "preserve",
    "incremental": true,
    "paths": {
      "@/*": ["./src/*"]
    }
  },
  "include": ["next-env.d.ts", "**/*.ts", "**/*.tsx"],
  "exclude": ["node_modules"]
}
EOF

cat << 'EOF' > web/tailwind.config.js
/** @type {import('tailwindcss').Config} */
module.exports = {
  content: ['./src/**/*.{js,ts,jsx,tsx,mdx}'],
  theme: {
    extend: {
      colors: {
        dark: { 800: '#1e293b', 900: '#0f172a', 950: '#020617' }
      }
    }
  },
  plugins: []
}
EOF

cat << 'EOF' > web/postcss.config.js
module.exports = {
  plugins: { tailwindcss: {}, autoprefixer: {} }
}
EOF

cat << 'EOF' > web/src/app/globals.css
@tailwind base;
@tailwind components;
@tailwind utilities;

body {
  color: #fff;
  background-color: #020617;
  min-height: 100vh;
}
EOF

cat << 'EOF' > web/src/app/layout.tsx
import './globals.css';
import type { Metadata } from 'next';

export const metadata: Metadata = {
  title: 'Dangling Dice | High-Performance Free VPN & Developer Tools',
  description: 'Zero-log WireGuard VPN and developer security suite.',
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en">
      <body className="bg-slate-950 text-slate-100 antialiased">{children}</body>
    </html>
  );
}
EOF

cat << 'EOF' > web/src/app/page.tsx
'use client';
import React, { useState, useEffect } from 'react';
import { Shield, ShieldAlert, ShieldCheck, Download, Terminal, Lock, Globe, RefreshCw } from 'lucide-react';
import QRCode from 'qrcode';

export default function Home() {
  const [diagnostics, setDiagnostics] = useState<any>(null);
  const [loadingDiag, setLoadingDiag] = useState(true);
  const [guestConfig, setGuestConfig] = useState<string | null>(null);
  const [qrCodeUrl, setQrCodeUrl] = useState<string | null>(null);
  const [generating, setGenerating] = useState(false);

  useEffect(() => {
    fetchDiagnostics();
  }, []);

  const fetchDiagnostics = async () => {
    setLoadingDiag(true);
    try {
      const res = await fetch('/api/v1/tools/diagnostics');
      const data = await res.json();
      setDiagnostics(data);
    } catch (err) {
      setDiagnostics({ client_ip: 'Checking...', is_vpn_secured: false });
    } finally {
      setLoadingDiag(false);
    }
  };

  const generateGuestProfile = async () => {
    setGenerating(true);
    try {
      const res = await fetch('/api/v1/tools/guest-config', { method: 'POST' });
      const data = await res.json();
      setGuestConfig(data.config_text);
      if (data.config_text) {
        const qr = await QRCode.toDataURL(data.config_text);
        setQrCodeUrl(qr);
      }
    } catch (err) {
      alert('Error connecting to backend API');
    } finally {
      setGenerating(false);
    }
  };

  const downloadConf = () => {
    if (!guestConfig) return;
    const blob = new Blob([guestConfig], { type: 'text/plain' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = 'dangling-dice.conf';
    a.click();
  };

  return (
    <div className="min-h-screen flex flex-col justify-between p-6 max-w-6xl mx-auto space-y-10">
      <header className="flex justify-between items-center border-b border-slate-800 pb-4">
        <div className="flex items-center space-x-3">
          <span className="text-3xl">🎲</span>
          <span className="font-bold text-xl text-white">Dangling Dice VPN</span>
          <span className="text-xs uppercase px-2 py-0.5 rounded bg-emerald-500/10 text-emerald-400 border border-emerald-500/20 font-mono">Free Dev Tier</span>
        </div>
      </header>

      <div className="rounded-2xl border border-slate-800 bg-slate-900/60 p-6 flex flex-col sm:flex-row items-center justify-between gap-4">
        <div className="flex items-center space-x-4">
          <div className={`p-4 rounded-xl border ${diagnostics?.is_vpn_secured ? 'bg-emerald-500/10 border-emerald-500/30 text-emerald-400' : 'bg-amber-500/10 border-amber-500/30 text-amber-400'}`}>
            {diagnostics?.is_vpn_secured ? <ShieldCheck className="w-8 h-8" /> : <ShieldAlert className="w-8 h-8" />}
          </div>
          <div>
            <span className="text-xs font-mono text-slate-400 uppercase">Status Diagnostic</span>
            <h2 className="text-xl font-bold text-white mt-0.5">
              {diagnostics?.is_vpn_secured ? 'Secured by Dangling Dice VPN' : 'Direct Connection (Unprotected)'}
            </h2>
            <p className="text-sm text-slate-400 font-mono mt-1">IP: {diagnostics?.client_ip || 'Detecting...'}</p>
          </div>
        </div>
        <button onClick={fetchDiagnostics} disabled={loadingDiag} className="px-4 py-2 rounded-lg bg-slate-800 hover:bg-slate-700 text-sm font-medium border border-slate-700 flex items-center space-x-2">
          <RefreshCw className={`w-4 h-4 ${loadingDiag ? 'animate-spin' : ''}`} />
          <span>Test Leak</span>
        </button>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
        <div className="md:col-span-2 rounded-2xl border border-slate-800 bg-slate-900/40 p-6 space-y-6">
          <div>
            <span className="text-xs px-2.5 py-1 rounded-full bg-emerald-500/10 text-emerald-400 font-mono">Instant • Zero-Logs</span>
            <h3 className="text-2xl font-bold text-white mt-2">1-Click Free WireGuard VPN</h3>
            <p className="text-sm text-slate-400 mt-1">Connect your Android phone or computer in seconds.</p>
          </div>

          {!guestConfig ? (
            <button onClick={generateGuestProfile} disabled={generating} className="w-full py-4 rounded-xl bg-emerald-500 hover:bg-emerald-600 font-bold text-slate-950 flex items-center justify-center space-x-2 transition">
              <Terminal className="w-5 h-5" />
              <span>{generating ? 'Generating Keys...' : 'Generate Instant VPN Profile'}</span>
            </button>
          ) : (
            <div className="flex flex-col sm:flex-row gap-6 items-center bg-slate-950 p-4 rounded-xl border border-slate-800">
              {qrCodeUrl && (
                <div className="bg-white p-2 rounded-lg">
                  <img src={qrCodeUrl} alt="WireGuard QR" className="w-32 h-32" />
                  <span className="block text-center text-[9px] text-slate-900 font-bold mt-1">SCAN WITH PHONE</span>
                </div>
              )}
              <div className="flex-1 w-full space-y-3">
                <span className="text-xs text-emerald-400 font-mono">Profile Ready!</span>
                <pre className="bg-slate-900 p-2 rounded text-xs font-mono text-slate-300 max-h-24 overflow-x-auto">{guestConfig}</pre>
                <button onClick={downloadConf} className="w-full py-2 rounded bg-emerald-500 text-slate-950 font-bold text-sm flex items-center justify-center space-x-2">
                  <Download className="w-4 h-4" />
                  <span>Download .conf</span>
                </button>
              </div>
            </div>
          )}
        </div>

        <div className="rounded-2xl border border-slate-800 bg-slate-900/40 p-6 space-y-4">
          <div className="flex items-center space-x-2 text-white font-bold">
            <Globe className="w-5 h-5 text-emerald-400" />
            <span>Active Edge Node</span>
          </div>
          <div className="p-3 rounded-lg bg-slate-800/40 border border-slate-800 text-xs space-y-1">
            <div className="font-bold text-white">US East Gateway</div>
            <div className="text-slate-400">165.245.142.71:51820 UDP</div>
            <div className="text-emerald-400 font-mono pt-1">● WireGuard Kernel Active</div>
          </div>
        </div>
      </div>

      <footer className="text-center text-xs text-slate-500 pt-8 border-t border-slate-800">
        © 2026 Dangling Dice. Running directly on your bare server hardware.
      </footer>
    </div>
  );
}
EOF

cat << 'EOF' > web/Dockerfile
FROM node:20-alpine AS deps
WORKDIR /app
COPY package.json ./
RUN npm install

FROM node:20-alpine AS builder
WORKDIR /app
COPY --from=deps /app/node_modules ./node_modules
COPY . .
RUN npm run build

FROM node:20-alpine AS runner
WORKDIR /app
ENV NODE_ENV=production
RUN addgroup --system --gid 1001 nodejs && adduser --system --uid 1001 nextjs
COPY --from=builder /app/public ./public
COPY --from=builder --chown=nextjs:nodejs /app/.next/standalone ./
COPY --from=builder --chown=nextjs:nodejs /app/.next/static ./.next/static
USER nextjs
EXPOSE 3000
ENV PORT=3000
CMD ["node", "server.js"]
EOF

echo ">>> Running droplet bootstrap..."
./infra/droplet-setup.sh

echo ">>> Launching Docker containers..."
docker compose up -d --build

echo "=========================================================="
echo " DEPLOYMENT COMPLETE!"
echo " Visit: http://165.245.142.71 in your browser!"
echo "=========================================================="

