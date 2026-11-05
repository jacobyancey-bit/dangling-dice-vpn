/**
 * Dangling Dice VPN & Developer Privacy Suite — Drop-in Web Widget & Telemetry Client
 * Single Source of Truth (SSOT) tracking & interactive WireGuard client
 */
(function () {
  'use strict';

  const VPN_API_BASE = window.location.origin;

  // 1. Session & UTM Attribution Engine
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
      utm_campaign: urlParams.get('utm_campaign') || localStorage.getItem('dd_utm_campaign') || '',
      click_id: urlParams.get('gclid') || urlParams.get('fbclid') || urlParams.get('ttclid') || ''
    };
  }

  // Save UTMs to localStorage for cross-page persistence
  const utms = getUtmParams();
  if (utms.utm_source !== 'direct') {
    localStorage.setItem('dd_utm_source', utms.utm_source);
    localStorage.setItem('dd_utm_medium', utms.utm_medium);
    localStorage.setItem('dd_utm_campaign', utms.utm_campaign);
  }

  const sessionId = getOrSetSessionId();
  let assignedVariant = 'variant_a';

  // 2. Telemetry Ingestion Function (SSOT)
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

  // 3. Initialize Widget UI
  async function initWidget() {
    // Determine A/B test variant
    try {
      const res = await fetch(`${VPN_API_BASE}/api/v1/experiments/variant?session_id=${sessionId}`);
      const data = await res.json();
      assignedVariant = data.variant_assigned || 'variant_a';
    } catch (_) {}

    // Record initial page view event
    trackEvent('page_view', { path: window.location.pathname });

    const container = document.getElementById('dangling-dice-vpn-widget');
    if (!container) return;

    // Render Widget HTML styled to seamlessly match the website
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

        <!-- Diagnostic Leak Tester Section -->
        <div style="background: #0f172a; border: 1px solid #1e293b; border-radius: 10px; padding: 1rem; margin-bottom: 1.25rem; display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 0.75rem;">
          <div>
            <span style="font-size: 0.75rem; color: #94a3b8; text-transform: uppercase;">Detected Public IP:</span>
            <div id="dd-vpn-ip" style="font-family: 'JetBrains Mono', monospace; font-size: 0.95rem; font-weight: 700; color: #22c55e;">Fetching...</div>
          </div>
          <button id="dd-btn-test-leak" style="background: #1e293b; color: #e2e8f0; border: 1px solid #334155; padding: 0.5rem 1rem; border-radius: 8px; font-size: 0.8rem; font-weight: 600; cursor: pointer;">
            ⚡ Run Leak Shield Test
          </button>
        </div>

        <!-- 1-Click WireGuard Generator -->
        <div id="dd-vpn-action-box">
          <button id="dd-btn-generate-profile" style="width: 100%; background: linear-gradient(135deg, #10b981, #06b6d4); color: #020617; border: none; padding: 0.85rem; border-radius: 10px; font-size: 0.95rem; font-weight: 800; cursor: pointer; transition: transform 0.1s ease;">
            🚀 1-Click Generate Free WireGuard Profile
          </button>
        </div>

        <!-- Dynamic Output Container -->
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

    // Hook up elements
    const testLeakBtn = document.getElementById('dd-btn-test-leak');
    const genProfileBtn = document.getElementById('dd-btn-generate-profile');
    const ipLabel = document.getElementById('dd-vpn-ip');
    const statusBadge = document.getElementById('dd-vpn-status-badge');
    const outputBox = document.getElementById('dd-vpn-profile-output');
    const confText = document.getElementById('dd-vpn-conf-text');
    const downloadBtn = document.getElementById('dd-btn-download-conf');

    let currentConfigText = '';

    // Run Initial Leak Check
    async function checkLeak() {
      try {
        const res = await fetch(`${VPN_API_BASE}/api/v1/tools/diagnostics`);
        const data = await res.json();
        if (ipLabel) ipLabel.textContent = data.client_ip;
        if (data.is_vpn_secured) {
          if (statusBadge) {
            statusBadge.textContent = '● SECURED BY DANGLING DICE';
            statusBadge.style.color = '#22c55e';
            statusBadge.style.background = 'rgba(34, 197, 94, 0.12)';
            statusBadge.style.borderColor = 'rgba(34, 197, 94, 0.3)';
          }
        }
      } catch (_) {
        if (ipLabel) ipLabel.textContent = 'Local / Direct Network';
      }
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
        alert('Could not connect to Dangling Dice VPN Gateway.');
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

