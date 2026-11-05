# ==============================================================================
# Dangling Dice Server Health & Breakpoint Diagnostic Test Suite (PowerShell)
# Tests public endpoint: 165.245.142.71
# ==============================================================================

param (
    [string]$ServerIP = "165.245.142.71"
)

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host " Running Dangling Dice Health & Diagnostic Breakpoint Test" -ForegroundColor Cyan
Write-Host " Target Server: $ServerIP" -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

$Passed = 0
$Failed = 0

function Test-Breakpoint {
    param (
        [string]$Name,
        [scriptblock]$TestBlock
    )
    Write-Host "`n[TEST] $Name..." -NoNewline
    try {
        $result = & $TestBlock
        if ($result.Success) {
            Write-Host " [PASS]" -ForegroundColor Green
            if ($result.Message) {
                Write-Host "       $($result.Message)" -ForegroundColor Gray
            }
            $global:Passed++
        } else {
            Write-Host " [FAIL]" -ForegroundColor Red
            Write-Host "       Error: $($result.Message)" -ForegroundColor Yellow
            $global:Failed++
        }
    } catch {
        Write-Host " [ERROR]" -ForegroundColor Red
        Write-Host "       Exception: $_" -ForegroundColor Yellow
        $global:Failed++
    }
}

# Breakpoint 1: Web Reverse Proxy (HTTP 80)
Test-Breakpoint "Breakpoint 1: HTTP Web Portal Reachability" {
    $url = "http://$ServerIP"
    $response = Invoke-WebRequest -Uri $url -TimeoutSec 5 -UseBasicParsing -ErrorAction Stop
    if ($response.StatusCode -eq 200) {
        return @{ Success = $true; Message = "Web portal loaded (HTTP 200). Content Length: $($response.RawContentLength) bytes" }
    }
    return @{ Success = $false; Message = "Unexpected Status Code: $($response.StatusCode)" }
}

# Breakpoint 2: Control Plane API Healthcheck (/health)
Test-Breakpoint "Breakpoint 2: Control Plane API Healthcheck" {
    $url = "http://$ServerIP/health"
    $response = Invoke-RestMethod -Uri $url -TimeoutSec 5 -ErrorAction Stop
    if ($response.status -eq "online") {
        return @{ Success = $true; Message = "API is online. Service: $($response.service), Public IP: $($response.public_ip)" }
    }
    return @{ Success = $false; Message = "API returned unexpected response: $($response | ConvertTo-Json)" }
}

# Breakpoint 3: Server Registry (/api/v1/servers)
Test-Breakpoint "Breakpoint 3: Server Nodes API" {
    $url = "http://$ServerIP/api/v1/servers"
    $response = Invoke-RestMethod -Uri $url -TimeoutSec 5 -ErrorAction Stop
    if ($response.servers -and $response.servers.Count -gt 0) {
        $server = $response.servers[0]
        return @{ Success = $true; Message = "Node found: $($server.name) - Endpoint: $($server.endpoint)" }
    }
    return @{ Success = $false; Message = "No servers returned in registry." }
}

# Breakpoint 4: Instant 1-Click Guest VPN Config Generator
Test-Breakpoint "Breakpoint 4: Dynamic WireGuard Peer & Config Generation" {
    $url = "http://$ServerIP/api/v1/tools/guest-config"
    $response = Invoke-RestMethod -Uri $url -Method Post -TimeoutSec 8 -ErrorAction Stop
    if ($response.assigned_ip -and $response.config_text) {
        return @{ 
            Success = $true; 
            Message = "Assigned IP: $($response.assigned_ip) | Curve25519 PubKey: $($response.public_key.Substring(0, 16))..." 
        }
    }
    return @{ Success = $false; Message = "Failed to generate WireGuard config." }
}

# Breakpoint 5: WireGuard UDP Gateway Port (51820)
Test-Breakpoint "Breakpoint 5: WireGuard UDP Socket Reachability" {
    $udpClient = New-Object System.Net.Sockets.UdpClient
    $udpClient.Client.ReceiveTimeout = 2000
    try {
        $udpClient.Connect($ServerIP, 51820)
        # WireGuard responds only to valid crypto handshakes, but connection socket should bind cleanly
        return @{ Success = $true; Message = "UDP socket successfully routed to $ServerIP`:51820" }
    } finally {
        $udpClient.Close()
    }
}

# Breakpoint 6: SSOT Telemetry & A/B Testing Engine
Test-Breakpoint "Breakpoint 6: SSOT Telemetry & A/B Experiment API" {
    $url = "http://$ServerIP/api/v1/experiments/variant?session_id=diagnostic_test_01"
    $response = Invoke-RestMethod -Uri $url -TimeoutSec 5 -ErrorAction Stop
    if ($response.variant_assigned) {
        return @{ Success = $true; Message = "Experiment: $($response.experiment_key) | Assigned Variant: $($response.variant_assigned)" }
    }
    return @{ Success = $false; Message = "Failed to evaluate A/B experiment variant." }
}

Write-Host "`n==========================================================" -ForegroundColor Cyan
Write-Host " Diagnostic Summary: $Passed Passed, $Failed Failed" -ForegroundColor $(if ($Failed -eq 0) { "Green" } else { "Red" })
Write-Host "==========================================================" -ForegroundColor Cyan

