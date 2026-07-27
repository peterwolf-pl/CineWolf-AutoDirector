import Link from "next/link";

import { CameraPathDiagram } from "@/components/camera-path-diagram";
import { ButtonLink } from "@/components/ui";

export default function NotFound() {
  return (
    <section className="not-found">
      <div className="not-found-card">
        <div className="not-found-path">
          <CameraPathDiagram label="A lost camera path" variant="flyby" />
        </div>
        <p className="technical-label">404 · Path unavailable</p>
        <h1>This camera path is lost.</h1>
        <p>The page you requested is not part of the current CineWolf route map.</p>
        <div className="button-row" style={{ justifyContent: "center" }}>
          <ButtonLink href="/">Return home</ButtonLink>
          <Link className="button button-secondary" href="/docs">Open documentation</Link>
        </div>
      </div>
    </section>
  );
}
