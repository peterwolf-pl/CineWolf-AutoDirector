export interface Vehicle {
  name: string;
  description: string;
  anchors: string[];
  shots: string[];
  availability?: string;
}

export const vehicles: Vehicle[] = [
  {
    name: "Vanilla minecarts",
    description:
      "Minecart-aware framing can use the vehicle’s movement direction for profile, chase, and trackside-style compositions.",
    anchors: ["Front", "Rear", "Side", "Coupling", "Wheel"],
    shots: ["Vehicle Profile", "Side Tracking", "Chase", "Flyby"],
    availability: "Built-in vehicle profiling supports vanilla minecart-style entities.",
  },
  {
    name: "Minecart trains",
    description:
      "Long train compositions need anchors that communicate direction, consist length, and track context rather than a single generic offset.",
    anchors: ["Train front", "Train rear", "Coupling", "Side", "Trackside focus"],
    shots: ["Train flyover", "Trackside camera", "Vehicle Profile", "Side Tracking"],
    availability:
      "Generic train-like framing can be used when a vehicle is resolved; a named Minecart Chain Train provider is planned as an optional integration.",
  },
  {
    name: "Aircraft",
    description:
      "Aircraft compositions can favour direction, lead space, and anchor-based views over a standard entity follow path.",
    anchors: ["Cockpit", "Wing", "Engine", "Rear", "Runway focus"],
    shots: ["Wing camera", "Chase", "Flyby", "Runway camera", "Vehicle Profile"],
    availability:
      "Generic aircraft-style profiling is heuristic; a named aircraft provider is planned as an optional integration.",
  },
  {
    name: "Boats",
    description:
      "Boat footage benefits from lateral movement, a readable wake direction, and a low profile composition.",
    anchors: ["Front", "Rear", "Side", "Cockpit"],
    shots: ["Side Tracking", "Follow", "Flyby", "Vehicle Profile"],
    availability: "Built-in vehicle profiling recognises boat-style entities.",
  },
  {
    name: "Mounts",
    description:
      "Mounted movement can be framed around the rider, the mount, or the direction of travel.",
    anchors: ["Front", "Rear", "Side", "Cockpit"],
    shots: ["Follow", "Chase", "Side Tracking", "Close Detail"],
    availability: "Built-in vehicle profiling recognises common mount-style entities.",
  },
  {
    name: "Zip-lines",
    description:
      "Zip-line footage calls for parallel, low-angle, top-down, and station-to-station framing options.",
    anchors: ["Front", "Rear", "Side", "Station focus"],
    shots: ["Parallel tracking", "Low-angle view", "Top-down view", "Flyby"],
    availability:
      "A named zip-line provider is planned as an optional integration; this is not a compatibility claim for a specific mod release.",
  },
  {
    name: "Generic modded vehicles",
    description:
      "Soft providers can classify vehicle-like entities from available local metadata without making another mod a hard dependency.",
    anchors: ["Front", "Rear", "Side", "Center", "Custom"],
    shots: ["Vehicle Profile", "Side Tracking", "Chase", "Follow"],
    availability:
      "Support is profile- and heuristic-based, so unusual modded entities can require manual review of the generated framing.",
  },
  {
    name: "Third-party vehicle providers",
    description:
      "Provider-specific anchors can make a specialised vehicle composition more precise without coupling CineWolf to every vehicle mod.",
    anchors: ["Front", "Rear", "Side", "Cockpit", "Wing", "Engine", "Wheel", "Coupling"],
    shots: ["Vehicle Profile", "Trackside camera", "Wing camera", "Vehicle detail"],
    availability: "Provider integrations are optional and remain a planned expansion area.",
  },
];
