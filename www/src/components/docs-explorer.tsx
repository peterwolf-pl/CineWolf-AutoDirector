"use client";

import { useMemo, useState, useSyncExternalStore } from "react";

import { SearchIcon } from "@/components/icons";
import { CodeBlock } from "@/components/ui";

export type DocumentationEntry = {
  id: string;
  title: string;
  group: string;
  summary: string;
  body: string[];
  steps?: string[];
  code?: string;
};

function subscribeToHash(callback: () => void) {
  window.addEventListener("hashchange", callback);
  return () => window.removeEventListener("hashchange", callback);
}

function currentHash() {
  return window.location.hash.replace(/^#/, "");
}

export function DocumentationSidebar({
  entries,
  activeId,
  onSelect,
}: {
  entries: DocumentationEntry[];
  activeId: string;
  onSelect: (id: string) => void;
}) {
  const groups = Array.from(new Set(entries.map((entry) => entry.group)));

  return (
    <nav aria-label="Documentation sections" className="documentation-sidebar">
      <p className="technical-label">Documentation</p>
      {groups.map((group) => (
        <div className="docs-nav-group" key={group}>
          <p>{group}</p>
          {entries
            .filter((entry) => entry.group === group)
            .map((entry) => (
              <button
                aria-current={entry.id === activeId ? "page" : undefined}
                className={entry.id === activeId ? "docs-nav-active" : ""}
                key={entry.id}
                onClick={() => onSelect(entry.id)}
                type="button"
              >
                {entry.title}
              </button>
            ))}
        </div>
      ))}
    </nav>
  );
}

export function DocsExplorer({ entries }: { entries: DocumentationEntry[] }) {
  const [query, setQuery] = useState("");
  const [activeId, setActiveId] = useState(entries[0]?.id ?? "");
  const hashId = useSyncExternalStore(subscribeToHash, currentHash, () => "");
  const selectedId = entries.some((entry) => entry.id === hashId) ? hashId : activeId;
  const filtered = useMemo(() => {
    const normalized = query.trim().toLowerCase();
    if (!normalized) return entries;
    return entries.filter((entry) =>
      [entry.title, entry.group, entry.summary, ...entry.body]
        .join(" ")
        .toLowerCase()
        .includes(normalized),
    );
  }, [entries, query]);
  const active =
    filtered.find((entry) => entry.id === selectedId) ?? filtered[0] ?? entries[0];

  const selectSection = (id: string) => {
    setActiveId(id);
    window.location.hash = id;
  };

  if (!active) {
    return (
      <div className="missing-content" role="status">
        Documentation content is not available yet. Add an entry in <code>src/data/docs.ts</code>.
      </div>
    );
  }

  return (
    <div className="docs-explorer">
      <div className="docs-mobile-select">
        <label htmlFor="documentation-section">Documentation section</label>
        <select
          id="documentation-section"
          onChange={(event) => selectSection(event.target.value)}
          value={active.id}
        >
          {filtered.map((entry) => (
            <option key={entry.id} value={entry.id}>
              {entry.group} — {entry.title}
            </option>
          ))}
        </select>
      </div>
      <DocumentationSidebar activeId={active.id} entries={filtered} onSelect={selectSection} />
      <article className="docs-article">
        <div className="docs-search">
          <SearchIcon size={18} />
          <input
            aria-label="Search documentation"
            onChange={(event) => setQuery(event.target.value)}
            placeholder="Search documentation"
            type="search"
            value={query}
          />
        </div>
        <p className="technical-label">{active.group}</p>
        <h2 id={active.id}>{active.title}</h2>
        <p className="docs-lede">{active.summary}</p>
        {active.body.map((paragraph) => (
          <p key={paragraph}>{paragraph}</p>
        ))}
        {active.steps ? (
          <ol className="documentation-steps">
            {active.steps.map((step) => (
              <li key={step}>{step}</li>
            ))}
          </ol>
        ) : null}
        {active.code ? <CodeBlock>{active.code}</CodeBlock> : null}
      </article>
      <aside aria-label="On this page" className="docs-on-page">
        <p className="technical-label">On this page</p>
        <a href={`#${active.id}`}>{active.title}</a>
        {active.steps?.map((step, index) => (
          <a href={`#${active.id}`} key={step}>
            {index + 1}. {step}
          </a>
        ))}
      </aside>
    </div>
  );
}
