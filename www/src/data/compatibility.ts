/**
 * The single compatibility baseline used by download, installation, and
 * Flashback-facing content. Update this file for a new tested release.
 */
export interface Compatibility {
  version: string;
  minecraftVersion: string;
  fabricLoader: string;
  fabricApi: string;
  flashbackVersion: string;
  keyframes: readonly string[];
  limitations: readonly string[];
  warnings: readonly string[];
}

export const compatibility: Compatibility = {
  version: "2.0.31",
  minecraftVersion: "26.3",
  fabricLoader: ">=0.19.5",
  fabricApi: "0.160.7+26.3",
  flashbackVersion: "0.43.4+",
  keyframes: ["Camera", "FOV", "Replay-time (Timelapse)"],
  limitations: [
    "Vertical presets provide 9:16 composition metadata and a safe-area guide; they do not crop, resize, render, encode, or upload video.",
    "Collision correction works against the replay world loaded on the local client. An obstruction without a safe correction remains visible as a warning.",
    "Flashback does not expose a stable API for third-party event glyphs in its native timeline, so CineWolf uses its own event mini-timeline.",
  ],
  warnings: [
    "Install Flashback separately. CineWolf does not bundle, copy, or modify it.",
    "CineWolf enables its editor integration for Flashback 0.42.1 and newer 0.x builds (recommended 0.43.4).",
    "Use the supported Minecraft, Fabric Loader, and Fabric API combination before opening a replay project.",
  ],
};
