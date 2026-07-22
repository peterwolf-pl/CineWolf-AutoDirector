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

## Version 1.3.5 — complete

Shipped in 1.3.5:

- Reveal, Crane Up/Down, Spiral, Static Tracking, Side Tracking, Chase, Close Detail, Vehicle Profile generators.
- Visibility/framing analysis for groups, structures, and vehicles; soft vehicle providers.
- Collision strategies: lateral translation, radius reduction, path shortening, inserted control points.
- User preset import/export with schema validation and built-in protection.
- Richer non-destructive montage playback (seek, shot navigation, state restore).
- Flashback timeline investigation: native extension unavailable; custom overlay retained.
- Debug export with weak/probable/strong event strength and false-positive hints.

## Version 1.4 / 2.0

- Implement a separate `ReplayModReplayEditorAdapter` under the reserved `integration.replaymod` package.
- Add third-party camera/event/profile APIs and first-class group/structure selection UI.
- Add community preset libraries with explicit local import and validation.
- Deeper provider integrations (Minecart Chain Train, Planes, Zip-line, Blueprint Strings).
- Revisit true source edit lists only when the replay backend can represent them safely and atomically.
- Revisit native timeline glyphs only if Flashback publishes a stable extension surface.

## PeterWolf profile providers

Future soft providers can implement `CineWolfProfileProvider` without creating hard dependencies:

- Minecart Chain Train: locomotive, between-wagons, train flyover, trackside, and whole-train movement.
- PeterWolf's Planes: wing, chase, aircraft flyby, runway, turning orbit, and landing establishing shots.
- Zip-line: parallel tracking, low-angle, top-down, and station-to-station flyby.
- Blueprint Strings: build presentation, 360-degree orbit, vertical reveal, and before/after sequences.
