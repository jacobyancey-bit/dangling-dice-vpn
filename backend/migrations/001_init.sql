-- Dangling Dice Initial PostgreSQL Schema

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- 1. Users Table
CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255),
    tier VARCHAR(50) DEFAULT 'free' CHECK (tier IN ('free', 'pro', 'enterprise')),
    api_key VARCHAR(64) UNIQUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 2. WireGuard Peers / Connected Devices
CREATE TABLE IF NOT EXISTS peers (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID REFERENCES users(id) ON DELETE CASCADE,
    device_name VARCHAR(100) NOT NULL,
    public_key VARCHAR(64) UNIQUE NOT NULL,
    preshared_key VARCHAR(64),
    assigned_ip VARCHAR(45) UNIQUE NOT NULL, -- IPv4 (e.g. 10.8.0.5) or IPv6
    bytes_tx BIGINT DEFAULT 0,
    bytes_rx BIGINT DEFAULT 0,
    is_active BOOLEAN DEFAULT TRUE,
    expires_at TIMESTAMP WITH TIME ZONE, -- For guest/temporary developer tokens
    last_handshake_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 3. Developer Tunnels (For exposing local dev ports to subdomains)
CREATE TABLE IF NOT EXISTS dev_tunnels (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID REFERENCES users(id) ON DELETE CASCADE,
    subdomain VARCHAR(63) UNIQUE NOT NULL,
    target_port INT NOT NULL,
    peer_ip VARCHAR(45) NOT NULL,
    is_public BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 4. Server Nodes Registry (For multi-server & multi-hop scaling)
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

-- Initial index creation
CREATE INDEX IF NOT EXISTS idx_peers_user_id ON peers(user_id);
CREATE INDEX IF NOT EXISTS idx_peers_public_key ON peers(public_key);
CREATE INDEX IF NOT EXISTS idx_dev_tunnels_subdomain ON dev_tunnels(subdomain);

