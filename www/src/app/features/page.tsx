import type { Metadata } from "next";

import { CameraPathDiagram } from "@/components/camera-path-diagram";
import { FeatureCard, ScreenshotSlot } from "@/components/content-cards";
import { FeatureGlyph } from "@/components/feature-glyph";
import { BreadcrumbJsonLd } from "@/components/json-ld";
import { CtaSection, PageHero, SectionHeading } from "@/components/ui";
import { features } from "@/data/features";
import { pageMetadata } from "@/lib/metadata";

export const metadata: Metadata = pageMetadata({
  title: "Features",
  description:
    "Explore CineWolf AutoDirector’s camera path generation, collision-aware preview, editable Flashback keyframes, montage planning, vehicle profiles, and diagnostics.",
  path: "/features",
});

export default function FeaturesPage() {
  return (
    <div className="page">
      <BreadcrumbJsonLd items={[{ name: "Home", path: "/" }, { name: "Features", path: "/features" }]} />
      <PageHero
        description="A technical camera system should be clear about what it generates, what it checks, and what remains under your control in the replay editor."
        title="Plan the path. Keep the edit."
      />
      <section className="site-section">
        <SectionHeading
          description="Every feature is rooted in local replay sampling, explicit framing controls, and an output that remains editable after CineWolf finishes its work."
          label="Feature system"
          title="Built for controlled cinematic work"
        />
        <div className="feature-grid feature-grid-expanded">
          {features.map((feature) => (
            <FeatureCard
              description={feature.description}
              detail={feature.detail}
              example={feature.example}
              icon={<FeatureGlyph name={feature.icon} />}
              key={feature.title}
              title={feature.title}
            />
          ))}
        </div>
      </section>
      <section className="site-section">
        <div className="split-layout">
          <div>
            <SectionHeading
              description="The camera path visualiser makes the technical decision visible: target frame, camera positions, path geometry, timing, and any warnings are reviewed before a native timeline mutation."
              label="Review first"
              title="Preview is part of the workflow"
            />
            <ScreenshotSlot
              description="Add an approved in-editor path screenshot here when production imagery is available. Until then, this labelled slot keeps the site honest about what is visualised."
              title="Path preview screenshot"
              variant="tracking"
            />
          </div>
          <CameraPathDiagram label="A camera path diagram with target and timeline" variant="tracking" />
        </div>
      </section>
      <CtaSection
        description="Use the documentation to start with a single shot, then move from preview to editable timeline keys at your own pace."
        title="Make the replay camera deliberate"
      />
    </div>
  );
}
