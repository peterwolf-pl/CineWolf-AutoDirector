# Montage planner

## Inputs and output

The planner consumes:

- the validated source replay range;
- ranked targets, scored events, and deterministic scenes;
- a `MontagePreset` plus user overrides;
- the immutable supported shot types from `ShotGeneratorRegistry`;
- path feasibility, collision, duration, keyframe, and replay-speed limits;
- locked/manual edits from the current draft when regenerating.

It produces an editable ordered plan. Each shot records its source event, target, registered shot type, source interval, output start/duration, replay speed, framing, generated `ShotRequest`, enabled/locked state, warnings, and planning reasons.

## Narrative structure

The planner attempts a progression rather than simply sorting the top scores:

1. Introduction
2. Development
3. Main action
4. Climax
5. Resolution
6. Final shot

Short formats can combine stages. Trailer uses a slower introduction followed by rising action and a climax. Cinematic Showcase favors longer spatial presentation. TikTok and YouTube Short prioritize an early hook and centered vertical framing.

## Built-in preset behavior

- **15 Seconds:** 3–5 fast shots, one central event, short establishing opener, strong Dolly Out/Orbit finish.
- **30 Seconds:** 5–8 moderate shots covering an introduction, movement, primary/secondary events, and final wide view.
- **60 Seconds:** 8–14 narrative shots spanning setup, development, climax, detail, and resolution.
- **Trailer:** 7–12 progressively paced shots, high movement/combat/vehicle/flight weights, and optional bounded replay-speed changes. Its non-chronological preference is retained as preset intent, but Flashback 0.41.1 forces the generated native plan to remain chronological and CineWolf reports that limitation.
- **TikTok:** 7–12 short 9:16 shots, fast opening action, close/medium center-safe framing, high cut/movement intensity.
- **YouTube Short:** 7–12 9:16 shots, strong opening with calmer progression and a wider ending.
- **Cinematic Showcase:** 5–12 longer 16:9 shots, lower cut/movement intensity, high build/marker/pause value, wide intro/outro.

Duration and aspect ratio are independent. Selecting 9:16 does not silently replace the chosen duration.

## Shot selection and fallbacks

Version 1.2.0 can generate only registered Orbit, Follow, Flyby, Dolly In, and Dolly Out paths. Event-to-shot preferences therefore resolve as follows:

| Event/narrative need | Preferred current generators |
| --- | --- |
| Establishing/location/build | Dolly In, Orbit, Flyby |
| High speed/vehicle/flight | Follow, Flyby |
| Acceleration/deceleration | Follow, Dolly Out |
| Sharp turn | Orbit, Follow |
| Combat/damage/death | Follow, Orbit, short Flyby, Dolly Out |
| Landing | Flyby, Follow, Dolly Out |
| Pause/detail | Dolly In, Orbit |
| Final wide/hero | Dolly Out, Orbit |

The ordered template list is filtered through `supportedTypes()`. If the first choice is unavailable, the next supported type is used and the fallback is recorded. A plan fails clearly only when no compatible registered generator exists.

Names such as Reveal, Crane, Spiral, Static Tracking, Side Tracking, Chase, and Wing Profile describe future creative intent; version 1.2.0 does not pretend they are installed generators.

## Duration allocation

The planner first reserves enabled intro/outro templates, then allocates the remaining output budget among selected events. Every unlocked shot stays inside the preset/user minimum and maximum duration, and the enabled count respects both preset and global caps.

Rounding is deterministic. If the exact target cannot be reached without violating hard bounds, the planner reports the residual and adjusts the least important unlocked shots in stable order. Locked shots keep their durations; an impossible locked draft is rejected rather than silently rewritten.

## Diversity

Selection penalizes:

- consecutive identical shot types;
- repeated event categories or source moments;
- similar azimuth, distance, height, movement direction, and framing;
- excessive Orbit use;
- technical/collision risk and insufficient lead-in/out footage.

It rewards alternating wide/medium/close framing, elevation and direction changes, logical narrative progression, and compatibility between event motion and camera motion. Deterministic tie-breaking uses event rank, source peak time, shot type, and stable IDs.

## Output time, source time, and speed

The planner validates output time and source replay time separately. Every mapping must have positive output duration, positive playback speed, and non-decreasing source replay ticks.

Version 1.2.0 does not support reverse playback. Flashback 0.41.1 also lacks a stable true source edit-list abstraction for jumping between non-adjacent moments. CineWolf therefore selects one continuous source window, keeps adjacent mapping boundaries identical, and requires every assigned event peak to lie inside its shot.

Speed changes obey:

- configured minimum replay speed;
- configured maximum replay speed;
- maximum change between adjacent mappings;
- monotonic source progression;
- no slow motion for inactive transition footage.

Slow motion may be allocated around a high-scoring peak and faster playback may be allocated to lower-value portions of the same continuous window. Native Timelapse points use source ticks as key positions and elapsed output ticks as values. Audio is not processed and may sound accelerated/slowed in exported playback.

## Transitions

Version 1.2.0 writes hard camera cuts. `MATCH_CUT`, `MOTION_CUT`, dissolve, and fade can be planning metadata for future selection logic, but CineWolf does not render dissolves or fades.

The planner avoids cutting exactly through an event peak. Shot boundaries include enough lead-in/out footage when available.

## Editable draft behavior

Before insertion, each draft row exposes enabled state, order, source event/time, target, shot type, event score, duration, replay speed, framing, warnings, and planning reasons.

Supported edits include enable/disable, move, lock, replace type, change target/parameters, preview, remove, duplicate, and regenerate unlocked shots. Regeneration preserves locked rows exactly and rebalances only unlocked duration/order slots.

Detected events and shots appear on CineWolf's own compact mini-timeline. Clicking an event seeks to its peak and links to associated draft rows. Native Flashback replay markers are read-only and unchanged.

## Camera-path preparation

Confirmed enabled rows are processed in this order:

1. Validate every shot request against its sampled target cache.
2. Generate raw `CameraPathPlan` samples through the shared registry.
3. Run local-world collision adjustment when enabled.
4. Recompute look-at orientation for moved samples.
5. Simplify the adjusted path and enforce the global keyframe cap.
6. Validate vertical framing and replay-time mappings.
7. Build one conflict report and one atomic write plan.

No timeline write begins if a required shot fails.

## Conflict handling and undo

Conflict checking covers camera position/rotation, FOV, replay-time mappings, and known CineWolf output in the confirmed source interval. Available decisions are Cancel, Add while preserving existing keys, or Replace inside the confirmed interval. Cancel is the default. **Place After Last** is rejected for source-bound Flashback payloads because shifting native keys would change the replay moment they describe.

One montage is one native Flashback history transaction and one guarded CineWolf undo handle. A write failure restores the pre-write scene snapshot. Immediate undo delegates to native Flashback history when no intervening edit changed the state, restoring generated/replaced camera, FOV, Timelapse, and track metadata while leaving unrelated keys untouched.

## Limitations

- Only five camera generators are available in 1.2.0.
- Source cuts, reverse playback, and relocation are unavailable; the generated source window is continuous and chronological.
- Preview and timeline insertion depend on Flashback 0.41.1 internals guarded by an exact version gate.
- Vertical framing validation projects the tracked target bounding box through generated path geometry, but group and secondary-subject cropping still require manual overlay review and the check cannot guarantee an external exporter's final crop.
- Planner quality depends on evidence and sampling coverage; the user remains able to disable or replace every proposed shot.
