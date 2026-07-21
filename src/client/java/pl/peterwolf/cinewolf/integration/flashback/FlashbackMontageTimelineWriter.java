package pl.peterwolf.cinewolf.integration.flashback;

import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.keyframe.Keyframe;
import com.moulberry.flashback.keyframe.KeyframeType;
import com.moulberry.flashback.keyframe.impl.CameraKeyframe;
import com.moulberry.flashback.keyframe.impl.FOVKeyframe;
import com.moulberry.flashback.keyframe.impl.TimelapseKeyframe;
import com.moulberry.flashback.keyframe.interpolation.InterpolationType;
import com.moulberry.flashback.keyframe.types.CameraKeyframeType;
import com.moulberry.flashback.keyframe.types.FOVKeyframeType;
import com.moulberry.flashback.keyframe.types.TimelapseKeyframeType;
import com.moulberry.flashback.playback.ReplayServer;
import com.moulberry.flashback.state.EditorScene;
import com.moulberry.flashback.state.EditorSceneHistoryAction;
import com.moulberry.flashback.state.EditorSceneHistoryEntry;
import com.moulberry.flashback.state.EditorState;
import com.moulberry.flashback.state.EditorStateManager;
import com.moulberry.flashback.state.KeyframeTrack;
import net.minecraft.client.Minecraft;
import org.joml.Vector3d;
import pl.peterwolf.cinewolf.model.EasingType;
import pl.peterwolf.cinewolf.montage.timeline.MontageTimelineConflictDetector;
import pl.peterwolf.cinewolf.montage.timeline.MontageTimelineConflictMode;
import pl.peterwolf.cinewolf.montage.timeline.MontageTimelineConflictReport;
import pl.peterwolf.cinewolf.montage.timeline.MontageTimelineInterval;
import pl.peterwolf.cinewolf.montage.timeline.MontageTimelinePlacementResolver;
import pl.peterwolf.cinewolf.montage.timeline.MontageTimelinePlanBuilder;
import pl.peterwolf.cinewolf.montage.timeline.MontageTimelineWriteOptions;
import pl.peterwolf.cinewolf.montage.timeline.MontageTimelineWritePlan;
import pl.peterwolf.cinewolf.montage.timeline.MontageTimelineWriteRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;

/** Atomic native Flashback write boundary for a complete CineWolf montage. */
public final class FlashbackMontageTimelineWriter {
    private static final int CAMERA_COLOUR = 0xFF36C6F4;
    private static final int FOV_COLOUR = 0xFF8B5CF6;
    private static final int TIMELAPSE_COLOUR = 0xFFF59E0B;

    private final MontageTimelinePlanBuilder planBuilder = new MontageTimelinePlanBuilder();
    private final MontageTimelinePlacementResolver placementResolver = new MontageTimelinePlacementResolver();
    private OperationGuard lastOperation;

    public InspectionResult inspect(MontageTimelineWriteRequest request, MontageTimelineWriteOptions options) {
        MontageTimelinePlanBuilder.BuildResult build = planBuilder.build(request);
        if (!build.valid()) {
            return InspectionResult.invalid(build.errors(), build.warnings());
        }
        if (!Minecraft.getInstance().isSameThread()) {
            return InspectionResult.invalid(List.of("montage.timeline.client_thread_required"), build.warnings());
        }
        EditorState state = EditorStateManager.getCurrent();
        ReplayServer replayServer = Flashback.getReplayServer();
        if (state == null || replayServer == null) {
            return InspectionResult.invalid(List.of("montage.timeline.flashback_state_unavailable"), build.warnings());
        }

        long stamp = state.acquireRead();
        try {
            EditorScene scene = state.getCurrentScene(stamp);
            MontageTimelinePlacementResolver.Resolution resolution = placementResolver.resolve(
                    build.plan().orElseThrow(), normalize(options), existingTracks(scene));
            List<String> errors = new ArrayList<>(resolution.errors());
            resolution.plan().ifPresent(plan -> validateReplayBounds(plan, replayServer.getTotalReplayTicks(), errors));
            return new InspectionResult(errors.isEmpty() && resolution.valid(), resolution.plan(),
                    resolution.detectedConflicts(), distinct(errors),
                    combine(build.warnings(), resolution.warnings()));
        } finally {
            state.release(stamp);
        }
    }

    public WriteResult write(MontageTimelineWriteRequest request) {
        return write(request, MontageTimelineWriteOptions.cancelOnConflict());
    }

    public WriteResult write(MontageTimelineWriteRequest request, MontageTimelineWriteOptions options) {
        MontageTimelinePlanBuilder.BuildResult build = planBuilder.build(request);
        UUID montageId = request == null ? new UUID(0L, 0L) : request.montageId();
        if (!build.valid()) return WriteResult.failure(montageId, build.errors(), build.warnings());
        if (!Minecraft.getInstance().isSameThread()) {
            return WriteResult.failure(montageId, List.of("montage.timeline.client_thread_required"), build.warnings());
        }
        EditorState state = EditorStateManager.getCurrent();
        ReplayServer replayServer = Flashback.getReplayServer();
        if (state == null || replayServer == null) {
            return WriteResult.failure(montageId, List.of("montage.timeline.flashback_state_unavailable"), build.warnings());
        }

        long stamp = state.acquireWrite();
        try {
            EditorScene scene = state.getCurrentScene(stamp);
            MontageTimelineWriteOptions normalizedOptions = normalize(options);
            MontageTimelinePlacementResolver.Resolution resolution = placementResolver.resolve(
                    build.plan().orElseThrow(), normalizedOptions, existingTracks(scene));
            List<String> errors = new ArrayList<>(resolution.errors());
            MontageTimelineWritePlan plan = resolution.plan().orElse(null);
            if (plan != null) validateReplayBounds(plan, replayServer.getTotalReplayTicks(), errors);
            List<String> warnings = combine(build.warnings(), resolution.warnings());
            if (plan == null || !errors.isEmpty()) {
                return WriteResult.failure(montageId, distinct(errors), warnings,
                        resolution.detectedConflicts(), Optional.ofNullable(plan).map(MontageTimelineWritePlan::outputInterval));
            }

            NativePayload payload;
            try {
                payload = nativePayload(plan);
            } catch (RuntimeException exception) {
                return WriteResult.failure(montageId, List.of("montage.timeline.native_keyframe_preparation_failed"),
                        warnings, resolution.detectedConflicts(), Optional.of(plan.outputInterval()));
            }

            List<EditorSceneHistoryAction> undo = new ArrayList<>();
            List<EditorSceneHistoryAction> redo = new ArrayList<>();
            List<RemovedTrackState> removed = normalizedOptions.conflictMode() == MontageTimelineConflictMode.REPLACE
                    ? captureReplacements(scene, plan.outputInterval(), redo) : List.of();
            appendAddActions(payload, redo);
            appendUndoActions(removed, undo);
            SceneSnapshot before = SceneSnapshot.capture(scene);
            boolean pushed = false;
            try {
                scene.push(new EditorSceneHistoryEntry(List.copyOf(undo), List.copyOf(redo),
                        "CineWolf montage " + shortId(plan.montageId())));
                pushed = true;
                GeneratedTracks generated = generatedTracks(scene);
                applyGeneratedMetadata(generated, plan.montageId());
                verifyGeneratedPayload(generated, payload);
                state.markDirty();
                int modCountAfterWrite = state.modCount;
                lastOperation = new OperationGuard(state, state.getSceneIndex(), plan.montageId(),
                        generated.asList(), modCountAfterWrite);
                return new WriteResult(true, plan.montageId(), payload.camera().size(), payload.fov().size(),
                        payload.timelapse().size(), Optional.of(plan.outputInterval()), resolution.detectedConflicts(),
                        List.of(), warnings, "Inserted native Camera, FOV, and Timelapse tracks as one operation");
            } catch (RuntimeException exception) {
                try {
                    if (pushed) scene.undo(ignored -> { });
                } catch (RuntimeException ignored) {
                    // The complete scene snapshot below is the authoritative rollback path.
                } finally {
                    before.restore(scene);
                    state.markDirty();
                }
                return WriteResult.failure(plan.montageId(), List.of("montage.timeline.atomic_write_rolled_back"),
                        warnings, resolution.detectedConflicts(), Optional.of(plan.outputInterval()));
            }
        } finally {
            state.release(stamp);
        }
    }

    public UndoResult undoLast() {
        if (!Minecraft.getInstance().isSameThread()) {
            return new UndoResult(false, new UUID(0L, 0L), "Montage undo must run on the client thread");
        }
        OperationGuard operation = lastOperation;
        EditorState state = EditorStateManager.getCurrent();
        if (operation == null || state == null || state != operation.editorState()
                || state.getSceneIndex() != operation.sceneIndex()) {
            return new UndoResult(false, new UUID(0L, 0L), "No CineWolf montage operation is available for this scene");
        }

        long stamp = state.acquireWrite();
        try {
            EditorScene scene = state.getCurrentScene(stamp);
            if (state.modCount != operation.modCountAfterWrite()) {
                lastOperation = null;
                return new UndoResult(false, operation.montageId(),
                        "The Flashback timeline changed after montage generation; undo was not applied");
            }
            if (!scene.keyframeTracks.containsAll(operation.generatedTracks())) {
                lastOperation = null;
                return new UndoResult(false, operation.montageId(),
                        "Generated montage tracks were changed or removed; undo was not applied");
            }
            try {
                scene.undo(ignored -> { });
                state.markDirty();
                lastOperation = null;
                return new UndoResult(true, operation.montageId(),
                        "Undid the last CineWolf montage through Flashback history");
            } catch (RuntimeException exception) {
                return new UndoResult(false, operation.montageId(),
                        "Flashback could not undo the CineWolf montage operation");
            }
        } finally {
            state.release(stamp);
        }
    }

    public void clearUndo() {
        lastOperation = null;
    }

    private static MontageTimelineWriteOptions normalize(MontageTimelineWriteOptions options) {
        return options == null ? MontageTimelineWriteOptions.cancelOnConflict() : options;
    }

    private static void validateReplayBounds(MontageTimelineWritePlan plan, int totalReplayTicks,
                                             List<String> errors) {
        if (plan.sourceInterval().endTick() > totalReplayTicks) {
            errors.add("montage.timeline.source_exceeds_replay");
        }
    }

    private static List<MontageTimelineConflictDetector.ExistingTrack> existingTracks(EditorScene scene) {
        List<MontageTimelineConflictDetector.ExistingTrack> result = new ArrayList<>();
        for (KeyframeTrack track : scene.keyframeTracks) {
            MontageTimelineConflictDetector.TrackType type = trackType(track.keyframeType);
            if (type != null) {
                result.add(new MontageTimelineConflictDetector.ExistingTrack(type, track.enabled,
                        List.copyOf(track.keyframesByTick.keySet())));
            }
        }
        return List.copyOf(result);
    }

    private static MontageTimelineConflictDetector.TrackType trackType(KeyframeType<?> type) {
        if (type == CameraKeyframeType.INSTANCE) return MontageTimelineConflictDetector.TrackType.CAMERA;
        if (type == FOVKeyframeType.INSTANCE) return MontageTimelineConflictDetector.TrackType.FOV;
        if (type == TimelapseKeyframeType.INSTANCE) return MontageTimelineConflictDetector.TrackType.TIMELAPSE;
        return null;
    }

    private static NativePayload nativePayload(MontageTimelineWritePlan plan) {
        List<NativeKeyframe> camera = plan.cameraKeyframes().stream().map(point -> new NativeKeyframe(
                point.timelineTick(), new CameraKeyframe(new Vector3d(point.position().x(), point.position().y(),
                point.position().z()), (float) point.yaw(), (float) point.pitch(), (float) point.roll(),
                point.holdAfter() ? InterpolationType.HOLD : interpolation(point.easing())))).toList();
        List<NativeKeyframe> fov = plan.fovKeyframes().stream().map(point -> new NativeKeyframe(
                point.timelineTick(), new FOVKeyframe((float) point.fov(),
                point.holdAfter() ? InterpolationType.HOLD : interpolation(point.easing())))).toList();
        List<NativeKeyframe> timelapse = plan.timelapseKeyframes().stream().map(point -> new NativeKeyframe(
                point.timelineTick(), new TimelapseKeyframe(point.outputElapsedTick()))).toList();
        return new NativePayload(camera, fov, timelapse);
    }

    private static List<RemovedTrackState> captureReplacements(EditorScene scene, MontageTimelineInterval interval,
                                                                List<EditorSceneHistoryAction> redo) {
        List<RemovedTrackState> removed = new ArrayList<>();
        for (int trackIndex = 0; trackIndex < scene.keyframeTracks.size(); trackIndex++) {
            int originalIndex = trackIndex;
            KeyframeTrack track = scene.keyframeTracks.get(trackIndex);
            if (trackType(track.keyframeType) == null) continue;
            TreeMap<Integer, Keyframe> keys = new TreeMap<>();
            new TreeMap<>(track.keyframesByTick.subMap(interval.startTick(), true, interval.endTick(), true))
                    .forEach((tick, keyframe) -> {
                        keys.put(tick, keyframe.copy());
                        redo.add(new EditorSceneHistoryAction.RemoveKeyframe(track.keyframeType, originalIndex, tick));
            });
            if (!keys.isEmpty()) {
                removed.add(new RemovedTrackState(track, originalIndex, copyKeyframes(keys)));
            }
        }
        return List.copyOf(removed);
    }

    private static void appendAddActions(NativePayload payload, List<EditorSceneHistoryAction> redo) {
        redo.add(new EditorSceneHistoryAction.AddTrack(CameraKeyframeType.INSTANCE, 0));
        redo.add(new EditorSceneHistoryAction.AddTrack(FOVKeyframeType.INSTANCE, 1));
        redo.add(new EditorSceneHistoryAction.AddTrack(TimelapseKeyframeType.INSTANCE, 2));
        payload.camera().forEach(entry -> redo.add(new EditorSceneHistoryAction.SetKeyframe(
                CameraKeyframeType.INSTANCE, 0, entry.tick(), entry.keyframe())));
        payload.fov().forEach(entry -> redo.add(new EditorSceneHistoryAction.SetKeyframe(
                FOVKeyframeType.INSTANCE, 1, entry.tick(), entry.keyframe())));
        payload.timelapse().forEach(entry -> redo.add(new EditorSceneHistoryAction.SetKeyframe(
                TimelapseKeyframeType.INSTANCE, 2, entry.tick(), entry.keyframe())));
    }

    private static void appendUndoActions(List<RemovedTrackState> removed, List<EditorSceneHistoryAction> undo) {
        undo.add(new EditorSceneHistoryAction.RemoveTrack(TimelapseKeyframeType.INSTANCE, 2));
        undo.add(new EditorSceneHistoryAction.RemoveTrack(FOVKeyframeType.INSTANCE, 1));
        undo.add(new EditorSceneHistoryAction.RemoveTrack(CameraKeyframeType.INSTANCE, 0));
        for (RemovedTrackState trackState : removed) {
            trackState.keyframes().forEach((tick, keyframe) -> undo.add(new EditorSceneHistoryAction.SetKeyframe(
                    trackState.track().keyframeType, trackState.originalIndex(), tick, keyframe.copy())));
        }
    }

    private static GeneratedTracks generatedTracks(EditorScene scene) {
        if (scene.keyframeTracks.size() < 3
                || scene.keyframeTracks.get(0).keyframeType != CameraKeyframeType.INSTANCE
                || scene.keyframeTracks.get(1).keyframeType != FOVKeyframeType.INSTANCE
                || scene.keyframeTracks.get(2).keyframeType != TimelapseKeyframeType.INSTANCE) {
            throw new IllegalStateException("Flashback did not create the expected montage tracks");
        }
        return new GeneratedTracks(scene.keyframeTracks.get(0), scene.keyframeTracks.get(1),
                scene.keyframeTracks.get(2));
    }

    private static void applyGeneratedMetadata(GeneratedTracks generated, UUID montageId) {
        String prefix = "CineWolf Montage " + shortId(montageId);
        generated.camera().customName = prefix + " Camera";
        generated.camera().customColour = CAMERA_COLOUR;
        generated.fov().customName = prefix + " FOV";
        generated.fov().customColour = FOV_COLOUR;
        generated.timelapse().customName = prefix + " Replay Time";
        generated.timelapse().customColour = TIMELAPSE_COLOUR;
    }

    private static void verifyGeneratedPayload(GeneratedTracks generated, NativePayload payload) {
        if (generated.camera().keyframesByTick.size() != payload.camera().size()
                || generated.fov().keyframesByTick.size() != payload.fov().size()
                || generated.timelapse().keyframesByTick.size() != payload.timelapse().size()) {
            throw new IllegalStateException("Flashback montage history entry was only partially applied");
        }
    }

    private static InterpolationType interpolation(EasingType easing) {
        return switch (easing) {
            case LINEAR, SMOOTHSTEP, SMOOTHERSTEP -> InterpolationType.LINEAR;
            case EASE_IN -> InterpolationType.EASE_IN;
            case EASE_OUT -> InterpolationType.EASE_OUT;
            case EASE_IN_OUT_CUBIC -> InterpolationType.EASE_IN_OUT;
        };
    }

    private static String shortId(UUID montageId) {
        return montageId.toString().substring(0, 8);
    }

    private static TreeMap<Integer, Keyframe> copyKeyframes(Map<Integer, Keyframe> source) {
        TreeMap<Integer, Keyframe> copy = new TreeMap<>();
        source.forEach((tick, keyframe) -> copy.put(tick, keyframe.copy()));
        return copy;
    }

    private static List<String> combine(List<String> first, List<String> second) {
        List<String> result = new ArrayList<>(first);
        result.addAll(second);
        return distinct(result);
    }

    private static List<String> distinct(List<String> values) {
        return values.stream().distinct().toList();
    }

    public record InspectionResult(
            boolean valid,
            Optional<MontageTimelineWritePlan> plan,
            MontageTimelineConflictReport conflicts,
            List<String> errors,
            List<String> warnings
    ) {
        public InspectionResult {
            plan = plan == null ? Optional.empty() : plan;
            conflicts = conflicts == null ? MontageTimelineConflictReport.empty() : conflicts;
            errors = List.copyOf(errors == null ? List.of() : errors);
            warnings = List.copyOf(warnings == null ? List.of() : warnings);
        }

        static InspectionResult invalid(List<String> errors, List<String> warnings) {
            return new InspectionResult(false, Optional.empty(), MontageTimelineConflictReport.empty(),
                    errors, warnings);
        }
    }

    public record WriteResult(
            boolean success,
            UUID montageId,
            int cameraKeyframes,
            int fovKeyframes,
            int timelapseKeyframes,
            Optional<MontageTimelineInterval> outputInterval,
            MontageTimelineConflictReport conflicts,
            List<String> errors,
            List<String> warnings,
            String message
    ) {
        public WriteResult {
            outputInterval = outputInterval == null ? Optional.empty() : outputInterval;
            conflicts = conflicts == null ? MontageTimelineConflictReport.empty() : conflicts;
            errors = List.copyOf(errors == null ? List.of() : errors);
            warnings = List.copyOf(warnings == null ? List.of() : warnings);
        }

        static WriteResult failure(UUID montageId, List<String> errors, List<String> warnings) {
            return failure(montageId, errors, warnings, MontageTimelineConflictReport.empty(), Optional.empty());
        }

        static WriteResult failure(UUID montageId, List<String> errors, List<String> warnings,
                                   MontageTimelineConflictReport conflicts,
                                   Optional<MontageTimelineInterval> interval) {
            return new WriteResult(false, montageId, 0, 0, 0, interval, conflicts, errors, warnings,
                    "Montage timeline write was rejected before mutation");
        }
    }

    public record UndoResult(boolean success, UUID montageId, String message) {
    }

    private record NativeKeyframe(int tick, Keyframe keyframe) {
    }

    private record NativePayload(List<NativeKeyframe> camera, List<NativeKeyframe> fov,
                                 List<NativeKeyframe> timelapse) {
    }

    private record GeneratedTracks(KeyframeTrack camera, KeyframeTrack fov, KeyframeTrack timelapse) {
        List<KeyframeTrack> asList() {
            return List.of(camera, fov, timelapse);
        }
    }

    private record RemovedTrackState(KeyframeTrack track, int originalIndex, TreeMap<Integer, Keyframe> keyframes) {
    }

    private record OperationGuard(EditorState editorState, int sceneIndex, UUID montageId,
                                  List<KeyframeTrack> generatedTracks, int modCountAfterWrite) {
    }

    private record SceneSnapshot(List<TrackSnapshot> tracks) {
        static SceneSnapshot capture(EditorScene scene) {
            return new SceneSnapshot(scene.keyframeTracks.stream().map(TrackSnapshot::capture).toList());
        }

        void restore(EditorScene scene) {
            scene.keyframeTracks.clear();
            for (TrackSnapshot snapshot : tracks) {
                snapshot.restore();
                scene.keyframeTracks.add(snapshot.track());
            }
        }
    }

    private record TrackSnapshot(KeyframeTrack track, TreeMap<Integer, Keyframe> keyframes, boolean enabled,
                                 String customName, int customColour) {
        static TrackSnapshot capture(KeyframeTrack track) {
            return new TrackSnapshot(track, copyKeyframes(track.keyframesByTick), track.enabled,
                    track.customName, track.customColour);
        }

        void restore() {
            track.keyframesByTick.clear();
            keyframes.forEach((tick, keyframe) -> track.keyframesByTick.put(tick, keyframe.copy()));
            track.enabled = enabled;
            track.customName = customName;
            track.customColour = customColour;
        }
    }
}
