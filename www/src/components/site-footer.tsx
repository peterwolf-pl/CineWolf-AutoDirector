import Image from "next/image";
import Link from "next/link";

import { ExternalIcon } from "@/components/icons";
import { footerNavigation, siteConfig } from "@/lib/site";

export function SiteFooter() {
  const externalLinks = [
    { href: siteConfig.modrinthUrl, label: "Modrinth" },
    { href: siteConfig.githubUrl, label: "GitHub" },
    { href: siteConfig.supportUrl, label: "Support" },
  ].filter((link): link is { href: string; label: string } => Boolean(link.href));

  return (
    <footer className="site-footer">
      <div className="site-shell footer-grid">
        <div className="footer-brand">
          <Image
            alt="CineWolf logo"
            height={46}
            src="/images/brand/cinewolf-logo.png"
            width={46}
          />
          <div>
            <strong>CineWolf AutoDirector</strong>
            <p>Built for cinematic Minecraft creators using Flashback.</p>
          </div>
        </div>
        <nav aria-label="Footer navigation" className="footer-navigation">
          {footerNavigation.map((item) => (
            <Link href={item.href} key={item.href}>
              {item.label}
            </Link>
          ))}
        </nav>
        {externalLinks.length > 0 ? (
          <nav aria-label="External links" className="footer-external-links">
            {externalLinks.map((link) => (
              <a href={link.href} key={link.label} rel="noreferrer" target="_blank">
                {link.label} <ExternalIcon size={14} />
              </a>
            ))}
          </nav>
        ) : null}
      </div>
      <div className="site-shell footer-bottom">
        <span>Created by PeterWolf</span>
        <span>© {new Date().getFullYear()} CineWolf AutoDirector</span>
      </div>
    </footer>
  );
}
