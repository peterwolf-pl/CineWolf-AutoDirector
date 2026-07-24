package pl.peterwolf.cinewolf.montage.plan;

import pl.peterwolf.cinewolf.config.MontageShotSettings.MontageShotPreferences;
import pl.peterwolf.cinewolf.model.TargetReference;
import pl.peterwolf.cinewolf.montage.preset.MontagePacing;
import pl.peterwolf.cinewolf.montage.preset.MontagePreset;
import pl.peterwolf.cinewolf.montage.preset.OutputAspectRatio;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record MontageRequest(
        MontagePreset preset,
        long sourceStartReplayTime,
        long sourceEndReplayTime,
        double outputDurationSeconds,
        OutputAspectRatio aspectRatio,
        MontagePacing pacing,
        Optional<TargetReference> mainTarget,
        boolean automaticTargetDetection,
        double minimumShotDuration,
        double maximumShotDuration,
        double cameraMovementIntensity,
        double cutFrequency,
        boolean allowReplaySpeedChanges,
        boolean preferChronologicalOrder,
        double minimumReplaySpeed,
        double maximumReplaySpeed,
        double maximumReplaySpeedChange,
        int maximumPlannedShots,
        MontageShotPreferences shotPreferences,
        List<ReplaySourceSegment> sourceSegments
) {
    public MontageRequest {
        Objects.requireNonNull(preset, "preset");
        if (sourceStartReplayTime < 0 || sourceEndReplayTime <= sourceStartReplayTime) {
            throw new IllegalArgumentException("Montage source range must move forwards");
        }
        if (!Double.isFinite(outputDurationSeconds) || outputDurationSeconds <= 0.0) {
            throw new IllegalArgumentException("Output duration must be positive");
        }
        Objects.requireNonNull(aspectRatio, "aspectRatio");
        Objects.requireNonNull(pacing, "pacing");
        mainTarget = Objects.requireNonNullElse(mainTarget, Optional.empty());
        if (!mainTarget.isPresent() && !automaticTargetDetection) {
            throw new IllegalArgumentException("A main target or automatic target detection is required");
        }
        if (!Double.isFinite(minimumShotDuration) || minimumShotDuration <= 0.0
                || !Double.isFinite(maximumShotDuration) || maximumShotDuration < minimumShotDuration) {
            throw new IllegalArgumentException("Invalid shot duration range");
        }
        cameraMovementIntensity = clamp01(cameraMovementIntensity);
        cutFrequency = clamp01(cutFrequency);
        if (!Double.isFinite(minimumReplaySpeed) || minimumReplaySpeed <= 0.0
                || !Double.isFinite(maximumReplaySpeed) || maximumReplaySpeed < minimumReplaySpeed) {
            throw new IllegalArgumentException("Invalid replay speed range");
        }
        if (!Double.isFinite(maximumReplaySpeedChange) || maximumReplaySpeedChange < 0.0) {
            throw new IllegalArgumentException("Invalid maximum replay speed change");
        }
        maximumPlannedShots = Math.max(1, maximumPlannedShots);
        shotPreferences = Objects.requireNonNullElse(shotPreferences, MontageShotPreferences.defaults());
        sourceSegments = ReplaySourceSegment.normalize(
                Objects.requireNonNullElse(sourceSegments, List.of()));
    }

    /** Compatibility constructor without explicit source segments. */
    public MontageRequest(
            MontagePreset preset,
            long sourceStartReplayTime,
            long sourceEndReplayTime,
            double outputDurationSeconds,
            OutputAspectRatio aspectRatio,
            MontagePacing pacing,
            Optional<TargetReference> mainTarget,
            boolean automaticTargetDetection,
            double minimumShotDuration,
            double maximumShotDuration,
            double cameraMovementIntensity,
            double cutFrequency,
            boolean allowReplaySpeedChanges,
            boolean preferChronologicalOrder,
            double minimumReplaySpeed,
            double maximumReplaySpeed,
            double maximumReplaySpeedChange,
            int maximumPlannedShots,
            MontageShotPreferences shotPreferences
    ) {
        this(preset, sourceStartReplayTime, sourceEndReplayTime, outputDurationSeconds, aspectRatio, pacing,
                mainTarget, automaticTargetDetection, minimumShotDuration, maximumShotDuration,
                cameraMovementIntensity, cutFrequency, allowReplaySpeedChanges, preferChronologicalOrder,
                minimumReplaySpeed, maximumReplaySpeed, maximumReplaySpeedChange, maximumPlannedShots,
                shotPreferences, List.of());
    }

    /** Compatibility constructor without explicit shot preferences. */
    public MontageRequest(
            MontagePreset preset,
            long sourceStartReplayTime,
            long sourceEndReplayTime,
            double outputDurationSeconds,
            OutputAspectRatio aspectRatio,
            MontagePacing pacing,
            Optional<TargetReference> mainTarget,
            boolean automaticTargetDetection,
            double minimumShotDuration,
            double maximumShotDuration,
            double cameraMovementIntensity,
            double cutFrequency,
            boolean allowReplaySpeedChanges,
            boolean preferChronologicalOrder,
            double minimumReplaySpeed,
            double maximumReplaySpeed,
            double maximumReplaySpeedChange,
            int maximumPlannedShots
    ) {
        this(preset, sourceStartReplayTime, sourceEndReplayTime, outputDurationSeconds, aspectRatio, pacing,
                mainTarget, automaticTargetDetection, minimumShotDuration, maximumShotDuration,
                cameraMovementIntensity, cutFrequency, allowReplaySpeedChanges, preferChronologicalOrder,
                minimumReplaySpeed, maximumReplaySpeed, maximumReplaySpeedChange, maximumPlannedShots,
                MontageShotPreferences.defaults(), List.of());
    }

    public static MontageRequest fromPreset(MontagePreset preset, long sourceStart, long sourceEnd,
                                            Optional<TargetReference> target) {
        return new MontageRequest(preset, sourceStart, sourceEnd, preset.targetDurationSeconds(),
                preset.aspectRatio(), preset.pacing(), target, target.isEmpty(), preset.minimumShotDuration(),
                preset.maximumShotDuration(), preset.style().cameraMovementIntensity(), preset.style().cutFrequency(),
                preset.style().allowReplaySpeedChanges(), preset.style().preferChronologicalOrder(),
                preset.style().minimumReplaySpeed(), preset.style().maximumReplaySpeed(),
                preset.style().maximumReplaySpeedChange(), preset.maximumShotCount(),
                MontageShotPreferences.defaults(), List.of());
    }

    /** Source windows used for analysis/planning (falls back to the continuous start/end range). */
    public List<ReplaySourceSegment> resolvedSourceSegments() {
        if (!sourceSegments.isEmpty()) return sourceSegments;
        return List.of(ReplaySourceSegment.of(sourceStartReplayTime, sourceEndReplayTime));
    }

    public long totalSourceDurationTicks() {
        return ReplaySourceSegment.totalDurationTicks(resolvedSourceSegments());
    }

    public boolean multiSegment() {
        return resolvedSourceSegments().size() > 1;
    }

    public MontageRequest withOutputDuration(double durationSeconds) {
        return new MontageRequest(preset, sourceStartReplayTime, sourceEndReplayTime, durationSeconds, aspectRatio,
                pacing, mainTarget, automaticTargetDetection, minimumShotDuration, maximumShotDuration,
                cameraMovementIntensity, cutFrequency, allowReplaySpeedChanges, preferChronologicalOrder,
                minimumReplaySpeed, maximumReplaySpeed, maximumReplaySpeedChange, maximumPlannedShots,
                shotPreferences, sourceSegments);
    }

    public MontageRequest withSourceBounds(long start, long end) {
        return new MontageRequest(preset, start, end, outputDurationSeconds, aspectRatio, pacing, mainTarget,
                automaticTargetDetection, minimumShotDuration, maximumShotDuration, cameraMovementIntensity,
                cutFrequency, allowReplaySpeedChanges, preferChronologicalOrder, minimumReplaySpeed,
                maximumReplaySpeed, maximumReplaySpeedChange, maximumPlannedShots, shotPreferences, List.of());
    }

    public MontageRequest withSourceSegments(List<ReplaySourceSegment> segments) {
        List<ReplaySourceSegment> normalized = ReplaySourceSegment.normalize(segments);
        if (normalized.isEmpty()) {
            return new MontageRequest(preset, sourceStartReplayTime, sourceEndReplayTime, outputDurationSeconds,
                    aspectRatio, pacing, mainTarget, automaticTargetDetection, minimumShotDuration,
                    maximumShotDuration, cameraMovementIntensity, cutFrequency, allowReplaySpeedChanges,
                    preferChronologicalOrder, minimumReplaySpeed, maximumReplaySpeed, maximumReplaySpeedChange,
                    maximumPlannedShots, shotPreferences, List.of());
        }
        long start = normalized.getFirst().startTick();
        long end = normalized.getLast().endTick();
        return new MontageRequest(preset, start, end, outputDurationSeconds, aspectRatio, pacing, mainTarget,
                automaticTargetDetection, minimumShotDuration, maximumShotDuration, cameraMovementIntensity,
                cutFrequency, allowReplaySpeedChanges, preferChronologicalOrder, minimumReplaySpeed,
                maximumReplaySpeed, maximumReplaySpeedChange, maximumPlannedShots, shotPreferences, normalized);
    }

    public MontageRequest withShotDurations(double minimum, double maximum) {
        return new MontageRequest(preset, sourceStartReplayTime, sourceEndReplayTime, outputDurationSeconds,
                aspectRatio, pacing, mainTarget, automaticTargetDetection, minimum, maximum,
                cameraMovementIntensity, cutFrequency, allowReplaySpeedChanges, preferChronologicalOrder,
                minimumReplaySpeed, maximumReplaySpeed, maximumReplaySpeedChange, maximumPlannedShots,
                shotPreferences, sourceSegments);
    }

    private static double clamp01(double value) {
        return Double.isFinite(value) ? Math.max(0.0, Math.min(1.0, value)) : 0.5;
    }
}
