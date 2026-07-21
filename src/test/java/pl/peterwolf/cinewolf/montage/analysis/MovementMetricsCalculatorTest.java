package pl.peterwolf.cinewolf.montage.analysis;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static pl.peterwolf.cinewolf.montage.analysis.AnalysisTestFixtures.PLAYER;
import static pl.peterwolf.cinewolf.montage.analysis.AnalysisTestFixtures.sample;
import static pl.peterwolf.cinewolf.montage.analysis.AnalysisTestFixtures.snapshot;

class MovementMetricsCalculatorTest {
    private final MovementMetricsCalculator calculator = new MovementMetricsCalculator();

    @Test
    void usesForwardCentralAndBackwardDifferencesAtBoundaries() {
        List<ReplaySample> samples = List.of(sample(0, snapshot(PLAYER, 0, 0, 0)),
                sample(10, snapshot(PLAYER, 1, 0, 0)), sample(20, snapshot(PLAYER, 2, 0, 0)));

        List<MovementMetrics> metrics = calculator.calculate(samples, 0.15).get(PLAYER);

        assertEquals(DifferenceMethod.FORWARD, metrics.get(0).differenceMethod());
        assertEquals(DifferenceMethod.CENTRAL, metrics.get(1).differenceMethod());
        assertEquals(DifferenceMethod.BACKWARD, metrics.get(2).differenceMethod());
        metrics.forEach(metric -> assertEquals(2.0, metric.velocity().x(), 1.0e-9));
        metrics.forEach(metric -> assertEquals(0.0, metric.acceleration(), 1.0e-9));
    }

    @Test
    void calculatesVerticalSpeedHeadingAndAccelerationFromSmoothedVelocity() {
        List<ReplaySample> samples = List.of(sample(0, snapshot(PLAYER, 0, 0, 0)),
                sample(10, snapshot(PLAYER, 1, 0.5, 0)), sample(20, snapshot(PLAYER, 3, 2, 0)),
                sample(30, snapshot(PLAYER, 6, 4.5, 0)));

        List<MovementMetrics> metrics = calculator.calculate(samples, 0.15).get(PLAYER);

        assertTrue(metrics.stream().anyMatch(metric -> metric.acceleration() > 0.5));
        assertTrue(metrics.stream().anyMatch(metric -> metric.verticalSpeed() > 1.0));
        assertTrue(metrics.stream().allMatch(metric -> Double.isFinite(metric.smoothedSpeed())));
        assertEquals(0.0, metrics.get(1).headingDegrees(), 1.0e-9);
    }

    @Test
    void accumulatesAndResetsStationaryDuration() {
        List<ReplaySample> samples = List.of(sample(0, snapshot(PLAYER, 0, 0, 0)),
                sample(10, snapshot(PLAYER, 0, 0, 0)), sample(20, snapshot(PLAYER, 0, 0, 0)),
                sample(30, snapshot(PLAYER, 0, 0, 0)), sample(40, snapshot(PLAYER, 4, 0, 0)));

        List<MovementMetrics> metrics = calculator.calculate(samples, 0.15).get(PLAYER);

        assertTrue(metrics.stream().mapToLong(MovementMetrics::stationaryDurationTicks).max().orElse(0) >= 10);
        assertEquals(0, metrics.getLast().stationaryDurationTicks());
    }

    @Test
    void singleSampleProducesFiniteUnavailableMetrics() {
        MovementMetrics metric = calculator.calculate(List.of(sample(5, snapshot(PLAYER, 1, 2, 3))), 0.15)
                .get(PLAYER).getFirst();

        assertEquals(DifferenceMethod.UNAVAILABLE, metric.differenceMethod());
        assertEquals(0.0, metric.speed(), 1.0e-9);
        assertEquals(2.0, metric.altitude(), 1.0e-9);
    }
}
