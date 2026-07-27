import type { Metadata } from "next";

import { absoluteUrl, siteConfig } from "@/lib/site";

type PageMetadata = {
  title: string;
  description: string;
  path: string;
};

export function pageMetadata({
  title,
  description,
  path,
}: PageMetadata): Metadata {
  const url = absoluteUrl(path);
  const fullTitle =
    path === "/"
      ? "CineWolf AutoDirector - Cinematic Camera Mod for Minecraft Flashback"
      : `${title} | CineWolf AutoDirector`;

  return {
    title: path === "/" ? fullTitle : title,
    description,
    alternates: { canonical: url },
    openGraph: {
      type: "website",
      url,
      title: fullTitle,
      description,
      siteName: siteConfig.name,
      images: [
        {
          url: absoluteUrl("/images/brand/cinewolf-banner.png"),
          width: 2172,
          height: 724,
          alt: "CineWolf AutoDirector cinematic camera paths in Minecraft",
        },
      ],
    },
    twitter: {
      card: "summary_large_image",
      title: fullTitle,
      description,
      images: [absoluteUrl("/images/brand/cinewolf-banner.png")],
    },
  };
}
