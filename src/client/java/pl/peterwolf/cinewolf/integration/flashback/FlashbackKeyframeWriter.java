package pl.peterwolf.cinewolf.integration.flashback;

import com.moulberry.flashback.keyframe.Keyframe;
import com.moulberry.flashback.keyframe.KeyframeType;
import com.moulberry.flashback.keyframe.impl.CameraKeyframe;
import com.moulberry.flashback.keyframe.impl.FOVKeyframe;
import com.moulberry.flashback.keyframe.interpolation.InterpolationType;
import com.moulberry.flashback.keyframe.types.CameraKeyframeType;
import com.moulberry.flashback.keyframe.types.FOVKeyframeType;
import com.moulberry.flashback.state.EditorScene;
import com.moulberry.flashback.state.EditorSceneHistoryAction;
import com.moulberry.flashback.state.EditorSceneHistoryEntry;
import com.moulberry.flashback.state.EditorState;
import com.moulberry.flashback.state.EditorStateManager;
import com.moulberry.flashback.state.KeyframeTrack;
import org.joml.Vector3d;
import pl.peterwolf.cinewolf.api.ReplayEditorAdapter;
import pl.peterwolf.cinewolf.model.CameraPathPlan;
import pl.peterwolf.cinewolf.model.CameraSample;
import pl.peterwolf.cinewolf.model.EasingType;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import pl.peterwolf.cinewolf.undo.CineWolfUndoManager;

public final class FlashbackKeyframeWriter {
    private final CineWolfUndoManager<OperationSnapshot> undoManager = new CineWolfUndoManager<>();

    public ReplayEditorAdapter.KeyframeConflictReport detectConflicts(CameraPathPlan plan) {
        EditorState state = EditorStateManager.getCurrent();
        if (state == null) return new ReplayEditorAdapter.KeyframeConflictReport(0, 0);
        int start = Math.toIntExact(plan.request().replayStartTime());
        int end = Math.toIntExact(plan.request().replayEndTime());
        int camera = 0;
        int fov = 0;
        long stamp = state.acquireRead();
        try {
            EditorScene scene = state.getCurrentScene(stamp);
            for (KeyframeTrack track : scene.keyframeTracks) {
                int count = track.keyframesByTick.subMap(start, true, end, true).size();
                if (track.keyframeType == CameraKeyframeType.INSTANCE) camera += count;
                if (track.keyframeType == FOVKeyframeType.INSTANCE) fov += count;
            }
        } finally {
            state.release(stamp);
        }
        return new ReplayEditorAdapter.KeyframeConflictReport(camera, fov);
    }

    public ReplayEditorAdapter.KeyframeWriteResult write(CameraPathPlan plan, ReplayEditorAdapter.KeyframeWriteOptions options) {
        if (!plan.valid()) return new ReplayEditorAdapter.KeyframeWriteResult(false, 0, 0, "cinewolf.write.invalid_path");
        EditorState state = EditorStateManager.getCurrent();
        if (state == null) return new ReplayEditorAdapter.KeyframeWriteResult(false, 0, 0, "cinewolf.write.editor_unavailable");
        ReplayEditorAdapter.KeyframeConflictReport conflicts = detectConflicts(plan);
        if (conflicts.hasConflicts() && options.conflictMode() == ReplayEditorAdapter.ConflictMode.CANCEL) {
            return new ReplayEditorAdapter.KeyframeWriteResult(false, 0, 0, "cinewolf.write.conflicts");
        }

        LinkedHashMap<Integer, CameraSample> cameraSamples = uniqueTicks(plan.simplifiedSamples());
        if (cameraSamples.size() < 2) {
            return new ReplayEditorAdapter.KeyframeWriteResult(false, 0, 0, "cinewolf.write.two_ticks");
        }
        LinkedHashMap<Integer, CameraSample> fovSamples = simplifyFov(cameraSamples);
        InterpolationType interpolation = interpolation(plan.request().easing());

        long stamp = state.acquireWrite();
        try {
            EditorScene scene = state.getCurrentScene(stamp);
            List<EditorSceneHistoryAction> undo = new ArrayList<>();
            List<EditorSceneHistoryAction> redo = new ArrayList<>();
            Map<KeyframeTrack, Map<Integer, Keyframe>> removed = new IdentityHashMap<>();
            int start = Math.toIntExact(plan.request().replayStartTime());
            int end = Math.toIntExact(plan.request().replayEndTime());

            if (options.conflictMode() == ReplayEditorAdapter.ConflictMode.REPLACE_INSIDE_INTERVAL) {
                captureAndRemoveActions(scene, start, end, removed, undo, redo);
            }

            redo.add(new EditorSceneHistoryAction.AddTrack(CameraKeyframeType.INSTANCE, 0));
            for (Map.Entry<Integer, CameraSample> entry : cameraSamples.entrySet()) {
                CameraSample sample = entry.getValue();
                CameraKeyframe keyframe = new CameraKeyframe(new Vector3d(sample.position().x(), sample.position().y(), sample.position().z()),
                        (float) sample.yaw(), (float) sample.pitch(), (float) sample.roll(), interpolation);
                redo.add(new EditorSceneHistoryAction.SetKeyframe(CameraKeyframeType.INSTANCE, 0, entry.getKey(), keyframe));
            }

            redo.add(new EditorSceneHistoryAction.AddTrack(FOVKeyframeType.INSTANCE, 1));
            for (Map.Entry<Integer, CameraSample> entry : fovSamples.entrySet()) {
                redo.add(new EditorSceneHistoryAction.SetKeyframe(FOVKeyframeType.INSTANCE, 1, entry.getKey(),
                        new FOVKeyframe((float) entry.getValue().fov(), interpolation)));
            }

            undo.add(new EditorSceneHistoryAction.RemoveTrack(FOVKeyframeType.INSTANCE, 1));
            undo.add(new EditorSceneHistoryAction.RemoveTrack(CameraKeyframeType.INSTANCE, 0));
            appendRestoreActions(scene, removed, undo);

            scene.push(new EditorSceneHistoryEntry(undo, redo,
                    "CineWolf " + plan.request().shotType().label() + " shot"));
            KeyframeTrack cameraTrack = scene.keyframeTracks.get(0);
            KeyframeTrack fovTrack = scene.keyframeTracks.get(1);
            cameraTrack.customName = "CineWolf " + plan.request().shotType().label() + " Camera";
            cameraTrack.customColour = 0xFF36C6F4;
            fovTrack.customName = "CineWolf " + plan.request().shotType().label() + " FOV";
            fovTrack.customColour = 0xFF8B5CF6;
            state.markDirty();
            undoManager.remember(new OperationSnapshot(state, state.getSceneIndex(), cameraTrack, fovTrack,
                    removed, state.modCount));
            return new ReplayEditorAdapter.KeyframeWriteResult(true, cameraSamples.size(), fovSamples.size(),
                    "cinewolf.write.success");
        } catch (RuntimeException exception) {
            return new ReplayEditorAdapter.KeyframeWriteResult(false, 0, 0,
                    "cinewolf.write.failed");
        } finally {
            state.release(stamp);
        }
    }

    public ReplayEditorAdapter.UndoResult undoLast() {
        OperationSnapshot operation = undoManager.peek().orElse(null);
        EditorState state = EditorStateManager.getCurrent();
        if (operation == null || state == null || state != operation.editorState() || state.getSceneIndex() != operation.sceneIndex()) {
            return new ReplayEditorAdapter.UndoResult(false, "cinewolf.undo.unavailable");
        }

        long stamp = state.acquireWrite();
        try {
            EditorScene scene = state.getCurrentScene(stamp);
            if (state.modCount != operation.expectedModCount()) {
                return new ReplayEditorAdapter.UndoResult(false,
                        "cinewolf.undo.timeline_changed");
            }
            if (!scene.keyframeTracks.contains(operation.cameraTrack()) || !scene.keyframeTracks.contains(operation.fovTrack())) {
                return new ReplayEditorAdapter.UndoResult(false, "cinewolf.undo.tracks_changed");
            }
            scene.undo(ignored -> { });
            state.markDirty();
            undoManager.clear();
            return new ReplayEditorAdapter.UndoResult(true, "cinewolf.undo.success");
        } finally {
            state.release(stamp);
        }
    }

    private static void captureAndRemoveActions(EditorScene scene, int start, int end,
                                                Map<KeyframeTrack, Map<Integer, Keyframe>> removed,
                                                List<EditorSceneHistoryAction> undo,
                                                List<EditorSceneHistoryAction> redo) {
        for (int trackIndex = 0; trackIndex < scene.keyframeTracks.size(); trackIndex++) {
            KeyframeTrack track = scene.keyframeTracks.get(trackIndex);
            if (track.keyframeType != CameraKeyframeType.INSTANCE && track.keyframeType != FOVKeyframeType.INSTANCE) continue;
            Map<Integer, Keyframe> entries = new TreeMap<>();
            for (Map.Entry<Integer, Keyframe> entry : new TreeMap<>(track.keyframesByTick.subMap(start, true, end, true)).entrySet()) {
                entries.put(entry.getKey(), entry.getValue().copy());
                redo.add(new EditorSceneHistoryAction.RemoveKeyframe(track.keyframeType, trackIndex, entry.getKey()));
            }
            if (!entries.isEmpty()) removed.put(track, entries);
        }
    }

    private static void appendRestoreActions(EditorScene scene, Map<KeyframeTrack, Map<Integer, Keyframe>> removed,
                                             List<EditorSceneHistoryAction> undo) {
        for (Map.Entry<KeyframeTrack, Map<Integer, Keyframe>> trackEntry : removed.entrySet()) {
            int originalIndex = scene.keyframeTracks.indexOf(trackEntry.getKey());
            KeyframeType<?> type = trackEntry.getKey().keyframeType;
            for (Map.Entry<Integer, Keyframe> keyframe : trackEntry.getValue().entrySet()) {
                undo.add(new EditorSceneHistoryAction.SetKeyframe(type, originalIndex, keyframe.getKey(), keyframe.getValue().copy()));
            }
        }
    }

    private static LinkedHashMap<Integer, CameraSample> uniqueTicks(List<CameraSample> samples) {
        LinkedHashMap<Integer, CameraSample> result = new LinkedHashMap<>();
        for (CameraSample sample : samples) result.put(Math.toIntExact(sample.replayTime()), sample);
        return result;
    }

    private static LinkedHashMap<Integer, CameraSample> simplifyFov(LinkedHashMap<Integer, CameraSample> samples) {
        LinkedHashMap<Integer, CameraSample> result = new LinkedHashMap<>();
        List<Map.Entry<Integer, CameraSample>> entries = new ArrayList<>(samples.entrySet());
        result.put(entries.getFirst().getKey(), entries.getFirst().getValue());
        for (int i = 1; i < entries.size() - 1; i++) {
            double previous = entries.get(i - 1).getValue().fov();
            double current = entries.get(i).getValue().fov();
            double next = entries.get(i + 1).getValue().fov();
            if (Math.abs(current - (previous + next) * 0.5) > 0.05) result.put(entries.get(i).getKey(), entries.get(i).getValue());
        }
        result.put(entries.getLast().getKey(), entries.getLast().getValue());
        return result;
    }

    private static InterpolationType interpolation(EasingType easing) {
        return switch (easing) {
            case LINEAR, SMOOTHSTEP, SMOOTHERSTEP -> InterpolationType.LINEAR;
            case EASE_IN -> InterpolationType.EASE_IN;
            case EASE_OUT -> InterpolationType.EASE_OUT;
            case EASE_IN_OUT_CUBIC -> InterpolationType.EASE_IN_OUT;
        };
    }

    private record OperationSnapshot(EditorState editorState, int sceneIndex, KeyframeTrack cameraTrack,
                                     KeyframeTrack fovTrack, Map<KeyframeTrack, Map<Integer, Keyframe>> removedKeyframes,
                                     int expectedModCount) {
    }
}
