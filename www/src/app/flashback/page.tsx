import type { Metadata } from "next";

import { ScreenshotSlot } from "@/components/content-cards";
import { BreadcrumbJsonLd } from "@/components/json-ld";
import { Callout, CtaSection, PageHero, SectionHeading } from "@/components/ui";
import { compatibility } from "@/data/compatibility";
import { pageMetadata } from "@/lib/metadata";

export const metadata: Metadata = pageMetadata({
  title: "Flashback Integration",
  description:
    "Read CineWolf AutoDirector’s tested Flashback, Minecraft, Fabric Loader, and Fabric API requirements, editable keyframe support, timeline safeguards, and compatibility warnings.",
  path: "/flashback",
});

export default function FlashbackPage() {
  return (
    <div className="page">
      <BreadcrumbJsonLd items={[{ name: "Home", path: "/" }, { name: "Flashback", path: "/flashback" }]} />
      <PageHero
        description="CineWolf is designed around Flashback’s editable replay timeline. The integration is version-gated, preview-first, and explicit about what it can safely write."
        title="Designed for Flashback"
      />
      <section className="site-section">
        <SectionHeading
          description="The tested stack lives in one compatibility file so a future release can update requirements without scattering support claims across the site."
          label="Tested compatibility"
          title="Install the exact supported editor stack"
        />
        <div className="technical-table-wrap">
          <table className="technical-table">
            <thead><tr><th scope="col">Requirement</th><th scope="col">Tested baseline</th><th scope="col">Why it matters</th></tr></thead>
            <tbody>
              <tr><th scope="row">CineWolf AutoDirector</th><td>{compatibility.version}</td><td>Current local release baseline.</td></tr>
              <tr><th scope="row">Minecraft</th><td>{compatibility.minecraftVersion}</td><td>Replay and Fabric runtime target.</td></tr>
              <tr><th scope="row">Fabric Loader</th><td>{compatibility.fabricLoader}</td><td>Client-side Fabric loading requirement.</td></tr>
              <tr><th scope="row">Fabric API</th><td>{compatibility.fabricApi}</td><td>Required Fabric API baseline.</td></tr>
              <tr><th scope="row">Flashback</th><td>Exactly {compatibility.flashbackVersion}</td><td>Required editor integration. Install separately; it is not bundled.</td></tr>
            </tbody>
          </table>
        </div>
      </section>
      <section className="site-section">
        <div className="split-layout">
          <div>
            <SectionHeading
              description="Generated output remains native to the replay editor. CineWolf plans and validates the camera work, then writes only the supported keyframe types after you confirm the operation."
              label="Timeline support"
              title="Editable keys, safe writes"
            />
            <div className="keyframe-list">
              {compatibility.keyframes.map((keyframe) => <div key={keyframe}><span>Keyframe</span><strong>{keyframe}</strong></div>)}
              <div><span>Preview</span><strong>Non-destructive, before write</strong></div>
              <div><span>Conflicts</span><strong>Checked over the affected interval</strong></div>
              <div><span>Undo</span><strong>One guarded logical operation</strong></div>
            </div>
          </div>
          <ScreenshotSlot
            description="Replace this slot with an approved Flashback timeline screenshot when production capture is available."
            title="Editable timeline screenshot"
            variant="tracking"
          />
        </div>
      </section>
      <section className="site-section">
        <SectionHeading label="Known limits" title="Clear compatibility warnings, not vague promises" />
        <div className="warning-stack">
          {compatibility.warnings.map((warning) => <Callout key={warning} title="Compatibility warning" tone="warning"><p>{warning}</p></Callout>)}
          {compatibility.limitations.map((limitation) => <Callout key={limitation} title="Known limitation"><p>{limitation}</p></Callout>)}
        </div>
      </section>
      <CtaSection
        description="Check the tested stack first, then use the documentation to create and preview a first shot before you write the timeline."
        title="Keep the integration dependable"
      />
    </div>
  );
}
