package pl.peterwolf.cinewolf.api;

import pl.peterwolf.cinewolf.compatibility.CompatibilityStatus;
import pl.peterwolf.cinewolf.compatibility.FlashbackCapabilities;
import pl.peterwolf.cinewolf.model.CameraPathPlan;
import pl.peterwolf.cinewolf.model.TargetPose;
import pl.peterwolf.cinewolf.model.TargetReference;
import pl.peterwolf.cinewolf.model.Vec3d;
import pl.peterwolf.cinewolf.project.v2.ReplayIdentity;

import java.util.List;
import java.util.Optional;

/**
 * Flashback-isolating adapter boundary used by CineWolf core/UI.
 * Only Flashback integration classes may implement this with Flashback types.
 */
public interface ReplayEditorAdapter {
    boolean isAvailable();

    boolean isReplayEditorOpen();

    default boolean isReplayOpen() {
        return isReplayEditorOpen();
    }

    default boolean isEditorScreenOpen() {
        return isReplayEditorOpen();
    }

    default String editorVersion() {
        return "unknown";
    }

    default CompatibilityStatus compatibilityStatus() {
        return null;
    }

    default FlashbackCapabilities capabilities() {
        CompatibilityStatus status = compatibilityStatus();
        return status == null ? FlashbackCapabilities.none() : status.capabilities();
    }

    default ReplayIdentity getReplayIdentity() {
        return new ReplayIdentity("unknown", "unknown", 0, java.time.Instant.EPOCH, "", "");
    }

    ReplayTimeRange getSelectedTimeRange();

    long getCurrentReplayTime();

    long getTotalReplayTime();

    default Optional<ReplayTimeRange> getSelectedTimeRangeOptional() {
        ReplayTimeRange range = getSelectedTimeRange();
        return range != null && range.selected() ? Optional.of(range) : Optional.empty();
    }

    default ReplayPlaybackState getPlaybackState() {
        return new ReplayPlaybackState(false, getCurrentReplayTime(), 1.0);
    }

    List<ReplayEntityDescriptor> listEntities(long replayTime);

    Optional<TargetPose> resolveEntity(TargetReference target, long replayTime);

    default Optional<TargetPose> resolveTargetPose(TargetReference target, long replayTime) {
        return resolveEntity(target, replayTime);
    }

    CameraPose getCurrentCameraPose();

    KeyframeConflictReport detectConflicts(CameraPathPlan plan);

    KeyframeWriteResult writeCameraPath(CameraPathPlan plan, KeyframeWriteOptions options);

    UndoResult undoLastCineWolfOperation();

    void refreshTimeline();

    default void refreshEditor() {
        refreshTimeline();
    }

    default void close() {
        // no-op
    }

    record ReplayTimeRange(long startTick, long endTick, boolean selected) {
    }

    record ReplayEntityDescriptor(TargetReference reference, String name, String entityType, String shortIdentifier,
                                  boolean available) {
    }

    record CameraPose(Vec3d position, double yaw, double pitch, double roll, double fov) {
    }

    record ReplayPlaybackState(boolean playing, long replayTick, double speed) {
    }

    enum ConflictMode {
        CANCEL,
        ADD_WITHOUT_DELETING,
        REPLACE_INSIDE_INTERVAL,
        INSERT_AFTER_EXISTING,
        SEPARATE_LAYER
    }

    record KeyframeWriteOptions(ConflictMode conflictMode) {
        public KeyframeWriteOptions {
            conflictMode = conflictMode == null ? ConflictMode.CANCEL : conflictMode;
        }
    }

    record KeyframeConflictReport(int cameraKeyframes, int fovKeyframes) {
        public boolean hasConflicts() {
            return cameraKeyframes > 0 || fovKeyframes > 0;
        }

        public int total() {
            return cameraKeyframes + fovKeyframes;
        }
    }

    record KeyframeWriteResult(boolean success, int cameraKeyframes, int fovKeyframes, String message) {
    }

    record UndoResult(boolean success, String message) {
    }
}
