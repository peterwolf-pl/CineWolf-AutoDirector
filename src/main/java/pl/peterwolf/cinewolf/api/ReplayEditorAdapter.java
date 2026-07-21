package pl.peterwolf.cinewolf.api;

import pl.peterwolf.cinewolf.model.CameraPathPlan;
import pl.peterwolf.cinewolf.model.TargetPose;
import pl.peterwolf.cinewolf.model.TargetReference;
import pl.peterwolf.cinewolf.model.Vec3d;

import java.util.List;
import java.util.Optional;

public interface ReplayEditorAdapter {
    boolean isAvailable();

    boolean isReplayEditorOpen();

    ReplayTimeRange getSelectedTimeRange();

    long getCurrentReplayTime();

    long getTotalReplayTime();

    List<ReplayEntityDescriptor> listEntities(long replayTime);

    Optional<TargetPose> resolveEntity(TargetReference target, long replayTime);

    CameraPose getCurrentCameraPose();

    KeyframeConflictReport detectConflicts(CameraPathPlan plan);

    KeyframeWriteResult writeCameraPath(CameraPathPlan plan, KeyframeWriteOptions options);

    UndoResult undoLastCineWolfOperation();

    void refreshTimeline();

    record ReplayTimeRange(long startTick, long endTick, boolean selected) {
    }

    record ReplayEntityDescriptor(TargetReference reference, String name, String entityType, String shortIdentifier,
                                  boolean available) {
    }

    record CameraPose(Vec3d position, double yaw, double pitch, double roll, double fov) {
    }

    enum ConflictMode { CANCEL, ADD_WITHOUT_DELETING, REPLACE_INSIDE_INTERVAL }

    record KeyframeWriteOptions(ConflictMode conflictMode) {
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
