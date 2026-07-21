package pl.peterwolf.cinewolf.montage.analysis;

import pl.peterwolf.cinewolf.model.TargetReference;
import pl.peterwolf.cinewolf.model.Vec3d;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class MovementMetricsCalculator {
    public Map<TargetReference, List<MovementMetrics>> calculate(List<ReplaySample> samples,
                                                                  double stationarySpeedThreshold) {
        Map<TargetReference, List<Observation>> observations = collect(samples);
        LinkedHashMap<TargetReference, List<MovementMetrics>> result = new LinkedHashMap<>();
        observations.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(target -> target.uuid().toString())))
                .forEach(entry -> result.put(entry.getKey(), calculateTarget(entry.getKey(), entry.getValue(),
                        Math.max(0.0, stationarySpeedThreshold))));
        return Collections.unmodifiableMap(result);
    }

    private static Map<TargetReference, List<Observation>> collect(List<ReplaySample> samples) {
        LinkedHashMap<TargetReference, List<Observation>> result = new LinkedHashMap<>();
        samples.stream().sorted(Comparator.comparingLong(ReplaySample::replayTime)).forEach(sample ->
                sample.entities().forEach((target, snapshot) -> result.computeIfAbsent(target, ignored -> new ArrayList<>())
                        .add(new Observation(sample.replayTime(), snapshot))));
        result.values().forEach(values -> values.sort(Comparator.comparingLong(Observation::replayTime)));
        return result;
    }

    private static List<MovementMetrics> calculateTarget(TargetReference target, List<Observation> observations,
                                                          double stationarySpeedThreshold) {
        if (observations.isEmpty()) return List.of();
        List<Vec3d> rawVelocity = new ArrayList<>(observations.size());
        List<DifferenceMethod> methods = new ArrayList<>(observations.size());
        for (int index = 0; index < observations.size(); index++) {
            Derivative derivative = positionDerivative(observations, index);
            rawVelocity.add(derivative.value());
            methods.add(derivative.method());
        }

        List<Vec3d> smoothed = smooth(rawVelocity);
        List<Double> smoothedSpeeds = smoothed.stream().map(Vec3d::length).toList();
        List<Double> accelerations = new ArrayList<>(observations.size());
        for (int index = 0; index < observations.size(); index++) {
            accelerations.add(scalarDerivative(observations, smoothedSpeeds, index));
        }

        List<MovementMetrics> metrics = new ArrayList<>(observations.size());
        long stationaryDuration = 0L;
        double previousHeading = Double.NaN;
        for (int index = 0; index < observations.size(); index++) {
            Observation current = observations.get(index);
            Vec3d displacement = index == 0 ? Vec3d.ZERO
                    : current.snapshot().pose().position().subtract(observations.get(index - 1).snapshot().pose().position());
            double speed = rawVelocity.get(index).length();
            double smoothedSpeed = smoothedSpeeds.get(index);
            if (smoothedSpeed <= stationarySpeedThreshold) {
                if (index > 0) stationaryDuration += Math.max(0L, current.replayTime() - observations.get(index - 1).replayTime());
            } else {
                stationaryDuration = 0L;
            }

            double heading = heading(smoothed.get(index));
            double headingChange = Double.isFinite(heading) && Double.isFinite(previousHeading)
                    ? wrapDegrees(heading - previousHeading) : 0.0;
            double angularVelocity = 0.0;
            if (index > 0) {
                double seconds = ticksToSeconds(current.replayTime() - observations.get(index - 1).replayTime());
                if (seconds > 0.0) angularVelocity = headingChange / seconds;
            }
            if (Double.isFinite(heading)) previousHeading = heading;

            metrics.add(new MovementMetrics(target, current.replayTime(), current.snapshot().pose().position(),
                    displacement, rawVelocity.get(index), smoothed.get(index), speed, smoothedSpeed,
                    accelerations.get(index), smoothed.get(index).y(), heading, headingChange, angularVelocity,
                    current.snapshot().pose().position().y(), current.snapshot().groundProximity(), stationaryDuration,
                    methods.get(index)));
        }
        return List.copyOf(metrics);
    }

    private static Derivative positionDerivative(List<Observation> values, int index) {
        if (values.size() < 2) return new Derivative(Vec3d.ZERO, DifferenceMethod.UNAVAILABLE);
        if (index == 0) {
            return new Derivative(velocity(values.get(0), values.get(1)), DifferenceMethod.FORWARD);
        }
        if (index == values.size() - 1) {
            return new Derivative(velocity(values.get(index - 1), values.get(index)), DifferenceMethod.BACKWARD);
        }
        Observation previous = values.get(index - 1);
        Observation next = values.get(index + 1);
        double seconds = ticksToSeconds(next.replayTime() - previous.replayTime());
        Vec3d value = seconds <= 0.0 ? Vec3d.ZERO
                : next.snapshot().pose().position().subtract(previous.snapshot().pose().position()).multiply(1.0 / seconds);
        return new Derivative(value, DifferenceMethod.CENTRAL);
    }

    private static Vec3d velocity(Observation left, Observation right) {
        double seconds = ticksToSeconds(right.replayTime() - left.replayTime());
        return seconds <= 0.0 ? Vec3d.ZERO
                : right.snapshot().pose().position().subtract(left.snapshot().pose().position()).multiply(1.0 / seconds);
    }

    private static List<Vec3d> smooth(List<Vec3d> values) {
        if (values.size() < 2) return List.copyOf(values);
        List<Vec3d> result = new ArrayList<>(values.size());
        for (int index = 0; index < values.size(); index++) {
            Vec3d current = values.get(index);
            if (index == 0) result.add(current.multiply(0.75).add(values.get(1).multiply(0.25)));
            else if (index == values.size() - 1) {
                result.add(values.get(index - 1).multiply(0.25).add(current.multiply(0.75)));
            } else {
                result.add(values.get(index - 1).multiply(0.25).add(current.multiply(0.5))
                        .add(values.get(index + 1).multiply(0.25)));
            }
        }
        return List.copyOf(result);
    }

    private static double scalarDerivative(List<Observation> observations, List<Double> values, int index) {
        if (values.size() < 2) return 0.0;
        if (index == 0) return divide(values.get(1) - values.get(0),
                observations.get(1).replayTime() - observations.get(0).replayTime());
        if (index == values.size() - 1) return divide(values.get(index) - values.get(index - 1),
                observations.get(index).replayTime() - observations.get(index - 1).replayTime());
        return divide(values.get(index + 1) - values.get(index - 1),
                observations.get(index + 1).replayTime() - observations.get(index - 1).replayTime());
    }

    private static double divide(double delta, long ticks) {
        double seconds = ticksToSeconds(ticks);
        return seconds <= 0.0 ? 0.0 : delta / seconds;
    }

    private static double ticksToSeconds(long ticks) {
        return ticks / 20.0;
    }

    private static double heading(Vec3d velocity) {
        double horizontal = Math.hypot(velocity.x(), velocity.z());
        return horizontal < 1.0e-6 ? Double.NaN : Math.toDegrees(Math.atan2(velocity.z(), velocity.x()));
    }

    private static double wrapDegrees(double value) {
        double wrapped = value % 360.0;
        if (wrapped > 180.0) wrapped -= 360.0;
        if (wrapped < -180.0) wrapped += 360.0;
        return wrapped;
    }

    private record Observation(long replayTime, ReplayEntitySnapshot snapshot) {
    }

    private record Derivative(Vec3d value, DifferenceMethod method) {
    }
}
