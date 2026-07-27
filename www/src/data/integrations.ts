export interface Integration {
  name: string;
  description: string;
  shots: string[];
  status: "Planned" | "Optional";
}

/**
 * Named PeterWolf ecosystem profiles are intentionally listed as future,
 * optional work. CineWolf 1.3.11 does not claim any named integration ships.
 */
export const integrations: Integration[] = [
  {
    name: "Minecart Chain Train",
    description:
      "Planned optional profile for locomotive, consist, and track-aware framing. It is not a current runtime dependency.",
    shots: [
      "Locomotive camera",
      "Between-wagons camera",
      "Train flyover",
      "Trackside camera",
      "Full train tracking",
    ],
    status: "Planned",
  },
  {
    name: "PeterWolf's Planes",
    description:
      "Planned optional profile for aircraft anchors and flight-specific framing. It is not a current runtime dependency.",
    shots: [
      "Wing camera",
      "Chase camera",
      "Flyby",
      "Runway camera",
      "Orbit during a turn",
      "Landing sequence",
    ],
    status: "Planned",
  },
  {
    name: "Zip-line",
    description:
      "Planned optional profile for cable movement and station-aware framing. It is not a current runtime dependency.",
    shots: ["Parallel tracking", "Low-angle view", "Top-down view", "Station-to-station flyby"],
    status: "Planned",
  },
  {
    name: "Blueprint Strings",
    description:
      "Planned optional profile for building presentation and before-and-after sequences. It is not a current runtime dependency.",
    shots: ["Building showcase", "360-degree orbit", "Vertical reveal", "Before-and-after sequence"],
    status: "Planned",
  },
];
