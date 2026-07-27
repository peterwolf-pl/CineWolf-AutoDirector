import type { Metadata } from "next";

import { RoadmapTimeline } from "@/components/content-cards";
import { BreadcrumbJsonLd } from "@/components/json-ld";
import { Callout, CtaSection, PageHero, SectionHeading } from "@/components/ui";
import { roadmap } from "@/data/roadmap";
import { pageMetadata } from "@/lib/metadata";

export const metadata: Metadata = pageMetadata({
  title: "Roadmap",
  description:
    "Review CineWolf AutoDirector’s released cinematic camera work and clearly labelled planned development for Flashback replay creators.",
  path: "/roadmap",
});

export default function RoadmapPage() {
  const entries = roadmap.map((entry) => ({ ...entry, limitations: entry.limitations.join(" ") }));

  return (
    <div className="page">
      <BreadcrumbJsonLd items={[{ name: "Home", path: "/" }, { name: "Roadmap", path: "/roadmap" }]} />
      <PageHero
        description="The roadmap separates shipped work from experimental history and planned directions. A status label is never used as a substitute for a real release note."
        title="A cinematic workflow, built in public"
      />
      <section className="site-section">
        <Callout title="Status labels" tone="success">
          <p>Released means the feature is present in a confirmed release. Experimental records historical work. Planned is direction, not a delivery date or compatibility claim.</p>
        </Callout>
      </section>
      <section className="site-section site-section-tight">
        <SectionHeading
          description="Roadmap data is kept in src/data/roadmap.ts so version, status, technical focus, and known limitations can be updated independently of the visual timeline."
          label="Version timeline"
          title="Released foundations and deliberate next steps"
        />
        <RoadmapTimeline entries={entries} />
      </section>
      <CtaSection
        description="Use the release notes for confirmed details, and treat planned work as a direction rather than an installed feature."
        title="Build on what is proven"
      />
    </div>
  );
}
