# Roadmap

## Version 1.2.0 — Generate Montage

Version 1.2.0 introduces the deterministic local montage foundation:

- Cancellable coarse-to-detailed sampling and immutable replay snapshots.
- Movement, event evidence, merging, scoring, ranking, and scene/planning models.
- Seven data-driven presets and a registry prepared for future user-defined presets.
- Reuse of Orbit, Follow, Flyby, Dolly In, and Dolly Out generators.
- Multi-shot preview, vertical safe-area metadata, local collision adjustment, conflict handling, and one logical undo boundary.
- No AI, cloud service, external processing, telemetry, or upload path.

Known 1.2 constraints are deliberate: the native montage uses one continuous chronological source window because Flashback 0.41.1 cannot represent a stable source edit list; event markers live on a CineWolf mini-timeline rather than Flashback's native timeline; vertical output is composition metadata and a guide rather than a renderer/exporter.

## Version 1.3

- Add Reveal, Crane Up/Down, Spiral, Static Tracking, Side Tracking, Chase, close-detail, and vehicle-profile generators.
- Add stronger visibility/framing analysis for groups, large structures, and modded vehicles.
- Improve collision strategies with lateral translation, radius reduction, path shortening, and inserted control points.
- Add replay-specific montage project persistence and user-defined preset import/export after schema hardening.
- Add richer non-destructive camera playback for montage preview.
- Investigate safe native-timeline event visualization if Flashback exposes a supported extension surface.
- Improve debug JSON export and false-positive diagnostics.

## Version 2.0

- Implement a separate `ReplayModReplayEditorAdapter` under the reserved `integration.replaymod` package.
- Add third-party camera/event/profile APIs.
- Add community preset libraries with explicit local import and validation.
- Add group, structure, building-volume, and selected-area targets.
- Revisit true source edit lists only when the replay backend can represent them safely and atomically.

## PeterWolf profile providers

Future soft providers can implement `CineWolfProfileProvider` without creating hard dependencies:

- Minecart Chain Train: locomotive, between-wagons, train flyover, trackside, and whole-train movement.
- PeterWolf's Planes: wing, chase, aircraft flyby, runway, turning orbit, and landing establishing shots.
- Zip-line: parallel tracking, low-angle, top-down, and station-to-station flyby.
- Blueprint Strings: build presentation, 360-degree orbit, vertical reveal, and before/after sequences.
