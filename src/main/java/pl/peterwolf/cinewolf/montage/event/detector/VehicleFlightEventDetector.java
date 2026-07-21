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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class VehicleFlightEventDetector implements ReplayEventDetector {
    private static final Set<ReplayEventType> TYPES = Set.copyOf(EnumSet.of(
            ReplayEventType.VEHICLE_ENTER, ReplayEventType.VEHICLE_EXIT, ReplayEventType.VEHICLE_MOVEMENT,
            ReplayEventType.FLIGHT_START, ReplayEventType.FLIGHT, ReplayEventType.LANDING));

    @Override
    public Set<ReplayEventType> supportedTypes() {
        return TYPES;
    }

    @Override
    public List<ReplayEvent> detect(ReplaySampleWindow window, ReplayAnalysisContext context, double sensitivity) {
        List<ReplayEvent> events = new ArrayList<>();
        DetectorThresholds thresholds = context.detectorThresholds();
        window.movementMetrics().entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(target -> target.uuid().toString())))
                .forEach(entry -> {
                    if (!window.includes(entry.getKey())) return;
                    List<FlightPoint> points = points(window, entry.getKey(), entry.getValue(), thresholds);
                    detectVehicle(entry.getKey(), points, thresholds, sensitivity, events);
                    detectFlight(entry.getKey(), points, thresholds, sensitivity, events);
                });
        events.sort(Comparator.comparingLong(ReplayEvent::startReplayTime).thenComparing(ReplayEvent::type));
        return List.copyOf(events);
    }

    private static void detectVehicle(TargetReference target, List<FlightPoint> points,
                                      DetectorThresholds thresholds, double sensitivity, List<ReplayEvent> events) {
        for (int index = 1; index < points.size(); index++) {
            FlightPoint before = points.get(index - 1);
            FlightPoint current = points.get(index);
            if (!before.snapshot().inVehicle() && current.snapshot().inVehicle()) {
                events.add(vehicleTransition(ReplayEventType.VEHICLE_ENTER, target, current));
            } else if (before.snapshot().inVehicle() && !current.snapshot().inVehicle()) {
                events.add(vehicleTransition(ReplayEventType.VEHICLE_EXIT, target, current));
            }
        }

        double movementThreshold = thresholds.sensitivityAdjusted(thresholds.positionChangeDistance(), sensitivity);
        List<MovementMetrics> metrics = points.stream().map(FlightPoint::metric).toList();
        for (List<MovementMetrics> segment : DetectorSupport.segments(metrics, metric -> {
            FlightPoint point = byTime(points, metric.replayTime());
            return point != null && point.snapshot().inVehicle() && metric.smoothedSpeed() >= 0.3;
        }, maximumGap(points, thresholds))) {
            double distance = pathDistance(segment);
            if (distance < movementThreshold) continue;
            MovementMetrics peak = segment.stream().max(Comparator.comparingDouble(MovementMetrics::smoothedSpeed))
                    .orElseThrow();
            FlightPoint peakPoint = byTime(points, peak.replayTime());
            EventEvidence evidence = EventEvidence.of(EventEvidence.DetectionSource.ENTITY_STATE,
                    EventEvidence.Measurement.atLeast("vehicle_path_distance", distance, "blocks", movementThreshold),
                    EventEvidence.Measurement.observed("vehicle_peak_speed", peak.smoothedSpeed(),
                            "blocks_per_second"));
            if (peakPoint != null && peakPoint.snapshot().vehicleType().isPresent())
                evidence = evidence.withAttribute("vehicle_type", peakPoint.snapshot().vehicleType().orElseThrow());
            events.add(ReplayEvent.create(ReplayEventType.VEHICLE_MOVEMENT, segment.getFirst().replayTime(),
                    peak.replayTime(), segment.getLast().replayTime(), Set.of(target), peak.position(),
                    DetectorSupport.normalize(distance, movementThreshold), 0.9, evidence));
        }
    }

    private static ReplayEvent vehicleTransition(ReplayEventType type, TargetReference target, FlightPoint point) {
        EventEvidence evidence = EventEvidence.of(EventEvidence.DetectionSource.ENTITY_STATE,
                EventEvidence.Measurement.observed("vehicle_state_transition", 1.0, "boolean"));
        if (type == ReplayEventType.VEHICLE_ENTER) {
            if (point.snapshot().vehicleUuid().isPresent())
                evidence = evidence.withAttribute("vehicle_uuid", point.snapshot().vehicleUuid().orElseThrow().toString());
            if (point.snapshot().vehicleType().isPresent())
                evidence = evidence.withAttribute("vehicle_type", point.snapshot().vehicleType().orElseThrow());
        }
        return ReplayEvent.create(type, point.metric().replayTime(), point.metric().replayTime(),
                point.metric().replayTime(), Set.of(target), point.metric().position(), 0.55, 0.98, evidence);
    }

    private static void detectFlight(TargetReference target, List<FlightPoint> points,
                                     DetectorThresholds thresholds, double sensitivity, List<ReplayEvent> events) {
        List<List<FlightPoint>> groups = flightGroups(points, thresholds);
        for (List<FlightPoint> group : groups) {
            long duration = group.getLast().metric().replayTime() - group.getFirst().metric().replayTime();
            boolean explicit = group.stream().anyMatch(point -> point.snapshot().explicitFlight());
            if (!explicit && duration < thresholds.flightMinimumDurationTicks()) continue;
            int startIndex = points.indexOf(group.getFirst());
            boolean observableStart = startIndex > 0 && !points.get(startIndex - 1).flightCandidate();
            double confidence = explicit ? 0.97 : 0.7;
            if (observableStart) {
                FlightPoint start = group.getFirst();
                EventEvidence startEvidence = flightEvidence(start, thresholds, explicit)
                        .withAttribute("flight_detection", explicit ? "explicit_state" : "sustained_airborne_motion");
                events.add(ReplayEvent.create(ReplayEventType.FLIGHT_START, start.metric().replayTime(),
                        start.metric().replayTime(), start.metric().replayTime(), Set.of(target), start.metric().position(),
                        explicit ? 0.85 : 0.6, confidence, startEvidence));
            }

            FlightPoint peak = group.stream().max(Comparator.comparingDouble(point -> point.metric().smoothedSpeed()))
                    .orElseThrow();
            double minimumSpeed = thresholds.sensitivityAdjusted(1.0, sensitivity);
            EventEvidence evidence = flightEvidence(peak, thresholds, explicit).merge(
                    EventEvidence.of(explicit ? EventEvidence.DetectionSource.ENTITY_STATE
                                    : EventEvidence.DetectionSource.DERIVED_MOVEMENT,
                            EventEvidence.Measurement.atLeast("flight_duration", duration, "ticks",
                                    explicit ? 0.0 : thresholds.flightMinimumDurationTicks()),
                            EventEvidence.Measurement.atLeast("flight_speed", peak.metric().smoothedSpeed(),
                                    "blocks_per_second", minimumSpeed)))
                    .withAttribute("flight_detection", explicit ? "explicit_state" : "sustained_airborne_motion");
            events.add(ReplayEvent.create(ReplayEventType.FLIGHT, group.getFirst().metric().replayTime(),
                    peak.metric().replayTime(), group.getLast().metric().replayTime(), Set.of(target),
                    peak.metric().position(), Math.max(0.5, DetectorSupport.normalize(peak.metric().smoothedSpeed(),
                            Math.max(1.0, minimumSpeed))), confidence, evidence));

            int endIndex = points.indexOf(group.getLast());
            if (endIndex + 1 < points.size()) {
                FlightPoint landing = points.get(endIndex + 1);
                boolean closeToGround = landing.snapshot().onGround()
                        || (Double.isFinite(landing.snapshot().groundProximity())
                        && landing.snapshot().groundProximity() <= thresholds.landingGroundProximity());
                if (closeToGround && !landing.snapshot().explicitFlight()) {
                    EventEvidence landingEvidence = EventEvidence.of(EventEvidence.DetectionSource.ENTITY_STATE,
                            EventEvidence.Measurement.atMost("ground_proximity",
                                    Double.isFinite(landing.snapshot().groundProximity())
                                            ? landing.snapshot().groundProximity() : 0.0,
                                    "blocks", thresholds.landingGroundProximity()),
                            EventEvidence.Measurement.observed("on_ground", landing.snapshot().onGround() ? 1.0 : 0.0,
                                    "boolean"));
                    events.add(ReplayEvent.create(ReplayEventType.LANDING, landing.metric().replayTime(),
                            landing.metric().replayTime(), landing.metric().replayTime(), Set.of(target),
                            landing.metric().position(), 0.8, explicit ? 0.95 : 0.75, landingEvidence));
                }
            }
        }
    }

    private static EventEvidence flightEvidence(FlightPoint point, DetectorThresholds thresholds, boolean explicit) {
        EventEvidence.DetectionSource source = explicit ? EventEvidence.DetectionSource.ENTITY_STATE
                : EventEvidence.DetectionSource.DERIVED_MOVEMENT;
        return EventEvidence.of(source,
                EventEvidence.Measurement.atLeast("ground_clearance",
                        Double.isFinite(point.snapshot().groundProximity()) ? point.snapshot().groundProximity() : 0.0,
                        "blocks", explicit ? 0.0 : thresholds.flightGroundClearance()),
                EventEvidence.Measurement.observed("creative_flying", point.snapshot().creativeFlying() ? 1.0 : 0.0,
                        "boolean"),
                EventEvidence.Measurement.observed("elytra_flying", point.snapshot().elytraFlying() ? 1.0 : 0.0,
                        "boolean"));
    }

    private static List<List<FlightPoint>> flightGroups(List<FlightPoint> points, DetectorThresholds thresholds) {
        List<List<FlightPoint>> result = new ArrayList<>();
        List<FlightPoint> current = new ArrayList<>();
        long maxGap = maximumGap(points, thresholds);
        for (FlightPoint point : points) {
            boolean contiguous = current.isEmpty()
                    || point.metric().replayTime() - current.getLast().metric().replayTime() <= maxGap;
            if (point.flightCandidate() && contiguous) current.add(point);
            else {
                if (!current.isEmpty()) result.add(List.copyOf(current));
                current.clear();
                if (point.flightCandidate()) current.add(point);
            }
        }
        if (!current.isEmpty()) result.add(List.copyOf(current));
        return List.copyOf(result);
    }

    private static List<FlightPoint> points(ReplaySampleWindow window, TargetReference target,
                                            List<MovementMetrics> metrics, DetectorThresholds thresholds) {
        Map<Long, ReplayEntitySnapshot> snapshots = new HashMap<>();
        window.samples().forEach(sample -> {
            ReplayEntitySnapshot snapshot = sample.entities().get(target);
            if (snapshot != null) snapshots.put(sample.replayTime(), snapshot);
        });
        List<FlightPoint> result = new ArrayList<>();
        for (MovementMetrics metric : metrics) {
            ReplayEntitySnapshot snapshot = snapshots.get(metric.replayTime());
            if (snapshot == null) continue;
            boolean inferredAirborne = !snapshot.onGround() && Double.isFinite(snapshot.groundProximity())
                    && snapshot.groundProximity() >= thresholds.flightGroundClearance()
                    && (metric.smoothedSpeed() >= 0.6 || Math.abs(metric.verticalSpeed()) >= 0.6);
            result.add(new FlightPoint(metric, snapshot, snapshot.explicitFlight() || inferredAirborne));
        }
        return List.copyOf(result);
    }

    private static FlightPoint byTime(List<FlightPoint> points, long time) {
        for (FlightPoint point : points) if (point.metric().replayTime() == time) return point;
        return null;
    }

    private static long maximumGap(List<FlightPoint> points, DetectorThresholds thresholds) {
        if (points.size() < 2) return thresholds.eventMergeGapTicks();
        List<Long> gaps = new ArrayList<>();
        for (int index = 1; index < points.size(); index++) {
            gaps.add(points.get(index).metric().replayTime() - points.get(index - 1).metric().replayTime());
        }
        gaps.sort(Long::compareTo);
        return Math.max(thresholds.eventMergeGapTicks(), gaps.get(gaps.size() / 2) * 2);
    }

    private static double pathDistance(List<MovementMetrics> segment) {
        double distance = 0.0;
        for (int index = 1; index < segment.size(); index++) {
            distance += segment.get(index - 1).position().distanceTo(segment.get(index).position());
        }
        return distance;
    }

    private record FlightPoint(MovementMetrics metric, ReplayEntitySnapshot snapshot, boolean flightCandidate) {
    }
}
