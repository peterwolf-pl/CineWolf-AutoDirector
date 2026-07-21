package pl.peterwolf.cinewolf.config;

import pl.peterwolf.cinewolf.montage.event.EventScoringProfile;
import pl.peterwolf.cinewolf.montage.event.ReplayEventType;

import java.util.EnumMap;
import java.util.Map;

/** Persisted, explainable weights used by deterministic event ranking. */
public final class EventScoringConfig {
    public double importanceWeight = 0.35;
    public double cinematicWeight = 0.25;
    public double uniquenessWeight = 0.15;
    public double presetWeight = 0.25;
    public double markerBonus = 0.15;
    public double selectedTargetBonus = 0.1;
    public double repetitionPenalty = 0.08;
    public double technicalRiskPenalty = 0.2;
    public Map<ReplayEventType, Double> baseImportance = defaultImportance();

    public EventScoringProfile toModel() {
        return new EventScoringProfile(importanceWeight, cinematicWeight, uniquenessWeight, presetWeight,
                markerBonus, selectedTargetBonus, repetitionPenalty, technicalRiskPenalty, baseImportance);
    }

    public void normalize() {
        EventScoringProfile value = toModel();
        importanceWeight = value.importanceWeight();
        cinematicWeight = value.cinematicWeight();
        uniquenessWeight = value.uniquenessWeight();
        presetWeight = value.presetWeight();
        markerBonus = value.markerBonus();
        selectedTargetBonus = value.selectedTargetBonus();
        repetitionPenalty = value.repetitionPenalty();
        technicalRiskPenalty = value.technicalRiskPenalty();
        baseImportance = new EnumMap<>(value.baseImportance());
    }

    private static Map<ReplayEventType, Double> defaultImportance() {
        return new EnumMap<>(EventScoringProfile.defaults().baseImportance());
    }
}
