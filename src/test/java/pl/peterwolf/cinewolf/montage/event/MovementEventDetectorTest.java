package pl.peterwolf.cinewolf.montage.event;

import org.junit.jupiter.api.Test;
import pl.peterwolf.cinewolf.model.Vec3d;
import pl.peterwolf.cinewolf.montage.analysis.AnalysisTestFixtures;
import pl.peterwolf.cinewolf.montage.analysis.DifferenceMethod;
import pl.peterwolf.cinewolf.montage.analysis.MovementMetrics;
import pl.peterwolf.cinewolf.montage.analysis.ReplayAnalysisContext;
import pl.peterwolf.cinewolf.montage.analysis.ReplaySample;
import pl.peterwolf.cinewolf.montage.event.detector.MovementEventDetector;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static pl.peterwolf.cinewolf.montage.analysis.AnalysisTestFixtures.PLAYER;
import static pl.peterwolf.cinewolf.montage.analysis.AnalysisTestFixtures.sample;
import static pl.peterwolf.cinewolf.montage.analysis.AnalysisTestFixtures.snapshot;

class MovementEventDetectorTest {
    @Test
    void detectsAllMovementEventTypesFromTypedMetrics() {
        List<MovementMetrics> metrics = List.of(
                metric(0, 0, 0, 0, 0, 0, 0, 0),
                metric(10, 5, 0, 0, 10, 6, 0, 0),
                metric(20, 10, 0, 0, 10, 0, 0, 90),
                metric(30, 12, 0, 0, 4, -6, 3, 0),
                metric(40, 13, 3, 0, 1, -6, 3, 0),
                metric(50, 14, 3, 0, 1, 0, -3, 0),
                metric(60, 15, 0, 0, 1, 0, -3, 0));
        List<ReplaySample> samples = metrics.stream()
                .map(metric -> sample(metric.replayTime(), snapshot(PLAYER, metric.position().x(),
                        metric.position().y(), metric.position().z()))).toList();
        ReplaySampleWindow window = new ReplaySampleWindow(samples, Map.of(PLAYER, metrics), Set.of(PLAYER));

        List<ReplayEvent> events = new MovementEventDetector().detect(window,
                ReplayAnalysisContext.defaults(samples), 1.0);
        Set<ReplayEventType> types = events.stream().map(ReplayEvent::type).collect(Collectors.toSet());

        assertTrue(types.containsAll(Set.of(ReplayEventType.POSITION_CHANGE, ReplayEventType.HIGH_SPEED,
                ReplayEventType.ACCELERATION, ReplayEventType.DECELERATION, ReplayEventType.SHARP_TURN,
                ReplayEventType.ALTITUDE_GAIN, ReplayEventType.ALTITUDE_LOSS)), () -> "Detected: " + types);
        ReplayEvent highSpeed = events.stream().filter(event -> event.type() == ReplayEventType.HIGH_SPEED)
                .findFirst().orElseThrow();
        assertEquals(10, highSpeed.peakReplayTime(), "equal peak speeds must choose the earliest timestamp");
        ReplayEvent turn = events.stream().filter(event -> event.type() == ReplayEventType.SHARP_TURN)
                .findFirst().orElseThrow();
        assertTrue(turn.evidence().attributes().stream()
                .anyMatch(attribute -> attribute.name().equals("turn_direction")));
        assertTrue(turn.evidence().measurements().stream()
                .anyMatch(measurement -> measurement.name().equals("maximum_angular_velocity")));
        events.forEach(event -> {
            assertFalse(event.evidence().measurements().isEmpty());
            assertTrue(event.confidence() > 0.0);
            assertEquals(event.eventId(), ReplayEvent.create(event.type(), event.startReplayTime(),
                    event.peakReplayTime(), event.endReplayTime(), event.targets(), event.location(),
                    event.magnitude(), event.confidence(), event.evidence()).eventId());
        });
    }

    private static MovementMetrics metric(long time, double x, double y, double z, double speed,
                                          double acceleration, double verticalSpeed, double headingChange) {
        Vec3d position = new Vec3d(x, y, z);
        Vec3d velocity = new Vec3d(speed, verticalSpeed, 0);
        return new MovementMetrics(PLAYER, time, position, Vec3d.ZERO, velocity, velocity, speed, speed,
                acceleration, verticalSpeed, 0.0, headingChange, headingChange * 2.0, y, 0.0, 0,
                DifferenceMethod.CENTRAL);
    }
}
