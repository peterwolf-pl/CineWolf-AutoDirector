import type { MetadataRoute } from "next";

import { absoluteUrl } from "@/lib/site";

// Required for `output: "export"` metadata routes in Next.js 16.
export const dynamic = "force-static";

export default function robots(): MetadataRoute.Robots {
  return {
    rules: { userAgent: "*", allow: "/" },
    sitemap: absoluteUrl("/sitemap.xml"),
  };
}
