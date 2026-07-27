import Link from "next/link";

import { DownloadIcon, ExternalIcon } from "@/components/icons";
import { CompatibilityBadge, StatusBadge } from "@/components/ui";
import { siteConfig } from "@/lib/site";

type DownloadCardProps = {
  detailed?: boolean;
};

export function DownloadCard({ detailed = false }: DownloadCardProps) {
  const directDownload = siteConfig.downloadUrl;
  const modrinthUrl = siteConfig.modrinthUrl;
  const primaryHref = directDownload ?? modrinthUrl;
  const primaryLabel = directDownload ? "Download CineWolf" : "Download on Modrinth";

  return (
    <section className={`download-card ${detailed ? "download-card-detailed" : ""}`} aria-labelledby="download-card-heading">
      <div className="download-card-heading">
        <div>
          <p className="technical-label">Current release</p>
          <h2 id="download-card-heading">Download CineWolf</h2>
        </div>
        <StatusBadge status="Current" />
      </div>
      <dl className="release-details">
        <div><dt>Version</dt><dd>{siteConfig.version}</dd></div>
        <div><dt>Minecraft</dt><dd>{siteConfig.minecraftVersion}</dd></div>
        <div><dt>Fabric</dt><dd>Loader 0.19.3+</dd></div>
        <div><dt>Flashback</dt><dd>{siteConfig.flashbackVersion} required</dd></div>
        <div><dt>File type</dt><dd>Fabric mod JAR</dd></div>
        <div><dt>Release status</dt><dd>Current</dd></div>
      </dl>
      <div className="compatibility-row">
        <CompatibilityBadge>Client-side</CompatibilityBadge>
        <CompatibilityBadge>Local processing</CompatibilityBadge>
        <CompatibilityBadge>Flashback required</CompatibilityBadge>
      </div>
      {primaryHref ? (
        <div className="download-actions">
          <a className="button button-primary download-button" href={primaryHref} rel="noreferrer" target="_blank">
            <DownloadIcon size={18} /> {primaryLabel} <ExternalIcon size={15} />
          </a>
          {directDownload && modrinthUrl ? (
            <a className="button button-secondary download-button" href={modrinthUrl} rel="noreferrer" target="_blank">
              Get on Modrinth <ExternalIcon size={15} />
            </a>
          ) : null}
        </div>
      ) : (
        <div className="download-unavailable" role="status">
          <button className="button button-disabled download-button" disabled type="button">
            <DownloadIcon size={18} /> Download not configured
          </button>
          <p>Set <code>NEXT_PUBLIC_CINEWOLF_DOWNLOAD_URL</code> or <code>NEXT_PUBLIC_MODRINTH_URL</code> to enable download links.</p>
        </div>
      )}
      <p className="compatibility-warning">
        CineWolf requires Flashback {siteConfig.flashbackVersion}. An unsupported version disables the integration and reports a compatibility warning.
      </p>
      {detailed ? (
        <div className="download-card-actions">
          {modrinthUrl ? (
            <a className="text-link" href={modrinthUrl} rel="noreferrer" target="_blank">
              Open Modrinth page <ExternalIcon size={14} />
            </a>
          ) : null}
          <Link className="text-link" href="/changelog">Read changelog <ExternalIcon size={14} /></Link>
          <Link className="text-link" href="/docs">Open installation documentation <ExternalIcon size={14} /></Link>
        </div>
      ) : null}
    </section>
  );
}
