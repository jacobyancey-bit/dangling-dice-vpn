#!/usr/bin/env bash
# ==============================================================================
# Dangling Dice VPN & Developer Platform - Droplet Bootstrap Script
# Target: Ubuntu 24.04 LTS / 22.04 LTS on DigitalOcean (165.245.142.71)
# ==============================================================================
set -euo pipefail

echo "=========================================================="
echo " Starting Dangling Dice Server Foundation Bootstrap"
echo " Target IP: 165.245.142.71"
echo "=========================================================="

# 1. Update system packages
export DEBIAN_FRONTEND=noninteractive
apt-get update && apt-get upgrade -y
apt-get install -y \
    ca-certificates \
    curl \
    gnupg \
    lsb-release \
    git \
    ufw \
    fail2ban \
    wireguard \
    wireguard-tools \
    iptables \
    nftables \
    net-tools \
    htop \
    jq

# 2. Configure Linux Kernel for High-Performance Networking & Forwarding
echo ">>> Configuring Kernel parameters..."
cat << 'EOF' > /etc/sysctl.d/99-dangling-dice.conf
# Enable IPv4 and IPv6 packet forwarding for VPN gateway
net.ipv4.ip_forward=1
net.ipv6.conf.all.forwarding=1

# Network performance and BBR congestion control
net.core.default_qdisc=fq
net.ipv4.tcp_congestion_control=bbr

# Increase buffer limits for high-throughput traffic
net.core.rmem_max=16777216
net.core.wmem_max=16777216
net.ipv4.tcp_rmem=4096 87380 16777216
net.ipv4.tcp_wmem=4096 65536 16777216

# Protect against SYN flood attacks
net.ipv4.tcp_syncookies=1
net.ipv4.tcp_max_syn_backlog=4096

# File descriptor limits
fs.file-max=2097152
EOF

sysctl --system

# 3. Create a 2GB Swap file (Prevents OOM crashes on droplets)
if [ ! -f /swapfile ]; then
    echo ">>> Creating 2GB swap space for system stability..."
    fallocate -l 2G /swapfile
    chmod 600 /swapfile
    mkswap /swapfile
    swapon /swapfile
    echo '/swapfile none swap sw 0 0' >> /etc/fstab
fi

# 4. Install Docker & Docker Compose Plugin
if ! command -v docker &> /dev/null; then
    echo ">>> Installing Docker Engine..."
    install -m 0755 -d /etc/apt/keyrings
    curl -fsSL https://download.docker.com/linux/ubuntu/gpg | gpg --dearmor -o /etc/apt/keyrings/docker.gpg
    chmod a+r /etc/apt/keyrings/docker.gpg

    echo \
      "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu \
      $(. /etc/os-release && echo "$VERSION_CODENAME") stable" | \
      tee /etc/apt/sources.list.d/docker.list > /dev/null

    apt-get update
    apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
    systemctl enable docker
    systemctl start docker
    echo ">>> Docker installed successfully."
fi

# 5. Security & Firewall Configuration (UFW)
echo ">>> Configuring Firewall rules..."
ufw --force reset
ufw default deny incoming
ufw default allow outgoing

# Allow SSH, Web (HTTP/HTTPS), and WireGuard UDP
ufw allow 22/tcp comment 'SSH'
ufw allow 80/tcp comment 'HTTP (Web Portal & Caddy)'
ufw allow 443/tcp comment 'HTTPS'
ufw allow 443/udp comment 'HTTP/3 (QUIC)'
ufw allow 51820/udp comment 'WireGuard VPN Gateway'

# Abuse Prevention: Block outbound SMTP (Port 25) to prevent spam blacklisting
iptables -A OUTPUT -p tcp --dport 25 -j REJECT --reject-with icmp-port-unreachable
if command -v ip6tables &> /dev/null; then
    ip6tables -A OUTPUT -p tcp --dport 25 -j REJECT --reject-with icmp6-port-unreachable
fi

# Enable UFW
echo "y" | ufw enable
ufw status verbose

# 6. Generate Server WireGuard Cryptographic Keypair & wg0 Interface
WG_DIR="/etc/wireguard"
mkdir -p "${WG_DIR}"
chmod 700 "${WG_DIR}"

if [ ! -f "${WG_DIR}/server_private.key" ]; then
    echo ">>> Generating Server WireGuard cryptographic keys..."
    wg genkey | tee "${WG_DIR}/server_private.key" | wg pubkey > "${WG_DIR}/server_public.key"
    chmod 600 "${WG_DIR}/server_private.key"
fi

SERVER_PRIVKEY=$(cat "${WG_DIR}/server_private.key")
SERVER_PUBKEY=$(cat "${WG_DIR}/server_public.key")
PUB_IFACE=$(ip route get 8.8.8.8 | awk '{print $5; exit}')

echo ">>> Detected primary network interface: ${PUB_IFACE}"

# Create the master wg0.conf interface file
cat << EOF > "${WG_DIR}/wg0.conf"
[Interface]
Address = 10.8.0.1/24
ListenPort = 51820
PrivateKey = ${SERVER_PRIVKEY}
SaveConfig = false

# NAT Masquerading and Packet Forwarding
PostUp = iptables -A FORWARD -i wg0 -j ACCEPT; iptables -t nat -A POSTROUTING -o ${PUB_IFACE} -j MASQUERADE
PostDown = iptables -D FORWARD -i wg0 -j ACCEPT; iptables -t nat -D POSTROUTING -o ${PUB_IFACE} -j MASQUERADE
EOF

chmod 600 "${WG_DIR}/wg0.conf"

# Start and enable the WireGuard systemd service
systemctl enable wg-quick@wg0
systemctl restart wg-quick@wg0

PUBLIC_IP=$(curl -4 -s ifconfig.me || curl -4 -s icanhazip.com || echo "165.245.142.71")

echo "=========================================================="
echo " Server Foundation Bootstrap Complete!"
echo " Public IP:         ${PUBLIC_IP}"
echo " Interface:         wg0 (10.8.0.1/24 on port 51820/udp)"
echo " Server Public Key: ${SERVER_PUBKEY}"
echo " Docker:            $(docker --version)"
echo " Docker Compose:    $(docker compose version)"
echo " WireGuard Service: $(systemctl is-active wg-quick@wg0)"
echo "=========================================================="
