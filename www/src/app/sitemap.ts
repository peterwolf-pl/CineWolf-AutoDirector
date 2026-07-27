import type { MetadataRoute } from "next";

import { absoluteUrl } from "@/lib/site";

// Required for `output: "export"` metadata routes in Next.js 16.
export const dynamic = "force-static";

const routes = [
  "",
  "/features",
  "/shots",
  "/montage",
  "/vehicles",
  "/flashback",
  "/download",
  "/docs",
  "/roadmap",
  "/changelog",
  "/faq",
  "/privacy",
];

export default function sitemap(): MetadataRoute.Sitemap {
  return routes.map((path) => ({
    url: absoluteUrl(path || "/"),
    lastModified: new Date("2026-07-24"),
    changeFrequency: path === "" ? "weekly" : "monthly",
    priority: path === "" ? 1 : 0.7,
  }));
}
