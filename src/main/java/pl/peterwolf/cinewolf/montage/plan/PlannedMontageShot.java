package pl.peterwolf.cinewolf.montage.plan;

import pl.peterwolf.cinewolf.model.ShotRequest;
import pl.peterwolf.cinewolf.model.ShotType;
import pl.peterwolf.cinewolf.model.TargetReference;
import pl.peterwolf.cinewolf.montage.event.ReplayEvent;
import pl.peterwolf.cinewolf.montage.preset.FramingType;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record PlannedMontageShot(
        UUID shotId,
        int order,
        ReplayEvent sourceEvent,
        double sourceEventScore,
        TargetReference target,
        ShotType shotType,
        FramingType framing,
        long sourceReplayStartTime,
        long sourceReplayEndTime,
        double outputStartSeconds,
        double outputDurationSeconds,
        double replaySpeed,
        ShotRequest shotRequest,
        boolean enabled,
        boolean locked,
        List<String> planningReasons,
        List<MontageWarning> warnings
) {
    public PlannedMontageShot {
        Objects.requireNonNull(shotId, "shotId");
        if (order < 0) throw new IllegalArgumentException("order cannot be negative");
        Objects.requireNonNull(sourceEvent, "sourceEvent");
        sourceEventScore = finiteOrZero(sourceEventScore);
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(shotType, "shotType");
        Objects.requireNonNull(framing, "framing");
        if (sourceReplayStartTime < 0 || sourceReplayEndTime <= sourceReplayStartTime) {
            throw new IllegalArgumentException("Shot source time must move forwards");
        }
        if (!Double.isFinite(outputStartSeconds) || outputStartSeconds < 0.0
                || !Double.isFinite(outputDurationSeconds) || outputDurationSeconds <= 0.0) {
            throw new IllegalArgumentException("Invalid montage output time");
        }
        if (!Double.isFinite(replaySpeed) || replaySpeed <= 0.0) {
            throw new IllegalArgumentException("Replay speed must be positive");
        }
        Objects.requireNonNull(shotRequest, "shotRequest");
        if (shotRequest.shotType() != shotType || !shotRequest.target().equals(target)) {
            throw new IllegalArgumentException("Shot request must match planned type and target");
        }
        planningReasons = List.copyOf(Objects.requireNonNullElse(planningReasons, List.of()));
        warnings = List.copyOf(Objects.requireNonNullElse(warnings, List.of()));
    }

    public double outputEndSeconds() {
        return outputStartSeconds + outputDurationSeconds;
    }

    public MontageTimeMapping timeMapping() {
        return new MontageTimeMapping(outputStartSeconds, outputEndSeconds(), sourceReplayStartTime,
                sourceReplayEndTime, replaySpeed);
    }

    public PlannedMontageShot withOrderAndOutput(int replacementOrder, double replacementOutputStart) {
        return copy(shotId, replacementOrder, target, shotType, framing, sourceReplayStartTime, sourceReplayEndTime,
                replacementOutputStart, outputDurationSeconds, replaySpeed, shotRequest, enabled, locked,
                planningReasons, warnings);
    }

    public PlannedMontageShot withEnabled(boolean replacement) {
        return copy(shotId, order, target, shotType, framing, sourceReplayStartTime, sourceReplayEndTime,
                outputStartSeconds, outputDurationSeconds, replaySpeed, shotRequest, replacement, locked,
                planningReasons, warnings);
    }

    public PlannedMontageShot withLocked(boolean replacement) {
        return copy(shotId, order, target, shotType, framing, sourceReplayStartTime, sourceReplayEndTime,
                outputStartSeconds, outputDurationSeconds, replaySpeed, shotRequest, enabled, replacement,
                planningReasons, warnings);
    }

    public PlannedMontageShot withRequest(ShotRequest replacement, FramingType replacementFraming,
                                           List<String> additionalReasons) {
        List<String> reasons = new java.util.ArrayList<>(planningReasons);
        reasons.addAll(Objects.requireNonNullElse(additionalReasons, List.of()));
        double replacementSpeed = ((replacement.replayEndTime() - replacement.replayStartTime()) / 20.0)
                / outputDurationSeconds;
        return copy(shotId, order, replacement.target(), replacement.shotType(), replacementFraming,
                replacement.replayStartTime(), replacement.replayEndTime(), outputStartSeconds,
                outputDurationSeconds, replacementSpeed, replacement, enabled, locked, reasons, warnings);
    }

    public PlannedMontageShot duplicate(UUID replacementId) {
        return copy(replacementId, order, target, shotType, framing, sourceReplayStartTime, sourceReplayEndTime,
                outputStartSeconds, outputDurationSeconds, replaySpeed, shotRequest, enabled, false,
                append(planningReasons, "montage.reason.duplicated"), warnings);
    }

    private PlannedMontageShot copy(UUID id, int newOrder, TargetReference newTarget, ShotType newType,
                                    FramingType newFraming, long newSourceStart, long newSourceEnd,
                                    double newOutputStart, double newDuration, double newSpeed,
                                    ShotRequest request, boolean newEnabled, boolean newLocked,
                                    List<String> reasons, List<MontageWarning> newWarnings) {
        return new PlannedMontageShot(id, newOrder, sourceEvent, sourceEventScore, newTarget, newType, newFraming,
                newSourceStart, newSourceEnd, newOutputStart, newDuration, newSpeed, request, newEnabled,
                newLocked, reasons, newWarnings);
    }

    private static List<String> append(List<String> source, String value) {
        java.util.ArrayList<String> result = new java.util.ArrayList<>(source);
        result.add(value);
        return List.copyOf(result);
    }

    private static double finiteOrZero(double value) {
        return Double.isFinite(value) ? Math.max(0.0, value) : 0.0;
    }
}
