export type RoadmapStatus = "Released" | "In Development" | "Planned" | "Experimental";

export interface RoadmapItem {
  version: string;
  status: RoadmapStatus;
  features: string[];
  focus: string;
  limitations: string[];
}

export const roadmap: RoadmapItem[] = [
  {
    version: "0.1",
    status: "Released",
    features: ["Core camera shots", "Replay target selection", "3D path preview", "Editable camera and FOV keyframes"],
    focus: "The first local, client-side camera-path workflow for Flashback.",
    limitations: ["The early release intentionally did not include collision avoidance."],
  },
  {
    version: "1.1",
    status: "Experimental",
    features: ["Collision-aware camera-path work", "Local-world clearance checks", "Continuous correction recovery"],
    focus: "An experimental collision-aware path stage later incorporated into the released 1.2 and 1.3 series.",
    limitations: ["There is no confirmed standalone 1.1 release record; this stage is retained as historical context rather than a release claim."],
  },
  {
    version: "1.2",
    status: "Released",
    features: ["Generate Montage", "Local deterministic event analysis", "Seven montage presets", "Multi-shot preview and guarded undo"],
    focus: "Turn a selected replay range into an editable, evidence-backed shot plan.",
    limitations: ["Vertical formats provide composition guidance rather than video rendering or export."],
  },
  {
    version: "1.3.11",
    status: "Released",
    features: [
      "Fourteen registered shot generators",
      "Advanced framing and vehicle profiles",
      "Montage highlight hotkeys and source regions",
      "Faster bounded detailed replay sampling",
    ],
    focus: "Expand creative coverage while keeping replay analysis, preview, and timeline writes local and deterministic.",
    limitations: [
      "First-class group and structure selection controls are not presented as a completed editor workflow.",
      "Named PeterWolf ecosystem profiles remain optional planned integrations.",
    ],
  },
  {
    version: "1.4",
    status: "Planned",
    features: [
      "First-class group selection",
      "First-class structure and area selection",
      "Local project organisation",
      "Expanded provider APIs",
    ],
    focus: "Turn existing framing models into clear, editable subject-selection workflows.",
    limitations: ["Planned scope is subject to change and is not a release promise."],
  },
  {
    version: "2.0",
    status: "Planned",
    features: ["Cinematic production platform direction", "Optional provider ecosystem", "Future workflow research"],
    focus: "Explore a broader, reliable production workflow without compromising local control or editable output.",
    limitations: ["This is a long-range direction, not a confirmed feature list or delivery date."],
  },
];
