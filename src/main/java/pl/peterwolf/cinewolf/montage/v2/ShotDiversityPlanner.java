package pl.peterwolf.cinewolf.montage.v2;

import pl.peterwolf.cinewolf.model.ShotType;
import pl.peterwolf.cinewolf.montage.event.ReplayEventType;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Scores candidate shot types for diversity and narrative fit. */
public final class ShotDiversityPlanner {
    public ShotType select(
            List<ShotType> candidates,
            List<ShotType> history,
            ReplayEventType eventType,
            NarrativePhase phase,
            MontageStyleProfile style,
            Set<ShotType> supported
    ) {
        Objects.requireNonNull(candidates, "candidates");
        Objects.requireNonNull(style, "style");
        if (candidates.isEmpty()) return ShotType.FOLLOW;
        ShotType best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        Map<ShotType, Integer> counts = counts(history);
        for (ShotType candidate : candidates) {
            if (supported != null && !supported.contains(candidate)) continue;
            if (style.restrictedShots().contains(candidate)) continue;
            double score = 0.0;
            if (style.preferredShots().contains(candidate)) score += 2.0;
            if (!history.isEmpty() && history.get(history.size() - 1) == candidate) score -= 3.5;
            score -= counts.getOrDefault(candidate, 0) * 1.2;
            if (candidate == ShotType.ORBIT) score -= counts.getOrDefault(ShotType.ORBIT, 0) * 0.8;
            if (candidate == ShotType.CHASE) score -= counts.getOrDefault(ShotType.CHASE, 0) * 0.8;
            if (candidate == ShotType.CLOSE_DETAIL) score -= counts.getOrDefault(ShotType.CLOSE_DETAIL, 0) * 0.6;
            score += phaseAffinity(candidate, phase);
            score += eventAffinity(candidate, eventType);
            if (score > bestScore) {
                bestScore = score;
                best = candidate;
            }
        }
        return best == null ? candidates.get(0) : best;
    }

    private static Map<ShotType, Integer> counts(List<ShotType> history) {
        Map<ShotType, Integer> map = new EnumMap<>(ShotType.class);
        if (history == null) return map;
        for (ShotType type : history) {
            map.merge(type, 1, Integer::sum);
        }
        return map;
    }

    private static double phaseAffinity(ShotType type, NarrativePhase phase) {
        return switch (phase) {
            case HOOK -> type == ShotType.REVEAL || type == ShotType.DOLLY_IN ? 1.5 : 0.2;
            case ESTABLISHING -> type == ShotType.ORBIT || type == ShotType.CRANE_UP ? 1.4 : 0.1;
            case SUBJECT_INTRODUCTION -> type == ShotType.FOLLOW || type == ShotType.STATIC_TRACKING ? 1.2 : 0.1;
            case ACTION -> type == ShotType.CHASE || type == ShotType.FLYBY || type == ShotType.SIDE_TRACKING ? 1.5 : 0.0;
            case CLIMAX -> type == ShotType.CLOSE_DETAIL || type == ShotType.CHASE ? 1.4 : 0.2;
            case RESOLUTION, FINAL_IMAGE -> type == ShotType.DOLLY_OUT || type == ShotType.CRANE_UP ? 1.5 : 0.2;
            default -> 0.0;
        };
    }

    private static double eventAffinity(ShotType type, ReplayEventType eventType) {
        if (eventType == null) return 0.0;
        return switch (eventType) {
            case HIGH_SPEED, ACCELERATION -> type == ShotType.CHASE || type == ShotType.SIDE_TRACKING ? 1.2 : 0.0;
            case SHARP_TURN -> type == ShotType.ORBIT || type == ShotType.SPIRAL ? 1.0 : 0.0;
            case FLIGHT, FLIGHT_START -> type == ShotType.FLYBY || type == ShotType.CRANE_UP ? 1.2 : 0.0;
            case LANDING -> type == ShotType.STATIC_TRACKING || type == ShotType.DOLLY_OUT ? 1.1 : 0.0;
            case VEHICLE_MOVEMENT, VEHICLE_ENTER -> type == ShotType.VEHICLE_PROFILE || type == ShotType.SIDE_TRACKING ? 1.2 : 0.0;
            case COMBAT, DAMAGE, DEATH -> type == ShotType.CLOSE_DETAIL || type == ShotType.CHASE ? 1.0 : 0.0;
            default -> 0.0;
        };
    }

    public List<String> diversityWarnings(List<ShotType> history) {
        List<String> warnings = new ArrayList<>();
        if (history == null || history.size() < 3) return warnings;
        Map<ShotType, Integer> counts = counts(history);
        for (Map.Entry<ShotType, Integer> entry : counts.entrySet()) {
            if (entry.getValue() >= Math.max(3, history.size() / 2)) {
                warnings.add("montage.diversity.overused:" + entry.getKey());
            }
        }
        return warnings;
    }
}
