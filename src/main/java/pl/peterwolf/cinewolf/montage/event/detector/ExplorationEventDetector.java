package pl.peterwolf.cinewolf.montage.event.detector;

import pl.peterwolf.cinewolf.model.TargetReference;
import pl.peterwolf.cinewolf.montage.analysis.MovementMetrics;
import pl.peterwolf.cinewolf.montage.analysis.ReplayAnalysisContext;
import pl.peterwolf.cinewolf.montage.analysis.ReplayEntitySnapshot;
import pl.peterwolf.cinewolf.montage.event.DetectorThresholds;
import pl.peterwolf.cinewolf.montage.event.EventEvidence;
import pl.peterwolf.cinewolf.montage.event.ReplayEvent;
import pl.peterwolf.cinewolf.montage.event.ReplayEventDetector;
import pl.peterwolf.cinewolf.montage.event.ReplayEventType;
import pl.peterwolf.cinewolf.montage.event.ReplaySampleWindow;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Detects sustained sightseeing / exploration: moderate ground movement covering meaningful
 * distance without vehicle/flight context and without high-speed peaks.
 */
public final class ExplorationEventDetector implements ReplayEventDetector {
    private static final Set<ReplayEventType> TYPES = Set.copyOf(EnumSet.of(ReplayEventType.EXPLORATION));
    private static final double MIN_SPEED = 0.35;
    private static final long MIN_DURATION_TICKS = 80L;
    private static final double PATH_DISTANCE_MULTIPLIER = 8.0;
    private static final double MIN_HEADING_CHANGE = 35.0;

    @Override
    public Set<ReplayEventType> supportedTypes() {
        return TYPES;
    }

    @Override
    public List<ReplayEvent> detect(ReplaySampleWindow window, ReplayAnalysisContext context, double sensitivity) {
        DetectorThresholds thresholds = context.detectorThresholds();
        double maxSpeed = thresholds.playerHighSpeed() * 0.95;
        double minPath = thresholds.sensitivityAdjusted(
                thresholds.positionChangeDistance() * PATH_DISTANCE_MULTIPLIER, sensitivity);
        double normalizedSensitivity = Double.isFinite(sensitivity)
                ? Math.max(0.0, Math.min(1.0, sensitivity)) : 0.5;
        long minDuration = Math.max(40L, Math.round(MIN_DURATION_TICKS * (1.15 - normalizedSensitivity * 0.3)));
        long maxGap = Math.max(thresholds.eventMergeGapTicks() * 2L, 20L);

        List<ReplayEvent> events = new ArrayList<>();
        for (TargetReference target : orderedTargets(window)) {
            if (!window.includes(target)) continue;
            List<MovementMetrics> metrics = window.metricsFor(target);
            if (metrics.size() < 3) continue;
            List<List<MovementMetrics>> segments = DetectorSupport.segments(metrics,
                    metric -> isExploringSample(window, target, metric, maxSpeed), maxGap);
            for (List<MovementMetrics> segment : segments) {
                long duration = segment.getLast().replayTime() - segment.getFirst().replayTime();
                if (duration < minDuration) continue;
                double pathDistance = pathDistance(segment);
                if (pathDistance < minPath) continue;
                double headingChange = totalHeadingChange(segment);
                double span = horizontalSpan(segment);
                boolean meandering = headingChange >= MIN_HEADING_CHANGE || span >= minPath * 0.6;
                if (!meandering && pathDistance < minPath * 1.5) continue;

                MovementMetrics peak = segment.stream()
                        .max(Comparator.comparingDouble(MovementMetrics::smoothedSpeed)
                                .thenComparingLong(metric -> -metric.replayTime()))
                        .orElseThrow();
                double magnitude = DetectorSupport.normalize(pathDistance, minPath);
                EventEvidence evidence = EventEvidence.of(EventEvidence.DetectionSource.DERIVED_MOVEMENT,
                        EventEvidence.Measurement.atLeast("path_distance", pathDistance, "blocks", minPath),
                        EventEvidence.Measurement.atLeast("duration", duration, "ticks", minDuration),
                        EventEvidence.Measurement.observed("peak_speed", peak.smoothedSpeed(), "blocks_per_second"),
                        EventEvidence.Measurement.observed("total_heading_change", headingChange, "degrees"),
                        EventEvidence.Measurement.observed("horizontal_span", span, "blocks"))
                        .withAttribute("activity_mode", "exploration");
                events.add(ReplayEvent.create(ReplayEventType.EXPLORATION, segment.getFirst().replayTime(),
                        peak.replayTime(), segment.getLast().replayTime(), Set.of(target), peak.position(),
                        magnitude, 0.78, evidence));
            }
        }
        events.sort(Comparator.comparingLong(ReplayEvent::startReplayTime).thenComparing(ReplayEvent::type));
        return List.copyOf(events);
    }

    private static boolean isExploringSample(ReplaySampleWindow window, TargetReference target,
                                             MovementMetrics metric, double maxSpeed) {
        if (metric.smoothedSpeed() < MIN_SPEED || metric.smoothedSpeed() > maxSpeed) return false;
        Optional<ReplayEntitySnapshot> snapshot = DetectorSupport.snapshot(window, target, metric.replayTime());
        if (snapshot.isEmpty()) return true;
        ReplayEntitySnapshot state = snapshot.get();
        if (state.inVehicle() || state.explicitFlight()) return false;
        return !state.creativeFlying() && !state.elytraFlying();
    }

    private static List<TargetReference> orderedTargets(ReplaySampleWindow window) {
        return window.movementMetrics().keySet().stream()
                .sorted(Comparator.comparing(target -> target.uuid().toString()))
                .toList();
    }

    private static double pathDistance(List<MovementMetrics> segment) {
        double distance = 0.0;
        for (int index = 1; index < segment.size(); index++) {
            distance += segment.get(index - 1).position().distanceTo(segment.get(index).position());
        }
        return distance;
    }

    private static double totalHeadingChange(List<MovementMetrics> segment) {
        double total = 0.0;
        for (MovementMetrics metric : segment) total += Math.abs(metric.headingChangeDegrees());
        return total;
    }

    private static double horizontalSpan(List<MovementMetrics> segment) {
        double minX = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;
        for (MovementMetrics metric : segment) {
            minX = Math.min(minX, metric.position().x());
            maxX = Math.max(maxX, metric.position().x());
            minZ = Math.min(minZ, metric.position().z());
            maxZ = Math.max(maxZ, metric.position().z());
        }
        double dx = maxX - minX;
        double dz = maxZ - minZ;
        return Math.sqrt(dx * dx + dz * dz);
    }
}
