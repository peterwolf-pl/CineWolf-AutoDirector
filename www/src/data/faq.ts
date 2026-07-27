import { compatibility } from "./compatibility";

export interface FaqItem {
  question: string;
  answer: string;
}

export const faq: FaqItem[] = [
  {
    question: "What is CineWolf AutoDirector?",
    answer:
      "CineWolf AutoDirector is a client-side Fabric extension for Flashback that turns replay activity into editable cinematic camera paths, shots, and multi-shot montage plans.",
  },
  {
    question: "Is CineWolf an orbit camera mod?",
    answer:
      "No. Orbit is one of fourteen shot generators. CineWolf also plans tracking, flyby, dolly, reveal, crane, spiral, close-detail, vehicle-profile, and montage workflows.",
  },
  {
    question: "Does CineWolf use AI?",
    answer:
      "No. Replay analysis and montage planning run locally using deterministic rules; CineWolf does not require an AI service or API.",
  },
  {
    question: "Does it require an external server?",
    answer:
      "No. CineWolf processes the replay locally on your client. It does not require an account, cloud processing, replay upload, or an external server for local functionality.",
  },
  {
    question: "Does it support Flashback?",
    answer: `Yes. CineWolf ${compatibility.version} supports Flashback ${compatibility.flashbackVersion} exactly.`,
  },
  {
    question: "Does it support ReplayMod?",
    answer: "CineWolf 2.0 focuses exclusively on Flashback.",
  },
  {
    question: "Which Minecraft versions are supported?",
    answer: `The tested baseline is Minecraft ${compatibility.minecraftVersion}, Fabric Loader ${compatibility.fabricLoader}, Fabric API ${compatibility.fabricApi}, and Flashback ${compatibility.flashbackVersion}.`,
  },
  {
    question: "Can generated shots be edited?",
    answer:
      "Yes. CineWolf generates native Camera, FOV, and replay-time keyframes that remain editable in the Flashback replay editor.",
  },
  {
    question: "Can CineWolf avoid walls and terrain?",
    answer:
      "With obstacle handling set to Avoid, CineWolf checks the locally loaded replay world and can try height adjustment, lateral translation, radius reduction, path shortening, and added control points. It shows a warning when no safe bounded correction is found.",
  },
  {
    question: "Can it create TikTok and YouTube Short shots?",
    answer:
      "Yes. The built-in TikTok and YouTube Short presets use 9:16 composition metadata and a safe-area guide. CineWolf does not crop, render, encode, or upload video.",
  },
  {
    question: "Can it film trains and aircraft?",
    answer:
      "CineWolf includes vehicle-aware framing and soft profile logic for vehicle-like entities. Named train and aircraft integrations are planned optional profiles, not current runtime dependencies.",
  },
  {
    question: "Does CineWolf upload my replay?",
    answer:
      "No. CineWolf does not upload replay data and does not require cloud processing or telemetry for local functionality.",
  },
  {
    question: "Can I create my own presets?",
    answer:
      "Yes. CineWolf supports validated local user-preset import and export. Built-in presets are protected, and invalid imported files are isolated instead of overwriting them.",
  },
  {
    question: "What happens if a generated path conflicts with existing keyframes?",
    answer:
      "CineWolf checks the affected timeline interval before writing. Cancel is the safe default; Add preserves existing keys, while Replace is constrained to the confirmed interval.",
  },
  {
    question: "Can I undo a generated montage?",
    answer:
      "Yes. A confirmed montage is written as one guarded logical operation. CineWolf can undo it while the expected editor state has not been changed by another timeline action.",
  },
  {
    question: "Can I select a group or a structure?",
    answer:
      "CineWolf has group and structure framing models, but first-class group and structure selection controls are shown on the roadmap rather than presented as a completed editor workflow.",
  },
];
