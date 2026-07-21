package pl.peterwolf.cinewolf.montage.timeline;

public record MontageTimelineConflictReport(
        int cameraKeyframes,
        int fovKeyframes,
        int timelapseKeyframes,
        int cameraActiveSegments,
        int fovActiveSegments,
        int timelapseActiveSegments,
        int lastRelevantTick
) {
    public MontageTimelineConflictReport {
        if (cameraKeyframes < 0 || fovKeyframes < 0 || timelapseKeyframes < 0
                || cameraActiveSegments < 0 || fovActiveSegments < 0 || timelapseActiveSegments < 0
                || lastRelevantTick < -1) {
            throw new IllegalArgumentException("Conflict counts cannot be negative");
        }
    }

    public static MontageTimelineConflictReport empty() {
        return new MontageTimelineConflictReport(0, 0, 0, 0, 0, 0, -1);
    }

    public int keyframeCount() {
        return cameraKeyframes + fovKeyframes + timelapseKeyframes;
    }

    public int activeSegmentCount() {
        return cameraActiveSegments + fovActiveSegments + timelapseActiveSegments;
    }

    public boolean hasConflicts() {
        return keyframeCount() > 0 || activeSegmentCount() > 0;
    }
}
