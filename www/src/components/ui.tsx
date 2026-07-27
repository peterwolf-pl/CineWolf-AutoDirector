import Link from "next/link";
import type { ReactNode } from "react";

import { ArrowRightIcon, CheckIcon } from "@/components/icons";

type ButtonLinkProps = {
  href: string;
  children: ReactNode;
  variant?: "primary" | "secondary" | "quiet";
  className?: string;
  external?: boolean;
};

export function ButtonLink({
  href,
  children,
  variant = "primary",
  className = "",
  external = false,
}: ButtonLinkProps) {
  const classes = `button button-${variant} ${className}`.trim();
  const content = <>{children}</>;

  if (external) {
    return (
      <a className={classes} href={href} rel="noreferrer" target="_blank">
        {content}
      </a>
    );
  }

  return (
    <Link className={classes} href={href}>
      {content}
    </Link>
  );
}

export function SectionHeading({
  title,
  description,
  label,
  align = "left",
}: {
  title: string;
  description?: string;
  label?: string;
  align?: "left" | "center";
}) {
  return (
    <div className={`section-heading section-heading-${align}`}>
      {label ? <p className="technical-label">{label}</p> : null}
      <h2>{title}</h2>
      {description ? <p>{description}</p> : null}
    </div>
  );
}

export function CompatibilityBadge({ children }: { children: ReactNode }) {
  return <span className="compatibility-badge">{children}</span>;
}

export function StatusBadge({ status }: { status: string }) {
  const normalized = status.toLowerCase().replace(/\s+/g, "-");
  return <span className={`status-badge status-${normalized}`}>{status}</span>;
}

export function Callout({
  title,
  children,
  tone = "default",
}: {
  title: string;
  children: ReactNode;
  tone?: "default" | "warning" | "success";
}) {
  return (
    <aside className={`callout callout-${tone}`}>
      <strong>{title}</strong>
      <div>{children}</div>
    </aside>
  );
}

export function CodeBlock({ children }: { children: ReactNode }) {
  return <pre className="code-block"><code>{children}</code></pre>;
}

export function FeatureCheckList({ items }: { items: string[] }) {
  return (
    <ul className="check-list">
      {items.map((item) => (
        <li key={item}>
          <CheckIcon size={18} />
          <span>{item}</span>
        </li>
      ))}
    </ul>
  );
}

export function CtaSection({
  title,
  description,
}: {
  title: string;
  description?: string;
}) {
  return (
    <section className="cta-section" aria-labelledby="final-cta-title">
      <div>
        <p className="technical-label">CineWolf AutoDirector</p>
        <h2 id="final-cta-title">{title}</h2>
        {description ? <p>{description}</p> : null}
      </div>
      <div className="button-row">
        <ButtonLink href="/download">
          Download CineWolf <ArrowRightIcon size={17} />
        </ButtonLink>
        <ButtonLink href="/docs" variant="secondary">
          Read Documentation
        </ButtonLink>
      </div>
    </section>
  );
}

export function PageHero({
  title,
  description,
  children,
}: {
  title: string;
  description: string;
  children?: ReactNode;
}) {
  return (
    <section className="page-hero">
      <div className="page-hero-copy">
        <p className="technical-label">CineWolf AutoDirector</p>
        <h1>{title}</h1>
        <p>{description}</p>
        {children}
      </div>
      <div aria-hidden="true" className="page-hero-rule" />
    </section>
  );
}
