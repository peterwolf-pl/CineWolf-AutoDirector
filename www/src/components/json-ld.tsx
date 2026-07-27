import { absoluteUrl, siteConfig } from "@/lib/site";

type JsonLdProps = {
  data: Record<string, unknown> | Array<Record<string, unknown>>;
};

export function JsonLd({ data }: JsonLdProps) {
  return (
    <script
      dangerouslySetInnerHTML={{
        __html: JSON.stringify(data).replace(/</g, "\\u003c"),
      }}
      type="application/ld+json"
    />
  );
}

export function BreadcrumbJsonLd({
  items,
}: {
  items: Array<{ name: string; path: string }>;
}) {
  return (
    <JsonLd
      data={{
        "@context": "https://schema.org",
        "@type": "BreadcrumbList",
        itemListElement: items.map((item, index) => ({
          "@type": "ListItem",
          position: index + 1,
          name: item.name,
          item: absoluteUrl(item.path),
        })),
      }}
    />
  );
}

export function SoftwareApplicationJsonLd() {
  return (
    <JsonLd
      data={{
        "@context": "https://schema.org",
        "@type": "SoftwareApplication",
        name: siteConfig.name,
        description: siteConfig.description,
        applicationCategory: "MultimediaApplication",
        operatingSystem: "Minecraft Java Edition on Fabric",
        softwareVersion: siteConfig.version,
        author: { "@type": "Person", name: siteConfig.author },
        applicationSubCategory: "Minecraft cinematic replay camera tool",
        softwareRequirements: [
          `Minecraft ${siteConfig.minecraftVersion}`,
          "Fabric Loader 0.19.3 or newer",
          "Fabric API 0.153.0+26.2",
          `Flashback ${siteConfig.flashbackVersion}`,
        ],
        ...(siteConfig.downloadUrl ? { downloadUrl: siteConfig.downloadUrl } : {}),
      }}
    />
  );
}
