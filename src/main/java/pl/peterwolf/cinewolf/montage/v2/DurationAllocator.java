package pl.peterwolf.cinewolf.montage.v2;

import pl.peterwolf.cinewolf.model.ShotType;
import pl.peterwolf.cinewolf.montage.event.ScoredReplayEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Allocates non-uniform shot durations from score, phase, and style. */
public final class DurationAllocator {
    public List<Double> allocate(
            List<NarrativePlanner.PhasedEvent> events,
            MontageStyleProfile style,
            double totalOutputSeconds,
            double minShot,
            double maxShot
    ) {
        Objects.requireNonNull(events, "events");
        Objects.requireNonNull(style, "style");
        if (events.isEmpty()) return List.of();
        minShot = Math.max(0.5, minShot);
        maxShot = Math.max(minShot, maxShot);
        totalOutputSeconds = Math.max(minShot, totalOutputSeconds);

        double[] weights = new double[events.size()];
        double sum = 0.0;
        for (int i = 0; i < events.size(); i++) {
            weights[i] = weight(events.get(i), style);
            sum += weights[i];
        }
        if (sum <= 0.0) {
            double equal = clamp(totalOutputSeconds / events.size(), minShot, maxShot);
            List<Double> equalList = new ArrayList<>();
            for (int i = 0; i < events.size(); i++) equalList.add(equal);
            return scaleToTotal(equalList, totalOutputSeconds, minShot, maxShot);
        }
        List<Double> raw = new ArrayList<>(events.size());
        for (double weight : weights) {
            raw.add(clamp(totalOutputSeconds * (weight / sum), minShot, maxShot));
        }
        return scaleToTotal(raw, totalOutputSeconds, minShot, maxShot);
    }

    private static double weight(NarrativePlanner.PhasedEvent phased, MontageStyleProfile style) {
        ScoredReplayEvent scored = phased.event();
        double score = Math.max(0.05, scored.finalScore());
        double phaseBias = switch (phased.phase()) {
            case HOOK, FINAL_IMAGE -> 1.15;
            case CLIMAX -> 1.25;
            case ACTION -> 1.1;
            case ESTABLISHING, RESOLUTION -> 1.05;
            default -> 1.0;
        };
        double lengthBias = switch (style.shotLengthProfile()) {
            case VERY_SHORT -> 0.85;
            case SHORT -> 0.95;
            case LONG -> 1.2;
            case MEDIUM, MIXED -> 1.0;
        };
        return score * phaseBias * lengthBias;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static List<Double> scaleToTotal(List<Double> values, double total, double min, double max) {
        double sum = values.stream().mapToDouble(Double::doubleValue).sum();
        if (sum <= 0.0) return values;
        double scale = total / sum;
        List<Double> scaled = new ArrayList<>(values.size());
        for (double value : values) {
            scaled.add(clamp(value * scale, min, max));
        }
        // Final residual adjustment on last shot.
        double adjustedSum = scaled.stream().mapToDouble(Double::doubleValue).sum();
        if (!scaled.isEmpty()) {
            double last = clamp(scaled.get(scaled.size() - 1) + (total - adjustedSum), min, max);
            scaled.set(scaled.size() - 1, last);
        }
        return List.copyOf(scaled);
    }

    public double preferredDuration(ShotType type, NarrativePhase phase, MontageStyleProfile style) {
        double base = switch (style.shotLengthProfile()) {
            case VERY_SHORT -> 1.8;
            case SHORT -> 2.5;
            case MEDIUM -> 4.0;
            case LONG -> 6.0;
            case MIXED -> 3.5;
        };
        if (type == ShotType.ORBIT || type == ShotType.SPIRAL) base *= 1.15;
        if (type == ShotType.CLOSE_DETAIL) base *= 0.85;
        if (phase == NarrativePhase.CLIMAX) base *= 0.9;
        if (phase == NarrativePhase.FINAL_IMAGE) base *= 1.1;
        return base;
    }
}
