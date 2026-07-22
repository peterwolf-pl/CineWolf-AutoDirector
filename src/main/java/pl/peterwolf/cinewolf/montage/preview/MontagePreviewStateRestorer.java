package pl.peterwolf.cinewolf.montage.preview;

import pl.peterwolf.cinewolf.api.ReplayEditorAdapter;
import pl.peterwolf.cinewolf.model.Vec3d;

/** Captures and restores editor camera/replay state after non-destructive preview. */
public final class MontagePreviewStateRestorer {
    private long restoreTick;
    private boolean restorePaused;
    private ReplayEditorAdapter.CameraPose restoreCamera;
    private boolean captured;

    public void capture(long tick, boolean paused, ReplayEditorAdapter.CameraPose camera) {
        restoreTick = tick;
        restorePaused = paused;
        restoreCamera = camera;
        captured = true;
    }

    public void capture(ReplayEditorAdapter adapter) {
        capture(adapter.getCurrentReplayTime(), false, adapter.getCurrentCameraPose());
    }

    public Snapshot snapshot() {
        return new Snapshot(restoreTick, restorePaused, restoreCamera, captured);
    }

    public void clear() {
        captured = false;
        restoreCamera = null;
    }

    public boolean captured() {
        return captured;
    }

    public long restoreTick() {
        return restoreTick;
    }

    public boolean restorePaused() {
        return restorePaused;
    }

    public ReplayEditorAdapter.CameraPose restoreCamera() {
        return restoreCamera;
    }

    public record Snapshot(long tick, boolean paused, ReplayEditorAdapter.CameraPose camera, boolean captured) {
        public Snapshot {
            if (camera == null) {
                camera = new ReplayEditorAdapter.CameraPose(Vec3d.ZERO, 0, 0, 0, 70);
            }
        }
    }
}
