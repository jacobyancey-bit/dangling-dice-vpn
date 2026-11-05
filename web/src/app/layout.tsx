import './globals.css';
import type { Metadata } from 'next';
import { Inter } from 'next/font/google';

const inter = Inter({ subsets: ['latin'] });

export const metadata: Metadata = {
  title: 'Dangling Dice | High-Performance Free VPN & Developer Tools',
  description: 'Enterprise-grade, zero-log WireGuard VPN and security suite built directly for developers.',
};

export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <html lang="en">
      <body className={`${inter.className} bg-dark-950 text-slate-100 antialiased`}>
        {children}
      </body>
    </html>
  );
}

