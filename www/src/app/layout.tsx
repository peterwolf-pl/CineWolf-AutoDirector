import type { Metadata, Viewport } from "next";

import { PageMotion } from "@/components/page-motion";
import { SiteFooter } from "@/components/site-footer";
import { SiteHeader } from "@/components/site-header";
import { siteConfig } from "@/lib/site";

import "./globals.css";

export const metadata: Metadata = {
  metadataBase: new URL(siteConfig.url),
  title: {
    default: "CineWolf AutoDirector - Cinematic Camera Mod for Minecraft Flashback",
    template: "%s | CineWolf AutoDirector",
  },
  description: siteConfig.description,
  applicationName: siteConfig.name,
  keywords: [
    "Minecraft cinematic camera mod",
    "Minecraft replay camera",
    "Flashback camera mod",
    "Minecraft camera path generator",
    "Minecraft cinematic replay",
    "Minecraft montage generator",
    "Minecraft tracking camera",
    "Minecraft orbit camera",
    "Minecraft vehicle camera",
    "Minecraft replay editor tool",
  ],
  authors: [{ name: siteConfig.author }],
  creator: siteConfig.author,
  publisher: siteConfig.author,
  robots: { index: true, follow: true },
  icons: {
    icon: "/images/brand/cinewolf-logo.png",
    apple: "/images/brand/cinewolf-logo.png",
  },
};

export const viewport: Viewport = {
  themeColor: "#070808",
  colorScheme: "dark",
};

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="en">
      <body>
        <a className="skip-link" href="#main-content">Skip to content</a>
        <SiteHeader />
        <main className="main-content" id="main-content">{children}</main>
        <SiteFooter />
        <PageMotion />
      </body>
    </html>
  );
}
