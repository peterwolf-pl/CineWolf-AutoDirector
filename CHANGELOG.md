# Changelog

## 1.3.0 - 2026-07-21

- Added configurable zero-phase camera-path smoothing for both single shots and generated montages.
- Added independent position and aim/rotation strengths plus a time-based smoothing window.
- Added deterministic rejection of isolated high-speed out-and-back position and aiming glitches while preserving sustained fast moves, real turns, shot endpoints, teleports, and collision anchors.
- Smoothed target-relative camera offsets without shrinking orbit radii or changing programmed Dolly distances.
- Added delayed hover descriptions for camera smoothing and the main montage settings and actions in English and Polish.
- Preserved completed replay analysis when only path-smoothing settings change; only generated paths and previews are invalidated.
- Bumped the configuration schema to version 4 and added safe migration/default normalization for the new settings.

## 1.2.2 - 2026-07-21

- Fixed montage generation aborting with `Historical collision checking did not complete safely` when a single replay sample had no continuous fully visible camera solution.
- Changed the correction threshold from a fatal predicted-path test into a bounded real camera movement limit; the camera now holds or retreats from its previous safe position instead of jumping branches.
- Restored combined raised/radial candidate searches when the basic collision search has no result.
- Added a continuity-preserving fallback for temporarily unresolved samples, keeping the previous collision offset instead of pulsing back to the raw path; only an unavailable replay world remains fatal.
- Added per-shot collision fallback diagnostics and synchronized the runtime version string with the packaged mod version.

## 1.2.1 - 2026-07-21

- Fixed severe camera shaking near walls by keeping collision corrections continuous between samples instead of independently snapping each sample back to the nominal path.
- Added collision-release hysteresis and a bounded cinematic recovery speed, while retaining clearance and focus-visibility checks for every adjusted sample.
- Stabilized Dolly In/Out heading with a windowed target travel direction so noisy per-tick replay velocity cannot flip the camera to the opposite side of the subject.
- Recalculated collision-adjusted look rotation sequentially and stopped treating an ordinary collision correction as a semantic cut/teleport.
- Preserved collision-constrained camera keys during simplification, bounded synchronous world probes, and rejected paths that cannot transition to a safe solution without a large branch jump.
- Added regressions for alternating collision states, gradual collision recovery, unresolved clearance, and noisy Dolly velocity.

## 1.2.0 - 2026-07-21

- Added the local deterministic **Generate Montage** workflow with cancellable coarse-to-detailed replay sampling.
- Added typed replay snapshots, movement metrics, event evidence, thresholds, confidence, merging, deduplication, scoring, and ranking foundations.
- Added movement, combat/damage/death, vehicle, flight, landing, block-action, pause, and replay-marker analysis paths where Flashback exposes reliable data.
- Added data-driven 15 Seconds, 30 Seconds, 60 Seconds, Trailer, TikTok, YouTube Short, and Cinematic Showcase presets.
- Added separate output duration, aspect ratio, pacing, shot bounds, intro/outro templates, framing, movement, cut, and replay-speed settings.
- Added registry capability queries so montage planning can use only the five currently registered shot generators.
- Added multi-shot path preview support, a 9:16 safe-area guide, local-world collision adjustment, and versioned montage configuration.
- Added Flashback replay-state readiness checks, replay marker snapshots, and local replay packet/action capture without AI, cloud services, telemetry, or uploads.
- Preserved continuous monotonic source time and encoded speed changes using Flashback's real native mapping: source replay ticks on the track axis and elapsed output ticks as Timelapse values. Unsupported source cuts, reverse playback, and source relocation are rejected.
- Kept event visualization inside CineWolf's own mini-timeline rather than modifying Flashback's native timeline or replay markers.
- Bumped the configuration schema to version 3, including persisted detector-threshold, event-scoring, and shot-diversity profiles while preserving existing manual-shot and valid montage settings.
- Expanded English/Polish localization, automated coverage, architecture documentation, and the 30-step montage manual checklist.

## 0.1.1 - 2026-07-21

- Fixed preview requests near the end of a replay by clamping Duration to the final available tick.
- Added a localized timeline notice when the requested duration is shortened.
- Added focused regression tests for selected and duration-based replay intervals.

## 0.1.0 - 2026-07-21

- Added the first CineWolf AutoDirector MVP for Minecraft 26.2, Fabric, and Flashback 0.41.1.
- Added Orbit, Follow, Flyby, Dolly In, and Dolly Out camera generators.
- Added stable replay target selection, cancellable bounded adaptive target sampling, 3D path preview, teleport-safe path simplification, native camera/FOV keyframe writing, conflict handling, and one-operation undo.
- Added a Flashback ImGui editor panel, versioned configuration, compatibility checks, English/Polish localization, automated core tests, and integration documentation.
- Collision avoidance was intentionally disabled in 0.1.x.
- ReplayMod support was deferred to a separate adapter planned for version 2.0.
