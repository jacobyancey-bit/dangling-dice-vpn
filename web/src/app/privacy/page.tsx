'use client';

import React from 'react';
import { ShieldCheck, Lock, EyeOff, Server, FileText, ArrowLeft } from 'lucide-react';

export default function PrivacyPolicyPage() {
  return (
    <div className="min-h-screen bg-slate-950 text-slate-200 font-sans">
      {/* Header */}
      <header className="border-b border-slate-800 bg-slate-900/60 backdrop-blur sticky top-0 z-50">
        <div className="max-w-4xl mx-auto px-6 py-4 flex items-center justify-between">
          <div className="flex items-center space-x-3">
            <span className="text-2xl">🎲</span>
            <div>
              <h1 className="text-lg font-bold text-white">Dangling Dice VPN — Privacy Policy</h1>
              <p className="text-xs text-emerald-400 font-mono">Google Play VpnService Disclosure & Zero-Log Pledge</p>
            </div>
          </div>
          <a
            href="/"
            className="inline-flex items-center space-x-2 px-4 py-2 rounded-lg bg-slate-800 hover:bg-slate-700 text-sm font-medium text-white transition border border-slate-700"
          >
            <ArrowLeft className="w-4 h-4" />
            <span>Back to VPN Portal</span>
          </a>
        </div>
      </header>

      {/* Content */}
      <main className="max-w-4xl mx-auto px-6 py-12 space-y-8">
        {/* Prominent VpnService Disclosure Banner (Required by Google Play Policy) */}
        <section className="rounded-2xl border border-emerald-500/30 bg-emerald-500/10 p-6 space-y-3">
          <div className="flex items-center space-x-2 text-emerald-400 font-bold text-base">
            <ShieldCheck className="w-6 h-6" />
            <h2>Prominent Disclosure: Android VpnService Usage</h2>
          </div>
          <p className="text-sm text-slate-200 leading-relaxed">
            <strong>Dangling Dice VPN</strong> uses the official Android <code className="text-emerald-300">android.net.VpnService</code> API as its core functionality to establish an encrypted, device-level <strong>WireGuard® (ChaCha20-Poly1305)</strong> virtual private network tunnel between your Android device and our dedicated VPN gateway servers. We do <strong>not</strong> redirect, manipulate, or monetize user traffic from other apps on your device, and we never collect or sell your browsing history or DNS queries.
          </p>
        </section>

        {/* Zero-Login & Zero-Log Architecture */}
        <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
          <div className="rounded-2xl border border-slate-800 bg-slate-900/50 p-6 space-y-3">
            <div className="flex items-center space-x-2 text-white font-bold">
              <Lock className="w-5 h-5 text-emerald-400" />
              <h3>1. Zero-Login Cryptographic Identity</h3>
            </div>
            <p className="text-sm text-slate-400 leading-relaxed">
              We do not require your name, email address, phone number, or password to use Dangling Dice VPN. Your device generates a local 256-bit Curve25519 keypair inside Android private storage. Only your public cryptographic key is registered with the WireGuard gateway to route encrypted packets.
            </p>
          </div>

          <div className="rounded-2xl border border-slate-800 bg-slate-900/50 p-6 space-y-3">
            <div className="flex items-center space-x-2 text-white font-bold">
              <EyeOff className="w-5 h-5 text-emerald-400" />
              <h3>2. Strict Zero-Activity-Log Policy</h3>
            </div>
            <p className="text-sm text-slate-400 leading-relaxed">
              We never log, inspect, store, or share the websites you visit, the applications you use, your packet contents, or your DNS queries. Optional Ad &amp; Malware blocking is performed via encrypted sinkhole DNS (<code className="text-slate-300">94.140.14.14</code>) without tying queries to any personal identity.
            </p>
          </div>
        </div>

        {/* Detailed Sections */}
        <section className="rounded-2xl border border-slate-800 bg-slate-900/40 p-8 space-y-6">
          <div className="space-y-2">
            <h3 className="text-lg font-bold text-white flex items-center gap-2">
              <Server className="w-5 h-5 text-emerald-400" />
              <span>3. Minimal Technical Data Processed</span>
            </h3>
            <p className="text-sm text-slate-400 leading-relaxed">
              To maintain an active WireGuard tunnel and prevent automated bot abuse, our control plane processes only:
            </p>
            <ul className="list-disc list-inside text-sm text-slate-300 space-y-1.5 pt-1">
              <li><strong>Curve25519 Public Key &amp; Internal Tunnel IP (<code className="text-emerald-400">10.8.0.x</code>):</strong> Required by the Linux kernel WireGuard module to route encrypted return packets to your device.</li>
              <li><strong>Ephemeral Aggregate Transfer Counters:</strong> Real-time RX/TX byte counters displayed on your device screen during an active session.</li>
              <li><strong>Split-Tunneling Package Selection (<code className="text-emerald-400">QUERY_ALL_PACKAGES</code>):</strong> Evaluated strictly locally on your Android device so you can choose which installed apps bypass the VPN tunnel. Installed app lists are never transmitted off your device.</li>
            </ul>
          </div>

          <div className="space-y-2 pt-4 border-t border-slate-800">
            <h3 className="text-lg font-bold text-white flex items-center gap-2">
              <FileText className="w-5 h-5 text-emerald-400" />
              <span>4. Contact &amp; Data Deletion Requests</span>
            </h3>
            <p className="text-sm text-slate-400 leading-relaxed">
              Because Dangling Dice VPN uses anonymous Zero-Login cryptographic keys, you can reset your identity at any time by clearing the app storage or uninstalling the application on your Android device. For questions regarding privacy or security, contact <strong>Dangling Dice Studios™</strong> via our official hub at <a href="https://danglingdicestudios.duckdns.org" className="text-emerald-400 underline">danglingdicestudios.duckdns.org</a>.
            </p>
          </div>
        </section>
      </main>
    </div>
  );
}
