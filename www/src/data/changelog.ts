export type ChangelogCategory =
  | "Added"
  | "Changed"
  | "Fixed"
  | "Performance"
  | "Compatibility"
  | "Known Issues";

export interface ChangelogEntry {
  version: string;
  date: string;
  groups: {
    category: ChangelogCategory;
    items: string[];
  }[];
}

export const changelog: ChangelogEntry[] = [
  {
    version: "2.0.1",
    date: "2026-07-25",
    groups: [
      {
        category: "Fixed",
        items: [
          "H/J/K marks during live Flashback recording now use the real recorder tick and the recording replay UUID, so moments reappear in Generate Montage on the finished replay.",
          "User-marked moments are force-kept in analysis sampling windows and pinned first when planning montage shots.",
          "Native CineWolf Flashback markers still feed the montage when the highlight store is empty or ambient markers are disabled.",
        ],
      },
    ],
  },
  {
    version: "1.3.11",
    date: "2026-07-24",
    groups: [
      {
        category: "Added",
        items: [
          "H marks a montage moment with a ±1.5 second window; J starts or completes a fragment; K cancels an unfinished fragment.",
          "Highlights persist per replay, appear in Generate Montage, and can be promoted to source regions.",
        ],
      },
      {
        category: "Performance",
        items: [
          "Reduced the default detailed sampling rate from 16 to 10 samples per second for long replays.",
          "Added a maximum detailed-sample cap, coverage budget, adaptive seek interval, and a preference for high-signal compact windows.",
        ],
      },
    ],
  },
  {
    version: "1.3.10",
    date: "2026-07-24",
    groups: [
      {
        category: "Added",
        items: [
          "Independent camera-target and aim path-smoothing controls, including strength, window, and outlier rejection.",
          "Multi-region source montages, with controls to add a Flashback selection, suggest start/middle/end thirds, seek a region, remove a region, or seek a planned shot source.",
        ],
      },
      {
        category: "Changed",
        items: [
          "Timeline mapping bridges source cuts with a one-tick output advance and hard cuts between selected regions.",
          "Analysis samples only the selected regions and lays out each region in the generated plan.",
        ],
      },
    ],
  },
  {
    version: "1.3.9",
    date: "2026-07-24",
    groups: [
      {
        category: "Added",
        items: [
          "Obstacle handling modes: Avoid moves the camera, Clip hides blocking world geometry from the view, and None leaves the path uncorrected.",
          "Optional entity clipping for non-subject entities on the line of sight.",
        ],
      },
      {
        category: "Compatibility",
        items: ["Migrated the legacy collisionAvoidance boolean into the obstacle-handling mode enum."],
      },
    ],
  },
  {
    version: "1.3.8",
    date: "2026-07-24",
    groups: [
      {
        category: "Added",
        items: [
          "Montage shot preferences to enable or disable individual generator types during analysis and regeneration.",
          "Montage limits for camera distance, height, orbit diameter, and look-ahead.",
        ],
      },
      {
        category: "Changed",
        items: [
          "The planner and shot template resolver honour the allowed shot set and clamp generated framing geometry to the configured limits.",
          "Added English and Polish text for the new montage controls.",
        ],
      },
    ],
  },
  {
    version: "1.3.7",
    date: "2026-07-24",
    groups: [
      {
        category: "Fixed",
        items: [
          "Removed periodic camera pulses caused by isolated seek or interpolation pose spikes.",
          "Prevented bad future samples from pulling framing through capped velocity, look-ahead, and chase-prediction leads.",
          "Added an extra client tick after a replay seek and rejects stale interpolation destinations far from the rendered pose.",
        ],
      },
    ],
  },
  {
    version: "1.3.6",
    date: "2026-07-24",
    groups: [
      {
        category: "Fixed",
        items: [
          "Reduced intra-shot camera jumps caused by keyframe simplification, flight tracking, collision recovery, and abrupt look-at changes.",
          "Improved Follow, Chase, and Side Tracking direction changes, camera step limits, chase distance, and FOV smoothing.",
        ],
      },
      {
        category: "Performance",
        items: ["Preserved samples required by look-at curvature, angular speed, collision constraints, and tighter keyframe spacing."],
      },
    ],
  },
  {
    version: "1.3.5",
    date: "2026-07-22",
    groups: [
      {
        category: "Added",
        items: [
          "Nine new shot generators: Reveal, Crane Up, Crane Down, Spiral, Static Tracking, Side Tracking, Chase, Close Detail, and Vehicle Profile.",
          "Cinematic target models for groups, structures, areas, vehicles, and details, with provider-based vehicle anchors.",
          "Visibility analysis, framing validation, group visible-ratio checks, structure-framing distance, and vehicle lead-space scoring.",
          "Validated user montage preset import/export, preview caching, non-destructive playback, and extended debug export.",
        ],
      },
      {
        category: "Changed",
        items: [
          "Strengthened collision avoidance with lateral translation, orbit-radius reduction, path shortening, and inserted control points.",
          "Extended montage planning and built-in presets to use the full generator library.",
        ],
      },
      {
        category: "Compatibility",
        items: ["Native Flashback timeline extension remains unavailable; CineWolf retains its own event overlay."],
      },
    ],
  },
  {
    version: "1.3.2",
    date: "2026-07-21",
    groups: [
      {
        category: "Fixed",
        items: [
          "Fixed montage analysis when a requested output duration exceeded the selected continuous source range at the configured replay speed.",
          "Fits output duration to available footage and reports the requested and fitted lengths instead of aborting after analysis.",
        ],
      },
    ],
  },
  {
    version: "1.3.1",
    date: "2026-07-21",
    groups: [
      {
        category: "Fixed",
        items: [
          "Fixed periodic camera shake from sampling replay entities mid-way through client interpolation.",
          "Added common-mode pulse rejection while preserving sustained reversals, discontinuities, collision anchors, and intentional shot motion.",
        ],
      },
    ],
  },
  {
    version: "1.3.0",
    date: "2026-07-21",
    groups: [
      {
        category: "Added",
        items: [
          "Configurable zero-phase camera-path smoothing for individual shots and generated montages.",
          "Independent position and aim/rotation strengths, a time-based smoothing window, and deterministic isolated-glitch rejection.",
        ],
      },
      {
        category: "Changed",
        items: [
          "Preserved completed replay analysis when only path-smoothing settings changed; generated paths and previews are refreshed instead.",
          "Bumped the configuration schema to version 4 with migration and default normalisation.",
        ],
      },
    ],
  },
  {
    version: "1.2.2",
    date: "2026-07-21",
    groups: [
      {
        category: "Fixed",
        items: [
          "Avoided aborting montage generation when one historical collision sample had no continuous fully visible solution.",
          "Preserves a previous safe correction as a bounded fallback and reports per-shot diagnostics; an unavailable replay world remains fatal.",
        ],
      },
    ],
  },
  {
    version: "1.2.1",
    date: "2026-07-21",
    groups: [
      {
        category: "Fixed",
        items: [
          "Stabilised collision corrections near walls with release hysteresis and a bounded cinematic recovery speed.",
          "Stabilised Dolly In and Dolly Out heading and preserved collision-constrained camera keys during simplification.",
        ],
      },
    ],
  },
  {
    version: "1.2.0",
    date: "2026-07-21",
    groups: [
      {
        category: "Added",
        items: [
          "Generate Montage with cancellable coarse-to-detailed local replay sampling, event evidence, scoring, ranking, and planning.",
          "Built-in 15 Seconds, 30 Seconds, 60 Seconds, Trailer, TikTok, YouTube Short, and Cinematic Showcase presets.",
          "Multi-shot preview, a 9:16 safe-area guide, local-world collision adjustment, versioned montage configuration, and guarded undo.",
        ],
      },
      {
        category: "Compatibility",
        items: [
          "Writes source-bound Camera, FOV, and replay-time keyframes while preserving monotonic source time and configured speed limits.",
          "Keeps event visualisation inside CineWolf’s mini-timeline rather than modifying native replay markers.",
        ],
      },
    ],
  },
  {
    version: "0.1.1",
    date: "2026-07-21",
    groups: [
      {
        category: "Fixed",
        items: [
          "Clamped preview duration to the final available replay tick and added a localised notice when a request is shortened.",
        ],
      },
    ],
  },
  {
    version: "0.1.0",
    date: "2026-07-21",
    groups: [
      {
        category: "Added",
        items: [
          "Initial CineWolf AutoDirector release for Minecraft 26.2, Fabric, and Flashback 0.41.1.",
          "Orbit, Follow, Flyby, Dolly In, and Dolly Out generators with 3D path preview and native editable keyframes.",
          "Compatibility checks, local configuration, English and Polish localisation, automated core tests, and integration documentation.",
        ],
      },
      {
        category: "Known Issues",
        items: ["Collision avoidance was intentionally not enabled in the first 0.1 release."],
      },
    ],
  },
];
