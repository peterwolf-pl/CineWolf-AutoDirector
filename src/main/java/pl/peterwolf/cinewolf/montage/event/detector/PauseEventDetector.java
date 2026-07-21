package pl.peterwolf.cinewolf.montage.event.detector;

import pl.peterwolf.cinewolf.model.TargetReference;
import pl.peterwolf.cinewolf.montage.analysis.MovementMetrics;
import pl.peterwolf.cinewolf.montage.analysis.ReplayAnalysisContext;
import pl.peterwolf.cinewolf.montage.analysis.ReplayEntitySnapshot;
import pl.peterwolf.cinewolf.montage.analysis.ReplaySample;
import pl.peterwolf.cinewolf.montage.event.DetectorThresholds;
import pl.peterwolf.cinewolf.montage.event.EventEvidence;
import pl.peterwolf.cinewolf.montage.event.ReplayEvent;
import pl.peterwolf.cinewolf.montage.event.ReplayEventDetector;
import pl.peterwolf.cinewolf.montage.event.ReplayEventType;
import pl.peterwolf.cinewolf.montage.event.ReplaySampleWindow;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class PauseEventDetector implements ReplayEventDetector {
    @Override
    public Set<ReplayEventType> supportedTypes() {
        return Set.of(ReplayEventType.PAUSE);
    }

    @Override
    public List<ReplayEvent> detect(ReplaySampleWindow window, ReplayAnalysisContext context, double sensitivity) {
        DetectorThresholds thresholds = context.detectorThresholds();
        double speedThreshold = thresholds.sensitivityAdjusted(thresholds.pauseMaximumSpeed(), 1.0 - sensitivity);
        long durationThreshold = Math.max(1L, Math.round(thresholds.pauseMinimumDurationTicks()
                * (1.25 - Math.max(0.0, Math.min(1.0, sensitivity)) * 0.5)));
        List<ReplayEvent> events = new ArrayList<>();
        window.movementMetrics().entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(target -> target.uuid().toString())))
                .forEach(entry -> detectTarget(window, entry.getKey(), entry.getValue(), thresholds,
                        speedThreshold, durationThreshold, events));
        events.sort(Comparator.comparingLong(ReplayEvent::startReplayTime));
        return List.copyOf(events);
    }

    private static void detectTarget(ReplaySampleWindow window, TargetReference target, List<MovementMetrics> metrics,
                                     DetectorThresholds thresholds, double speedThreshold, long durationThreshold,
                                     List<ReplayEvent> events) {
        if (!window.includes(target)) return;
        long maxGap = Math.max(thresholds.eventMergeGapTicks(), typicalGap(metrics) * 2);
        for (List<MovementMetrics> segment : DetectorSupport.segments(metrics,
                metric -> metric.smoothedSpeed() <= speedThreshold && quietState(window, target, metric.replayTime()),
                maxGap)) {
            long duration = segment.getLast().replayTime() - segment.getFirst().replayTime();
            if (duration < durationThreshold || hasActions(window, segment.getFirst().replayTime(),
                    segment.getLast().replayTime())) continue;
            MovementMetrics peak = segment.get(segment.size() / 2);
            EventEvidence evidence = EventEvidence.of(EventEvidence.DetectionSource.DERIVED_MOVEMENT,
                    EventEvidence.Measurement.atMost("maximum_speed",
                            segment.stream().mapToDouble(MovementMetrics::smoothedSpeed).max().orElse(0.0),
                            "blocks_per_second", speedThreshold),
                    EventEvidence.Measurement.atLeast("stationary_duration", duration, "ticks", durationThreshold));
            events.add(ReplayEvent.create(ReplayEventType.PAUSE, segment.getFirst().replayTime(), peak.replayTime(),
                    segment.getLast().replayTime(), Set.of(target), peak.position(),
                    Math.min(1.0, duration / (double) (durationThreshold * 2)), 0.85, evidence));
        }
    }

    private static boolean quietState(ReplaySampleWindow window, TargetReference target, long time) {
        ReplayEntitySnapshot snapshot = window.snapshotAt(target, time).orElse(null);
        return snapshot != null && snapshot.alive() && !snapshot.attacking() && !snapshot.swinging()
                && snapshot.hurtTime() == 0;
    }

    private static boolean hasActions(ReplaySampleWindow window, long start, long end) {
        for (ReplaySample sample : window.samples()) {
            if (sample.replayTime() >= start && sample.replayTime() <= end && !sample.actions().isEmpty()) return true;
        }
        return false;
    }

    private static long typicalGap(List<MovementMetrics> metrics) {
        if (metrics.size() < 2) return 1L;
        List<Long> gaps = new ArrayList<>();
        for (int index = 1; index < metrics.size(); index++) {
            gaps.add(metrics.get(index).replayTime() - metrics.get(index - 1).replayTime());
        }
        gaps.sort(Long::compareTo);
        return Math.max(1L, gaps.get(gaps.size() / 2));
    }
}
