# Changelog

## 2.0.22 - 2026-07-27

- Fixed Flashback exports ending at a discontinuous source cut and omitting every later montage shot
- Source-time cuts now use a one-second Timelapse bridge, preventing frame-by-frame export stepping from overshooting the I–O end
- Added a 24 FPS regression reproducing the reported `140–875` export: all 181 frames of the final shot are now reached

## 2.0.21 - 2026-07-27

- Fixed a render-thread deadlock when finalizing a generated montage in Flashback 0.41.1
- The montage writer now updates Flashback's export range through the already write-locked scene instead of trying to acquire the same non-reentrant lock twice
- Synchronized the version shown in CineWolf startup logs with the packaged mod version

## 2.0.20 - 2026-07-26

- Updated top-right video logo/watermark to use transparent background (`icon_trans.png`)
- After writing a montage to Flashback, set export I/O to the full montage source range so Start Export covers every written keyframe
- Flashback 0.41.1 capabilities: camera **roll** and **entity tracking** are available (API present); stop showing permanent “Unavailable …” banners for them

## 2.0.19 - 2026-07-25

- Activity detection for survival gameplay:
  - **Tree cutting** — grouped log/wood/stem destruction
  - **Farming** — planting and harvesting crops/farmland
  - **Mining** — stone/ore destruction groups
  - **Exploration** — sustained moderate on-foot sightseeing movement
- Specialized events replace generic block placement/destruction when block-type evidence is clear; controlled by the existing “Include Building Events” toggle (except exploration, which is always available with movement analysis)

## 2.0.5 - 2026-07-25

- New shot / setting **3rd Person**: camera rides **another player's head/eyes** and tracks the main subject
  - Generate Montage toggle: “3rd person (from another player)”
  - auto-picks the nearest/most-present other player in the analysis window as camera host
  - available as shot type `THIRD_PERSON` for manual selection

## 2.0.4 - 2026-07-25

- Collision UI now shows **why** samples could not be cleared (probe budget / no safe candidate / continuity, with counts)
- Indoor scene heuristics:
  1. smaller distance/height framing indoors
  2. prefer **Room Corner / Static Tracking** over Orbit/Crane/Flyby
  3. when Obstacle is **AVOID** but path looks indoor → relax to **ceiling + CLIP** (hide occluders) instead of thrashing AVOID

## 2.0.3 - 2026-07-25

- New shot **Room Corner** (`ROOM_CORNER`): for closed rooms, camera sits in a corner at the player’s **eye height** and tracks them (CCTV-style)
  - path bounds estimate a room corner; client world pass snaps to real walls when available
  - preferred by planner for pause / building / indoor-like events

## 2.0.2 - 2026-07-25

- Indoor ceiling clearance: when a solid roof sits above the subject, the camera is pulled just under it (not through the ceiling)
  - applies during the world collision pass (AVOID) and as a lightweight ceiling-only pass otherwise
  - probes both the column above the player’s head and the camera column

## 2.0.1 - 2026-07-25

- Fixed H/J/K montage marks during live Flashback recording:
  - moments now use the real recorder tick (`writtenTicks`) instead of always tick `0`
  - highlights are stored under Flashback’s recording UUID so they reappear when the finished replay is opened
- User H/J/K moments are force-kept in montage analysis (sampling windows + REPLAY_MARKER events) and pinned first when planning shots
- Native Flashback markers written by CineWolf (`CineWolf: …` / fragment start-end) feed the montage even if the highlight store is empty or ambient markers are disabled

## 2.0.0 - 2026-07-24

CineWolf AutoDirector 2.0 is a Flashback-only cinematic production platform.

### Flashback integration
- Strengthened `FlashbackReplayEditorAdapter` boundary with capabilities, compatibility status, replay identity, playback state, and transaction diagnostics.
- Versioned `FlashbackCompatibilityRegistry` with `SUPPORTED` / `EXPERIMENTAL` / `UNSUPPORTED` / `MISSING` levels.
- Flashback is a soft `suggests` dependency; CineWolf loads without crashing when Flashback is missing.
- Capability-aware camera writes; unsupported options are disabled with clear messaging.
- Atomic write path captures pre-write snapshots via `FlashbackTransactionManager`.

### Public API 2.0
- Stable package `pl.peterwolf.cinewolf.api.v2` with `CineWolfIntegration`, registration context, and isolated `CineWolfIntegrationManager`.
- Failing third-party integrations never disable the rest of CineWolf.

### Vehicles
- Rich `VehicleProfile` model with state, capabilities, oriented bounds, and expanded anchors.
- Categories: train/convoy/mount/ground vehicle/zip-line and existing types.
- Soft PeterWolf integrations: Minecart Chain Train, Planes, Zip-line (no hard dependencies).
- Generic fallback for unknown modded vehicles.

### Montage Engine 2.0
- Style profiles (Clean Cinematic, High Energy, Documentary, Vehicle/Architecture Showcase, Trailer, Vertical Fast Cut, Slow Atmospheric, Action Tracking, Train Journey, Flight Showcase).
- Narrative phase planning, duration allocation, shot diversity, vehicle-aware templates, capability-aware shot resolution.

### Community presets & projects
- Local community preset library with validated bundles, search/filter, favourites, ratings (no network).
- `CineWolfProjectV2` with replay identity, migration from schema 1, autosave, and recovery.

### Diagnostics & preview
- Diagnostics export v2 with integration/timeline/event false-positive hints and redaction.
- Montage preview loop modes and playback speed controls.
- Status header shows Flashback compatibility and capability limitations.

### Removed / out of scope
- No ReplayMod package, adapters, or future-support placeholders.

## 1.3.11 - 2026-07-24

- Hotkeys to mark montage moments/fragments while watching a replay (or recording):
  - **H** mark moment (±1.5 s window, optional Flashback marker)
  - **J** start/end fragment
  - **K** cancel unfinished fragment
- Highlights persist per replay and appear in Generate Montage; can be promoted to source regions.
- Detailed sampling is much faster on long replays:
  - default detailed rate **16 → 10**/s
  - hard cap `maximumDetailedSamples` (default **360**)
  - coverage budget `maximumDetailedCoverageFraction` (default **35%**)
  - adaptive seek interval + prefer high-signal compact windows
  - UI knobs for the new limits

## 1.3.10 - 2026-07-24

- Added independent **camera target / aim** path-smoothing controls: target strength, target window, and target outlier rejection (UI + config).
- Multi-region source montage: pick discontinuous replay windows (e.g. 10 s start + 10 s middle + 10 s end) and assemble them into one continuous output.
- Generate Montage UI: add Flashback selection as a region, suggest start/middle/end thirds, seek/remove regions, seek planned shot source.
- Flashback Timelapse now bridges source cuts with a one-tick output advance instead of rejecting multi-region plans.
- Analysis samples only the selected regions; planner lays out each region and hard-cuts between them.

## 1.3.9 - 2026-07-24

- Added obstacle handling modes: **Avoid** (move camera), **Clip** (hide occluders between camera and subject), **None**.
- Clip mode keeps the generated camera path and removes blocking blocks from view at runtime (section rebuild + air substitution).
- Optional entity clip hides non-subject entities on the line of sight.
- Legacy `collisionAvoidance` boolean is migrated into the new mode enum.
- UI controls on single-shot and Generate Montage panels (EN/PL).

## 1.3.8 - 2026-07-24

- Added montage shot preferences: enable/disable each generator type used by Analyze/Regenerate.
- Added min/max camera distance, height, orbit diameter, and montage look-ahead limits (config + Generate Montage UI).
- Planner and shot template resolver honor the allowed type set and clamp framing geometry to user limits.
- English and Polish localization for the new controls.

## 1.3.7 - 2026-07-24

- Fixed periodic “player yanked far away” camera pulses caused by isolated seek/interpolation pose spikes.
- Added `TargetPoseSanitizer` (applied inside `SampledTargetPoseResolver`) to rewrite out-and-back target samples.
- Clamp estimated target velocity (64 b/s) and avoid lerping camera targets through teleports.
- Cap look-ahead aim lead and chase prediction lead so one bad future sample cannot pull framing.
- Wider common-mode path jitter rejection (up to 6 blocks shared camera+aim pulse).
- Wait one extra client tick after replay seek before sampling entity poses; reject stale interpolation destinations >10 blocks from rendered pose.

## 1.3.6 - 2026-07-24

- Reduced intra-shot camera jumps from keyframe simplification, flight tracking, collision recovery, and look-at whip.
- `CameraPathSimplifier` now preserves look-at curvature, high angular-speed samples, and neighbors of collision-constrained keys.
- Added `CameraPathMotionLimiter` (position/look-at step caps + rate-limited yaw/pitch) after generation and after collision.
- Follow / Chase / Side Tracking: rate-limited direction changes, camera step caps at high speed, smoother chase distance/FOV.
- Look-at solver: near-pass damping and configurable max yaw/pitch rates.
- Collision continuity: slower recovery, longer hysteresis, rate-limited reorientation after multi-strategy correction.
- Tighter default keyframe tolerances and max keyframe interval (0.5 s) for smoother Flashback playback.
- Hard montage cuts between shots are unchanged.

## 1.3.5 - 2026-07-22

- Added nine shot generators: Reveal, Crane Up, Crane Down, Spiral, Static Tracking, Side Tracking, Chase, Close Detail, and Vehicle Profile.
- Expanded cinematic targets with group, structure, area, vehicle, and detail models plus provider-based vehicle anchors.
- Added reusable target visibility analysis, stronger framing validation, group visible ratio, structure framing distance, and vehicle lead-space scoring.
- Strengthened collision avoidance with scored strategies: lateral translation, orbit radius reduction, path shortening, and inserted control points.
- Extended montage planner and built-in presets to use the full generator library.
- Added user montage preset import/export with schema validation, checksums, quarantine of corrupt files, and protection against overwriting built-ins.
- Improved non-destructive montage playback (seek, shot navigation, preview cache, state restorer) without writing temporary native keyframes.
- Investigated Flashback 0.41.1 native timeline extension: unavailable; custom CineWolf event overlay remains the supported surface.
- Extended debug export with event strength (weak/probable/strong), false-positive hints, visibility/collision diagnostics, and path redaction.
- Registered all new shots in manual UI and localization (English/Polish).

## 1.3.2 - 2026-07-21


- Fixed montage analysis failing when the requested output was longer than the selected continuous source at the configured replay speed.
- Automatically fit the output duration to the maximum footage available from the selected source range while preserving chronological source time and replay-speed limits.
- Added a localized plan warning that reports both the requested and fitted output durations.
- Added a regression for the reported 195..628-tick source range with a 25-second, 1x Cinematic Showcase request.

## 1.3.1 - 2026-07-21

- Fixed periodic camera shake caused by sampling remote replay entities part-way through Minecraft's client interpolation steps.
- Sampled the stable interpolation destination for position and rotation while keeping focus points and bounding boxes aligned with that pose.
- Added low-amplitude common-mode pulse rejection for camera and aim paths, without flattening sustained direction reversals, discontinuities, collision anchors, or intentional shot motion.
- Added regressions for subtle five-tick-style replay jitter and genuine target reversals.

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
