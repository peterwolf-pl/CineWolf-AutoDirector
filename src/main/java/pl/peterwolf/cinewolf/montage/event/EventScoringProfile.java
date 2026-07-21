package pl.peterwolf.cinewolf.montage.event;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

public record EventScoringProfile(
        double importanceWeight,
        double cinematicWeight,
        double uniquenessWeight,
        double presetWeight,
        double markerBonus,
        double selectedTargetBonus,
        double repetitionPenalty,
        double technicalRiskPenalty,
        Map<ReplayEventType, Double> baseImportance
) {
    public EventScoringProfile {
        importanceWeight = nonNegative(importanceWeight, 0.35);
        cinematicWeight = nonNegative(cinematicWeight, 0.25);
        uniquenessWeight = nonNegative(uniquenessWeight, 0.15);
        presetWeight = nonNegative(presetWeight, 0.25);
        markerBonus = nonNegative(markerBonus, 0.15);
        selectedTargetBonus = nonNegative(selectedTargetBonus, 0.1);
        repetitionPenalty = nonNegative(repetitionPenalty, 0.08);
        technicalRiskPenalty = nonNegative(technicalRiskPenalty, 0.2);
        EnumMap<ReplayEventType, Double> values = new EnumMap<>(ReplayEventType.class);
        for (ReplayEventType type : ReplayEventType.values()) values.put(type, 0.5);
        if (baseImportance != null) {
            baseImportance.forEach((type, value) -> {
                if (type != null && value != null && Double.isFinite(value)) values.put(type, clamp01(value));
            });
        }
        baseImportance = Collections.unmodifiableMap(values);
    }

    public static EventScoringProfile defaults() {
        EnumMap<ReplayEventType, Double> importance = new EnumMap<>(ReplayEventType.class);
        importance.put(ReplayEventType.DEATH, 1.0);
        importance.put(ReplayEventType.COMBAT, 0.9);
        importance.put(ReplayEventType.DAMAGE, 0.75);
        importance.put(ReplayEventType.FLIGHT_START, 0.8);
        importance.put(ReplayEventType.LANDING, 0.8);
        importance.put(ReplayEventType.HIGH_SPEED, 0.75);
        importance.put(ReplayEventType.SHARP_TURN, 0.72);
        importance.put(ReplayEventType.REPLAY_MARKER, 0.85);
        importance.put(ReplayEventType.BLOCK_PLACEMENT, 0.6);
        importance.put(ReplayEventType.BLOCK_DESTRUCTION, 0.6);
        importance.put(ReplayEventType.PAUSE, 0.25);
        return new EventScoringProfile(0.35, 0.25, 0.15, 0.25,
                0.15, 0.1, 0.08, 0.2, importance);
    }

    public double baseImportance(ReplayEventType type) {
        return baseImportance.getOrDefault(type, 0.5);
    }

    private static double nonNegative(double value, double fallback) {
        return Double.isFinite(value) && value >= 0.0 ? value : fallback;
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
