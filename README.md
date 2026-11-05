# 🎲 Dangling Dice VPN & Developer Infrastructure

An enterprise-grade, cloud-agnostic, zero-log **WireGuard VPN and Developer Platform** built to run anywhere—from a single DigitalOcean Droplet to bare-metal data centers and colocation racks.

---

## 🏗️ Architecture Overview

- **Reverse Proxy & Auto-SSL**: Caddy 2.8 with automated Let's Encrypt certificates & HTTP/3 QUIC support.
- **Control Plane API**: Go (Golang) microservice handling IPAM (IP Address Management), peer cryptographic registration, and real-time leak diagnostics.
- **VPN Engine**: Linux In-Kernel WireGuard (UDP `51820`), ChaCha20-Poly1305 encryption, and `nftables` wire-speed NAT masquerading.
- **Data Layer**: PostgreSQL 16 for persistent accounts and peer registry; Redis 7 for high-speed rate limiting and telemetry.
- **Web Portal & Dev Tools**: Next.js 14, Tailwind CSS, real-time WebRTC/DNS leak tester, and 1-click guest profile generator with instant QR codes.

---

## 🚀 Quickstart: Deploying on DigitalOcean in 5 Minutes

### Step 1: Create a DigitalOcean Droplet
1. Log into your **DigitalOcean** dashboard.
2. Click **Create** $\rightarrow$ **Droplets**.
3. Choose:
   - **Image**: `Ubuntu 24.04 LTS x64`
   - **Size**: Basic Droplet $\rightarrow$ Regular or Premium AMD/Intel ($12 to $18/mo for 2 vCPU / 2GB RAM is ideal).
   - **Datacenter Region**: Choose your preferred primary gateway (e.g., New York `NYC3`, Frankfurt `FRA1`, or San Francisco `SFO3`).
   - **Authentication**: Add your SSH Key.
4. Click **Create Droplet**.

---

### Step 2: Run the Server Bootstrap Script
SSH into your newly created Droplet:
```bash
ssh root@YOUR_DROPLET_PUBLIC_IP
```

Clone your repository (or copy the `infra/droplet-setup.sh` script) and execute it:
```bash
chmod +x infra/droplet-setup.sh
sudo ./infra/droplet-setup.sh
```

**What this script does automatically**:
- Configures Linux kernel forwarding (`net.ipv4.ip_forward=1`) and BBR congestion control.
- Sets up a 2GB swap space to guarantee system stability and prevent OOM crashes.
- Configures `ufw` firewall (allows SSH, Web 80/443, and WireGuard UDP 51820).
- Blocks outgoing TCP port 25 to prevent spam abuse and protect your IP reputation.
- Generates the server's master WireGuard cryptographic keys in `/etc/wireguard/`.
- Installs the latest Docker Engine and Docker Compose.

---

### Step 3: Configure Environment Variables
Copy `.env.example` to `.env`:
```bash
cp .env.example .env
nano .env
```
Update the following key fields:
- `DOMAIN`: Your primary domain (e.g., `danglingdice.com`).
- `API_DOMAIN`: Your API subdomain (e.g., `api.danglingdice.com`).
- `TOOLS_DOMAIN`: Your developer tools subdomain (e.g., `tools.danglingdice.com`).
- `SERVER_PUBLIC_IP`: The public IPv4 address of your Droplet.
- `POSTGRES_PASSWORD` and `JWT_SECRET`: Secure random passwords.

*(Note: Point your DNS A-records for these domains to your Droplet's IP address).*

---

### Step 4: Launch the Multi-Service Platform
```bash
docker compose up -d
```

Check the status of your running services:
```bash
docker compose ps
docker compose logs -f api
```

Visit `https://danglingdice.com` or `http://YOUR_SERVER_IP` to see the live Developer Dashboard & 1-Click VPN Generator!

---

## 📱 Testing Your VPN Connection

### Using the Official WireGuard Client (Instant)
1. Open the Web Dashboard.
2. Click **1-Click Generate VPN Profile**.
3. Either:
   - Scan the displayed QR code with the official **WireGuard app** on Android or iOS.
   - Or download the `.conf` profile and import it into WireGuard on Windows, macOS, or Linux.
4. Toggle the switch to **Connected**.
5. Click **Re-check Leak Test** on the web dashboard to verify that your IP is secured!

