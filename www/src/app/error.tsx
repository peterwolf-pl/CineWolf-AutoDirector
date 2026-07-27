"use client";

import { useEffect } from "react";

import { ButtonLink } from "@/components/ui";

export default function ErrorPage({
  error,
  unstable_retry,
}: {
  error: Error & { digest?: string };
  unstable_retry: () => void;
}) {
  useEffect(() => {
    console.error(error);
  }, [error]);

  return (
    <section className="error-page">
      <div className="error-page-card">
        <p className="technical-label">Unexpected path error</p>
        <h1>The timeline could not be read.</h1>
        <p>Try the route again. If it continues, return to the CineWolf home page and open the route from the site navigation.</p>
        <div className="button-row" style={{ justifyContent: "center" }}>
          <button className="button button-primary" onClick={unstable_retry} type="button">Try again</button>
          <ButtonLink href="/">Return home</ButtonLink>
        </div>
      </div>
    </section>
  );
}
