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
import java.util.Set;
import java.util.function.Predicate;

public final class MovementEventDetector implements ReplayEventDetector {
    private static final Set<ReplayEventType> TYPES = Set.copyOf(EnumSet.of(
            ReplayEventType.POSITION_CHANGE, ReplayEventType.HIGH_SPEED, ReplayEventType.ACCELERATION,
            ReplayEventType.DECELERATION, ReplayEventType.SHARP_TURN, ReplayEventType.ALTITUDE_GAIN,
            ReplayEventType.ALTITUDE_LOSS));

    @Override
    public Set<ReplayEventType> supportedTypes() {
        return TYPES;
    }

    @Override
    public List<ReplayEvent> detect(ReplaySampleWindow window, ReplayAnalysisContext context, double sensitivity) {
        DetectorThresholds thresholds = context.detectorThresholds();
        List<ReplayEvent> events = new ArrayList<>();
        for (MapEntry entry : orderedMetrics(window)) {
            TargetReference target = entry.target();
            if (!window.includes(target) || entry.metrics().size() < 2) continue;
            List<MovementMetrics> metrics = entry.metrics();
            detectPosition(target, metrics, thresholds, sensitivity, events);
            detectHighSpeed(window, target, metrics, thresholds, sensitivity, events);
            detectAcceleration(target, metrics, thresholds, sensitivity, events);
            detectTurns(target, metrics, thresholds, sensitivity, events);
            detectAltitude(target, metrics, thresholds, sensitivity, events);
        }
        events.sort(Comparator.comparingLong(ReplayEvent::startReplayTime).thenComparing(ReplayEvent::type));
        return List.copyOf(events);
    }

    private static void detectPosition(TargetReference target, List<MovementMetrics> metrics,
                                       DetectorThresholds thresholds, double sensitivity, List<ReplayEvent> events) {
        double distanceThreshold = thresholds.sensitivityAdjusted(thresholds.positionChangeDistance(), sensitivity);
        List<List<MovementMetrics>> segments = DetectorSupport.segments(metrics,
                metric -> metric.smoothedSpeed() >= 0.2, maximumGap(metrics, thresholds));
        for (List<MovementMetrics> segment : segments) {
            double distance = pathDistance(segment);
            if (distance < distanceThreshold) continue;
            MovementMetrics peak = segment.stream().max(Comparator.comparingDouble(MovementMetrics::smoothedSpeed)
                    .thenComparingLong(metric -> -metric.replayTime())).orElseThrow();
            EventEvidence evidence = EventEvidence.of(EventEvidence.DetectionSource.DERIVED_MOVEMENT,
                    EventEvidence.Measurement.atLeast("path_distance", distance, "blocks", distanceThreshold),
                    EventEvidence.Measurement.observed("peak_speed", peak.smoothedSpeed(), "blocks_per_second"));
            events.add(ReplayEvent.create(ReplayEventType.POSITION_CHANGE, segment.getFirst().replayTime(),
                    peak.replayTime(), segment.getLast().replayTime(), Set.of(target), peak.position(),
                    DetectorSupport.normalize(distance, distanceThreshold), 0.82, evidence));
        }
    }

    private static void detectHighSpeed(ReplaySampleWindow window, TargetReference target, List<MovementMetrics> metrics,
                                        DetectorThresholds thresholds, double sensitivity, List<ReplayEvent> events) {
        double local = percentile(metrics.stream().map(MovementMetrics::smoothedSpeed).sorted().toList(), 0.75) * 0.9;
        for (List<MovementMetrics> segment : DetectorSupport.segments(metrics,
                metric -> metric.smoothedSpeed() >= contextualHighSpeedThreshold(window, target, metric, thresholds,
                        sensitivity, local), maximumGap(metrics, thresholds))) {
            MovementMetrics peak = segment.stream().max(Comparator.comparingDouble(MovementMetrics::smoothedSpeed))
                    .orElseThrow();
            double threshold = contextualHighSpeedThreshold(window, target, peak, thresholds, sensitivity, local);
            events.add(DetectorSupport.movementEvent(ReplayEventType.HIGH_SPEED, target, segment,
                    MovementMetrics::smoothedSpeed, threshold, "smoothed_speed", "blocks_per_second", 0.84,
                    EventEvidence.DetectionSource.DERIVED_MOVEMENT));
        }
    }

    private static void detectAcceleration(TargetReference target, List<MovementMetrics> metrics,
                                           DetectorThresholds thresholds, double sensitivity,
                                           List<ReplayEvent> events) {
        double threshold = thresholds.sensitivityAdjusted(thresholds.acceleration(), sensitivity);
        for (List<MovementMetrics> segment : DetectorSupport.segments(metrics,
                metric -> metric.acceleration() >= threshold, maximumGap(metrics, thresholds))) {
            events.add(DetectorSupport.movementEvent(ReplayEventType.ACCELERATION, target, segment,
                    MovementMetrics::acceleration, threshold, "acceleration", "blocks_per_second_squared", 0.8,
                    EventEvidence.DetectionSource.DERIVED_MOVEMENT));
        }
        for (List<MovementMetrics> segment : DetectorSupport.segments(metrics,
                metric -> metric.acceleration() <= -threshold, maximumGap(metrics, thresholds))) {
            events.add(DetectorSupport.movementEvent(ReplayEventType.DECELERATION, target, segment,
                    metric -> -metric.acceleration(), threshold, "deceleration", "blocks_per_second_squared", 0.8,
                    EventEvidence.DetectionSource.DERIVED_MOVEMENT));
        }
    }

    private static void detectTurns(TargetReference target, List<MovementMetrics> metrics,
                                    DetectorThresholds thresholds, double sensitivity, List<ReplayEvent> events) {
        double threshold = thresholds.sensitivityAdjusted(thresholds.turnDegrees(), sensitivity);
        Predicate<MovementMetrics> active = metric -> metric.smoothedSpeed() >= thresholds.turnMinimumSpeed()
                && Math.abs(metric.headingChangeDegrees()) >= threshold;
        for (List<MovementMetrics> segment : DetectorSupport.segments(metrics, active,
                maximumGap(metrics, thresholds))) {
            MovementMetrics peak = segment.stream().max(Comparator
                    .comparingDouble(metric -> Math.abs(metric.angularVelocityDegreesPerSecond())))
                    .orElseThrow();
            double totalHeadingChange = segment.stream().mapToDouble(MovementMetrics::headingChangeDegrees).sum();
            double peakHeadingChange = segment.stream().mapToDouble(metric -> Math.abs(metric.headingChangeDegrees()))
                    .max().orElse(0.0);
            double maximumAngularVelocity = segment.stream()
                    .mapToDouble(metric -> Math.abs(metric.angularVelocityDegreesPerSecond())).max().orElse(0.0);
            double averageSpeed = segment.stream().mapToDouble(MovementMetrics::smoothedSpeed)
                    .average().orElse(0.0);
            EventEvidence evidence = EventEvidence.of(EventEvidence.DetectionSource.DERIVED_MOVEMENT,
                            EventEvidence.Measurement.atLeast("peak_heading_change", peakHeadingChange,
                                    "degrees", threshold),
                            EventEvidence.Measurement.observed("total_heading_change", totalHeadingChange, "degrees"),
                            EventEvidence.Measurement.observed("maximum_angular_velocity", maximumAngularVelocity,
                                    "degrees_per_second"),
                            EventEvidence.Measurement.observed("average_turn_speed", averageSpeed,
                                    "blocks_per_second"),
                            EventEvidence.Measurement.observed("duration", segment.getLast().replayTime()
                                    - segment.getFirst().replayTime(), "ticks"))
                    .withAttribute("turn_direction", totalHeadingChange < 0.0 ? "clockwise" : "counterclockwise");
            events.add(ReplayEvent.create(ReplayEventType.SHARP_TURN, segment.getFirst().replayTime(),
                    peak.replayTime(), segment.getLast().replayTime(), Set.of(target), peak.position(),
                    DetectorSupport.normalize(Math.max(Math.abs(totalHeadingChange), peakHeadingChange), threshold),
                    0.82, evidence));
        }
    }

    private static void detectAltitude(TargetReference target, List<MovementMetrics> metrics,
                                       DetectorThresholds thresholds, double sensitivity, List<ReplayEvent> events) {
        double speedThreshold = thresholds.sensitivityAdjusted(thresholds.altitudeVerticalSpeed(), sensitivity);
        double changeThreshold = thresholds.sensitivityAdjusted(thresholds.altitudeMinimumChange(), sensitivity);
        altitudeDirection(target, metrics, thresholds, events, speedThreshold, changeThreshold, true);
        altitudeDirection(target, metrics, thresholds, events, speedThreshold, changeThreshold, false);
    }

    private static void altitudeDirection(TargetReference target, List<MovementMetrics> metrics,
                                          DetectorThresholds thresholds, List<ReplayEvent> events,
                                          double speedThreshold, double changeThreshold, boolean gain) {
        Predicate<MovementMetrics> active = gain ? metric -> metric.verticalSpeed() >= speedThreshold
                : metric -> metric.verticalSpeed() <= -speedThreshold;
        for (List<MovementMetrics> segment : DetectorSupport.segments(metrics, active,
                maximumGap(metrics, thresholds))) {
            double change = segment.getLast().altitude() - segment.getFirst().altitude();
            double directedChange = gain ? change : -change;
            if (directedChange < changeThreshold) continue;
            MovementMetrics peak = segment.stream().max(Comparator.comparingDouble(metric -> gain
                    ? metric.verticalSpeed() : -metric.verticalSpeed())).orElseThrow();
            ReplayEventType type = gain ? ReplayEventType.ALTITUDE_GAIN : ReplayEventType.ALTITUDE_LOSS;
            EventEvidence evidence = EventEvidence.of(EventEvidence.DetectionSource.DERIVED_MOVEMENT,
                    EventEvidence.Measurement.atLeast("altitude_change", directedChange, "blocks", changeThreshold),
                    EventEvidence.Measurement.atLeast("vertical_speed", Math.abs(peak.verticalSpeed()),
                            "blocks_per_second", speedThreshold));
            events.add(ReplayEvent.create(type, segment.getFirst().replayTime(), peak.replayTime(),
                    segment.getLast().replayTime(), Set.of(target), peak.position(),
                    DetectorSupport.normalize(directedChange, changeThreshold), 0.82, evidence));
        }
    }

    private static double contextualHighSpeedThreshold(ReplaySampleWindow window, TargetReference target,
                                                       MovementMetrics metric, DetectorThresholds thresholds,
                                                       double sensitivity, double localThreshold) {
        ReplayEntitySnapshot snapshot = DetectorSupport.snapshot(window, target, metric.replayTime()).orElse(null);
        String entityType = target.entityType().toLowerCase(java.util.Locale.ROOT);
        boolean vehicleEntity = entityType.contains("minecart") || entityType.contains("boat")
                || entityType.contains("horse") || entityType.contains("vehicle")
                || entityType.contains("plane") || entityType.contains("aircraft") || entityType.contains("train");
        boolean flightEntity = entityType.contains("elytra") || entityType.contains("projectile")
                || entityType.contains("falling_block");
        double contextual = ((snapshot != null && snapshot.explicitFlight()) || flightEntity)
                ? thresholds.flightHighSpeed()
                : ((snapshot != null && snapshot.inVehicle()) || vehicleEntity)
                ? thresholds.vehicleHighSpeed() : thresholds.playerHighSpeed();
        return Math.max(thresholds.sensitivityAdjusted(contextual, sensitivity), localThreshold);
    }

    private static double pathDistance(List<MovementMetrics> segment) {
        double distance = 0.0;
        for (int index = 1; index < segment.size(); index++) {
            distance += segment.get(index - 1).position().distanceTo(segment.get(index).position());
        }
        return distance;
    }

    private static double percentile(List<Double> sorted, double percentile) {
        if (sorted.isEmpty()) return 0.0;
        int index = (int) Math.floor(Math.max(0.0, Math.min(1.0, percentile)) * (sorted.size() - 1));
        return sorted.get(index);
    }

    private static long maximumGap(List<MovementMetrics> metrics, DetectorThresholds thresholds) {
        if (metrics.size() < 2) return thresholds.eventMergeGapTicks();
        List<Long> gaps = new ArrayList<>();
        for (int index = 1; index < metrics.size(); index++) gaps.add(metrics.get(index).replayTime() - metrics.get(index - 1).replayTime());
        gaps.sort(Long::compareTo);
        return Math.max(thresholds.eventMergeGapTicks(), gaps.get(gaps.size() / 2) * 2);
    }

    private static List<MapEntry> orderedMetrics(ReplaySampleWindow window) {
        return window.movementMetrics().entrySet().stream()
                .map(entry -> new MapEntry(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparing(entry -> entry.target().uuid().toString())).toList();
    }

    private record MapEntry(TargetReference target, List<MovementMetrics> metrics) {
    }
}
