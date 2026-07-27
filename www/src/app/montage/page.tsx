import type { Metadata } from "next";

import { CameraPathDiagram } from "@/components/camera-path-diagram";
import { ScreenshotSlot } from "@/components/content-cards";
import { BreadcrumbJsonLd } from "@/components/json-ld";
import { MontageProcessDiagram, MontageWorkflow } from "@/components/montage-workflow";
import { Callout, CtaSection, PageHero, SectionHeading } from "@/components/ui";
import { pageMetadata } from "@/lib/metadata";

export const metadata: Metadata = pageMetadata({
  title: "Generate Montage",
  description:
    "Learn how CineWolf AutoDirector analyses selected replay regions locally, plans a varied cinematic sequence, previews it, and creates editable Flashback keyframes.",
  path: "/montage",
});

const workflow = [
  { title: "Replay range", description: "Pick one or more replay source regions." },
  { title: "Event analysis", description: "Sample replay activity with local deterministic rules." },
  { title: "Scene detection", description: "Segment activity into usable moments." },
  { title: "Montage plan", description: "Allocate duration and a varied shot set." },
  { title: "Shot preview", description: "Review camera paths, warnings, and timing." },
  { title: "Flashback keyframes", description: "Confirm one atomic, undoable timeline write." },
];

const planningStages = [
  ["Replay analysis pipeline", "Inspect selected source regions without uploading them."],
  ["Event detection and ranking", "Use position, speed, turns, altitude, combat, damage, death, vehicle, flight, landing, block, pause, and marker evidence where available."],
  ["Scene segmentation", "Divide selected replay activity into moments that can support a clear camera decision."],
  ["Sequence planning", "Choose shot types, preserve diversity, and assign a duration based on the selected preset."],
  ["Vertical formats", "TikTok and YouTube Short presets supply a 9:16 composition guide; CineWolf does not crop, render, or encode video."],
  ["Replay-time control", "Use Flashback Timelapse keyframes for supported replay-speed changes and selected-region cuts."],
  ["Editable preview", "Inspect the proposed plan before timeline generation, then keep the written output editable and guarded by undo."],
];

export default function MontagePage() {
  return (
    <div className="page">
      <BreadcrumbJsonLd items={[{ name: "Home", path: "/" }, { name: "Generate Montage", path: "/montage" }]} />
      <PageHero
        description="Generate Montage plans a multi-shot sequence from selected replay activity. It does not invent footage, send a replay away, or lock the result into a rendered video."
        title="Turn replay activity into a sequence"
      />
      <section className="site-section">
        <SectionHeading
          description="The process stays visible from source selection to editable Flashback timeline keys. Each stage has a review point before the next one begins."
          label="Montage process"
          title="From replay range to editable keyframes"
        />
        <MontageProcessDiagram />
        <div className="montage-panel montage-page-panel">
          <MontageWorkflow steps={workflow} />
        </div>
      </section>
      <section className="site-section">
        <SectionHeading
          description="The planner uses explicit replay evidence and the shot preferences you enable. It is local, deterministic production assistance—not generative AI."
          label="Planning details"
          title="A reviewable plan at every stage"
        />
        <div className="info-grid planning-grid">
          {planningStages.map(([title, description], index) => (
            <article className="info-panel" key={title}>
              <p className="technical-label">{String(index + 1).padStart(2, "0")}</p>
              <h3>{title}</h3>
              <p>{description}</p>
            </article>
          ))}
        </div>
      </section>
      <section className="site-section">
        <div className="split-layout">
          <div>
            <SectionHeading
              description="Use highlights to nominate a moment or a replay fragment, then promote the selected material into a montage source region. Source regions are normalised chronologically and connected with explicit hard cuts."
              label="Local editor controls"
              title="Build the source range with intent"
            />
            <Callout title="Highlight controls" tone="success">
              <p><strong>H</strong> marks a moment around the current replay time, <strong>J</strong> starts or completes a fragment, and <strong>K</strong> cancels an unfinished fragment. Highlights remain local to the replay and can be promoted to source regions.</p>
            </Callout>
          </div>
          <ScreenshotSlot
            description="Replace this honest slot with an approved Generate Montage panel screenshot when one is available for publication."
            title="Montage plan screenshot"
            variant="spiral"
          />
        </div>
      </section>
      <section className="site-section">
        <div className="split-layout">
          <CameraPathDiagram label="A planned cinematic montage path" variant="spiral" />
          <div>
            <SectionHeading
              description="Before keys are written, CineWolf checks the target compatibility, preview state, obstacle warnings, timeline interval, and expected undo state. The resulting Camera, FOV, and replay-time keys remain editable in Flashback."
              label="Safe timeline write"
              title="Review. Preview. Generate. Undo."
            />
            <Callout title="What vertical presets do" tone="warning">
              <p>They provide composition metadata and a safe-area guide for 9:16 planning. They do not crop a replay, render a video, encode media, or upload anything.</p>
            </Callout>
          </div>
        </div>
      </section>
      <CtaSection
        description="Start with a single guided montage workflow, inspect the proposed sequence, and retain full edit control after it is written."
        title="Plan your next cinematic cut locally"
      />
    </div>
  );
}
