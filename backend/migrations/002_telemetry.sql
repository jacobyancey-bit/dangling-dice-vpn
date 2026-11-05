-- Dangling Dice Telemetry, Ad Attribution & A/B Testing Schema

-- 1. Ad Campaigns & Traffic Sources
CREATE TABLE IF NOT EXISTS ad_campaigns (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    campaign_name VARCHAR(100) NOT NULL,
    utm_source VARCHAR(50) NOT NULL,    -- e.g. tiktok, google, reddit, youtube, twitter
    utm_medium VARCHAR(50),            -- e.g. cpc, bio, video, banner
    utm_campaign VARCHAR(100),          -- e.g. launch_free_vpn_promo
    utm_content VARCHAR(100),
    click_id VARCHAR(100),              -- gclid, fbclid, ttclid
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 2. A/B Testing Experiments
CREATE TABLE IF NOT EXISTS ab_experiments (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    experiment_key VARCHAR(50) UNIQUE NOT NULL, -- e.g. 'vpn_hero_cta'
    name VARCHAR(100) NOT NULL,
    description TEXT,
    variants JSONB NOT NULL,                   -- e.g. ["variant_a", "variant_b"]
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 3. Telemetry & Funnel Events (The Single Source of Truth)
CREATE TABLE IF NOT EXISTS telemetry_events (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    event_name VARCHAR(60) NOT NULL,           -- 'page_view', 'leak_test_run', 'profile_gen', 'android_download', 'vpn_handshake'
    session_id VARCHAR(64) NOT NULL,           -- Anonymous persistent client hash
    platform VARCHAR(20) NOT NULL,             -- 'website_widget', 'web_standalone', 'android_app'
    experiment_key VARCHAR(50),
    variant_assigned VARCHAR(50),
    utm_source VARCHAR(50),
    utm_campaign VARCHAR(100),
    client_ip VARCHAR(45),
    user_agent TEXT,
    metadata JSONB,                            -- Extra context (e.g. latency, leak status, peer IP)
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 4. Server Diagnostics & Performance Metrics
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

-- Indexes for ultra-fast analytics querying
CREATE INDEX IF NOT EXISTS idx_telemetry_event_name ON telemetry_events(event_name);
CREATE INDEX IF NOT EXISTS idx_telemetry_created_at ON telemetry_events(created_at);
CREATE INDEX IF NOT EXISTS idx_telemetry_utm_source ON telemetry_events(utm_source);
CREATE INDEX IF NOT EXISTS idx_telemetry_platform ON telemetry_events(platform);
