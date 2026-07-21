package pl.peterwolf.cinewolf.montage.plan;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Pure validation performed before a Flashback write transaction is assembled. */
public final class MontageTimeMappingValidator {
    private static final double EPSILON_SECONDS = 1.0e-6;
    private static final double SPEED_EPSILON = 1.0e-5;

    public ValidationResult validate(List<MontageTimeMapping> mappings, double expectedOutputDurationSeconds,
                                     double minimumReplaySpeed, double maximumReplaySpeed,
                                     double maximumAdjacentSpeedChange) {
        Objects.requireNonNull(mappings, "mappings");
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        if (mappings.isEmpty()) {
            errors.add("montage.mapping.empty");
            return new ValidationResult(errors, warnings);
        }
        if (!Double.isFinite(expectedOutputDurationSeconds) || expectedOutputDurationSeconds <= 0.0) {
            errors.add("montage.mapping.invalid_output_duration");
        }
        if (!Double.isFinite(minimumReplaySpeed) || minimumReplaySpeed <= 0.0
                || !Double.isFinite(maximumReplaySpeed) || maximumReplaySpeed < minimumReplaySpeed) {
            errors.add("montage.mapping.invalid_speed_bounds");
        }
        if (!Double.isFinite(maximumAdjacentSpeedChange) || maximumAdjacentSpeedChange < 0.0) {
            errors.add("montage.mapping.invalid_speed_change");
        }

        List<MontageTimeMapping> ordered = mappings.stream()
                .sorted(Comparator.comparingDouble(MontageTimeMapping::outputStartSeconds))
                .toList();
        if (!ordered.equals(mappings)) errors.add("montage.mapping.not_output_ordered");

        MontageTimeMapping previous = null;
        for (MontageTimeMapping mapping : ordered) {
            if (Math.abs(mapping.playbackSpeed() - mapping.derivedPlaybackSpeed()) > SPEED_EPSILON) {
                errors.add("montage.mapping.speed_mismatch");
            }
            if (mapping.playbackSpeed() + SPEED_EPSILON < minimumReplaySpeed
                    || mapping.playbackSpeed() - SPEED_EPSILON > maximumReplaySpeed) {
                errors.add("montage.mapping.speed_out_of_bounds");
            }
            if (previous != null) {
                if (mapping.outputStartSeconds() + EPSILON_SECONDS < previous.outputEndSeconds()) {
                    errors.add("montage.mapping.output_overlap");
                } else if (mapping.outputStartSeconds() - previous.outputEndSeconds() > EPSILON_SECONDS) {
                    warnings.add("montage.mapping.output_gap");
                }
                if (mapping.replayStartTime() < previous.replayEndTime()) {
                    errors.add("montage.mapping.source_not_monotonic");
                }
                if (Math.abs(mapping.playbackSpeed() - previous.playbackSpeed())
                        > maximumAdjacentSpeedChange + SPEED_EPSILON) {
                    errors.add("montage.mapping.speed_change_too_large");
                }
            }
            previous = mapping;
        }

        if (Math.abs(ordered.getFirst().outputStartSeconds()) > EPSILON_SECONDS) {
            warnings.add("montage.mapping.output_starts_after_zero");
        }
        if (Math.abs(ordered.getLast().outputEndSeconds() - expectedOutputDurationSeconds) > 0.05) {
            errors.add("montage.mapping.output_duration_mismatch");
        }
        return new ValidationResult(distinct(errors), distinct(warnings));
    }

    private static List<String> distinct(List<String> values) {
        return values.stream().distinct().toList();
    }

    public record ValidationResult(List<String> errors, List<String> warnings) {
        public ValidationResult {
            errors = List.copyOf(Objects.requireNonNullElse(errors, List.of()));
            warnings = List.copyOf(Objects.requireNonNullElse(warnings, List.of()));
        }

        public boolean valid() {
            return errors.isEmpty();
        }
    }
}
