import Image from "next/image";
import type { Metadata } from "next";

import { CameraPathDiagram, CollisionComparison } from "@/components/camera-path-diagram";
import { FeatureCard, IntegrationCard } from "@/components/content-cards";
import { DownloadCard } from "@/components/download-card";
import { FeatureGlyph } from "@/components/feature-glyph";
import { ArrowRightIcon, CameraIcon } from "@/components/icons";
import { BreadcrumbJsonLd, SoftwareApplicationJsonLd } from "@/components/json-ld";
import { MontageWorkflow } from "@/components/montage-workflow";
import { ButtonLink, CompatibilityBadge, CtaSection, SectionHeading, StatusBadge } from "@/components/ui";
import { integrations } from "@/data/integrations";
import { roadmap } from "@/data/roadmap";
import { shots } from "@/data/shots";
import { pageMetadata } from "@/lib/metadata";

export const metadata: Metadata = pageMetadata({
  title: "CineWolf AutoDirector - Cinematic Camera Mod for Minecraft Flashback",
  description:
    "Generate professional camera paths, tracking shots, collision-aware movement and editable cinematic montages directly inside the Minecraft Flashback replay editor.",
  path: "/",
});

const coreValues = [
  {
    title: "Virtual Camera Operator",
    description:
      "CineWolf plans camera movement around a selected player, mob, vehicle, structure, group, or area.",
    icon: "route",
  },
  {
    title: "Editable Flashback Keyframes",
    description: "Generated shots remain editable inside the Flashback timeline.",
    icon: "keyframes",
  },
  {
    title: "Collision-Aware Paths",
    description:
      "Camera paths can move above, beside, or around walls, trees, roofs, mountains, and other obstacles.",
    icon: "shield",
  },
  {
    title: "Automatic Montage",
    description:
      "CineWolf can analyse important replay events and build a multi-shot cinematic sequence.",
    icon: "timeline",
  },
];

const montageSteps = [
  { title: "Select a replay range", description: "Choose one or more source regions." },
  { title: "Choose a montage preset", description: "Set duration, pace, and format." },
  { title: "Analyse replay events", description: "Use local deterministic rules." },
  { title: "Review generated shots", description: "Inspect diversity and warnings." },
  { title: "Edit or reorder shots", description: "Keep the intended sequence." },
  { title: "Generate Flashback keyframes", description: "Confirm one guarded timeline write." },
];

const targetTypes = ["Players", "Mobs", "Groups", "Vehicles", "Structures", "Areas", "Detail points"];

export default function Home() {
  const previewShots = shots.slice(0, 14);
  const roadMapPreview = [
    roadmap.find((entry) => entry.version === "0.1"),
    roadmap.find((entry) => entry.version === "1.1"),
    roadmap.find((entry) => entry.version === "1.2"),
    roadmap.find((entry) => entry.version === "1.3.11"),
    roadmap.find((entry) => entry.version === "2.0"),
  ].filter((entry): entry is (typeof roadmap)[number] => Boolean(entry));

  return (
    <div className="page">
      <SoftwareApplicationJsonLd />
      <BreadcrumbJsonLd items={[{ name: "Home", path: "/" }]} />

      <section className="hero" aria-labelledby="hero-title">
        <div className="hero-copy">
          <div className="hero-lockup">
            <Image
              alt="CineWolf silver wolf aperture logo"
              height={200}
              priority
              src="/images/brand/cinewolf-logo.png"
              width={200}
            />
            <div className="hero-lockup-copy">
              <strong>Cine<span>Wolf</span></strong>
              <small>AutoDirector</small>
            </div>
          </div>
          <h1 id="hero-title">Your Virtual Camera Operator <span>for Minecraft</span></h1>
          <p className="hero-supporting-line">Generate professional cinematic camera paths directly inside Flashback.</p>
          <p className="hero-description">
            CineWolf AutoDirector analyses your replay, tracks selected subjects, plans camera movement, avoids obstacles, and creates editable Flashback keyframes.
          </p>
          <div className="button-row">
            <ButtonLink href="/download">Download CineWolf <ArrowRightIcon size={17} /></ButtonLink>
            <ButtonLink href="/shots" variant="secondary">Explore Camera Shots</ButtonLink>
          </div>
          <div className="hero-badges" aria-label="Compatibility information">
            <CompatibilityBadge>Minecraft Fabric</CompatibilityBadge>
            <CompatibilityBadge>Flashback</CompatibilityBadge>
            <CompatibilityBadge>Client-side</CompatibilityBadge>
            <CompatibilityBadge>Local processing</CompatibilityBadge>
            <CompatibilityBadge>No external server</CompatibilityBadge>
            <CompatibilityBadge>No AI service required</CompatibilityBadge>
          </div>
        </div>
        <div className="hero-visual">
          <CameraPathDiagram label="A red camera path curves around a selected target in an editable timeline preview" variant="orbit" />
          <div className="hero-art-label">
            <span>Subject tracking / editable path</span>
            <span>00:00 → 00:20</span>
          </div>
        </div>
      </section>

      <section className="site-section">
        <SectionHeading
          description="Build a deliberate camera plan from replay activity, then keep the generated result open for creative editing."
          title="More than an orbit camera"
        />
        <div className="feature-grid">
          {coreValues.map((value) => (
            <FeatureCard
              description={value.description}
              icon={<FeatureGlyph name={value.icon} />}
              key={value.title}
              title={value.title}
            />
          ))}
        </div>
      </section>

      <section className="site-section">
        <div className="shot-preview-layout">
          <div>
            <SectionHeading
              description="Every generator starts with a camera behaviour, framing controls, and a path that can be inspected before it touches the timeline."
              label="Shot library"
              title="A camera language for every replay beat"
            />
            <div className="shot-preview-list">
              {previewShots.map((shot, index) => (
                <div className="shot-preview-item" key={shot.slug}>
                  <span>{String(index + 1).padStart(2, "0")}</span>
                  <div>
                    <h3>{shot.name}</h3>
                    <p>{shot.description}</p>
                  </div>
                </div>
              ))}
            </div>
            <ButtonLink className="home-section-link" href="/shots" variant="quiet">View all shot types <ArrowRightIcon size={16} /></ButtonLink>
          </div>
          <div className="banner-panel">
            <Image
              alt="CineWolf AutoDirector camera paths over a Minecraft world"
              fill
              sizes="(max-width: 900px) 100vw, 48vw"
              src="/images/brand/cinewolf-banner.png"
            />
            <div className="banner-panel-copy">
              <p className="technical-label">Direct with intent</p>
              <strong>Orbit, Follow, Chase, Flyby, Dolly, Reveal, Crane, Spiral, Tracking, Detail, and Vehicle Profile.</strong>
              <p>Use the path visualiser to review the flow before you generate editable keys.</p>
            </div>
          </div>
        </div>
      </section>

      <section className="site-section">
        <SectionHeading
          description="Generate Montage turns selected replay ranges into a clear, editable sequence. It analyses replay events locally, suggests a varied shot plan, then waits for your review."
          label="Generate Montage"
          title="Turn a replay into a cinematic sequence"
        />
        <div className="montage-panel">
          <MontageWorkflow steps={montageSteps} />
          <div className="preset-list" aria-label="Montage presets">
            {["15 Seconds", "30 Seconds", "60 Seconds", "Trailer", "TikTok", "YouTube Short", "Cinematic Showcase"].map((preset) => <span key={preset}>{preset}</span>)}
          </div>
          <div className="event-list" aria-label="Detected replay signals">
            {["Position", "Speed", "Acceleration", "Turns", "Altitude", "Combat", "Damage", "Death", "Vehicles", "Flight", "Landing", "Block placement", "Block destruction", "Pauses", "Replay markers"].map((event) => <span key={event}>{event}</span>)}
          </div>
          <p className="deterministic-note">All analysis runs locally using deterministic rules.</p>
        </div>
        <div className="home-inline-action">
          <ButtonLink href="/montage" variant="secondary">Explore the montage workflow <ArrowRightIcon size={16} /></ButtonLink>
        </div>
      </section>

      <section className="site-section">
        <div className="split-layout">
          <div>
            <SectionHeading
              description="Basic paths can pass through walls, trees, roofs, terrain, large builds, and vehicles. CineWolf checks the locally loaded replay world before you commit a path."
              label="Obstacle handling"
              title="Camera paths that respect the world"
            />
            <div className="info-panel">
              <h3>Available correction strategies</h3>
              <ul className="check-list">
                {["Height adjustment", "Lateral translation", "Radius reduction", "Path shortening", "Additional control points", "Visibility and collision warnings"].map((item) => <li key={item}><CameraIcon size={17} />{item}</li>)}
              </ul>
            </div>
          </div>
          <CollisionComparison />
        </div>
      </section>

      <section className="site-section">
        <SectionHeading
          description="CineWolf’s framing models can consider more than a single player. The current editor target picker is entity-based, while first-class group and structure selection controls remain part of the product roadmap."
          label="Subjects and framing"
          title="Film more than one player"
        />
        <div className="target-rail" aria-label="Supported subject and framing types">
          {targetTypes.map((target) => <span key={target}>{target}</span>)}
        </div>
        <div className="info-grid site-section-tight">
          <article className="info-panel"><h3>Group framing</h3><p>Evaluate spread, visible ratio, and a primary focus instead of forcing every composition into a close-up.</p></article>
          <article className="info-panel"><h3>Structure framing</h3><p>Use scale-aware distances and a wide, readable silhouette when a structure or area is the intended frame.</p></article>
          <article className="info-panel"><h3>Vehicle-aware framing</h3><p>Give moving subjects lead space and anchor-driven camera positions rather than a generic entity offset.</p></article>
        </div>
      </section>

      <section className="site-section">
        <div className="split-layout">
          <div>
            <SectionHeading
              description="Profile moving entities with anchors and a sense of travel direction. Built-in support handles common vehicle-style entities, while specialised providers are clearly marked when they are planned."
              label="Vehicle profiles"
              title="Camera profiles built for movement"
            />
            <div className="event-list vehicle-shot-list">
              {["Chase camera", "Trackside camera", "Wing camera", "Train flyover", "Runway camera", "Side tracking", "Vehicle detail", "Arrival and departure sequences"].map((shot) => <span key={shot}>{shot}</span>)}
            </div>
            <ButtonLink className="home-section-link" href="/vehicles" variant="quiet">Explore vehicle profiles <ArrowRightIcon size={16} /></ButtonLink>
          </div>
          <CameraPathDiagram label="A vehicle profile camera path follows a target" variant="vehicle" />
        </div>
      </section>

      <section className="site-section">
        <SectionHeading
          description="Optional PeterWolf ecosystem profiles are an explicit roadmap direction, not a hidden compatibility claim. Each integration remains optional and local."
          label="Ecosystem outlook"
          title="Expand the camera language when the replay needs it"
        />
        <div className="integration-grid">
          {integrations.map((integration) => <IntegrationCard {...integration} key={integration.name} />)}
        </div>
      </section>

      <section className="site-section">
        <div className="split-layout">
          <div>
            <SectionHeading
              description="CineWolf is designed around Flashback’s editable replay workflow—not around a locked render. It writes only after version checks, preview, and conflict review."
              label="Replay editor"
              title="Designed for Flashback"
            />
            <div className="check-list flashback-checks">
              {["Native camera keyframes", "Native FOV keyframes", "Replay-time control", "Editable generated paths", "Timeline conflict detection", "Atomic timeline writes", "Safe undo", "Non-destructive preview", "Compatibility checks"].map((item) => <li key={item}><CameraIcon size={17} />{item}</li>)}
            </div>
            <ButtonLink className="home-section-link" href="/flashback" variant="quiet">Read Flashback integration details <ArrowRightIcon size={16} /></ButtonLink>
          </div>
          <DownloadCard />
        </div>
      </section>

      <section className="site-section">
        <SectionHeading
          description="CineWolf’s replay analysis stays on your computer. The local workflow does not need a hosted service to make a plan."
          label="Local processing"
          title="Your replay stays on your computer"
        />
        <div className="info-grid">
          {["No external server", "No cloud processing", "No account required", "No replay upload", "No telemetry required", "No AI API", "No subscription required for local functionality"].map((item) => (
            <article className="info-panel local-trust-card" key={item}><CameraIcon size={19} /><p>{item}</p></article>
          ))}
        </div>
      </section>

      <section className="site-section">
        <SectionHeading
          description="Release history and planned work are separated clearly so a good direction is never mistaken for a shipped feature."
          label="Development roadmap"
          title="Build the next production workflow with care"
        />
        <div className="roadmap-preview">
          {roadMapPreview.map((entry) => (
            <article key={entry.version}>
              <div><span>{entry.version}</span><StatusBadge status={entry.status} /></div>
              <h3>{entry.features[0]}</h3>
              <p>{entry.focus}</p>
            </article>
          ))}
        </div>
        <div className="home-inline-action"><ButtonLink href="/roadmap" variant="secondary">View full roadmap <ArrowRightIcon size={16} /></ButtonLink></div>
      </section>

      <CtaSection
        description="Choose the shot, inspect the path, and keep creative control over every editable keyframe."
        title="Direct your next Minecraft replay"
      />
    </div>
  );
}
