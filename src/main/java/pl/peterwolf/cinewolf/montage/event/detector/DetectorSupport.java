package pl.peterwolf.cinewolf.montage.event.detector;

import pl.peterwolf.cinewolf.model.TargetReference;
import pl.peterwolf.cinewolf.model.Vec3d;
import pl.peterwolf.cinewolf.montage.analysis.MovementMetrics;
import pl.peterwolf.cinewolf.montage.analysis.ReplayEntitySnapshot;
import pl.peterwolf.cinewolf.montage.event.EventEvidence;
import pl.peterwolf.cinewolf.montage.event.ReplayEvent;
import pl.peterwolf.cinewolf.montage.event.ReplayEventType;
import pl.peterwolf.cinewolf.montage.event.ReplaySampleWindow;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;

final class DetectorSupport {
    private DetectorSupport() {
    }

    static List<List<MovementMetrics>> segments(List<MovementMetrics> metrics,
                                                Predicate<MovementMetrics> active, long maximumGapTicks) {
        List<List<MovementMetrics>> result = new ArrayList<>();
        List<MovementMetrics> current = new ArrayList<>();
        for (MovementMetrics metric : metrics) {
            boolean contiguous = current.isEmpty()
                    || metric.replayTime() - current.getLast().replayTime() <= maximumGapTicks;
            if (active.test(metric) && contiguous) {
                current.add(metric);
            } else {
                if (!current.isEmpty()) result.add(List.copyOf(current));
                current.clear();
                if (active.test(metric)) current.add(metric);
            }
        }
        if (!current.isEmpty()) result.add(List.copyOf(current));
        return List.copyOf(result);
    }

    static ReplayEvent movementEvent(ReplayEventType type, TargetReference target, List<MovementMetrics> segment,
                                     ToDoubleFunction<MovementMetrics> peakValue, double threshold,
                                     String measurementName, String unit, double confidence,
                                     EventEvidence.DetectionSource source) {
        MovementMetrics peak = segment.stream().max((left, right) -> {
            int value = Double.compare(peakValue.applyAsDouble(left), peakValue.applyAsDouble(right));
            return value != 0 ? value : Long.compare(right.replayTime(), left.replayTime());
        }).orElseThrow();
        double measured = peakValue.applyAsDouble(peak);
        double magnitude = normalize(measured, threshold);
        EventEvidence evidence = EventEvidence.of(source,
                EventEvidence.Measurement.atLeast(measurementName, measured, unit, threshold),
                EventEvidence.Measurement.observed("duration", segment.getLast().replayTime()
                        - segment.getFirst().replayTime(), "ticks"));
        return ReplayEvent.create(type, segment.getFirst().replayTime(), peak.replayTime(),
                segment.getLast().replayTime(), Set.of(target), peak.position(), magnitude, confidence, evidence);
    }

    static Optional<ReplayEntitySnapshot> snapshot(ReplaySampleWindow window, TargetReference target, long time) {
        return window.snapshotAt(target, time);
    }

    static Vec3d midpoint(Vec3d first, Vec3d second) {
        return first.lerp(second, 0.5);
    }

    static double normalize(double value, double threshold) {
        if (!Double.isFinite(value)) return 0.0;
        return Math.max(0.0, Math.min(1.0, Math.abs(value) / Math.max(1.0e-9, threshold * 2.0)));
    }
}
