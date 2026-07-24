# Montage Engine 2.0

Package: `pl.peterwolf.cinewolf.montage.v2`

## Components

- `MontageStyleProfile` / `MontageStyleProfiles` — 11 built-in styles
- `NarrativePlanner` — optional narrative phases
- `DurationAllocator` — score/phase weighted durations
- `ShotDiversityPlanner` — anti-repetition shot selection
- `VehicleMontagePlanner` — vehicle templates and boosts
- `CapabilityAwareShotResolver` — Flashback capability filtering
- `MontageEngineV2` — facade over `DefaultMontagePlanner`

## Design

Engine 2.0 improves planning metadata and candidate selection. Atomic plan construction still reuses the deterministic 1.x planner so Flashback write contracts remain stable.
