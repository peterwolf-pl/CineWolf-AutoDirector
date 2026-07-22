# Architecture

## Dependency direction

The deterministic camera, analysis, event, preset, scene, and planning core lives under `src/main/java` and has no Minecraft or Flashback imports. The client layer under `src/client/java` is responsible for seeking a replay, copying Minecraft state into immutable core models, rendering ImGui/world previews, resolving local-world collisions, and writing native Flashback keyframes.

```text
ReplayUIMixin
  -> AutoDirectorPanel
     -> manual-shot PreviewController
     -> montage UI/controller
        -> Flashback sampling state machine (client thread)
        -> immutable ReplaySample collection
        -> deterministic analysis worker
           -> movement metrics
           -> event detectors
           -> merge / deduplicate / score / rank
           -> scene segmentation
           -> montage planner
           -> ShotGeneratorRegistry
        -> zero-phase path smoothing + isolated-pulse rejection
        -> collision pass (loaded replay world)
        -> CameraPathSimplifier
        -> multi-shot preview + CineWolf event mini-timeline
        -> atomic Flashback write plan + guarded native undo handle
```

Client-only Flashback classes must never leak into records used by the analysis worker. `TargetReference`, `TargetPose`, `ReplaySample`, `ReplayEvent`, preset records, and montage plan records are immutable boundaries.

## Existing camera engine

`ShotRequest` describes one target, one registered shot type, its camera parameters, easing, output duration, and source replay interval. A `ReplayContext` supplies a `TargetPoseResolver`, bounded sampling settings, and optional adaptive ticks.

`CameraPathPlanner` resolves the request through `ShotGeneratorRegistry`. Version **1.3.5** registers fourteen generators:

- Orbit, Follow, Flyby, Dolly In, Dolly Out
- Reveal, Crane Up, Crane Down, Spiral
- Static Tracking, Side Tracking, Chase
- Close Detail, Vehicle Profile

`ShotRequest` may carry optional `ShotOptions` for generator-specific controls (reveal direction, tracking side, detail anchors, vehicle profile style, FOV endpoints, maintain-target-size, etc.) without breaking older call sites.

Every generator validates finite parameters and target availability, creates raw `CameraSample` values, bakes look-at rotation, and produces a `CameraPathPlan`. `CameraPathPlanner` then applies the shared pre-collision `CameraPathSmoother`. The centered time-window filter operates independently inside discontinuity-bounded segments, smooths look-at motion and target-relative camera offset, and recalculates finite orientation. Isolated-pulse rejection requires a large local residual, high speed on both sides, and a direction reversal, so it does not impose a global speed cap on legitimate Flyby shots. `CameraPathSimplifier` then applies positional Ramer–Douglas–Peucker reduction and preserves samples required by rotation error, FOV error, discontinuities, collision constraints, and maximum keyframe spacing.

The Flashback collision pass uses `CollisionPathContinuity` plus `CollisionStrategyResolver` (lateral translation, orbit radius reduction, path shortening, inserted control points) with scored diagnostics. Core geometry/visibility lives under `visibility/` and remains Flashback-free.

Expanded `CinematicTarget` types (entity/group/structure/area/vehicle/detail) and soft `VehicleProvider` hooks describe framing intent; runtime sampling still resolves through `TargetReference` + `TargetPoseResolver`.

The registry exposes `supports` and an immutable `supportedTypes` snapshot. Montage planning must select and fall back only inside this set.

## Replay sampling boundary

Flashback 0.41.1 has no independent arbitrary-timestamp entity query. The safe sequence is therefore:

1. Validate the selected range and remember the current replay tick and pause state.
2. Pause playback and begin a generation-scoped local action-capture session.
3. Seek to the next requested replay tick on the Minecraft client thread.
4. Wait until Flashback reports that snapshot processing and fast-forwarding are complete.
5. Copy bounded entity, marker, and action state into immutable `ReplaySample` values.
6. Repeat for coarse samples and detailed candidate windows.
7. Restore the original tick and pause state, including cancellation and failure paths.
8. Run metrics, detectors, scoring, scene segmentation, and planning on copied data off the render thread.

No worker retains `ClientLevel`, `Entity`, `ReplayServer`, or editor objects. Closing the replay invalidates the generation token, cancels analysis, clears action capture and caches, and prevents stale results from being published.

## Analysis core

`ReplayEntitySnapshot` enriches `TargetPose` with deterministic state usable by detectors, such as health availability, alive/on-ground state, ground proximity, vehicle identity, and explicit flight hints. `ObservedReplayAction` represents typed local packet evidence for block changes, projectiles, attacks, damage, and death. Replay markers are copied as read-only `ReplayMarkerSnapshot` values.

`MovementMetricsCalculator` groups observations by stable target UUID and calculates:

- displacement, raw and smoothed velocity, speed, and acceleration;
- vertical speed, altitude, heading, heading change, and angular velocity;
- stationary duration and ground proximity;
- the difference method used for each boundary or central derivative.

Central differences are used when both neighboring samples exist. Forward/backward differences are used at range edges. A deterministic three-sample weighted smoothing pass reduces seek/interpolation noise before event detection.

`CoarseDetailedSampleSelector` rate-limits the first pass, identifies active windows from state/action changes, adds bounded detail padding, merges overlapping windows, and keeps signal-bearing samples even when they fall between the regular cadence.

## Events and scoring

Detectors operate through `ReplayEventDetector` and produce typed `ReplayEvent` records. Each event carries a stable interval and peak, targets, location, magnitude, confidence, and `EventEvidence`. Evidence records the direct/derived source, measurements, thresholds, attributes, and related event types.

`EventMerger` merges nearby same-type events only when their target and spatial constraints match, deduplicates equivalent peaks, annotates related categories, and applies the configured event cap. `DefaultReplayEventScorer` calculates explicit importance, cinematic, uniqueness, and preset-compatibility components plus marker/selected-target bonuses and repetition/technical-risk penalties. It stores human-readable numeric reasons with every score.

Thresholds and scoring weights live in records rather than hidden UI constants. Detector sensitivity scales thresholds deterministically.

## Presets

`MontagePreset` separates duration, aspect ratio, pacing, shot duration/count bounds, supported shot preferences, event weights, intro/outro templates, framing, camera intensity, cut frequency, chronology, and replay-speed preferences.

`MontagePresetRegistry.createDefault()` installs seven immutable built-ins in stable order. A registry instance can accept an additional unique ID later, allowing future validated user-defined presets without a large preset switch statement. `MontageConfig` stores Gson-friendly detector, scoring, diversity, sampling, and performance overrides without overwriting legacy manual-shot fields. Schema version 4 also stores one shared pre-collision path-smoothing profile used by single shots and montage paths.

## Planning and time model

Planning consumes ranked events and scenes and emits an editable ordered montage. It balances preset compatibility, narrative position, duration budget, shot feasibility, target availability, and diversity. Locked user edits are inputs to regeneration and must not be replaced.

Four times remain distinct:

- source replay tick;
- local shot/output seconds;
- absolute montage camera-keyframe time;
- replay playback speed.

Source replay time must be continuous, strictly increasing at native key positions, and non-negative. Version 1.2.0 does not implement reverse playback or a true source edit list. The planner selects an event-bearing continuous source window and assigns every event peak to a shot inside that window. A preset that prefers non-chronological editing receives an explicit limitation warning and is kept chronological.

Flashback evaluates Camera, FOV, and Timelapse tracks on source replay ticks. A Timelapse keyframe value is elapsed output time in ticks. Between points `(sourceA, outputA)` and `(sourceB, outputB)`, Flashback derives `TPS = (sourceB - sourceA) / (outputB - outputA) * 20`. Camera/FOV interpolation is consequently remapped into output time by the native Timelapse track without relocating source-bound keys.

Hard cuts are the only generated transition. Match/motion/dissolve/fade may exist as planning metadata but are not rendered effects.

## Collision and simplification

Collision avoidance is a deterministic client-world pass after smoothing and raw path generation. `FlashbackWorldCollisionResolver` checks camera clearance and line of sight, first raising an obstructed sample and then trying bounded movement toward the focus point. It records adjusted and unresolved counts as path warnings.

Because collision can move samples and change look rotation, the final simplification pass must run after collision adjustment. Smoothing is deliberately not repeated after collision because averaging safe samples could move them back into geometry. Unresolved samples are never silently reported as safe.

## Preview and visualization

The world renderer can display several shot paths with bounded total samples and distinct colors. The vertical overlay draws a centered 9:16 frame plus configurable safe inset over the Flashback viewport; it does not change render resolution. For vertical output, a post-collision validator projects the tracked target bounding box at every camera sample into the intended 9:16 frustum and reports outside/unavailable samples.

Detected events are displayed on a compact CineWolf-owned mini-timeline inside the montage editor. This avoids patching Flashback's native timeline widgets and guarantees that native replay markers remain untouched.

Preview state is non-destructive and generation-scoped. Leaving preview restores the prior replay tick, pause state, selection, and temporary camera state.

## Timeline writing and undo

The UI submits a validated immutable write plan; it does not construct Flashback keyframes itself. Conflict inspection covers the source replay interval occupied by the native Camera/FOV/Timelapse tracks. Cancel remains the default; replacement is restricted to that exact user-confirmed interval.

All camera, FOV, Timelapse, and CineWolf track metadata mutations for one montage belong to one native Flashback history entry. Validation and camera generation complete before the first timeline mutation. A full scene snapshot is retained only as the authoritative rollback path if the native write itself throws, so a montage is never intentionally left half-written.

Explicit CineWolf undo is allowed only while the editor state, scene, modification counter, and generated track identities still match the completed operation. It then invokes Flashback's native scene undo, keeping the native history cursor aligned and restoring replaced keys. Unrelated keys outside the confirmed source interval are never deleted.

## Extension points

- `ShotGenerator` and `ShotGeneratorRegistry` add camera shot types.
- `ReplayEventDetector` adds deterministic detectors.
- `ReplayEventScorer` replaces or extends ranking policy.
- `MontagePresetRegistry` accepts future validated local presets.
- `ReplayEditorAdapter` isolates Flashback and a future ReplayMod backend.
- `CollisionResolver` isolates world-specific camera clearance.
- `CineWolfProfileProvider` reserves optional PeterWolf ecosystem profiles without hard dependencies.
