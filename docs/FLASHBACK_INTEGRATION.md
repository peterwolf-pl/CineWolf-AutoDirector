# Flashback integration

## Supported baseline

- Flashback: exactly 0.41.1
- Source revision inspected: `a612feaeb95ca0a65df884e8aa6e6d6c409ed3c1`
- Minecraft: 26.2
- External development dependency: `maven.modrinth:4das1Fjq:cGezdWTX`

Flashback remains external and is never shaded or copied into the CineWolf artifact.

## Used Flashback surfaces

| Flashback surface | Purpose |
| --- | --- |
| `Flashback.isInReplay()`, `Flashback.getReplayServer()` | Replay/editor availability and current replay server |
| `ReplayServer.getReplayTick()`, `getTotalReplayTicks()`, `goToReplayTick(int)` | Range validation and deterministic client-thread seeking |
| `ReplayServer.replayPaused` | Preserve and restore playback state |
| Replay-server rendering/snapshot/fast-forward state | Wait until a sought replay state is safe to copy |
| Replay metadata identifier and marker map | Stable local replay identity and read-only marker snapshots |
| `ReplayUI.isActive()`, `getSelectedEntity()` | Editor visibility and selected target |
| `ReplayUI.frameX/Y/Width/Height` | Non-destructive 9:16 composition overlay |
| `EditorStateManager.getCurrent()` | Current replay editor state |
| `EditorState.acquireRead/acquireWrite/release`, current scene, export range, `markDirty` | Thread-safe range, conflict, write, and refresh operations |
| `EditorScene.keyframeTracks`, `push(EditorSceneHistoryEntry)` | Atomic history operation and native tracks |
| Camera, FOV, and supported replay-time keyframe types | Editable output and monotonic source-time mapping |
| Replay packet-handler callbacks | Generation-scoped local evidence for attacks, damage, death, projectiles, and block changes |

All Flashback imports remain in the client integration, preview, UI, or mixin layers.

## Safe arbitrary-time sampling

Flashback exposes seeking but not an isolated `queryEntityAt(tick)` API. `FlashbackReplayEditorAdapter.resolveEntity` and `captureReplaySample` therefore accept a sample only when the requested tick equals the current replay tick. Montage analysis uses a cancellable client-tick state machine:

1. Store the original replay tick and pause flag.
2. Pause playback and seek to a requested sample tick.
3. Wait for the requested tick and for snapshot processing/fast-forwarding to finish.
4. Copy a bounded set of entities into immutable neutral snapshots.
5. Attach local action evidence and read-only replay markers.
6. Continue with the next tick.
7. Restore the original tick and pause flag before worker-side analysis begins.

Minecraft/Flashback objects are never read from the analysis worker. Cancellation, settings changes, replay close, and failures invalidate the current generation; stale results are discarded.

## Action capture

`ReplayGamePacketHandlerMixin` observes replay packets only while a generation-scoped `ReplayActionCapture` session is active. It records typed local facts rather than retaining packets or world objects. Supported evidence includes animation/attack signals, hurt/damage/death changes, projectile lifecycle signals, and block placement/destruction changes when the packet stream makes them distinguishable.

Action capture is bounded to the selected replay range and cleared on completion/cancellation. It has no network destination, telemetry sink, or persistent upload path. Snapshot-processing packets are excluded so replay reconstruction is not mistaken for user activity.

Packet evidence is optional. If a signal cannot be obtained reliably, the relevant event is omitted or given conservative inferred confidence; ordinary entity proximity alone is not reported as combat.

## Replay markers

CineWolf reads marker tick, description, color-derived identity data, and optional position from Flashback metadata. Markers can contribute event boundaries, labels, and scoring bonuses. CineWolf never deletes, edits, or creates native replay markers as part of analysis.

Event visualization uses CineWolf's own mini-timeline. Version 1.2.0 does not inject event markers into Flashback's native timeline.

## Mixins

`ReplayUIMixin` injects after `WindowType.renderAll()` in `ReplayUI.drawOverlayInternal()`, when Flashback's ImGui context and dockspace are active. This hosts the CineWolf panel and vertical guide without a second ImGui runtime.

`ReplayServerAccessor` exposes only the readiness state required to avoid copying an incomplete seek result.

`ReplayGamePacketHandlerMixin` captures bounded local replay actions for deterministic analysis. It does not alter packets or replay playback.

The mixin config is `required: false`, `remap: false` at each Flashback-specific injection, and guarded by `FlashbackMixinPlugin`. Mixins are applied only when the detected Flashback version is exactly 0.41.1.

## Access and reflection

- Mixin accessors: one narrow replay-server readiness accessor.
- Behavioral mixins: replay UI host plus generation-scoped packet observation.
- Access wideners/class tweakers: none.
- Reflection: none.
- Flashback source modifications: none.

## Timeline output

Manual shots and confirmed montages use native Flashback keyframes. Before writing, CineWolf validates every generated path and time mapping, detects conflicts, and prepares one history entry. Add mode preserves existing keys. For a montage, Replace mode is constrained to the confirmed source replay interval occupied by all three native tracks. Unrelated keys outside that interval are not removed.

Output montage time and source replay time are not interchangeable. Camera, FOV, and Timelapse track keys use source replay ticks as their native x-axis. Timelapse keyframe values store elapsed output ticks; Flashback calculates segment TPS as `source delta / output delta * 20`. CineWolf verifies this mapping, including the neutral case where 100 source ticks over 200 output ticks produce 10 TPS.

Flashback 0.41.1 does not expose a stable true source-cut/edit-list API. CineWolf therefore requires adjacent mappings to share the same source boundary and rejects gaps, reverse source time, and source relocation such as **Place After Last**. Speed changes obey configured minimum, maximum, and maximum-adjacent-change limits.

The full montage is one native Flashback history entry and one guarded CineWolf undo handle. Immediate CineWolf undo invokes `EditorScene.undo` only if no intervening timeline mutation occurred. If writing throws, a pre-write scene snapshot restores the complete state before failure is reported.

## Collision and preview

Collision checks use only the currently loaded local `ClientLevel`. The resolver checks camera clearance and focus visibility, chooses the nearest safe radial/elevated correction, and carries that correction forward per shot. A short release hysteresis prevents alternating safe/blocked samples from snapping back and forth; once the nominal path stays clear, the camera returns at a bounded cinematic speed. Collision-constrained samples are protected from ordinary path simplification. If one historical sample has no fully safe continuous branch inside the bounded probe budget, the resolver preserves the predicted prior offset and reports a warning instead of snapping to the raw path or aborting the montage. Generation remains fatal only when the replay world itself is unavailable.

Montage preview is temporary. Multi-path lines and the vertical guide do not mutate the native timeline. Exiting preview restores the prior replay/editor state.

## Compatibility behavior

`fabric.mod.json` requires Flashback to be installed but leaves its declared version broad enough for CineWolf to display its own precise compatibility message. On an unsupported version:

1. `FlashbackMixinPlugin` declines the Flashback mixins before target application.
2. The client initializer does not instantiate the adapter, panel, sampler, or preview renderer.
3. CineWolf logs the detected and supported versions once.
4. A red client chat message explains that editor integration is disabled.

No compatibility error is logged per frame. Supporting a future Flashback release requires source inspection, mixin target validation, timeline transaction validation, and the complete manual checklist before the exact version gate is changed.
