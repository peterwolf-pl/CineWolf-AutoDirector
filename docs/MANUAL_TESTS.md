# Manual test checklist

Use Minecraft 26.2, Java 25, Fabric Loader 0.19.3, Fabric API 0.153.0+26.2, Flashback 0.41.1, and the current CineWolf 1.3.0 build. Keep a backup copy of any replay project used for destructive conflict tests.

Record the replay name, selected tick range, preset, targets, settings, result, warnings, and any relevant log excerpt for each failure.

## Generate Montage 1.2 acceptance — 30 required tests

1. - [ ] **Open a replay with player movement.** Confirm the replay editor and CineWolf panel open without mixin/class-loading errors, entity targets appear, and no analysis starts automatically.

2. - [ ] **Select a 60-second timeline range.** Mark Flashback In/Out points exactly 60 seconds apart and confirm CineWolf displays the same bounded source range.

3. - [ ] **Run 15 Seconds analysis.** Select the 15 Seconds preset, analyze the 60-second range, and confirm progress advances without freezing rendering; the draft targets about 15 seconds and 3–5 enabled shots.

4. - [ ] **Review detected events.** Inspect several event rows/mini-timeline markers and confirm type, source timestamp, confidence, score, targets, evidence/thresholds, and scoring reasons are visible. Confirm this is CineWolf's mini-timeline, not native Flashback markers.

5. - [ ] **Preview the generated montage.** Start preview, navigate through proposed shots, and confirm the native timeline is unchanged before confirmation. Stop/exit and verify original replay time, pause state, and editor state are restored.

6. - [ ] **Disable one shot.** Disable a proposed row and confirm it is excluded from preview, duration/statistics, conflict detection, and final generation.

7. - [ ] **Reorder two shots.** Move one enabled row up/down and confirm displayed order, preview order, output times, and planning statistics update deterministically.

8. - [ ] **Lock one shot.** Lock a row after changing at least one parameter. Confirm the locked indicator and exact values persist.

9. - [ ] **Regenerate unlocked shots.** Run regeneration and verify the locked row is byte-for-byte/equivalent unchanged while unlocked rows may be replaced or rebalanced.

10. - [ ] **Generate the montage.** Confirm the final plan, resolve no-conflict generation, and verify editable native camera/FOV and any required replay-time keyframes are inserted as one CineWolf operation.

11. - [ ] **Edit generated camera keyframes.** Move or rotate one generated camera keyframe and change one FOV value in Flashback; confirm the montage output uses ordinary editable native tracks.

12. - [ ] **Undo the complete montage.** Use **Undo Last Montage** immediately after generation and confirm camera, rotation, FOV, replay-time keys, replaced keys, and CineWolf metadata return to the exact pre-generation state. Unrelated keys remain.

13. - [ ] **Test TikTok vertical safe-area overlay.** Select TikTok, preview at multiple GUI/window sizes, and verify a centered 9:16 outer guide plus safe inset. Confirm subject-tracking warnings appear when the target exits the safe area and that no render resolution is changed.

14. - [ ] **Test YouTube Short preset.** Confirm 9:16 metadata/guide, strong opening, calmer pacing than TikTok, center-safe target preference, and a wider final shot. Confirm duration can be adjusted independently of aspect ratio.

15. - [ ] **Test Trailer with replay speed changes.** Enable replay-speed changes and use separated high-value events. Confirm the UI reports the chronological-source limitation, all planned events lie inside one continuous source window, speeds stay inside configured limits, source time never decreases, and no unsupported source cut is written. Inspect two native Timelapse points and confirm Flashback TPS equals `source delta / output delta * 20`.

16. - [ ] **Test Cinematic Showcase on a static build.** Use a mostly stationary structure replay and confirm longer shots, lower cut/movement intensity, build/pause/marker priority, wide intro/outro, and no forced combat-style pacing.

17. - [ ] **Test a replay containing combat.** Confirm combat requires direct or supporting evidence, identifies known participants/location, exposes confidence, and does not classify unrelated nearby entities as combat.

18. - [ ] **Test a replay containing death.** Confirm explicit/reliable death ranks highly. Seek/unload the same entity separately and confirm temporary absence, dimension transition, or replay jump is not reported as death.

19. - [ ] **Test a minecart or vehicle replay.** Confirm enter, movement, turn/high-speed relations, stop, and exit are detected when available; vehicle type/UUID is shown where known and no optional-mod hard dependency is required.

20. - [ ] **Test flight and landing.** Use Elytra, creative flight, or a recorded aircraft. Confirm flight start, sustained flight, descent, and landing are separated, while an ordinary jump does not become a sustained flight event.

21. - [ ] **Test block placement and destruction.** Build and break multiple nearby blocks. Confirm actions group by time/space/actor instead of generating one montage event per block and replay snapshot reconstruction is not counted as building.

22. - [ ] **Test replay markers.** Add markers inside/outside the selected range. Confirm included markers appear read-only in CineWolf, affect boundaries/scores where enabled, and native marker data is never edited or deleted.

23. - [ ] **Test missing entities.** Select a target that is absent for part of the range. Confirm analysis reports bounded warnings/partial availability, does not crash, and can continue with other targets or a clear fallback.

24. - [ ] **Test dimension changes.** Use a replay crossing dimensions. Confirm a scene boundary/discontinuity, no camera interpolation through dimensions, no false death/high-speed event, and no shot spans an invalid dimension transition.

25. - [ ] **Cancel analysis during processing.** Cancel during coarse sampling and again during detailed/worker analysis. Confirm the operation stops, the original tick/pause state returns, no draft is published, action capture ends, and the UI remains responsive.

26. - [ ] **Close the replay during analysis.** Close during a seek and during worker processing. Confirm no crash/stale callback, all caches/action capture/overlays clear, and opening another replay does not receive the old result.

27. - [ ] **Test collision avoidance.** Use a path that repeatedly enters/leaves obstruction near a wall. Compare disabled/enabled results, confirm safe samples are raised/moved without alternating snapback or through-wall interpolation, and verify the camera returns gradually after at least 0.35 seconds of stable clearance. A temporarily unresolved sample must retain the previous camera offset and report a warning without aborting the montage; only an unavailable replay world may stop the pass after restoring replay state.

28. - [ ] **Test timeline conflicts.** Place camera, FOV, and replay-time keys inside and outside the output range. Verify Cancel is default, Add preserves existing keys, Replace removes only confirmed in-range keys, and any write failure rolls back the whole montage.

29. - [ ] **Test Polish localization.** Switch to Polish and inspect panel, advanced settings, presets, event types, progress, warnings/errors, shot editor, mini-timeline, conflicts, undo, vertical warnings, and scoring reasons. Confirm no user-facing English literal remains in the tested flow.

30. - [ ] **Test malformed configuration recovery.** Corrupt the version-4 configuration and restart. Confirm the malformed file is preserved, safe defaults load, the client does not crash, and valid legacy manual-shot or montage settings are not silently reset during migration.
31. - [ ] **Test camera-path smoothing controls.** In both Single Shot and Generate Montage, vary position strength, rotation strength, and smoothing window. Confirm endpoints and intended Orbit/Dolly framing remain stable while visible micro-jitter decreases.
32. - [ ] **Test sudden-movement rejection.** Use a replay containing a one-sample target/camera glitch and a separate sustained fast Flyby. Confirm the isolated out-and-back pulse is removed while the sustained move and real turns remain.
33. - [ ] **Test smoothing tooltips and invalidation.** Stop the cursor over each new setting and the main montage controls. Confirm a localized delayed tooltip appears and changing only smoothing clears generated paths without discarding completed replay analysis or the edited plan.

## Legacy manual-shot regression

- [ ] Generate Orbit, Follow, Flyby, Dolly In, and Dolly Out shots against moving targets.
- [ ] Confirm the selected Flashback In/Out range overrides manual Duration.
- [ ] Confirm a duration-based shot clamps at replay end and displays the localized notice.
- [ ] Confirm adaptive sampling remains bounded around turns, acceleration, elevation, and teleport discontinuities.
- [ ] Confirm Smoothstep/Smootherstep warnings and baked linear keyframes remain correct.
- [ ] Confirm Preview Path clears on parameter/target/timeline changes and stale previews cannot be written.
- [ ] Confirm manual Add/Replace conflict behavior and **Undo Last CineWolf Shot** still preserve unrelated keys.

## Startup and compatibility regression

- [ ] Start with supported Flashback 0.41.1 and confirm all three guarded CineWolf mixins apply without errors.
- [ ] Start with an intentionally unsupported Flashback build and confirm the integration mixins are declined, one clear compatibility message appears, and the client does not crash.
- [ ] Confirm no AI/cloud/telemetry/network-upload setting or background request exists during analysis, preview, generation, debug export, or replay close.
