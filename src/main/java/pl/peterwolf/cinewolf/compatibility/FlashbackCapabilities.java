package pl.peterwolf.cinewolf.compatibility;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Feature surface available for the detected Flashback version.
 * UI must disable unsupported options with tooltips rather than silently emulating them.
 */
public record FlashbackCapabilities(
        boolean cameraPositionKeyframes,
        boolean cameraRotationKeyframes,
        boolean fovKeyframes,
        boolean replayTimeKeyframes,
        boolean entityTracking,
        boolean timelineSelection,
        boolean timelineMarkers,
        boolean customTimelineOverlay,
        boolean nativeUndoTransactions,
        boolean nonDestructivePreview,
        boolean speedRamps,
        boolean rollKeyframes,
        boolean editorSelectionRestore,
        boolean customMetadata
) {
    public static FlashbackCapabilities none() {
        return new FlashbackCapabilities(
                false, false, false, false, false, false, false, false,
                false, false, false, false, false, false
        );
    }

    public static FlashbackCapabilities flashback0411() {
        return new FlashbackCapabilities(
                true,  // camera position
                true,  // camera rotation
                true,  // FOV
                true,  // Timelapse / replay-time mapping
                true,  // TrackEntityKeyframe + TrackEntityKeyframeType
                true,  // export/selection range
                true,  // native markers (read + limited write)
                true,  // CineWolf overlay only
                true,  // EditorScene history entry
                true,  // in-memory preview
                true,  // via Timelapse TPS
                true,  // CameraKeyframe.roll (and TrackEntityKeyframe.roll)
                true,  // restore tick/pause/selection
                false  // no public custom metadata track
        );
    }

    public Set<String> enabledFeatures() {
        Set<String> enabled = new LinkedHashSet<>();
        if (cameraPositionKeyframes) enabled.add("camera_position_keyframes");
        if (cameraRotationKeyframes) enabled.add("camera_rotation_keyframes");
        if (fovKeyframes) enabled.add("fov_keyframes");
        if (replayTimeKeyframes) enabled.add("replay_time_keyframes");
        if (entityTracking) enabled.add("entity_tracking");
        if (timelineSelection) enabled.add("timeline_selection");
        if (timelineMarkers) enabled.add("timeline_markers");
        if (customTimelineOverlay) enabled.add("custom_timeline_overlay");
        if (nativeUndoTransactions) enabled.add("native_undo_transactions");
        if (nonDestructivePreview) enabled.add("non_destructive_preview");
        if (speedRamps) enabled.add("speed_ramps");
        if (rollKeyframes) enabled.add("roll_keyframes");
        if (editorSelectionRestore) enabled.add("editor_selection_restore");
        if (customMetadata) enabled.add("custom_metadata");
        return Set.copyOf(enabled);
    }

    public Set<String> disabledFeatures() {
        Set<String> all = Set.of(
                "camera_position_keyframes", "camera_rotation_keyframes", "fov_keyframes",
                "replay_time_keyframes", "entity_tracking", "timeline_selection", "timeline_markers",
                "custom_timeline_overlay", "native_undo_transactions", "non_destructive_preview",
                "speed_ramps", "roll_keyframes", "editor_selection_restore", "custom_metadata"
        );
        Set<String> disabled = new LinkedHashSet<>(all);
        disabled.removeAll(enabledFeatures());
        return Set.copyOf(disabled);
    }

    public boolean supportsCameraWriting() {
        return cameraPositionKeyframes && cameraRotationKeyframes && fovKeyframes;
    }

    public boolean supportsMontageWriting() {
        return supportsCameraWriting() && replayTimeKeyframes && nativeUndoTransactions;
    }
}
