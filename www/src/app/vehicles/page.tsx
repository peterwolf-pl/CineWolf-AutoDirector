import type { Metadata } from "next";

import { CameraPathDiagram } from "@/components/camera-path-diagram";
import { IntegrationCard, VehicleCard } from "@/components/content-cards";
import { BreadcrumbJsonLd } from "@/components/json-ld";
import { CtaSection, PageHero, SectionHeading } from "@/components/ui";
import { integrations } from "@/data/integrations";
import { vehicles } from "@/data/vehicles";
import { pageMetadata } from "@/lib/metadata";

export const metadata: Metadata = pageMetadata({
  title: "Vehicle Camera Profiles",
  description:
    "Use CineWolf AutoDirector vehicle-aware framing for minecarts, boats, mounts, aircraft-like entities, zip-lines, and generic modded vehicles in Flashback replays.",
  path: "/vehicles",
});

export default function VehiclesPage() {
  return (
    <div className="page">
      <BreadcrumbJsonLd items={[{ name: "Home", path: "/" }, { name: "Vehicles", path: "/vehicles" }]} />
      <PageHero
        description="Vehicles need more than a rear follow camera. CineWolf uses movement direction, lead space, and available anchors to make the frame feel connected to the machine."
        title="Camera profiles built for movement"
      />
      <section className="site-section">
        <div className="split-layout">
          <div>
            <SectionHeading
              description="Vehicle Profile can work with common vehicle-style entities and soft local classification. Specialised integrations are always labelled separately, so a planned profile never masquerades as a shipped dependency."
              label="Vehicle-aware framing"
              title="Anchor the shot to what is moving"
            />
            <div className="event-list vehicle-shot-list">
              {["Front", "Rear", "Side", "Cockpit", "Wing", "Engine", "Wheel", "Coupling", "Train front", "Train rear"].map((anchor) => <span key={anchor}>{anchor}</span>)}
            </div>
          </div>
          <CameraPathDiagram label="Vehicle camera profile path with a leading camera" variant="vehicle" />
        </div>
      </section>
      <section className="site-section">
        <SectionHeading
          description="Each profile lists the framing anchors and shot ideas it is designed to support. Heuristic classification is shown plainly so it can be reviewed in the replay."
          label="Profile catalogue"
          title="Vehicles, anchors, and shot intent"
        />
        <div className="vehicle-grid">
          {vehicles.map((vehicle) => <VehicleCard {...vehicle} key={vehicle.name} />)}
        </div>
      </section>
      <section className="site-section">
        <SectionHeading
          description="These optional PeterWolf ecosystem profiles are planned expansion points. CineWolf does not claim that a named provider is installed, required, or currently shipped."
          label="Optional ecosystem outlook"
          title="Specialised profiles stay optional"
        />
        <div className="integration-grid">
          {integrations.map((integration) => <IntegrationCard {...integration} key={integration.name} />)}
        </div>
      </section>
      <CtaSection
        description="Use the existing vehicle profile controls today, then follow the roadmap for explicit provider and anchor expansions."
        title="Keep the movement readable"
      />
    </div>
  );
}
