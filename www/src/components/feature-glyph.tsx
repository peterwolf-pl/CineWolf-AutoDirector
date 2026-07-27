import type { ReactNode, SVGProps } from "react";

export function FeatureGlyph({
  name,
  ...props
}: { name: string } & SVGProps<SVGSVGElement>) {
  const common = {
    fill: "none",
    stroke: "currentColor",
    strokeLinecap: "round" as const,
    strokeLinejoin: "round" as const,
    strokeWidth: 1.75,
  };

  const glyphs: Record<string, ReactNode> = {
    crosshair: <><circle cx="12" cy="12" r="4" /><path d="M12 2v4m0 12v4M2 12h4m12 0h4" /></>,
    route: <><path d="M4 18c4-9 8-10 12-5 2 2 3 1 4-2" /><circle cx="4" cy="18" r="1.5" /><circle cx="20" cy="11" r="1.5" /></>,
    keyframes: <><path d="M4 6h16v12H4z" /><path d="m8 12 4-3 4 3-4 3z" /></>,
    shield: <><path d="M12 3 19 6v5c0 4.4-2.8 7.6-7 10-4.2-2.4-7-5.6-7-10V6z" /><path d="m8.5 12 2.2 2.2 4.8-5" /></>,
    eye: <><path d="M2.5 12s3.5-5 9.5-5 9.5 5 9.5 5-3.5 5-9.5 5-9.5-5-9.5-5Z" /><circle cx="12" cy="12" r="2" /></>,
    frame: <><path d="M4 9V5h4m8 0h4v4M20 15v4h-4M8 19H4v-4" /><circle cx="12" cy="12" r="2.5" /></>,
    building: <><path d="M4 21V4h12v17M16 9h4v12M8 8h2m-2 4h2m-2 4h2m4-8h2m-2 4h2" /></>,
    users: <><circle cx="9" cy="8" r="3" /><circle cx="17" cy="10" r="2" /><path d="M3 20c0-3.2 2.5-5.5 6-5.5S15 16.8 15 20m1-5c2.9 0 5 1.8 5 4.5" /></>,
    vehicle: <><path d="M4 15h16l-2-6H7z" /><circle cx="8" cy="17" r="2" /><circle cx="17" cy="17" r="2" /><path d="M10 9h4" /></>,
    sliders: <><path d="M4 7h16M4 12h16M4 17h16" /><circle cx="9" cy="7" r="2" /><circle cx="15" cy="12" r="2" /><circle cx="11" cy="17" r="2" /></>,
    folder: <><path d="M3 7h7l2 2h9v10H3z" /></>,
    undo: <><path d="M9 7 4 12l5 5" /><path d="M5 12h9a5 5 0 0 1 0 10" /></>,
    timeline: <><path d="M4 18V6m5 12v-5m5 5V9m5 9V4" /><path d="M3 20h18" /></>,
    bug: <><rect x="7" y="7" width="10" height="12" rx="3" /><path d="M12 4v3M4 10h3m10 0h3M4 15h3m10 0h3M9 19l-2 2m8-2 2 2" /></>,
    language: <><circle cx="12" cy="12" r="9" /><path d="M3 12h18M12 3c3 3.2 3 14.8 0 18M12 3c-3 3.2-3 14.8 0 18" /></>,
    cpu: <><rect x="7" y="7" width="10" height="10" rx="1" /><path d="M9 2v5m6-5v5m-6 10v5m6-5v5M2 9h5m-5 6h5m10-6h5m-5 6h5" /></>,
  };

  return (
    <svg aria-hidden="true" height="24" viewBox="0 0 24 24" width="24" {...common} {...props}>
      {glyphs[name] ?? glyphs.route}
    </svg>
  );
}
