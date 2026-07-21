# Montage analysis

## Scope

Generate Montage analyzes a selected Flashback replay range locally and deterministically. It does not render video and does not call AI, cloud, telemetry, or external processing services. Its output is a bounded immutable analysis result used by scene segmentation and montage planning.

## Pipeline

The complete workflow is split into explicit stages:

1. **Range validation** — require a forward, in-bounds Flashback In/Out range.
2. **Target discovery** — retain selected UUID targets or rank a bounded set of automatically discovered entities, preferring players when evidence scores are close.
3. **Coarse sampling** — seek at a low cadence to find motion, state changes, markers, and action-bearing windows.
4. **Candidate window selection** — pad active ticks, merge overlapping windows, and skip inactive spans.
5. **Detailed sampling** — resample only candidate windows at the configured higher cadence.
6. **Movement metrics** — calculate derivatives, smoothed motion, headings, altitude, and stationary duration.
7. **Event detection** — run enabled detectors over immutable sample windows.
8. **Event merging and deduplication** — combine the same underlying action and annotate related event types.
9. **Event scoring and ranking** — apply preset weights, bonuses, penalties, and stable tie-breaking.
10. **Scene segmentation** — split on meaningful time, position, dimension, marker, activity, and state transitions.
11. **Planning handoff** — publish immutable scenes, ranked events, target rankings, warnings, and statistics.

Progress uses named `AnalysisStage` values. Cancellation is checked between stages and inside long loops. A cancelled generation is not published as a partial successful result.

## Threading and replay safety

Flashback state is read only on the Minecraft client thread:

- Save original replay tick and pause state.
- Pause and seek to a requested tick.
- Wait until the tick matches and Flashback is no longer processing a snapshot or fast-forwarding.
- Copy entity state into `ReplayEntitySnapshot`; never retain `Entity` or `ClientLevel` references.
- Attach generation-scoped packet evidence and read-only replay marker snapshots.
- Restore the original replay state on completion, cancellation, failure, settings change, or replay close.

Metrics, detection, merging, scoring, segmentation, and planning operate on copied records away from the rendering callback. The render thread never executes a full analysis pass.

## Sampling strategy

User-facing defaults are:

- Coarse pass: 4 samples per second, configurable from 2 to 5.
- Detailed pass: 16 samples per second, configurable from 10 to 20.
- Maximum tracked entities: 16.
- Maximum total samples: 6,000.
- Maximum detected events: 512.
- Maximum planned shots: 16.
- Maximum montage keyframes: 2,000.

The selector preserves the first/last sample and every sample containing direct actions or markers. An active coarse sample opens a detail window with bounded padding; overlapping windows are merged. Samples are sorted by replay tick and duplicate ticks are replaced deterministically.

These limits are configuration safeguards, not targets. Analysis may use fewer samples after inactive-window elimination.

## Replay snapshots

Each `ReplaySample` contains:

- source replay tick;
- a deterministic target-to-entity snapshot map;
- zero or more replay marker snapshots;
- zero or more typed observed replay actions.

An entity snapshot can include pose, bounding box, movement, entity type, dimension, health availability, hurt/attack/swing state, alive/on-ground state, ground proximity, vehicle UUID/type, creative flight, and Elytra flight. Unknown data remains explicitly unknown; it is not replaced with a fabricated action.

Packet evidence uses typed records for block placement/destruction, projectile lifecycle, and combat attack/damage/death signals. Capture is active only for the current generation and selected range.

## Movement metrics

For every stable target UUID, the calculator emits:

- displacement;
- raw and three-sample-smoothed velocity;
- raw and smoothed speed;
- scalar acceleration/deceleration;
- vertical speed and altitude;
- horizontal heading, heading delta, and angular velocity;
- stationary duration;
- ground proximity;
- derivative method (`CENTRAL`, `FORWARD`, `BACKWARD`, or `UNAVAILABLE`).

Central differences use the observations on both sides. Forward/backward differences are used at range boundaries. A missing or non-positive time delta produces a safe zero derivative. Teleports and dimensions are scene/discontinuity signals and must not be treated as ordinary high speed.

## Target discovery

Automatic discovery is bounded before detailed sampling. Candidate ranking can use:

- visible sample count and duration;
- amount of non-trivial motion;
- important event participation;
- direct combat, vehicle, flight, and block-action evidence;
- marker proximity and event-cluster centrality;
- player status as a tie preference.

The selected-target setting always overrides automatic ranking. Missing targets are represented as absent at that sample; one entity disappearing must not automatically abort analysis of every other target.

## Determinism

Deterministic behavior depends on:

- stable UUID-based target references and event IDs;
- replay-time ordering before calculations;
- enum/UUID tie-breakers rather than hash-map iteration order;
- versioned thresholds and preset weights;
- no wall-clock values in scoring or planning;
- immutable result collections.

The same snapshot sequence and settings must produce the same metrics, events, ranks, scenes, and planned order.

## Cancellation and lifecycle

Changing range, target, preset, sensitivity, event switches, or sampling limits invalidates the active generation. Closing the replay:

- invalidates its cancellation token;
- ends packet capture;
- restores or abandons seek restoration only when the old replay server still exists;
- clears sample/pose/action caches;
- discards worker results carrying an old generation ID;
- releases preview and vertical-overlay state.

No world or entity reference is retained after replay exit.

## Diagnostics

Normal logs contain stage durations and aggregate counts, not every sample. Useful aggregate fields include sample count, target count, event counts by type, merge/dedup counts, scene count, rejected events, planned shots, fallback decisions, collision warnings, simplified keyframe count, and timeline write result.

When debug JSON export is enabled, the export is local and should contain schema/version data, request settings, target ranking, event summaries/evidence/scores, scene boundaries, montage plan, planning reasons, and warnings. Raw high-frequency sample arrays are omitted unless explicitly needed. Entity names already present in the replay are the only potentially identifying values; nothing is uploaded.

## Limitations

- Seeking many ticks can be slow because Flashback 0.41.1 exposes no isolated arbitrary-time query API.
- Direct packet signals depend on what the replay recorded and what the supported Flashback version replays through the observed handler.
- Unknown health, ownership, vehicle, ground, or flight state lowers confidence or suppresses an event.
- Dimension transitions, unloads, replay jumps, and temporary absence must not be classified as death without additional evidence.
- Sampling caps can miss very short actions; the result reports this as a warning rather than implying complete coverage.
