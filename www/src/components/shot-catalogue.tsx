"use client";

import { useMemo, useState } from "react";
import Link from "next/link";

import { CameraPathDiagram, type PathVariant } from "@/components/camera-path-diagram";
import { ArrowRightIcon, SearchIcon } from "@/components/icons";

export type CatalogueShot = {
  slug: string;
  name: string;
  description: string;
  behavior: string;
  bestFor: string[];
  targets: string[];
  parameters: string[];
  filters: string[];
  collision: string;
  montage: string;
  pathVariant: string;
};

const variantsBySlug: Record<string, PathVariant> = {
  orbit: "orbit",
  follow: "follow",
  flyby: "flyby",
  "dolly-in": "dolly",
  "dolly-out": "dolly",
  reveal: "reveal",
  "crane-up": "crane",
  "crane-down": "crane",
  spiral: "spiral",
  "static-tracking": "tracking",
  "side-tracking": "tracking",
  chase: "chase",
  "close-detail": "dolly",
  "vehicle-profile": "vehicle",
};

export function ShotCard({ shot }: { shot: CatalogueShot }) {
  return (
    <article className="shot-card" id={shot.slug}>
      <CameraPathDiagram compact label={`${shot.name} path diagram`} variant={variantsBySlug[shot.slug] ?? "orbit"} />
      <div className="shot-card-main">
        <h2>{shot.name}</h2>
        <p>{shot.description}</p>
      </div>
      <dl>
        <div>
          <dt>Behaviour</dt>
          <dd>{shot.behavior}</dd>
        </div>
        <div>
          <dt>Best for</dt>
          <dd>{shot.bestFor.join(", ")}</dd>
        </div>
        <div>
          <dt>Targets</dt>
          <dd>{shot.targets.join(", ")}</dd>
        </div>
        <div>
          <dt>Parameters</dt>
          <dd>{shot.parameters.join(", ")}</dd>
        </div>
        <div>
          <dt>Collision</dt>
          <dd>{shot.collision}</dd>
        </div>
        <div>
          <dt>Montage</dt>
          <dd>{shot.montage}</dd>
        </div>
      </dl>
      <Link className="shot-card-link" href="/docs">
        Read the guide <ArrowRightIcon size={15} />
      </Link>
    </article>
  );
}

export function ShotCatalogue({ shots }: { shots: CatalogueShot[] }) {
  const filters = [
    "Static",
    "Tracking",
    "Orbiting",
    "Vehicle",
    "Structure",
    "Dynamic",
    "Close-up",
    "Vertical",
    "Wide",
  ];
  const [activeFilter, setActiveFilter] = useState<string | null>(null);
  const [query, setQuery] = useState("");
  const visibleShots = useMemo(() => {
    const normalized = query.trim().toLowerCase();
    return shots.filter((shot) => {
      const matchesFilter = !activeFilter || shot.filters.includes(activeFilter);
      const matchesQuery =
        !normalized ||
        [shot.name, shot.description, shot.behavior, ...shot.bestFor, ...shot.targets]
          .join(" ")
          .toLowerCase()
          .includes(normalized);
      return matchesFilter && matchesQuery;
    });
  }, [activeFilter, query, shots]);

  return (
    <div className="shot-catalogue">
      <div className="catalogue-toolbar">
        <div aria-label="Shot filters" className="shot-filter-list" role="toolbar">
          {filters.map((filter) => {
            const selected = activeFilter === filter;
            return (
              <button
                aria-pressed={selected}
                className={selected ? "filter-active" : ""}
                key={filter}
                onClick={() => setActiveFilter(selected ? null : filter)}
                type="button"
              >
                {filter}
              </button>
            );
          })}
        </div>
        <label className="catalogue-search">
          <SearchIcon size={18} />
          <span className="sr-only">Search shots</span>
          <input
            onChange={(event) => setQuery(event.target.value)}
            placeholder="Search shots"
            type="search"
            value={query}
          />
        </label>
      </div>
      <p aria-live="polite" className="catalogue-count">
        {visibleShots.length} {visibleShots.length === 1 ? "shot" : "shots"} shown
      </p>
      {visibleShots.length > 0 ? (
        <div className="shot-grid">
          {visibleShots.map((shot) => (
            <ShotCard key={shot.slug} shot={shot} />
          ))}
        </div>
      ) : (
        <div className="missing-content" role="status">
          No shots match the selected filters. Clear a filter or try another search.
        </div>
      )}
    </div>
  );
}
