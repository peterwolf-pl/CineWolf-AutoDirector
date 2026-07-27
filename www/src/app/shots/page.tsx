import type { Metadata } from "next";

import { BreadcrumbJsonLd } from "@/components/json-ld";
import { ShotCatalogue } from "@/components/shot-catalogue";
import { CtaSection, PageHero, SectionHeading } from "@/components/ui";
import { shots } from "@/data/shots";
import { pageMetadata } from "@/lib/metadata";

export const metadata: Metadata = pageMetadata({
  title: "Camera Shots",
  description:
    "Browse all CineWolf AutoDirector camera generators: Orbit, Follow, Flyby, Dolly, Reveal, Crane, Spiral, Tracking, Chase, Close Detail, and Vehicle Profile.",
  path: "/shots",
});

export default function ShotsPage() {
  return (
    <div className="page">
      <BreadcrumbJsonLd items={[{ name: "Home", path: "/" }, { name: "Shots", path: "/shots" }]} />
      <PageHero
        description="Filter every current generator by the kind of composition you need, then inspect its camera behaviour, target fit, primary controls, collision handling, and montage role."
        title="Camera shots, ready to direct"
      />
      <section className="site-section">
        <SectionHeading
          description="A shot is not a preset icon. It is a defined camera behaviour that you can preview, adjust, and keep editable in Flashback."
          label="Shot catalogue"
          title="Choose the move that tells the story"
        />
        <ShotCatalogue shots={shots} />
      </section>
      <CtaSection
        description="Start with one generated shot, inspect its path, then let Generate Montage build a sequence from the same library."
        title="Make every move intentional"
      />
    </div>
  );
}
