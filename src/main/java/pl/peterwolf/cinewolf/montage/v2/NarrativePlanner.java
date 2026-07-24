package pl.peterwolf.cinewolf.montage.v2;

import pl.peterwolf.cinewolf.montage.event.ReplayEventType;
import pl.peterwolf.cinewolf.montage.event.ScoredReplayEvent;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Assigns narrative phases to ordered events without forcing every phase into short montages. */
public final class NarrativePlanner {
    public List<PhasedEvent> plan(List<ScoredReplayEvent> rankedEvents, int targetShotCount) {
        Objects.requireNonNull(rankedEvents, "rankedEvents");
        if (rankedEvents.isEmpty()) return List.of();
        int count = Math.max(1, Math.min(targetShotCount, rankedEvents.size()));
        List<ScoredReplayEvent> selected = rankedEvents.stream().limit(count).toList();
        List<PhasedEvent> result = new ArrayList<>(selected.size());
        for (int i = 0; i < selected.size(); i++) {
            double progress = selected.size() == 1 ? 0.5 : (double) i / (selected.size() - 1);
            NarrativePhase phase = phaseFor(progress, selected.get(i).event().type(), selected.size());
            result.add(new PhasedEvent(selected.get(i), phase, progress));
        }
        return List.copyOf(result);
    }

    private static NarrativePhase phaseFor(double progress, ReplayEventType type, int shotCount) {
        if (shotCount <= 2) {
            return progress < 0.5 ? NarrativePhase.HOOK : NarrativePhase.FINAL_IMAGE;
        }
        if (shotCount <= 4) {
            if (progress < 0.25) return NarrativePhase.HOOK;
            if (progress < 0.55) return NarrativePhase.DEVELOPMENT;
            if (progress < 0.8) return isClimaxType(type) ? NarrativePhase.CLIMAX : NarrativePhase.ACTION;
            return NarrativePhase.FINAL_IMAGE;
        }
        if (progress < 0.08) return NarrativePhase.HOOK;
        if (progress < 0.18) return NarrativePhase.ESTABLISHING;
        if (progress < 0.28) return NarrativePhase.SUBJECT_INTRODUCTION;
        if (progress < 0.55) return NarrativePhase.DEVELOPMENT;
        if (progress < 0.72) return NarrativePhase.ACTION;
        if (progress < 0.86) return isClimaxType(type) ? NarrativePhase.CLIMAX : NarrativePhase.ACTION;
        if (progress < 0.94) return NarrativePhase.RESOLUTION;
        return NarrativePhase.FINAL_IMAGE;
    }

    private static boolean isClimaxType(ReplayEventType type) {
        return type == ReplayEventType.DEATH
                || type == ReplayEventType.COMBAT
                || type == ReplayEventType.LANDING
                || type == ReplayEventType.HIGH_SPEED;
    }

    public record PhasedEvent(ScoredReplayEvent event, NarrativePhase phase, double narrativeProgress) {
    }
}
