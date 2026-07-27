import Link from "next/link";
import type { ReactNode } from "react";

import { ArrowRightIcon, CheckIcon, ExternalIcon } from "@/components/icons";
import { CameraPathDiagram, type PathVariant } from "@/components/camera-path-diagram";
import { StatusBadge } from "@/components/ui";

export function FeatureCard({
  title,
  description,
  detail,
  example,
  icon,
}: {
  title: string;
  description: string;
  detail?: string;
  example?: string;
  icon?: ReactNode;
}) {
  return (
    <article className="feature-card">
      {icon ? <div className="feature-card-icon">{icon}</div> : null}
      <h3>{title}</h3>
      <p>{description}</p>
      {detail ? <p className="feature-detail">{detail}</p> : null}
      {example ? <p className="feature-example">Example: {example}</p> : null}
    </article>
  );
}

export function IntegrationCard({
  name,
  description,
  shots,
  status = "Planned",
}: {
  name: string;
  description: string;
  shots: string[];
  status?: string;
}) {
  return (
    <article className="integration-card">
      <div className="card-topline">
        <span className="technical-label">PeterWolf ecosystem</span>
        <StatusBadge status={status} />
      </div>
      <h3>{name}</h3>
      <p>{description}</p>
      <ul>
        {shots.map((shot) => (
          <li key={shot}><CheckIcon size={16} />{shot}</li>
        ))}
      </ul>
    </article>
  );
}

export function VehicleCard({
  name,
  description,
  anchors,
  shots,
  availability,
}: {
  name: string;
  description: string;
  anchors: string[];
  shots: string[];
  availability?: string;
}) {
  return (
    <article className="vehicle-card">
      <div className="card-topline">
        <span className="technical-label">Vehicle profile</span>
        <StatusBadge status="Profile" />
      </div>
      <h3>{name}</h3>
      <p>{description}</p>
      <dl>
        <div>
          <dt>Anchors</dt>
          <dd>{anchors.join(", ")}</dd>
        </div>
        <div>
          <dt>Shot ideas</dt>
          <dd>{shots.join(", ")}</dd>
        </div>
      </dl>
      {availability ? <p className="vehicle-availability">{availability}</p> : null}
    </article>
  );
}

export function ScreenshotSlot({
  title,
  description,
  variant = "tracking",
}: {
  title: string;
  description: string;
  variant?: PathVariant;
}) {
  return (
    <div className="screenshot-slot">
      <CameraPathDiagram compact label={`${title} placeholder camera-path diagram`} variant={variant} />
      <div>
        <span className="technical-label">Screenshot slot</span>
        <strong>{title}</strong>
        <p>{description}</p>
      </div>
    </div>
  );
}

export function RoadmapTimeline({
  entries,
}: {
  entries: Array<{
    version: string;
    status: string;
    features: string[];
    focus: string;
    limitations: string;
  }>;
}) {
  return (
    <ol className="roadmap-timeline">
      {entries.map((entry) => (
        <li key={entry.version}>
          <div className="roadmap-marker" aria-hidden="true" />
          <article>
            <div className="card-topline">
              <p className="technical-label">{entry.version}</p>
              <StatusBadge status={entry.status} />
            </div>
            <h2>{entry.features[0]}</h2>
            <ul className="check-list">
              {entry.features.map((feature) => (
                <li key={feature}><CheckIcon size={17} />{feature}</li>
              ))}
            </ul>
            <div className="roadmap-meta">
              <p><strong>Technical focus</strong>{entry.focus}</p>
              <p><strong>Known limitations</strong>{entry.limitations}</p>
            </div>
          </article>
        </li>
      ))}
    </ol>
  );
}

export function ChangelogEntry({
  version,
  date,
  groups,
}: {
  version: string;
  date: string;
  groups: Array<{ category: string; items: string[] }>;
}) {
  return (
    <article className="changelog-entry" id={`version-${version.replaceAll(".", "-")}`}>
      <div className="changelog-heading">
        <h2>Version {version}</h2>
        <time dateTime={date}>{date}</time>
      </div>
      <div className="changelog-groups">
        {groups.map((group) => (
          <section key={group.category}>
            <h3>{group.category}</h3>
            {group.items.length > 0 ? (
              <ul>
                {group.items.map((item) => <li key={item}>{item}</li>)}
              </ul>
            ) : (
              <p>No items recorded.</p>
            )}
          </section>
        ))}
      </div>
    </article>
  );
}

export function DocumentationLink({ href, children }: { href: string; children: ReactNode }) {
  return (
    <Link className="text-link" href={href}>
      {children}<ArrowRightIcon size={15} />
    </Link>
  );
}

export function OptionalExternalLink({ href, children }: { href: string | null; children: ReactNode }) {
  if (!href) return null;
  return <a className="text-link" href={href} rel="noreferrer" target="_blank">{children}<ExternalIcon size={15} /></a>;
}
