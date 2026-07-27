import Image from "next/image";
import Link from "next/link";

import { DownloadIcon } from "@/components/icons";
import { MobileNavigation } from "@/components/mobile-navigation";
import { navigation, siteConfig } from "@/lib/site";

export function SiteHeader() {
  return (
    <header className="site-header">
      <div className="site-shell header-inner">
        <Link aria-label="CineWolf AutoDirector home" className="brand" href="/">
          <Image
            alt="CineWolf silver wolf aperture logo"
            className="brand-mark"
            height={46}
            priority
            src="/images/brand/cinewolf-logo.png"
            width={46}
          />
          <span className="brand-copy">
            <strong>Cine<span>Wolf</span></strong>
            <small>AutoDirector</small>
          </span>
        </Link>
        <nav aria-label="Primary navigation" className="desktop-navigation">
          {navigation.map((item) => (
            <Link href={item.href} key={item.href}>
              {item.label}
            </Link>
          ))}
        </nav>
        <Link className="header-download" href="/download">
          <DownloadIcon size={16} />
          <span>Download {siteConfig.shortName}</span>
        </Link>
        <MobileNavigation />
      </div>
    </header>
  );
}
