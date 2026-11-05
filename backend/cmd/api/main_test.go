package main

import (
	"bytes"
	"encoding/base64"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
)

func newTestServerState() *ServerState {
	return &ServerState{
		cfg: Config{
			APIPort:        "8080",
			ServerPublicIP: "165.245.142.71",
			VPNListenPort:  "51820",
			VPNGatewayIP:   "10.8.0.1",
			VPNDNSServer:   "1.1.1.1,1.0.0.1",
			WGInterface:    "wg0",
		},
		serverPubKey: "xTpevhK8n+3m9+yH8OaJ6uV0cQw4Mv+B9x7pY6s=",
		nextIPIndex:  2,
	}
}

func TestGenerateWireGuardKeyPair(t *testing.T) {
	priv, pub := generateWireGuardKeyPair()

	if priv == "" || pub == "" {
		t.Fatal("Expected non-empty private and public keys")
	}

	privBytes, err := base64.StdEncoding.DecodeString(priv)
	if err != nil || len(privBytes) != 32 {
		t.Fatalf("Invalid private key length or base64: %v", err)
	}

	pubBytes, err := base64.StdEncoding.DecodeString(pub)
	if err != nil || len(pubBytes) != 32 {
		t.Fatalf("Invalid public key length or base64: %v", err)
	}
}

func TestHandleHealth(t *testing.T) {
	state := newTestServerState()
	req := httptest.NewRequest(http.MethodGet, "/health", nil)
	rr := httptest.NewRecorder()

	state.handleHealth(rr, req)

	if rr.Code != http.StatusOK {
		t.Fatalf("Expected status 200, got %d", rr.Code)
	}

	var resp map[string]interface{}
	if err := json.Unmarshal(rr.Body.Bytes(), &resp); err != nil {
		t.Fatalf("Failed to parse JSON: %v", err)
	}

	if resp["status"] != "online" {
		t.Errorf("Expected status 'online', got %v", resp["status"])
	}
}

func TestHandleGenerateGuestConfigWithAdBlock(t *testing.T) {
	state := newTestServerState()

	payload := []byte(`{"adblock_enabled": true}`)
	req := httptest.NewRequest(http.MethodPost, "/api/v1/tools/guest-config", bytes.NewBuffer(payload))
	rr := httptest.NewRecorder()

	state.handleGenerateGuestConfig(rr, req)

	if rr.Code != http.StatusOK {
		t.Fatalf("Expected status 200, got %d", rr.Code)
	}

	var resp map[string]interface{}
	_ = json.Unmarshal(rr.Body.Bytes(), &resp)

	confText, _ := resp["config_text"].(string)
	if !strings.Contains(confText, "94.140.14.14") {
		t.Errorf("Expected AdGuard sinkhole DNS in config when adblock_enabled=true, got:\n%s", confText)
	}
	if resp["assigned_ip"] != "10.8.0.2" {
		t.Errorf("Expected assigned IP 10.8.0.2, got %v", resp["assigned_ip"])
	}
}

func TestDeterministicABExperimentVariant(t *testing.T) {
	state := newTestServerState()

	req1 := httptest.NewRequest(http.MethodGet, "/api/v1/experiments/variant?session_id=user_session_999", nil)
	rr1 := httptest.NewRecorder()
	state.handleGetExperimentVariant(rr1, req1)

	req2 := httptest.NewRequest(http.MethodGet, "/api/v1/experiments/variant?session_id=user_session_999", nil)
	rr2 := httptest.NewRecorder()
	state.handleGetExperimentVariant(rr2, req2)

	if rr1.Body.String() != rr2.Body.String() {
		t.Errorf("Expected deterministic variant assignment for identical session_id, got %s vs %s", rr1.Body.String(), rr2.Body.String())
	}
}

func TestHandleCreateDevTunnel(t *testing.T) {
	state := newTestServerState()

	payload := []byte(`{"subdomain": "my-api", "target_port": 3000, "peer_ip": "10.8.0.5"}`)
	req := httptest.NewRequest(http.MethodPost, "/api/v1/tools/dev-tunnel", bytes.NewBuffer(payload))
	rr := httptest.NewRecorder()

	state.handleCreateDevTunnel(rr, req)

	if rr.Code != http.StatusOK {
		t.Fatalf("Expected status 200, got %d", rr.Code)
	}

	var resp map[string]interface{}
	_ = json.Unmarshal(rr.Body.Bytes(), &resp)

	if resp["status"] != "active" {
		t.Errorf("Expected active tunnel status, got %v", resp["status"])
	}
}

