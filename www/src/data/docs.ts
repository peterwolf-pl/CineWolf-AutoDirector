import { compatibility } from "./compatibility";

export interface DocSection {
  id: string;
  title: string;
  group: string;
  summary: string;
  body: string[];
  steps?: string[];
  code?: string;
}

const testedStack = `Minecraft ${compatibility.minecraftVersion}, Fabric Loader ${compatibility.fabricLoader}, Fabric API ${compatibility.fabricApi}, and Flashback ${compatibility.flashbackVersion}`;

export const docs: DocSection[] = [
  {
    id: "installation",
    title: "Installation",
    group: "Getting started",
    summary: "Install the tested Fabric and Flashback stack before placing CineWolf in the client mods folder.",
    body: [
      `CineWolf ${compatibility.version} is tested with ${testedStack}. Flashback is required and remains a separate installation.`,
      "CineWolf is client-side. Keep a backup of an important replay project before testing a new mod combination.",
    ],
    steps: [
      "Install Fabric Loader for the tested Minecraft version.",
      "Install the matching Fabric API build.",
      `Install Flashback ${compatibility.flashbackVersion}.`,
      "Download the CineWolf AutoDirector JAR from the configured release link.",
      "Place all required JAR files in the Minecraft mods folder.",
      "Start Minecraft, open or create a Flashback replay, then open the CineWolf panel in the replay editor.",
    ],
    code: "mods/\n  fabric-api-<matching-version>.jar\n  flashback-0.41.1.jar\n  cinewolf-autodirector-1.3.11.jar",
  },
  {
    id: "first-shot",
    title: "First shot",
    group: "Getting started",
    summary: "Generate a single editable camera shot before building a full montage.",
    body: [
      "A single shot lets you review the generated 3D path, collision warnings, and timing before native timeline keys are written.",
      "Use Preview Path first when you want to inspect a path without changing the replay timeline.",
    ],
    steps: [
      "Open a replay in Flashback and choose a subject.",
      "Select a shot type and adjust its framing controls.",
      "Mark the replay In and Out points, or use the current time with a duration.",
      "Select Preview Path and review the result and its warnings.",
      "Select Generate Shot, resolve any timeline conflict, then edit the resulting keys if needed.",
    ],
  },
  {
    id: "target-selection",
    title: "Target selection",
    group: "Getting started",
    summary: "Choose an entity target that stays stable while CineWolf samples the replay.",
    body: [
      "CineWolf can start from its own selection, the editor selection, the crosshair, or the spectated entity. Entity targets are stored with a stable UUID-based reference.",
      "Group and structure framing models are available to the camera system, while first-class group and structure selection controls remain roadmap work.",
    ],
    steps: [
      "Move the replay to a moment where the subject is loaded and visible.",
      "Choose the entity in CineWolf or use an available editor, crosshair, or spectated-entity selection.",
      "Confirm that the target remains available across the intended replay range before generating a shot.",
    ],
  },
  {
    id: "orbit",
    title: "Orbit",
    group: "Shot guides",
    summary: "Circle a subject to establish space, scale, and direction.",
    body: [
      "Orbit maintains a target-relative circular camera path. It is useful for introductions, landmarks, and turning moments.",
      "Adjust radius, height, direction, speed, and FOV. Check collision warnings if the orbit passes near terrain or a build.",
    ],
  },
  {
    id: "follow",
    title: "Follow",
    group: "Shot guides",
    summary: "Track movement from a composed trailing offset.",
    body: [
      "Follow uses distance, height, look-ahead, camera speed, and FOV to keep a moving entity readable over time.",
      "It is a practical starting shot for traversal and continuous action, then can be refined directly in the Flashback timeline.",
    ],
  },
  {
    id: "flyby",
    title: "Flyby",
    group: "Shot guides",
    summary: "Pass the subject laterally to communicate speed and scene scale.",
    body: [
      "Flyby uses a lateral pass and a timed look-at rather than simply following behind the subject.",
      "Use it for high-speed movement, flight, arrivals, or a wide environmental transition.",
    ],
  },
  {
    id: "dolly",
    title: "Dolly",
    group: "Shot guides",
    summary: "Push in to concentrate attention or pull out to reveal context.",
    body: [
      "Dolly In reduces camera distance over the shot; Dolly Out increases it. Both retain an editable target-focused path.",
      "Start and end distance, height, duration, and FOV determine how quickly the composition changes.",
    ],
  },
  {
    id: "reveal",
    title: "Reveal",
    group: "Shot guides",
    summary: "Uncover a subject with a lateral movement into a clear composition.",
    body: [
      "Reveal is suited to entrances, build corners, subject introductions, and before-and-after moments.",
      "Use the reveal side, start and end distance, height, and FOV to define the opening movement, then inspect collision warnings before writing it.",
    ],
  },
  {
    id: "crane",
    title: "Crane",
    group: "Shot guides",
    summary: "Move vertically to establish or return to a subject’s scale.",
    body: [
      "Crane Up rises through a scene; Crane Down lowers the camera toward the subject. Both keep a target-relative look-at point.",
      "Use start and end height with distance and FOV for tall structures, take-offs, landings, and scene transitions.",
    ],
  },
  {
    id: "spiral",
    title: "Spiral",
    group: "Shot guides",
    summary: "Combine orbiting and vertical movement in a single helical camera path.",
    body: [
      "Spiral changes height and distance while rotating around the subject, making it useful for landmarks, turns, and vertical reveals.",
      "Tune radius, start and end height, rotation rate, and FOV, then preview the complete path before generating it.",
    ],
  },
  {
    id: "static-tracking",
    title: "Static Tracking",
    group: "Shot guides",
    summary: "Hold a camera position while tracking the subject with the lens.",
    body: [
      "Static Tracking keeps the camera in place and updates its aim as the selected subject moves.",
      "It works well for arrivals, isolated action beats, and controlled close moments where camera travel would distract.",
    ],
  },
  {
    id: "side-tracking",
    title: "Side Tracking",
    group: "Shot guides",
    summary: "Track a subject in parallel for a clear profile composition.",
    body: [
      "Side Tracking maintains a lateral offset as the subject moves and is particularly useful for races and vehicle motion.",
      "Choose the side, distance, height, look-ahead, and FOV to avoid a generic rear follow view.",
    ],
  },
  {
    id: "chase",
    title: "Chase",
    group: "Shot guides",
    summary: "Follow behind a subject to make momentum and direction legible.",
    body: [
      "Chase uses a responsive trailing path with a controlled prediction lead. It is suitable for high speed, flight, departures, and action.",
      "Review high-speed or collision warnings in Preview Path; stable input sampling is still important for a smooth result.",
    ],
  },
  {
    id: "close-detail",
    title: "Close Detail",
    group: "Shot guides",
    summary: "Make a compact, detail-oriented composition around a subject or reference point.",
    body: [
      "Close Detail is designed for quieter beats, equipment, textures, and impact moments where a wide path would dilute the frame.",
      "Use a detail anchor, distance, height, duration, and a narrower FOV to shape the final composition.",
    ],
  },
  {
    id: "vehicle-profile",
    title: "Vehicle Profile",
    group: "Shot guides",
    summary: "Frame a vehicle with its movement direction and available anchors in mind.",
    body: [
      "Vehicle Profile can use profile style, anchor, lateral side, distance, height, and FOV for vehicle-aware camera work.",
      "Built-in and soft vehicle profiling can expose anchors such as front, rear, side, cockpit, wing, wheel, and coupling; named ecosystem providers remain optional planned work.",
    ],
  },
  {
    id: "collision-avoidance",
    title: "Collision avoidance",
    group: "Workflow",
    summary: "Review and correct camera paths against the local replay world.",
    body: [
      "Avoid mode can try height adjustment, lateral translation, orbit-radius reduction, path shortening, and inserted control points. It uses the locally loaded replay world and retains diagnostics for the path review.",
      "Clip and None are alternative obstacle-handling modes. Clip changes the local view of blockers rather than claiming that the camera path is physically clear; None leaves the path uncorrected.",
    ],
    steps: [
      "Generate or preview a shot with obstacle handling set to Avoid.",
      "Read any collision or visibility warnings on the generated path.",
      "Adjust framing, timing, or obstacle handling when a warning remains unresolved.",
      "Generate only after the preview is acceptable for the intended replay scene.",
    ],
  },
  {
    id: "generate-montage",
    title: "Generate Montage",
    group: "Workflow",
    summary: "Analyse a replay range locally and turn the result into an editable multi-shot plan.",
    body: [
      "The workflow separates replay-range selection, event analysis, scene detection, shot planning, preview, and timeline generation. All analysis uses local deterministic rules.",
      "A proposed plan can be reviewed, adjusted, and regenerated before a native timeline write is confirmed.",
    ],
    steps: [
      "Select one or more replay source regions and choose a montage preset.",
      "Analyse the replay and review detected events with their scoring reasons.",
      "Review the proposed shots, diversity, duration, and any technical warnings.",
      "Preview the montage, including the 9:16 safe-area guide for vertical presets when relevant.",
      "Confirm generation, resolve a timeline conflict if needed, then edit or undo the result in Flashback.",
    ],
  },
  {
    id: "projects",
    title: "Projects",
    group: "Workflow",
    summary: "Keep the current local workflow clear while dedicated project organisation remains on the roadmap.",
    body: [
      "CineWolf stores configuration and user presets locally. Montage highlights can persist per replay and be promoted to source regions.",
      "A first-class local project workflow is planned. The site intentionally does not present cloud sync or a completed project-management UI as an available feature.",
    ],
  },
  {
    id: "presets",
    title: "Presets",
    group: "Workflow",
    summary: "Start with a built-in montage format, then use validated local imports for custom work.",
    body: [
      "Built-in presets are 15 Seconds, 30 Seconds, 60 Seconds, Trailer, TikTok, YouTube Short, and Cinematic Showcase.",
      "Presets keep duration, aspect ratio, pacing, event weights, shot bounds, framing, movement intensity, cut frequency, and replay-speed preferences as separate values.",
    ],
    steps: [
      "Choose a built-in preset that matches the intended output pace and aspect ratio.",
      "Adjust the exposed controls for the selected replay, then analyse the range.",
      "Import a local validated user preset only when you want to reuse a custom configuration.",
    ],
  },
  {
    id: "flashback-integration",
    title: "Flashback integration",
    group: "Reference",
    summary: "Use the exact tested Flashback version for the guarded editor integration.",
    body: [
      `CineWolf ${compatibility.version} supports Flashback ${compatibility.flashbackVersion} exactly and writes native Camera, FOV, and replay-time keyframes after validation.`,
      "Flashback is external: CineWolf does not bundle, copy, or modify it. On an unsupported Flashback version, CineWolf disables its editor integration rather than guessing at incompatible internals.",
      "Flashback has no stable public API for native timeline event glyphs. CineWolf therefore shows events in its own mini-timeline and leaves native replay markers unchanged.",
    ],
  },
  {
    id: "troubleshooting",
    title: "Troubleshooting",
    group: "Reference",
    summary: "Check the tested stack, replay state, and warnings before treating a generated path as a bug.",
    body: [
      "If the CineWolf panel is unavailable, confirm the exact supported Minecraft, Fabric Loader, Fabric API, and Flashback versions first. The Flashback integration is intentionally version-gated.",
      "If a camera path has a collision warning, preview it again around the affected replay time and adjust framing or obstacle handling. Unresolved warnings are intentionally visible instead of silently ignored.",
      "Long ranges can take noticeable time because replay sampling safely seeks and restores local replay state. Keep the range focused when iterating on a shot or montage plan.",
    ],
    steps: [
      "Verify the installed stack against the Installation section.",
      "Open the replay in Flashback before looking for the CineWolf panel.",
      "Use Preview Path and the CineWolf event mini-timeline to inspect warnings and scoring reasons.",
      "Keep a backup of an important replay project before testing a new mod combination or replacing timeline keys.",
    ],
  },
];
