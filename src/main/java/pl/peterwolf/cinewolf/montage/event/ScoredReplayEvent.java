package pl.peterwolf.cinewolf.montage.event;

import java.util.List;
import java.util.Objects;

public record ScoredReplayEvent(
        ReplayEvent event,
        double importanceScore,
        double cinematicScore,
        double uniquenessScore,
        double presetCompatibilityScore,
        double markerBonus,
        double targetBonus,
        double repetitionPenalty,
        double technicalRiskPenalty,
        double finalScore,
        List<String> scoringReasons
) {
    public ScoredReplayEvent {
        Objects.requireNonNull(event, "event");
        scoringReasons = List.copyOf(Objects.requireNonNullElse(scoringReasons, List.of()));
    }
}
