import { CameraIcon } from "@/components/icons";
import { useId } from "react";

const paths = {
  orbit: "M 54 156 C 54 76, 167 40, 257 88 C 343 134, 322 223, 242 234 C 156 246, 81 215, 99 136 C 112 78, 201 75, 268 116",
  follow: "M 45 185 C 90 170, 117 132, 164 130 S 242 168, 292 112 S 344 62, 383 79",
  flyby: "M 36 205 C 132 216, 132 112, 218 111 S 295 171, 388 64",
  dolly: "M 47 152 C 128 152, 203 152, 327 152",
  reveal: "M 37 197 C 100 198, 121 192, 157 162 S 230 107, 334 107",
  crane: "M 165 218 C 165 181, 166 138, 166 72",
  spiral: "M 73 210 C 42 102, 196 34, 319 95 C 417 144, 310 271, 174 216 C 67 172, 139 65, 244 100",
  tracking: "M 42 143 C 98 117, 158 114, 213 126 S 314 140, 383 103",
  chase: "M 39 179 C 112 180, 143 150, 210 145 S 297 136, 381 105",
  vehicle: "M 38 200 C 112 178, 136 129, 209 125 S 309 147, 388 70",
};

export type PathVariant = keyof typeof paths;

export function CameraPathDiagram({
  variant = "orbit",
  label = "Cinematic camera path",
  compact = false,
}: {
  variant?: PathVariant;
  label?: string;
  compact?: boolean;
}) {
  const path = paths[variant] ?? paths.orbit;
  const uniqueId = useId().replaceAll(":", "");
  const titleId = `${uniqueId}-title`;
  const gridId = `${uniqueId}-grid`;
  const arrowId = `${uniqueId}-arrow`;

  return (
    <div className={`camera-path-diagram ${compact ? "camera-path-compact" : ""}`} role="img">
      <svg aria-labelledby={titleId} viewBox="0 0 430 280">
        <title id={titleId}>{label}</title>
        <defs>
          <pattern height="24" id={gridId} patternUnits="userSpaceOnUse" width="24">
            <path d="M 24 0 L 0 0 0 24" fill="none" stroke="currentColor" strokeOpacity="0.08" strokeWidth="1" />
          </pattern>
          <marker id={arrowId} markerHeight="7" markerWidth="7" orient="auto-start-reverse" refX="5" refY="3.5">
            <path d="M0,0 L7,3.5 L0,7 Z" fill="currentColor" />
          </marker>
        </defs>
        <rect height="248" width="398" x="16" y="16" className="path-frame" rx="4" />
        <rect fill={`url(#${gridId})`} height="194" width="348" x="40" y="48" />
        <rect className="focus-frame" height="86" width="135" x="148" y="96" rx="2" />
        <path className="focus-corners" d="M148 118v-22h22M261 96h22v22M283 160v22h-22M170 182h-22v-22" />
        <path className="path-shadow" d={path} />
        <path className="camera-path" d={path} markerEnd={`url(#${arrowId})`} />
        <g className="path-node path-node-start" transform="translate(56 184)">
          <rect height="23" rx="2" width="30" x="-15" y="-11" />
          <circle cx="-16" cy="0" r="8" />
          <path d="M16 0h12" />
        </g>
        <g className="path-node path-node-mid" transform="translate(217 84)">
          <rect height="23" rx="2" width="30" x="-15" y="-11" />
          <circle cx="-16" cy="0" r="8" />
          <path d="M16 0h12" />
        </g>
        <g className="target-crosshair" transform="translate(216 139)">
          <circle r="10" />
          <path d="M-17 0h34M0-17v34" />
        </g>
        <g className="timeline-markers">
          <path d="M44 250h337" />
          {Array.from({ length: 12 }, (_, index) => (
            <path d={`M${58 + index * 26} 245v10`} key={index} />
          ))}
        </g>
      </svg>
      {!compact ? (
        <div className="path-diagram-caption">
          <CameraIcon size={16} />
          <span>Editable path preview</span>
        </div>
      ) : null}
    </div>
  );
}

export function CollisionComparison() {
  return (
    <div className="collision-comparison" role="img" aria-label="Before and after collision-aware camera path comparison">
      <div>
        <span className="technical-label">Basic path</span>
        <svg viewBox="0 0 300 150">
          <rect className="obstacle" height="78" width="74" x="121" y="35" rx="3" />
          <path className="blocked-path" d="M25 121 C100 61, 155 78, 276 33" />
          <circle className="comparison-target" cx="259" cy="42" r="10" />
          <text x="135" y="78">Wall</text>
        </svg>
        <p>Passes through an obstacle.</p>
      </div>
      <div>
        <span className="technical-label">CineWolf Avoid</span>
        <svg viewBox="0 0 300 150">
          <rect className="obstacle" height="78" width="74" x="121" y="35" rx="3" />
          <path className="safe-path" d="M25 121 C83 36, 106 21, 146 21 S 198 18, 220 40 S 242 57, 276 33" />
          <circle className="comparison-target" cx="259" cy="42" r="10" />
          <text x="135" y="78">Wall</text>
        </svg>
        <p>Raises and adjusts the route around it.</p>
      </div>
    </div>
  );
}
