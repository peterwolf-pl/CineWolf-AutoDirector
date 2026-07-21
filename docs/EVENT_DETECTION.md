# Event detection

## Event contract

Every event contains a deterministic ID, type, start/peak/end replay ticks, targets, location, normalized magnitude, normalized confidence, and typed evidence. Evidence records one or more sources:

- `DIRECT_ACTION` — an observed replay action such as damage or block change;
- `ENTITY_STATE` — a reliable snapshot transition such as health, vehicle, alive, or explicit flight state;
- `DERIVED_MOVEMENT` — deterministic inference from sampled movement;
- `REPLAY_MARKER` — read-only Flashback marker data;
- `AGGREGATED_ACTIONS` — several related actions grouped in time/space.

Measurements store value, unit, comparison, and the threshold used. Stable attributes are typed name/value pairs. Unsupported or unavailable evidence is never fabricated.

## Supported event vocabulary

| Event type | Primary evidence | Conservative rule |
| --- | --- | --- |
| `POSITION_CHANGE` | Derived movement | Group meaningful displacement above the configured distance |
| `HIGH_SPEED` | Smoothed speed + entity/vehicle/flight context | Use contextual thresholds and group consecutive samples |
| `ACCELERATION` | Smoothed-speed derivative | Positive acceleration above threshold for a meaningful span |
| `DECELERATION` | Smoothed-speed derivative | Negative acceleration above magnitude threshold |
| `SHARP_TURN` | Heading delta/angular velocity | Require horizontal movement above the minimum speed |
| `ALTITUDE_GAIN` | Vertical speed + height change | Sustained climb above speed and total-change thresholds |
| `ALTITUDE_LOSS` | Vertical speed + height change | Sustained descent above speed and total-change thresholds |
| `COMBAT` | Attack/damage/projectile packets, health/state, constrained inference | Require a direct signal or multiple supporting facts; proximity alone is insufficient |
| `DAMAGE` | Damage packet, health drop, hurt state | Keep separate from combat; merge repeated minor hits briefly |
| `DEATH` | Explicit death signal or reliable alive/health transition | Never equate unload, dimension transition, or seek absence with death |
| `VEHICLE_ENTER` | Passenger/vehicle UUID transition | Previous no vehicle, current known vehicle |
| `VEHICLE_EXIT` | Passenger/vehicle UUID transition | Previous known vehicle, current no vehicle |
| `VEHICLE_MOVEMENT` | Vehicle state + movement metrics | Group continuous movement and retain vehicle type/identifier when known |
| `FLIGHT_START` | Explicit flight or sustained airborne transition | Require more than a normal jump |
| `FLIGHT` | Explicit flight or sustained airborne speed/clearance | Group the airborne sequence and state inference source |
| `LANDING` | End of flight + ground/on-ground transition | Require falling/airborne context followed by ground proximity |
| `BLOCK_PLACEMENT` | Direct block-change action | Group nearby changes by time, space, and actor when known |
| `BLOCK_DESTRUCTION` | Direct block-change action | Group nearby changes; do not emit one montage event per block |
| `PAUSE` | Stationary duration + absence of strong action | Require speed below the pause limit for the configured duration |
| `REPLAY_MARKER` | Flashback replay metadata | One read-only CineWolf event per included native marker |

## Default thresholds

Core defaults before sensitivity scaling include:

- Position change: 1.5 blocks.
- Player high speed: 5.5 blocks/s.
- Vehicle high speed: 8.0 blocks/s.
- Flight high speed: 7.0 blocks/s.
- Acceleration magnitude: 3.0 blocks/s².
- Turn: 45 degrees while moving at least 1.0 blocks/s.
- Altitude: 1.0 blocks/s vertical speed and 2.0 blocks total change.
- Pause: at most 0.15 blocks/s for at least 40 ticks.
- Combat proximity support: 8 blocks.
- Flight: at least 20 ticks and 1.5 blocks inferred clearance.
- Landing ground proximity: 0.6 blocks.
- Block grouping: 40 ticks and 8 blocks.
- Same-event merge: 12 ticks and 16 blocks.
- Teleport discriminator: 32 blocks between relevant samples.

Sensitivity is clamped to `[0,1]` and scales thresholds deterministically. Higher sensitivity lowers a positive detection threshold; it does not bypass required evidence.

## Speed and movement context

Speed thresholds distinguish ordinary player movement, vehicle movement, and flight. A modded entity with unknown context uses conservative generic values. The detector peak is the maximum relevant smoothed measurement inside a contiguous active segment.

Turn detection uses smoothed horizontal direction. Near-stationary yaw changes do not become movement turns. Altitude events require both vertical speed and net elevation change so seek interpolation noise does not create climbs or descents.

## Combat and death

Direct replay packet evidence has the highest confidence. Health drops and hurt state can support damage. Attack animation, projectile lifecycle, known attacker/victim relationships, and nearby hostile activity can support combat, but ordinary nearby entities alone cannot.

Death requires an explicit signal or a reliable alive/health transition. Entity absence following a dimension change, teleport, replay seek, chunk unload, or temporary render-list omission is not sufficient.

## Vehicles and flight

Vehicle events store vehicle UUID/type when available but do not introduce hard dependencies on optional mods. Minecarts, boats, horses, and generic modded vehicles use the same neutral snapshot boundary. Optional profile providers can add better type-specific semantics later.

Flight uses explicit creative/Elytra state when available. Otherwise sustained airborne movement, ground clearance, vertical behavior, and vehicle state can produce a lower-confidence inference. A normal jump should fail the minimum-duration/clearance evidence. Landing requires a preceding flight/airborne sequence.

## Block activity

Individual block actions are grouped when their times, positions, and actors are compatible. Aggregates can retain count, bounds, rate, dominant block types, and placement/destruction balance. Replay snapshot reconstruction is excluded from capture so loading a state does not resemble building activity.

## Merging and deduplication

Same-type events merge only when:

- the time gap is inside the configured limit;
- targets overlap, unless one event is intentionally targetless;
- locations are inside the merge radius;
- the type is not `REPLAY_MARKER`.

The merged event keeps the strongest magnitude/confidence peak, unions targets/evidence, and uses a confidence-weighted location. Equivalent same-type peaks within one tick are deduplicated by `0.6 × confidence + 0.4 × magnitude`.

Related event pairs are annotated rather than blindly collapsed. Current relationships include acceleration/high speed, vehicle movement/high speed, flight/high speed, flight start/altitude gain, flight/altitude movement, flight/landing, combat/damage, combat/death, and vehicle movement/sharp turn.

## Scoring

The scorer uses:

```text
final = importanceWeight × importance
      + cinematicWeight × cinematic
      + uniquenessWeight × uniqueness
      + presetWeight × presetCompatibility
      + markerBonus
      + selectedTargetBonus
      - repetitionPenalty
      - technicalRiskPenalty
```

Default component weights are `0.35`, `0.25`, `0.15`, and `0.25`. Marker and selected-target bonuses default to `0.15` and `0.10`; repetition and technical-risk penalties default to `0.08` and `0.20`. A marker within 40 ticks can provide the marker bonus. Technical risk grows as confidence falls.

Every scored event stores numeric reasons. Stable ordering and UUID-based event IDs make ties reproducible.

## Known false positives and negatives

- Interpolated or low-rate replay motion can blur short acceleration and turn peaks.
- Modded vehicle/flight state may be unknown, producing conservative movement-only inference.
- Health changes caused by commands or non-combat mechanics can resemble damage unless packet context disambiguates them.
- A long jump or fall can resemble flight when ground state is unavailable; duration/clearance reduce but cannot eliminate this risk.
- Dense redstone/world updates can resemble player block activity if actor evidence is missing.
- Packet/action capture cannot recover facts absent from the recorded replay.

The UI should expose confidence, source, measurements, thresholds, and warnings so the user can reject an incorrect event or generated shot.
