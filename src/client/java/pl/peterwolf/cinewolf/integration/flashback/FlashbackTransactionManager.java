package pl.peterwolf.cinewolf.integration.flashback;

import com.moulberry.flashback.state.EditorScene;
import com.moulberry.flashback.state.EditorState;
import com.moulberry.flashback.state.EditorStateManager;
import com.moulberry.flashback.state.KeyframeTrack;
import pl.peterwolf.cinewolf.diagnostics.TimelineWriteDiagnostic;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Snapshot-based atomic transaction support for Flashback timeline writes.
 * Uses native history when the write path succeeds; full scene snapshot for rollback.
 */
public final class FlashbackTransactionManager {
    private SceneSnapshot lastSnapshot;
    private TimelineWriteDiagnostic lastDiagnostic;

    public SceneSnapshot captureSnapshot(String operationId) {
        EditorState state = EditorStateManager.getCurrent();
        if (state == null) return null;
        long stamp = state.acquireRead();
        try {
            EditorScene scene = state.getCurrentScene(stamp);
            Map<Integer, TrackSnapshot> tracks = new LinkedHashMap<>();
            for (int i = 0; i < scene.keyframeTracks.size(); i++) {
                KeyframeTrack track = scene.keyframeTracks.get(i);
                tracks.put(i, new TrackSnapshot(track.keyframeType.toString(),
                        new TreeMap<>(track.keyframesByTick)));
            }
            lastSnapshot = new SceneSnapshot(operationId, UUID.randomUUID().toString(), tracks,
                    System.currentTimeMillis());
            return lastSnapshot;
        } finally {
            state.release(stamp);
        }
    }

    public void markSuccess(String operationId, int camera, int fov, int replayTime, boolean nativeUndo,
                            List<String> warnings) {
        lastDiagnostic = new TimelineWriteDiagnostic(operationId, true, "write", camera, fov, replayTime,
                false, nativeUndo, List.of(), warnings, "ok");
    }

    public void markFailure(String operationId, boolean rolledBack, List<String> conflicts, String message) {
        lastDiagnostic = new TimelineWriteDiagnostic(operationId, false, "write", 0, 0, 0,
                rolledBack, false, conflicts, List.of(), message);
    }

    public TimelineWriteDiagnostic lastDiagnostic() {
        return lastDiagnostic;
    }

    public SceneSnapshot lastSnapshot() {
        return lastSnapshot;
    }

    public record TrackSnapshot(String keyframeType, TreeMap<Integer, Object> keyframes) {
        public TrackSnapshot {
            keyframes = keyframes == null ? new TreeMap<>() : new TreeMap<>(keyframes);
        }
    }

    public record SceneSnapshot(
            String operationId,
            String snapshotId,
            Map<Integer, TrackSnapshot> tracks,
            long capturedAtEpochMs
    ) {
        public SceneSnapshot {
            tracks = Map.copyOf(tracks == null ? Map.of() : tracks);
        }

        public int totalKeyframes() {
            int total = 0;
            for (TrackSnapshot track : tracks.values()) {
                total += track.keyframes().size();
            }
            return total;
        }
    }
}
