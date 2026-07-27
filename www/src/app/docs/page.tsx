import type { Metadata } from "next";

import { DocsExplorer } from "@/components/docs-explorer";
import { BreadcrumbJsonLd } from "@/components/json-ld";
import { CtaSection, PageHero, SectionHeading } from "@/components/ui";
import { docs } from "@/data/docs";
import { pageMetadata } from "@/lib/metadata";

export const metadata: Metadata = pageMetadata({
  title: "Documentation",
  description:
    "CineWolf AutoDirector documentation for installation, first shots, target selection, Orbit, Follow, Flyby, Dolly, Reveal, Crane, Spiral, vehicle profiles, collision avoidance, and Generate Montage.",
  path: "/docs",
});

export default function DocumentationPage() {
  return (
    <div className="page">
      <BreadcrumbJsonLd items={[{ name: "Home", path: "/" }, { name: "Documentation", path: "/docs" }]} />
      <PageHero
        description="Use the search and navigation to move from installation to a first editable shot, then through every current camera and montage workflow."
        title="Documentation for the edit room"
      />
      <section className="site-section">
        <SectionHeading
          description="Documentation content is structured in src/data/docs.ts so it can grow alongside the mod without turning the route component into a large content file."
          label="CineWolf manual"
          title="Start with a shot. Keep the controls visible."
        />
        <DocsExplorer entries={docs} />
      </section>
      <CtaSection
        description="Verify your Fabric and Flashback versions, then create a first single shot before you build a full montage."
        title="Open the replay with a plan"
      />
    </div>
  );
}
