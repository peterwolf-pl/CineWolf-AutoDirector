# Flashback compatibility

CineWolf AutoDirector 2.0 targets **Flashback 0.41.1** exactly for full editor integration.

## Levels

| Level | Meaning |
| --- | --- |
| `SUPPORTED` | Exact supported version; mixins and writers enabled |
| `PARTIALLY_SUPPORTED` | Reserved for future validated ranges |
| `EXPERIMENTAL` | Nearby 0.41.x patch; editor integration stays off until validated |
| `UNSUPPORTED` | Other versions; no risky integration |
| `MISSING` | Flashback not installed |

## Runtime behaviour

- Flashback is a **suggested** dependency (`fabric.mod.json`), not a hard crash dependency.
- When missing or unsupported, CineWolf still loads configuration and the integration API.
- Compatibility is logged **once**.
- A single red chat message explains the failure.
- Capability flags disable unsupported UI options with tooltips.

## Integration methods used on 0.41.1

1. Public Flashback classes (`Flashback`, `ReplayServer`, `EditorState`, keyframe types)
2. Fabric client lifecycle/tick events
3. Narrow mixin accessors (`ReplayServerAccessor`)
4. Rendering mixin host (`ReplayUIMixin`)
5. CineWolf-owned timeline overlay (no Flashback timeline patch)

## Capability surface (0.41.1)

Enabled: camera position/rotation, FOV, replay-time (Timelapse), timeline selection, markers, overlay, native undo history, non-destructive preview, speed ramps via Timelapse, editor selection restore.

Disabled / not exposed: entity-tracking keyframes, independent roll track, public custom metadata tracks.

## Updating support

A new Flashback release requires source inspection, mixin validation, timeline transaction tests, and the full manual checklist before the version gate changes.
