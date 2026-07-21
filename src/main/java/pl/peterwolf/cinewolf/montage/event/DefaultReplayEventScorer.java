package pl.peterwolf.cinewolf.montage.event;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class DefaultReplayEventScorer implements ReplayEventScorer {
    private static final long MARKER_PROXIMITY_TICKS = 40L;

    @Override
    public ScoredReplayEvent score(ReplayEvent event, EventScoringContext context) {
        EventScoringProfile profile = context.profile();
        double baseImportance = profile.baseImportance(event.type());
        double importance = clamp01(baseImportance * 0.4 + event.magnitude() * 0.35 + event.confidence() * 0.25);
        double cinematic = cinematicScore(event);
        int occurrences = Math.max(1, context.occurrenceCounts().getOrDefault(event.type(), 1));
        double uniqueness = 1.0 / occurrences;
        double preset = clamp01(context.presetWeights().getOrDefault(event.type(), 1.0));
        boolean markerNearby = event.type() == ReplayEventType.REPLAY_MARKER || context.replayMarkerTimes().stream()
                .anyMatch(time -> Math.abs(time - event.peakReplayTime()) <= MARKER_PROXIMITY_TICKS);
        double markerBonus = markerNearby ? profile.markerBonus() : 0.0;
        boolean selectedTarget = !context.selectedTargets().isEmpty()
                && event.targets().stream().anyMatch(context.selectedTargets()::contains);
        double targetBonus = selectedTarget ? profile.selectedTargetBonus() : 0.0;
        double repetitionPenalty = profile.repetitionPenalty() * ((occurrences - 1.0) / occurrences);
        double technicalRiskPenalty = profile.technicalRiskPenalty() * (1.0 - event.confidence());
        double finalScore = profile.importanceWeight() * importance
                + profile.cinematicWeight() * cinematic
                + profile.uniquenessWeight() * uniqueness
                + profile.presetWeight() * preset
                + markerBonus + targetBonus - repetitionPenalty - technicalRiskPenalty;
        finalScore = Math.max(0.0, finalScore);

        List<String> reasons = new ArrayList<>();
        reasons.add(format("importance", importance, baseImportance));
        reasons.add(format("cinematic", cinematic, event.magnitude()));
        reasons.add(String.format(Locale.ROOT, "uniqueness=%.4f;occurrences=%d", uniqueness, occurrences));
        reasons.add(String.format(Locale.ROOT, "preset_compatibility=%.4f", preset));
        if (markerNearby) reasons.add(String.format(Locale.ROOT, "marker_bonus=%.4f", markerBonus));
        if (selectedTarget) reasons.add(String.format(Locale.ROOT, "selected_target_bonus=%.4f", targetBonus));
        if (repetitionPenalty > 0.0) reasons.add(String.format(Locale.ROOT, "repetition_penalty=%.4f", repetitionPenalty));
        if (technicalRiskPenalty > 0.0) reasons.add(String.format(Locale.ROOT,
                "technical_risk_penalty=%.4f;confidence=%.4f", technicalRiskPenalty, event.confidence()));

        return new ScoredReplayEvent(event, importance, cinematic, uniqueness, preset, markerBonus, targetBonus,
                repetitionPenalty, technicalRiskPenalty, finalScore, reasons);
    }

    private static double cinematicScore(ReplayEvent event) {
        double typeScore = switch (event.type()) {
            case COMBAT, DEATH, FLIGHT_START, LANDING -> 0.95;
            case HIGH_SPEED, ACCELERATION, DECELERATION, SHARP_TURN, VEHICLE_MOVEMENT, FLIGHT -> 0.85;
            case ALTITUDE_GAIN, ALTITUDE_LOSS, BLOCK_PLACEMENT, BLOCK_DESTRUCTION, REPLAY_MARKER -> 0.75;
            case DAMAGE, VEHICLE_ENTER, VEHICLE_EXIT, POSITION_CHANGE -> 0.65;
            case PAUSE -> 0.35;
        };
        double durationSeconds = event.durationTicks() / 20.0;
        double durationFitness = durationSeconds <= 0.0 ? 0.55
                : durationSeconds <= 8.0 ? 1.0 : Math.max(0.35, 1.0 - (durationSeconds - 8.0) / 40.0);
        return clamp01(typeScore * 0.55 + event.magnitude() * 0.25 + durationFitness * 0.2);
    }

    private static String format(String name, double value, double secondary) {
        return String.format(Locale.ROOT, "%s=%.4f;basis=%.4f", name, value, secondary);
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
