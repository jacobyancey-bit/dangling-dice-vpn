'use client';

import React from 'react';
import { ShieldCheck, FileText, Scale, AlertTriangle, CreditCard, ArrowLeft } from 'lucide-react';

export default function TermsOfServicePage() {
  return (
    <div className="min-h-screen bg-slate-950 text-slate-200 font-sans">
      {/* Header */}
      <header className="border-b border-slate-800 bg-slate-900/60 backdrop-blur sticky top-0 z-50">
        <div className="max-w-4xl mx-auto px-6 py-4 flex items-center justify-between">
          <div className="flex items-center space-x-3">
            <span className="text-2xl">🎲</span>
            <div>
              <h1 className="text-lg font-bold text-white">Dangling Dice VPN — Terms of Service</h1>
              <p className="text-xs text-emerald-400 font-mono">Effective Date: November 2026 &bull; Dangling Dice Studios™</p>
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
        <section className="rounded-2xl border border-emerald-500/30 bg-emerald-500/10 p-6 space-y-3">
          <div className="flex items-center space-x-2 text-emerald-400 font-bold text-base">
            <Scale className="w-6 h-6" />
            <h2>1. Acceptance of Terms</h2>
          </div>
          <p className="text-sm text-slate-200 leading-relaxed">
            By downloading, installing, or using <strong>Dangling Dice VPN</strong> (the &ldquo;Service&rdquo;), provided by <strong>Dangling Dice Studios™</strong>, you agree to be bound by these Terms of Service and our <a href="/privacy" className="text-emerald-300 underline">Privacy Policy</a>. If you do not agree to these terms, do not use the Service.
          </p>
        </section>

        <section className="rounded-2xl border border-slate-800 bg-slate-900/50 p-8 space-y-6">
          <div className="space-y-2">
            <h3 className="text-lg font-bold text-white flex items-center gap-2">
              <ShieldCheck className="w-5 h-5 text-emerald-400" />
              <span>2. Description of Service</span>
            </h3>
            <p className="text-sm text-slate-400 leading-relaxed">
              Dangling Dice VPN provides an encrypted virtual private network (WireGuard®) tunnel designed to enhance network privacy, secure public Wi-Fi connections, and offer optional DNS-level ad and malware filtering. Because we operate a strict zero-log, zero-login architecture, we do not inspect, store, or sell your browsing traffic.
            </p>
          </div>

          <div className="space-y-2 pt-4 border-t border-slate-800">
            <h3 className="text-lg font-bold text-white flex items-center gap-2">
              <AlertTriangle className="w-5 h-5 text-amber-400" />
              <span>3. Acceptable Use Policy</span>
            </h3>
            <p className="text-sm text-slate-400 leading-relaxed">
              You agree to use Dangling Dice VPN solely for lawful purposes. You strictly agree <strong>not</strong> to use the Service to:
            </p>
            <ul className="list-disc pl-5 text-sm text-slate-400 space-y-1.5">
              <li>Engage in any unlawful, fraudulent, or malicious activity under applicable local, national, or international law.</li>
              <li>Conduct cyberattacks, denial-of-service (DDoS) attacks, unauthorized network scanning, or distribute malware.</li>
              <li>Send unsolicited bulk spam or attempt to compromise, overload, or disrupt our VPN gateway infrastructure.</li>
              <li>Violate intellectual property rights or third-party terms of service.</li>
            </ul>
            <p className="text-sm text-slate-400 leading-relaxed">
              We reserve the right to throttle, rate-limit, or terminate connections that abuse server capacity or threaten network stability.
            </p>
          </div>

          <div className="space-y-2 pt-4 border-t border-slate-800">
            <h3 className="text-lg font-bold text-white flex items-center gap-2">
              <CreditCard className="w-5 h-5 text-emerald-400" />
              <span>4. Free Tier, Ads &amp; Premium Pro Access</span>
            </h3>
            <p className="text-sm text-slate-400 leading-relaxed">
              Dangling Dice VPN offers a free tier supported by non-intrusive in-app banners and an optional Premium Pro tier that removes ads and unlocks additional features and server locations. Subscriptions and in-app purchases made through Google Play are billed and managed through your Google Play account and may be canceled at any time in your Google Play subscription settings in accordance with Google Play&apos;s billing and refund policies.
            </p>
          </div>

          <div className="space-y-2 pt-4 border-t border-slate-800">
            <h3 className="text-lg font-bold text-white flex items-center gap-2">
              <FileText className="w-5 h-5 text-emerald-400" />
              <span>5. Disclaimer of Warranties &amp; Limitation of Liability</span>
            </h3>
            <p className="text-sm text-slate-400 leading-relaxed">
              The Service is provided on an &ldquo;AS IS&rdquo; and &ldquo;AS AVAILABLE&rdquo; basis without warranties of any kind, whether express or implied. While we strive for high uptime and fast encrypted speeds, actual VPN throughput and availability depend on your internet service provider, network congestion, and regional routing. To the maximum extent permitted by law, Dangling Dice Studios™ shall not be liable for any indirect, incidental, or consequential damages arising from your use of or inability to use the Service.
            </p>
          </div>

          <div className="space-y-2 pt-4 border-t border-slate-800">
            <h3 className="text-lg font-bold text-white">6. Changes to These Terms &amp; Contact</h3>
            <p className="text-sm text-slate-400 leading-relaxed">
              We may update these Terms of Service from time to time. Continued use of Dangling Dice VPN after updates take effect constitutes acceptance of the revised terms. For support or legal inquiries, visit <a href="https://dangling-dice-studios.com" className="text-emerald-400 underline">dangling-dice-studios.com</a>.
            </p>
          </div>
        </section>
      </main>
    </div>
  );
}
