import type { Metadata } from "next";

import { DownloadCard } from "@/components/download-card";
import { BreadcrumbJsonLd } from "@/components/json-ld";
import { Callout, CtaSection, PageHero, SectionHeading } from "@/components/ui";
import { compatibility } from "@/data/compatibility";
import { pageMetadata } from "@/lib/metadata";

export const metadata: Metadata = pageMetadata({
  title: "Download",
  description:
    "Download CineWolf AutoDirector from Modrinth, check the tested Minecraft Fabric and Flashback versions, and follow the installation steps.",
  path: "/download",
});

const installationSteps = [
  "Install Fabric Loader",
  "Install Fabric API if required",
  "Install Flashback",
  "Download CineWolf AutoDirector",
  "Place the mod file in the Minecraft mods folder",
  "Start Minecraft",
  "Open or create a Flashback replay",
  "Open the CineWolf panel inside the replay editor",
];

export default function DownloadPage() {
  return (
    <div className="page">
      <BreadcrumbJsonLd items={[{ name: "Home", path: "/" }, { name: "Download", path: "/download" }]} />
      <PageHero
        description="Confirm the tested client stack, then install CineWolf alongside Flashback. Grab the release from Modrinth and follow the steps below."
        title="Download CineWolf with confidence"
      />
      <section className="site-section">
        <div className="download-layout">
          <DownloadCard detailed />
          <div>
            <SectionHeading
              description="Follow the complete client-side installation path. Flashback is required and remains a separate download."
              label="Installation"
              title="Set up the replay editor stack"
            />
            <ol className="installation-list">
              {installationSteps.map((step) => <li key={step}>{step}</li>)}
            </ol>
          </div>
        </div>
      </section>
      <section className="site-section">
        <Callout title="Compatibility warning" tone="warning">
          <p>CineWolf {compatibility.version} enables its Flashback integration only for Flashback {compatibility.flashbackVersion}. Install the tested Minecraft, Fabric Loader, and Fabric API combination before opening a replay project.</p>
        </Callout>
      </section>
      <CtaSection
        description="Prefer Modrinth for the latest JAR, then review the changelog and installation docs before opening your next Flashback replay."
        title="Prepare your next replay project"
      />
    </div>
  );
}
