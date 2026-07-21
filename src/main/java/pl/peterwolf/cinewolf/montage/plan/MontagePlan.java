package pl.peterwolf.cinewolf.montage.plan;

import pl.peterwolf.cinewolf.montage.preset.MontagePreset;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record MontagePlan(
        UUID montageId,
        MontagePreset preset,
        long sourceStartReplayTime,
        long sourceEndReplayTime,
        double outputDurationSeconds,
        List<PlannedMontageShot> shots,
        List<MontageTransition> transitions,
        List<MontageTimeMapping> timeMappings,
        MontagePlanStatistics statistics,
        List<MontageWarning> warnings
) {
    public MontagePlan {
        Objects.requireNonNull(montageId, "montageId");
        Objects.requireNonNull(preset, "preset");
        if (sourceStartReplayTime < 0 || sourceEndReplayTime <= sourceStartReplayTime) {
            throw new IllegalArgumentException("Montage source range must move forwards");
        }
        if (!Double.isFinite(outputDurationSeconds) || outputDurationSeconds <= 0.0) {
            throw new IllegalArgumentException("Montage output duration must be positive");
        }
        shots = List.copyOf(Objects.requireNonNullElse(shots, List.of()));
        transitions = List.copyOf(Objects.requireNonNullElse(transitions, List.of()));
        timeMappings = List.copyOf(Objects.requireNonNullElse(timeMappings, List.of()));
        Objects.requireNonNull(statistics, "statistics");
        warnings = List.copyOf(Objects.requireNonNullElse(warnings, List.of()));
    }

    public List<PlannedMontageShot> enabledShots() {
        return shots.stream().filter(PlannedMontageShot::enabled).toList();
    }
}
