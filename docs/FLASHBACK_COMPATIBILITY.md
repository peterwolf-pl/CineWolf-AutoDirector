# Flashback compatibility

CineWolf AutoDirector 2.0.33 targets **Flashback 0.43.4** as the recommended editor pin on Minecraft 26.3 and enables full integration on **Flashback 0.42.1 and newer 0.x**.

## Levels

| Level | Meaning |
| --- | --- |
| `SUPPORTED` | Flashback 0.42.1+ (0.x); mixins and writers enabled. Recommended pin: 0.43.4 |
| `PARTIALLY_SUPPORTED` | Reserved for future validated ranges |
| `EXPERIMENTAL` | Flashback 0.42.0 and legacy 0.41.x; editor integration stays off until validated |
| `UNSUPPORTED` | Other versions (including 1.0.0+); no risky integration |
| `MISSING` | Flashback not installed |

## Runtime behaviour

- Flashback is a **suggested** dependency (`fabric.mod.json`), not a hard crash dependency.
- When missing or unsupported, CineWolf still loads configuration and the integration API.
- Compatibility is logged **once**.
- A single red chat message explains the failure.
- Capability flags disable unsupported UI options with tooltips.

## Integration methods used on 0.42.1+ / 0.43.4

1. Public Flashback classes (`Flashback`, `ReplayServer`, `EditorState`, keyframe types)
2. Fabric client lifecycle/tick events
3. Narrow mixin accessors (`ReplayServerAccessor`)
4. Rendering mixin host (`ReplayUIMixin`)
5. CineWolf-owned timeline overlay (no Flashback timeline patch)

## Capability surface (0.42.1+ / 0.43.4)

Enabled: camera position/rotation (including camera roll), FOV, replay-time (Timelapse), entity-tracking keyframes (`TrackEntityKeyframe`), timeline selection, markers, overlay, native undo history, non-destructive preview, speed ramps via Timelapse, editor selection restore.

Disabled / not exposed: public custom metadata tracks.

After a successful montage write, CineWolf sets Flashback export I/O (`setExportTicks`) to the full native source interval occupied by the written Camera/FOV/Timelapse tracks so Start Export covers the whole montage.

## Updating support

A new Flashback **major** (1.x) requires source inspection, mixin validation, timeline transaction tests, and the full manual checklist before the version gate changes. Newer 0.x builds (0.43.4, 0.43.5, 0.44.x, …) are treated as supported until that major bump.
