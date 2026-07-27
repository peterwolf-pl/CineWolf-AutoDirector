export interface Feature {
  title: string;
  description: string;
  detail: string;
  example: string;
  icon: string;
}

export const features: Feature[] = [
  {
    title: "Target selection",
    description: "Choose a replay subject with stable identity rather than aiming at a transient camera position.",
    detail:
      "CineWolf can begin from its own selection, the editor selection, the crosshair, or the spectated entity, then keeps the selected entity reference stable through replay sampling.",
    example: "Follow one player through a replay segment without manually keyframing each turn.",
    icon: "crosshair",
  },
  {
    title: "Camera path generation",
    description: "Generate one of fourteen camera-path styles from a target, timing, and framing controls.",
    detail:
      "Each generator validates its inputs, samples a path, calculates a look-at orientation, and prepares an editable result before a timeline write is confirmed.",
    example: "Build an Orbit for an introduction, then adjust radius and duration before generating it.",
    icon: "route",
  },
  {
    title: "Editable keyframes",
    description: "Generated camera work remains editable inside the Flashback replay editor.",
    detail:
      "CineWolf writes native Camera, FOV, and replay-time keyframes only after validation and conflict inspection; it does not lock the generated path into a video file.",
    example: "Generate a Flyby, then fine-tune a single keyframe in the editor timeline.",
    icon: "keyframes",
  },
  {
    title: "Collision avoidance",
    description: "Keep a planned camera path from blindly passing through the local replay world.",
    detail:
      "Avoid mode scores height adjustment, lateral translation, radius reduction, path shortening, and inserted control points. If no safe correction is found, CineWolf surfaces a warning instead of claiming the path is clear.",
    example: "Raise and reroute a tracking camera around a roofline instead of clipping through it.",
    icon: "shield",
  },
  {
    title: "Visibility analysis",
    description: "Evaluate whether the subject remains readable from the planned camera position.",
    detail:
      "The local-world pass checks camera clearance and focus visibility, while diagnostics retain visibility and collision notes for review.",
    example: "Spot a blocked camera angle before it is written to the replay timeline.",
    icon: "eye",
  },
  {
    title: "Framing analysis",
    description: "Use camera distance, FOV, subject movement, and lead space to shape a deliberate composition.",
    detail:
      "CineWolf keeps framing calculations separate from replay analysis so that changing a creative control does not imply invented replay events.",
    example: "Give a fast vehicle more lead space without turning a profile shot into a generic follow shot.",
    icon: "frame",
  },
  {
    title: "Structure framing",
    description: "Framing models account for the scale of a structure or selected area.",
    detail:
      "Structure-aware distance calculations and diagnostics exist in the camera model. First-class structure-selection controls remain a roadmap item, so the site does not present them as a completed selection workflow.",
    example: "Plan a wide Crane Up around a tall build using a structure framing reference.",
    icon: "building",
  },
  {
    title: "Group framing",
    description: "Evaluate a group’s spread and visible ratio instead of treating every shot as a single-subject close-up.",
    detail:
      "The framing model can describe group coverage and a primary focus. First-class group-selection controls are planned rather than represented as a shipped editor workflow.",
    example: "Review whether a wide shot still includes the important members of a moving group.",
    icon: "users",
  },
  {
    title: "Vehicle profiles",
    description: "Use vehicle-relative framing where the subject’s direction and anchors matter.",
    detail:
      "Built-in and soft profile logic can expose anchors such as front, rear, side, cockpit, wing, wheel, and coupling without creating a hard dependency on third-party vehicle mods.",
    example: "Use a Vehicle Profile shot for a minecart or boat instead of an ordinary entity offset.",
    icon: "vehicle",
  },
  {
    title: "Presets",
    description: "Start with seven built-in montage presets, then import validated local presets when needed.",
    detail:
      "Duration, aspect ratio, pacing, event priorities, shot bounds, framing, and replay-speed preferences are separate controls. Invalid imported presets are isolated rather than overwriting the built-ins.",
    example: "Use TikTok for a 9:16 guide or Cinematic Showcase for a longer, lower-cut sequence.",
    icon: "sliders",
  },
  {
    title: "Projects",
    description: "A dedicated local montage-project workflow is part of the product direction, not a cloud workspace.",
    detail:
      "Current replay work is configured and reviewed locally. The roadmap keeps project organisation and first-class selection workflows explicit so they are not mistaken for already released features.",
    example: "Keep future project notes, source regions, and preset choices alongside a local replay workflow.",
    icon: "folder",
  },
  {
    title: "Undo and recovery",
    description: "Treat one confirmed montage write as one guarded operation.",
    detail:
      "CineWolf validates before mutation, keeps a rollback path for a failed write, and permits its undo only while the expected editor state has not been changed by another timeline operation.",
    example: "Undo a generated montage without deleting unrelated keys outside the confirmed interval.",
    icon: "undo",
  },
  {
    title: "Timeline visualisation",
    description: "Review detected events and planned shots without modifying native replay markers.",
    detail:
      "CineWolf uses its own compact event mini-timeline because Flashback 0.41.1 does not expose a stable native timeline-extension API for third-party event glyphs.",
    example: "Inspect why a movement or block-activity event was ranked before you generate a montage.",
    icon: "timeline",
  },
  {
    title: "Debug diagnostics",
    description: "Expose practical evidence instead of hiding uncertain replay analysis behind a score.",
    detail:
      "Debug export records event strength, possible false-positive hints, visibility and collision diagnostics, and path redaction controls.",
    example: "Check why a weak event was selected before you keep it in an editable montage plan.",
    icon: "bug",
  },
  {
    title: "English and Polish localisation",
    description: "Use the editor in English or Polish without relying on an external service.",
    detail:
      "CineWolf ships localisation for its interface and recent montage, collision, and shot controls.",
    example: "Switch the Minecraft language and use CineWolf’s matching interface text.",
    icon: "language",
  },
  {
    title: "Local deterministic analysis",
    description: "Analyse replay activity with local, deterministic rules.",
    detail:
      "CineWolf processes replay samples on the local client. It uses no account, cloud processing, telemetry requirement, or external service for local functionality.",
    example: "Re-run the same replay range and preset with the same inputs for a stable planning result.",
    icon: "cpu",
  },
];
