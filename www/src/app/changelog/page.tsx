import type { Metadata } from "next";

import { ChangelogEntry } from "@/components/content-cards";
import { BreadcrumbJsonLd, JsonLd } from "@/components/json-ld";
import { CtaSection, PageHero, SectionHeading } from "@/components/ui";
import { changelog } from "@/data/changelog";
import { pageMetadata } from "@/lib/metadata";
import { absoluteUrl, siteConfig } from "@/lib/site";

export const metadata: Metadata = pageMetadata({
  title: "Changelog",
  description:
    "Read verified CineWolf AutoDirector release notes for camera shots, Generate Montage, collision handling, Flashback compatibility, replay sampling, and camera stability fixes.",
  path: "/changelog",
});

export default function ChangelogPage() {
  const articleData = changelog.map((entry) => ({
    "@context": "https://schema.org",
    "@type": "Article",
    headline: `CineWolf AutoDirector ${entry.version} Changelog`,
    datePublished: entry.date,
    dateModified: entry.date,
    author: { "@type": "Person", name: siteConfig.author },
    publisher: { "@type": "Person", name: siteConfig.author },
    mainEntityOfPage: absoluteUrl(`/changelog#version-${entry.version.replaceAll(".", "-")}`),
    description: entry.groups.flatMap((group) => group.items).slice(0, 2).join(" "),
  }));

  return (
    <div className="page">
      <JsonLd data={articleData} />
      <BreadcrumbJsonLd items={[{ name: "Home", path: "/" }, { name: "Changelog", path: "/changelog" }]} />
      <PageHero
        description="Release notes are the source of truth for what changed. They preserve the technical detail behind compatibility, camera stability, collision handling, montage planning, and output safeguards."
        title="Changelog, without the fog"
      />
      <section className="site-section">
        <SectionHeading
          description="Entries are grouped by version and the categories Added, Changed, Fixed, Performance, Compatibility, and Known Issues. The data lives in src/data/changelog.ts."
          label="Release history"
          title="Every change has a category"
        />
        <div className="changelog-list">
          {changelog.map((entry) => <ChangelogEntry {...entry} key={entry.version} />)}
        </div>
      </section>
      <CtaSection
        description="Check the tested version details before adding CineWolf to an existing Flashback replay workflow."
        title="Use the release notes to plan the update"
      />
    </div>
  );
}
