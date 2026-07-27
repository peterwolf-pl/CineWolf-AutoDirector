import type { Metadata } from "next";

import { BreadcrumbJsonLd } from "@/components/json-ld";
import { Callout, CtaSection, PageHero, SectionHeading } from "@/components/ui";
import { pageMetadata } from "@/lib/metadata";

export const metadata: Metadata = pageMetadata({
  title: "Privacy",
  description:
    "CineWolf AutoDirector processes replay data locally, does not require cloud processing or replay uploads, and keeps local configuration, projects, and presets on the creator’s computer.",
  path: "/privacy",
});

const privacyItems = [
  ["Local replay processing", "CineWolf analyses replay samples on the local Minecraft client using deterministic rules."],
  ["No replay uploads", "Replay data is not uploaded to a CineWolf service as part of local functionality."],
  ["No account required", "The local camera and montage workflow does not require an account or subscription."],
  ["No cloud AI", "CineWolf does not use a cloud AI service or AI API to plan a local replay."],
  ["No external analytics by default", "The site and local mod workflow do not assume a third-party analytics platform is configured."],
  ["Local storage", "Configuration, local montage work, and local presets stay on the creator’s computer unless the creator chooses another backup workflow."],
];

export default function PrivacyPage() {
  return (
    <div className="page">
      <BreadcrumbJsonLd items={[{ name: "Home", path: "/" }, { name: "Privacy", path: "/privacy" }]} />
      <PageHero
        description="CineWolf is designed for local replay work. The product keeps the camera plan, preview, configuration, and editing workflow close to the Minecraft client."
        title="Your replay stays on your computer"
      />
      <section className="site-section">
        <SectionHeading
          description="These statements describe the current local functionality. A future optional analytics integration would be named here before it is enabled."
          label="Privacy commitments"
          title="Local by design"
        />
        <div className="privacy-grid">
          {privacyItems.map(([title, description]) => (
            <article key={title}><h2>{title}</h2><p>{description}</p></article>
          ))}
        </div>
      </section>
      <section className="site-section">
        <Callout title="Future analytics configuration" tone="warning">
          <p>No analytics platform is configured by default. If one is added later, list the provider, purpose, data categories, opt-out method, and retention policy here before enabling it.</p>
        </Callout>
      </section>
      <CtaSection
        description="Create, preview, and edit a cinematic replay plan without making cloud processing a requirement."
        title="Keep control of the footage"
      />
    </div>
  );
}
