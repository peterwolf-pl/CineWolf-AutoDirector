# Vehicle profiles

## Models

- `VehicleDescriptor` — generator-facing lightweight model
- `VehicleProfile` — rich planning model with state/capabilities/metadata
- `VehicleAnchor` / `VehicleAnchorKind` — framing anchors

## Resolution order

1. High-priority soft integrations (train/planes/zipline)
2. Builtin vanilla recognition
3. Soft-mod heuristics
4. Generic fallback (never crashes)
