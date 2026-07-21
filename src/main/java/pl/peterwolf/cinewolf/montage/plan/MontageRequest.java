package pl.peterwolf.cinewolf.montage.plan;

import pl.peterwolf.cinewolf.model.TargetReference;
import pl.peterwolf.cinewolf.montage.preset.MontagePacing;
import pl.peterwolf.cinewolf.montage.preset.MontagePreset;
import pl.peterwolf.cinewolf.montage.preset.OutputAspectRatio;

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
        int maximumPlannedShots
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
    }

    public static MontageRequest fromPreset(MontagePreset preset, long sourceStart, long sourceEnd,
                                            Optional<TargetReference> target) {
        return new MontageRequest(preset, sourceStart, sourceEnd, preset.targetDurationSeconds(),
                preset.aspectRatio(), preset.pacing(), target, target.isEmpty(), preset.minimumShotDuration(),
                preset.maximumShotDuration(), preset.style().cameraMovementIntensity(), preset.style().cutFrequency(),
                preset.style().allowReplaySpeedChanges(), preset.style().preferChronologicalOrder(),
                preset.style().minimumReplaySpeed(), preset.style().maximumReplaySpeed(),
                preset.style().maximumReplaySpeedChange(), preset.maximumShotCount());
    }

    private static double clamp01(double value) {
        return Double.isFinite(value) ? Math.max(0.0, Math.min(1.0, value)) : 0.5;
    }
}
