package pl.peterwolf.cinewolf.montage.analysis;

import pl.peterwolf.cinewolf.model.TargetReference;
import pl.peterwolf.cinewolf.montage.event.ReplayEventType;
import pl.peterwolf.cinewolf.montage.plan.ReplaySourceSegment;

import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public record ReplayAnalysisRequest(
        long startReplayTime,
        long endReplayTime,
        Set<TargetReference> selectedTargets,
        boolean automaticTargetDetection,
        Set<ReplayEventType> enabledEventTypes,
        double sensitivity,
        int coarseSamplesPerSecond,
        int detailedSamplesPerSecond,
        List<ReplaySourceSegment> sourceSegments
) {
    public ReplayAnalysisRequest {
        if (startReplayTime < 0 || endReplayTime <= startReplayTime) {
            throw new IllegalArgumentException("Replay analysis range must move forwards");
        }
        selectedTargets = Set.copyOf(Objects.requireNonNullElse(selectedTargets, Set.of()));
        enabledEventTypes = enabledEventTypes == null || enabledEventTypes.isEmpty()
                ? Set.copyOf(EnumSet.allOf(ReplayEventType.class)) : Set.copyOf(enabledEventTypes);
        if (!Double.isFinite(sensitivity)) sensitivity = 0.5;
        sensitivity = Math.max(0.0, Math.min(1.0, sensitivity));
        coarseSamplesPerSecond = Math.max(1, Math.min(20, coarseSamplesPerSecond));
        detailedSamplesPerSecond = Math.max(coarseSamplesPerSecond,
                Math.min(60, detailedSamplesPerSecond));
        sourceSegments = ReplaySourceSegment.normalize(
                Objects.requireNonNullElse(sourceSegments, List.of()));
    }

    public ReplayAnalysisRequest(
            long startReplayTime,
            long endReplayTime,
            Set<TargetReference> selectedTargets,
            boolean automaticTargetDetection,
            Set<ReplayEventType> enabledEventTypes,
            double sensitivity,
            int coarseSamplesPerSecond,
            int detailedSamplesPerSecond
    ) {
        this(startReplayTime, endReplayTime, selectedTargets, automaticTargetDetection, enabledEventTypes,
                sensitivity, coarseSamplesPerSecond, detailedSamplesPerSecond, List.of());
    }

    public static ReplayAnalysisRequest defaults(long startReplayTime, long endReplayTime) {
        return new ReplayAnalysisRequest(startReplayTime, endReplayTime, Set.of(), true,
                EnumSet.allOf(ReplayEventType.class), 0.5, 4, 16, List.of());
    }

    public List<ReplaySourceSegment> resolvedSourceSegments() {
        if (!sourceSegments.isEmpty()) return sourceSegments;
        return List.of(ReplaySourceSegment.of(startReplayTime, endReplayTime));
    }

    public boolean multiSegment() {
        return resolvedSourceSegments().size() > 1;
    }

    public long totalSourceDurationTicks() {
        return ReplaySourceSegment.totalDurationTicks(resolvedSourceSegments());
    }

    public boolean contains(long tick) {
        for (ReplaySourceSegment segment : resolvedSourceSegments()) {
            if (segment.contains(tick)) return true;
        }
        return false;
    }
}
