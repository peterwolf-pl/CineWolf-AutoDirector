# Vertical formats

## Scope

TikTok and YouTube Short presets use `VERTICAL_9_16` output metadata. CineWolf uses that intended aspect ratio for planning and shows a composition guide during preview. It does not change Minecraft's framebuffer, crop pixels, render video, encode media, or upload anything.

## Overlay geometry

`VerticalSafeAreaOverlay` reads the Flashback replay viewport rectangle and draws:

- a centered 9:16 outer frame;
- an inner configurable safe rectangle;
- horizontal and vertical center guides.

The outer guide uses up to 94% of available viewport height, falling back to 94% of width when necessary. The default safe fraction is `0.82`; configuration clamps it from `0.50` to `0.98`. The overlay is foreground ImGui geometry and does not mutate the replay timeline or camera.

It is shown only for vertical montage preview and hidden when preview ends, the aspect ratio changes, or the replay closes.

## Framing model

Framing is based on the intended 9:16 projection rather than the current game-window ratio. A shot template supplies `FramingType`:

- `EXTREME_WIDE`
- `WIDE`
- `MEDIUM`
- `CLOSE`
- `EXTREME_CLOSE`

Camera distance should account for target bounding box, target-group extent, vehicle/structure size, FOV, and intended aspect ratio. Fixed distance by entity type alone is insufficient.

TikTok defaults to closer center-safe framing with higher movement and cut intensity. YouTube Short keeps the subject center-safe but allows a calmer progression and wider ending.

## Safe-area validation

After optional collision adjustment, CineWolf projects all eight corners of the tracked target's sampled bounding box into the intended 9:16 camera frustum. The check uses the generated camera position/look direction, FOV, `9 / 16` aspect, and configured safe inset rather than the current window ratio. It reports how many camera samples place any corner outside the safe area. This also catches a tracked target that is too close or too wide for the chosen distance/FOV.

If a target pose is missing at a camera sample, CineWolf reports that framing could not be verified instead of claiming the shot is safe. The planner additionally flags Flyby/extreme-wide vertical compositions as higher risk. A warning does not silently change user-locked shot parameters.

Version 1.2.0 validates the one tracked target associated with each shot. It does not yet model a full multi-target group or identify an important secondary subject for crop validation; those cases require manual overlay review and remain version-1.3 work.

## Preview behavior

The overlay is a guide, not a guarantee of an external editor's final crop. Preview should be checked at representative points across every vertical shot, especially fast Follow/Flyby motion and multi-target combat.

CineWolf's multi-shot world path preview remains visible independently of the guide. Exiting preview clears both without writing keyframes.

## Export notes (Flashback 0.41.1)

When a montage uses `VERTICAL_9_16`, CineWolf applies Flashback export settings:

1. `ReplayVisuals.sizing = CHANGE_ASPECT_RATIO`
2. `ReplayVisuals.changeAspectRatio = ASPECT_9_16`
3. `internalExport.resolution = [1080, 1920]` (Flashback’s native 9:16 preset)

This runs when:

- the aspect combo is set to Vertical 9:16;
- montage paths finish generating;
- montage keyframes are written;
- vertical montage preview is active;
- the user clicks **Apply 9:16 to Flashback export**.

Flashback’s Export dialog then opens already in 9:16 / 1080×1920. Landscape montages map to 1920×1080.

No audio, captions, branding, padding, or social-platform upload behavior is implemented. YouTube Short and TikTok are composition/pacing presets, not integrations with those services.

## Generation adjustments for 9:16

- Extra camera distance (narrow horizontal FOV) and slightly higher height bias.
- Tighter FOV tables and reduced lateral speed.
- Intro/outro use MEDIUM framing instead of WIDE.
- Flyby is rewritten to side-tracking/follow when available.
- Paths that leave the safe area get an automatic radial pull-back before write.

## Manual verification

1. Select TikTok and confirm the guide is 9:16 regardless of window shape.
2. Resize the window and confirm the guide remains centered and inside the Flashback viewport.
3. Change the safe fraction and confirm only the inner inset changes.
4. Preview fast horizontal movement and inspect safe-area warnings.
5. Switch to YouTube Short and verify the same aspect with calmer preset values.
6. Switch to a 16:9 preset and confirm the vertical guide disappears.
7. Exit preview or close the replay and confirm no overlay remains.

See [Montage planner](MONTAGE_PLANNER.md) for framing selection and [manual tests](MANUAL_TESTS.md) for the complete 1.2 checklist.
