"use client";

import { usePathname } from "next/navigation";
import { useEffect } from "react";

const SELECTOR = [
  ".hero-copy > *",
  ".hero-visual",
  ".page-hero",
  ".site-section",
  ".feature-card",
  ".download-card",
  ".cta-section",
  ".callout",
  ".content-card",
  ".shot-card",
  ".docs-panel",
  ".installation-list li",
  ".workflow-step",
  ".roadmap-item",
  ".faq-item",
  ".stat-card",
].join(", ");

export function PageMotion() {
  const pathname = usePathname();

  useEffect(() => {
    if (window.matchMedia("(prefers-reduced-motion: reduce)").matches) {
      document.documentElement.classList.add("motion-reduced");
      return;
    }

    document.documentElement.classList.add("motion-ready");

    const elements = Array.from(document.querySelectorAll<HTMLElement>(SELECTOR));
    for (const element of elements) {
      element.classList.add("motion-target");
      element.classList.remove("motion-visible");
    }

    const observer = new IntersectionObserver(
      (entries) => {
        for (const entry of entries) {
          if (!entry.isIntersecting) {
            continue;
          }
          const target = entry.target as HTMLElement;
          target.classList.add("motion-visible");
          observer.unobserve(target);
        }
      },
      {
        rootMargin: "0px 0px -8% 0px",
        threshold: 0.12,
      },
    );

    for (const [index, element] of elements.entries()) {
      element.style.setProperty("--motion-delay", `${Math.min(index % 8, 7) * 45}ms`);
      // Hero children: show quickly without waiting for scroll.
      if (element.closest(".hero")) {
        requestAnimationFrame(() => element.classList.add("motion-visible"));
        continue;
      }
      observer.observe(element);
    }

    return () => observer.disconnect();
  }, [pathname]);

  return null;
}
