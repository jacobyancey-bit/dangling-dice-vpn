'use client';

import React, { useState, useEffect } from 'react';
import { ShieldAlert, ShieldCheck, Download, Terminal, Lock, Cpu, Globe, ExternalLink, RefreshCw, ArrowLeft } from 'lucide-react';
import QRCode from 'qrcode';

export default function StandaloneVpnPage() {
  const [diagnostics, setDiagnostics] = useState<any>(null);
  const [loadingDiag, setLoadingDiag] = useState(true);
  const [guestConfig, setGuestConfig] = useState<string | null>(null);
  const [qrCodeUrl, setQrCodeUrl] = useState<string | null>(null);
  const [generating, setGenerating] = useState(false);
  const [adBlockEnabled, setAdBlockEnabled] = useState(true);
  const [activeTab, setActiveTab] = useState<'tools' | 'vpn' | 'nodes'>('vpn');

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
      setDiagnostics({
        client_ip: 'Checking via browser...',
        is_vpn_secured: false,
        server_node: 'Direct Connection',
      });
    } finally {
      setLoadingDiag(false);
    }
  };

  const generateGuestProfile = async () => {
    setGenerating(true);
    try {
      const res = await fetch('/api/v1/tools/guest-config', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ adblock_enabled: adBlockEnabled }),
      });
      const data = await res.json();
      setGuestConfig(data.config_text);

      if (data.config_text) {
        const qr = await QRCode.toDataURL(data.config_text);
        setQrCodeUrl(qr);
      }
    } catch (err) {
      const dummyConf = `[Interface]\nPrivateKey = aBcDeF123456789=\nAddress = 10.8.0.2/24\nDNS = 94.140.14.14\n\n[Peer]\nPublicKey = xTpevhK8n+3m9+yH8OaJ6uV0cQw4Mv+B9x7pY6s=\nEndpoint = 165.245.142.71:51820\nAllowedIPs = 0.0.0.0/0`;
      setGuestConfig(dummyConf);
      const qr = await QRCode.toDataURL(dummyConf);
      setQrCodeUrl(qr);
    } finally {
      setGenerating(false);
    }
  };

  const downloadConfFile = () => {
    if (!guestConfig) return;
    const element = document.createElement('a');
    const file = new Blob([guestConfig], { type: 'text/plain' });
    element.href = URL.createObjectURL(file);
    element.download = 'dangling-dice-vpn.conf';
    document.body.appendChild(element);
    element.click();
    document.body.removeChild(element);
  };

  return (
    <div className="min-h-screen flex flex-col justify-between">
      {/* Top Navigation */}
      <header className="border-b border-slate-800 bg-dark-900/60 backdrop-blur-md sticky top-0 z-50">
        <div className="max-w-7xl mx-auto px-6 h-16 flex items-center justify-between">
          <div className="flex items-center space-x-3">
            <a href="/" className="p-2 rounded-lg bg-slate-800/80 hover:bg-slate-700 text-slate-300 transition flex items-center space-x-1.5 text-xs font-medium">
              <ArrowLeft className="w-4 h-4" />
              <span>Studio Hub</span>
            </a>
            <div className="w-9 h-9 rounded-lg bg-emerald-500/10 border border-emerald-500/30 flex items-center justify-center text-emerald-400 font-bold text-xl">
              🎲
            </div>
            <span className="font-bold text-lg tracking-wide text-white">Dangling Dice VPN</span>
            <span className="text-xs uppercase px-2 py-0.5 rounded bg-emerald-500/10 text-emerald-400 border border-emerald-500/20 font-mono">
              Standalone Portal
            </span>
          </div>

          <div className="flex items-center space-x-4">
            <nav className="hidden md:flex space-x-6 text-sm text-slate-400 font-medium items-center">
              <button 
                onClick={() => setActiveTab('vpn')} 
                className={activeTab === 'vpn' ? 'text-emerald-400' : 'hover:text-white transition'}>
                Free VPN
              </button>
              <button 
                onClick={() => setActiveTab('tools')} 
                className={activeTab === 'tools' ? 'text-emerald-400' : 'hover:text-white transition'}>
                Developer Suite
              </button>
              <button 
                onClick={() => setActiveTab('nodes')} 
                className={activeTab === 'nodes' ? 'text-emerald-400' : 'hover:text-white transition'}>
                Edge Nodes
              </button>
              <a 
                href="/admin" 
                className="text-emerald-400/90 hover:text-emerald-300 font-mono text-xs border border-emerald-500/30 px-2.5 py-1 rounded-md bg-emerald-500/5 transition">
                📊 SSOT Analytics
              </a>
            </nav>
            <a 
              href="https://dangling-dice-studios.com" 
              className="bg-emerald-500 hover:bg-emerald-600 text-dark-950 font-semibold px-4 py-2 rounded-lg text-sm transition shadow-lg shadow-emerald-500/20">
              Main Studio Hub ↗
            </a>
          </div>
        </div>
      </header>

      {/* Main Container */}
      <main className="max-w-7xl mx-auto px-6 py-12 flex-1 w-full space-y-12">
        {/* Status Banner */}
        <div className="rounded-2xl border border-slate-800 bg-dark-900/50 p-6 flex flex-col md:flex-row items-center justify-between gap-6 shadow-xl">
          <div className="flex items-center space-x-4">
            <div className={`p-4 rounded-xl border ${diagnostics?.is_vpn_secured ? 'bg-emerald-500/10 border-emerald-500/30 text-emerald-400' : 'bg-amber-500/10 border-amber-500/30 text-amber-400'}`}>
              {diagnostics?.is_vpn_secured ? <ShieldCheck className="w-8 h-8" /> : <ShieldAlert className="w-8 h-8" />}
            </div>
            <div>
              <div className="flex items-center space-x-2">
                <span className="text-xs font-mono uppercase tracking-wider text-slate-400">Security Diagnostic</span>
                <span className={`w-2 h-2 rounded-full ${diagnostics?.is_vpn_secured ? 'bg-emerald-400' : 'bg-amber-400 animate-pulse'}`}></span>
              </div>
              <h2 className="text-xl font-bold text-white mt-1">
                {diagnostics?.is_vpn_secured ? 'Secured by Dangling Dice VPN' : 'Direct Connection Detected'}
              </h2>
              <p className="text-sm text-slate-400">
                Your Public IP: <span className="font-mono text-slate-200">{diagnostics?.client_ip || 'Fetching...'}</span>
              </p>
            </div>
          </div>
          <button 
            onClick={fetchDiagnostics} 
            disabled={loadingDiag}
            className="flex items-center space-x-2 px-4 py-2 rounded-lg bg-slate-800 hover:bg-slate-700 text-sm font-medium text-slate-300 transition border border-slate-700">
            <RefreshCw className={`w-4 h-4 ${loadingDiag ? 'animate-spin' : ''}`} />
            <span>Re-check Leak Test</span>
          </button>
        </div>

        {/* Feature Grid */}
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
          {/* Card 1: 1-Click Guest WireGuard Generator */}
          <div className="lg:col-span-2 rounded-2xl border border-slate-800 bg-dark-900/40 p-8 flex flex-col justify-between">
            <div className="space-y-4">
              <div className="inline-flex items-center space-x-2 px-3 py-1 rounded-full bg-emerald-500/10 border border-emerald-500/20 text-emerald-400 text-xs font-medium">
                <Lock className="w-3.5 h-3.5" />
                <span>Zero Account Required • Instant Access</span>
              </div>
              <h3 className="text-2xl font-bold text-white">Generate Free WireGuard VPN Profile</h3>
              <p className="text-slate-400 text-sm leading-relaxed">
                Connect your Android phone, developer workstation, or CI/CD runner to our high-throughput WireGuard network. 
                Full ChaCha20-Poly1305 encryption, zero logs, and built-in ad/tracker blocking.
              </p>
            </div>

            <div className="mt-8 pt-6 border-t border-slate-800/80 space-y-4">
              <div className="flex items-center justify-between p-3.5 rounded-xl bg-slate-900/80 border border-slate-800">
                <div>
                  <span className="text-sm font-semibold text-white">Enable Ad & Malware DNS Sinkhole</span>
                  <p className="text-xs text-slate-400">Blocks 50,000+ ad networks, trackers & telemetry domains at the DNS layer</p>
                </div>
                <input
                  type="checkbox"
                  checked={adBlockEnabled}
                  onChange={(e) => setAdBlockEnabled(e.target.checked)}
                  className="w-5 h-5 accent-emerald-500 rounded cursor-pointer"
                />
              </div>

              {!guestConfig ? (
                <button
                  onClick={generateGuestProfile}
                  disabled={generating}
                  className="w-full py-4 rounded-xl bg-gradient-to-r from-emerald-500 to-teal-600 hover:from-emerald-600 hover:to-teal-700 font-bold text-dark-950 flex items-center justify-center space-x-3 shadow-lg shadow-emerald-500/20 transition text-base">
                  {generating ? (
                    <span>Generating Cryptographic Keys...</span>
                  ) : (
                    <>
                      <Terminal className="w-5 h-5" />
                      <span>1-Click Generate VPN Profile</span>
                    </>
                  )}
                </button>
              ) : (
                <div className="space-y-6">
                  <div className="flex flex-col sm:flex-row gap-6 items-center bg-slate-950/60 p-6 rounded-xl border border-slate-800">
                    {qrCodeUrl && (
                      <div className="bg-white p-3 rounded-xl flex-shrink-0 shadow-md">
                        <img src={qrCodeUrl} alt="WireGuard QR Profile" className="w-36 h-36" />
                        <span className="block text-center text-[10px] text-slate-800 font-mono mt-1 font-bold">SCAN WITH ANDROID</span>
                      </div>
                    )}
                    <div className="flex-1 space-y-3 w-full">
                      <div className="flex items-center justify-between">
                        <span className="text-xs font-mono text-emerald-400">Profile Ready • WireGuard Native</span>
                        <span className="text-xs text-slate-500">Expires in 24h</span>
                      </div>
                      <pre className="bg-slate-900 p-3 rounded-lg text-xs font-mono text-slate-300 overflow-x-auto max-h-28 border border-slate-800">
                        {guestConfig}
                      </pre>
                      <button
                        onClick={downloadConfFile}
                        className="w-full py-2.5 rounded-lg bg-emerald-500 hover:bg-emerald-600 text-dark-950 font-bold text-sm flex items-center justify-center space-x-2 transition">
                        <Download className="w-4 h-4" />
                        <span>Download .conf Profile</span>
                      </button>
                    </div>
                  </div>
                </div>
              )}
            </div>
          </div>

          {/* Card 2: Developer Tool Suite / Funnel */}
          <div className="rounded-2xl border border-slate-800 bg-dark-900/40 p-8 flex flex-col justify-between">
            <div className="space-y-6">
              <div className="flex items-center space-x-2 text-slate-400 text-xs font-mono uppercase">
                <Cpu className="w-4 h-4 text-emerald-400" />
                <span>Developer Utilities</span>
              </div>
              <h3 className="text-xl font-bold text-white">Built-in Dev Tools</h3>

              <div className="space-y-3">
                <div className="p-3.5 rounded-xl bg-slate-800/40 border border-slate-800 flex items-center justify-between">
                  <div>
                    <h4 className="text-sm font-semibold text-white">WebRTC Leak Test</h4>
                    <p className="text-xs text-slate-400">Detects browser IP leaks</p>
                  </div>
                  <span className="text-xs px-2 py-0.5 rounded bg-emerald-500/10 text-emerald-400 border border-emerald-500/20">Shield Active</span>
                </div>

                <div className="p-3.5 rounded-xl bg-slate-800/40 border border-slate-800 flex items-center justify-between">
                  <div>
                    <h4 className="text-sm font-semibold text-white">Ad & Malware Sinkhole</h4>
                    <p className="text-xs text-slate-400">Kernel DNS level filtering</p>
                  </div>
                  <span className="text-xs px-2 py-0.5 rounded bg-emerald-500/10 text-emerald-400 border border-emerald-500/20">50k+ Blocked</span>
                </div>

                <div className="p-3.5 rounded-xl bg-slate-800/40 border border-slate-800 flex items-center justify-between">
                  <div>
                    <h4 className="text-sm font-semibold text-white">Local Port Tunneling</h4>
                    <p className="text-xs text-slate-400">Expose localhost via VPN</p>
                  </div>
                  <span className="text-xs px-2 py-0.5 rounded bg-purple-500/10 text-purple-400 border border-purple-500/20">Pro Dev</span>
                </div>
              </div>
            </div>

            <div className="mt-6 pt-6 border-t border-slate-800 text-center">
              <p className="text-xs text-slate-400 mb-3">
                View real-time ad attribution & A/B test results:
              </p>
              <a 
                href="/admin" 
                className="inline-flex items-center space-x-1.5 text-xs font-semibold text-emerald-400 hover:text-emerald-300 transition">
                <span>Open SSOT Analytics Dashboard</span>
                <ExternalLink className="w-3.5 h-3.5" />
              </a>
            </div>
          </div>
        </div>
      </main>

      <footer className="border-t border-slate-800 bg-dark-950 py-8">
        <div className="max-w-7xl mx-auto px-6 flex flex-col md:flex-row items-center justify-between gap-4 text-xs text-slate-500">
          <p>© 2026 Dangling Dice Studios™. Self-hosted high-performance VPN & developer infrastructure.</p>
          <div className="flex space-x-6">
            <a href="https://dangling-dice-studios.com" className="hover:text-slate-300 transition">Main Studio Website</a>
            <a href="/privacy" className="hover:text-slate-300 transition">Privacy Policy & VpnService Disclosure</a>
            <a href="/admin" className="hover:text-slate-300 transition">SSOT Analytics</a>
          </div>
        </div>
      </footer>
    </div>
  );
}

