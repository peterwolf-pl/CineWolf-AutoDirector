package pl.peterwolf.cinewolf.camera;

import pl.peterwolf.cinewolf.model.CameraSample;
import pl.peterwolf.cinewolf.model.PathSmoothingSettings;
import pl.peterwolf.cinewolf.model.Vec3d;

import java.util.ArrayList;
import java.util.List;

/**
 * Zero-phase, pre-collision camera filter. It removes isolated out-and-back glitches and smooths each
 * continuous shot segment without changing its endpoints or crossing discontinuities.
 */
public final class CameraPathSmoother {
    private static final double TIME_EPSILON = 1.0e-9;
    private static final double REVERSAL_DOT_LIMIT = -0.25;
    private static final double MIN_DIRECTION_RESULTANT = 0.25;
    private static final double MIN_COMMON_MODE_JITTER = 0.01;
    private static final double MAX_COMMON_MODE_JITTER = 0.75;
    private static final double COMMON_MODE_RESIDUAL_MISMATCH = 0.25;
    private static final double ISOLATED_RESIDUAL_DOMINANCE = 1.5;
    private static final double OUTER_TREND_ALIGNMENT = 0.25;
    private static final double MAX_COMMON_MODE_SAMPLE_INTERVAL = 0.35;
    private final CameraLookAtSolver lookAtSolver = new CameraLookAtSolver();

    public List<CameraSample> smooth(List<CameraSample> samples, PathSmoothingSettings settings) {
        if (samples == null || samples.size() <= 2 || settings == null || !settings.enabled()) {
            return samples == null ? List.of() : List.copyOf(samples);
        }

        List<CameraSample> filtered = settings.outlierRejection()
                ? rejectIsolatedOutliers(samples, settings) : List.copyOf(samples);
        SegmentMap segments = SegmentMap.of(filtered);
        List<CameraSample> result = new ArrayList<>(filtered.size());
        double previousYaw = Double.NaN;

        for (int index = 0; index < filtered.size(); index++) {
            CameraSample original = filtered.get(index);
            if (segments.isAnchor(index) || !original.isFinite()) {
                result.add(original);
                previousYaw = original.yaw();
                continue;
            }

            SmoothedFrame frame = smoothFrame(filtered, segments, index, settings);
            CameraLookAtSolver.Orientation orientation = lookAtSolver.solve(
                    frame.position(), frame.lookAtPoint(), previousYaw);
            CameraSample smoothed = new CameraSample(original.cinematicTimeSeconds(), original.replayTime(),
                    frame.position(), orientation.quaternion(), orientation.yaw(), orientation.pitch(),
                    orientation.roll(), original.fov(), frame.lookAtPoint(),
                    original.discontinuity() || orientation.degenerate(), original.collisionConstrained());
            result.add(smoothed);
            previousYaw = smoothed.yaw();
        }
        return List.copyOf(result);
    }

    private List<CameraSample> rejectIsolatedOutliers(List<CameraSample> samples,
                                                       PathSmoothingSettings settings) {
        List<CameraSample> result = new ArrayList<>(samples);
        SegmentMap segments = SegmentMap.of(samples);
        double distanceThreshold = settings.outlierThresholdBlocks();
        double speedThreshold = settings.outlierSpeedThresholdBlocksPerSecond();

        for (int index = 1; index < samples.size() - 1; index++) {
            if (segments.isAnchor(index)) continue;
            CameraSample previous = samples.get(index - 1);
            CameraSample current = samples.get(index);
            CameraSample next = samples.get(index + 1);
            if (!previous.isFinite() || !current.isFinite() || !next.isFinite()) continue;

            CommonModeCorrection commonMode = rejectCommonModeJitter(samples, segments, index);
            Vec3d position = commonMode == null
                    ? rejectVectorOutlier(previous.position(), current.position(), next.position(),
                            previous.cinematicTimeSeconds(), current.cinematicTimeSeconds(), next.cinematicTimeSeconds(),
                            distanceThreshold, speedThreshold)
                    : commonMode.position();
            Vec3d lookAt = commonMode == null
                    ? rejectVectorOutlier(previous.lookAtPoint(), current.lookAtPoint(), next.lookAtPoint(),
                            previous.cinematicTimeSeconds(), current.cinematicTimeSeconds(), next.cinematicTimeSeconds(),
                            distanceThreshold, speedThreshold)
                    : commonMode.lookAtPoint();
            if (position == current.position() && lookAt == current.lookAtPoint()) continue;

            result.set(index, rebuild(current, position, lookAt,
                    index == 0 ? Double.NaN : result.get(index - 1).yaw()));
        }
        return List.copyOf(result);
    }

    /**
     * Removes a small one-sample translation shared by the camera and its aim point. This is the fingerprint of
     * a partially applied replay-entity interpolation step: the framing stays the same, but the whole view briefly
     * moves backwards and immediately catches up. The wider outer-trend check keeps real sustained reversals.
     */
    private static CommonModeCorrection rejectCommonModeJitter(List<CameraSample> samples, SegmentMap segments,
                                                                 int index) {
        if (index < segments.start(index) + 2 || index > segments.end(index) - 2) return null;
        CameraSample beforePrevious = samples.get(index - 2);
        CameraSample previous = samples.get(index - 1);
        CameraSample current = samples.get(index);
        CameraSample next = samples.get(index + 1);
        CameraSample afterNext = samples.get(index + 2);
        if (!beforePrevious.isFinite() || !afterNext.isFinite()) return null;

        double incomingTime = current.cinematicTimeSeconds() - previous.cinematicTimeSeconds();
        double outgoingTime = next.cinematicTimeSeconds() - current.cinematicTimeSeconds();
        double totalTime = next.cinematicTimeSeconds() - previous.cinematicTimeSeconds();
        if (incomingTime <= TIME_EPSILON || outgoingTime <= TIME_EPSILON || totalTime <= TIME_EPSILON
                || incomingTime > MAX_COMMON_MODE_SAMPLE_INTERVAL
                || outgoingTime > MAX_COMMON_MODE_SAMPLE_INTERVAL) return null;

        double amount = incomingTime / totalTime;
        Vec3d expectedPosition = previous.position().lerp(next.position(), amount);
        Vec3d expectedLookAt = previous.lookAtPoint().lerp(next.lookAtPoint(), amount);
        Vec3d positionResidual = current.position().subtract(expectedPosition);
        Vec3d lookAtResidual = current.lookAtPoint().subtract(expectedLookAt);
        double positionError = positionResidual.length();
        double lookAtError = lookAtResidual.length();
        if (!microJitterMagnitude(positionError) || !microJitterMagnitude(lookAtError)) return null;

        double largestError = Math.max(positionError, lookAtError);
        double neighboringError = Math.max(interpolationResidual(beforePrevious, previous, current),
                interpolationResidual(current, next, afterNext));
        if (largestError < neighboringError * ISOLATED_RESIDUAL_DOMINANCE) return null;
        if (positionResidual.subtract(lookAtResidual).length()
                > Math.max(MIN_COMMON_MODE_JITTER, largestError * COMMON_MODE_RESIDUAL_MISMATCH)) return null;
        if (positionResidual.normalizeOr(Vec3d.ZERO).dot(lookAtResidual.normalizeOr(Vec3d.ZERO)) < 0.9) return null;

        Vec3d incoming = current.lookAtPoint().subtract(previous.lookAtPoint());
        Vec3d outgoing = next.lookAtPoint().subtract(current.lookAtPoint());
        if (incoming.normalizeOr(Vec3d.ZERO).dot(outgoing.normalizeOr(Vec3d.ZERO)) >= REVERSAL_DOT_LIMIT) return null;

        Vec3d earlierTrend = previous.lookAtPoint().subtract(beforePrevious.lookAtPoint());
        Vec3d laterTrend = afterNext.lookAtPoint().subtract(next.lookAtPoint());
        if (!continuousOuterTrend(earlierTrend, laterTrend)) return null;
        return new CommonModeCorrection(expectedPosition, expectedLookAt);
    }

    private static double interpolationResidual(CameraSample left, CameraSample center, CameraSample right) {
        double interval = right.cinematicTimeSeconds() - left.cinematicTimeSeconds();
        if (interval <= TIME_EPSILON) return Double.POSITIVE_INFINITY;
        double amount = (center.cinematicTimeSeconds() - left.cinematicTimeSeconds()) / interval;
        return center.lookAtPoint().distanceTo(left.lookAtPoint().lerp(right.lookAtPoint(), amount));
    }

    private static boolean microJitterMagnitude(double value) {
        return value >= MIN_COMMON_MODE_JITTER && value <= MAX_COMMON_MODE_JITTER;
    }

    private static boolean continuousOuterTrend(Vec3d earlier, Vec3d later) {
        boolean earlierStationary = earlier.lengthSquared() <= TIME_EPSILON;
        boolean laterStationary = later.lengthSquared() <= TIME_EPSILON;
        if (earlierStationary || laterStationary) return earlierStationary && laterStationary;
        return earlier.normalizeOr(Vec3d.ZERO).dot(later.normalizeOr(Vec3d.ZERO)) >= OUTER_TREND_ALIGNMENT;
    }

    private SmoothedFrame smoothFrame(List<CameraSample> samples, SegmentMap segments, int index,
                                      PathSmoothingSettings settings) {
        CameraSample current = samples.get(index);
        double centerTime = current.cinematicTimeSeconds();
        double window = settings.windowSeconds();
        int start = segments.start(index);
        int end = segments.end(index);
        WeightedVec3Regression lookAtRegression = new WeightedVec3Regression();
        WeightedVec3Regression directionRegression = new WeightedVec3Regression();

        Vec3d currentOffset = current.position().subtract(current.lookAtPoint());
        Vec3d fallbackDirection = currentOffset.normalizeOr(new Vec3d(0.0, 0.0, 1.0));
        for (int neighbor = start; neighbor <= end; neighbor++) {
            CameraSample sample = samples.get(neighbor);
            double timeDistance = Math.abs(sample.cinematicTimeSeconds() - centerTime);
            if (timeDistance > window || !sample.isFinite()) continue;
            double weight = Math.max(0.0, 1.0 - timeDistance / window);
            if (weight <= 0.0) continue;
            Vec3d offset = sample.position().subtract(sample.lookAtPoint());
            Vec3d direction = offset.normalizeOr(fallbackDirection);
            double relativeTime = sample.cinematicTimeSeconds() - centerTime;
            lookAtRegression.add(relativeTime, sample.lookAtPoint(), weight);
            directionRegression.add(relativeTime, direction, weight);
        }
        if (lookAtRegression.totalWeight() <= TIME_EPSILON) {
            return new SmoothedFrame(current.position(), current.lookAtPoint());
        }

        Vec3d averageLookAt = lookAtRegression.valueAtCenter(current.lookAtPoint());
        Vec3d directionResultant = directionRegression.valueAtCenter(fallbackDirection);
        Vec3d averageDirection = directionResultant.length() < MIN_DIRECTION_RESULTANT
                ? fallbackDirection : directionResultant.normalizeOr(fallbackDirection);

        double positionStrength = settings.positionStrength();
        Vec3d positionBase = current.lookAtPoint().lerp(averageLookAt, positionStrength);
        Vec3d direction = fallbackDirection.lerp(averageDirection, positionStrength).normalizeOr(fallbackDirection);
        Vec3d position = positionBase.add(direction.multiply(currentOffset.length()));

        Vec3d lookAt = current.lookAtPoint().lerp(averageLookAt, settings.rotationStrength());
        return new SmoothedFrame(position, lookAt);
    }

    private CameraSample rebuild(CameraSample original, Vec3d position, Vec3d lookAt, double previousYaw) {
        CameraLookAtSolver.Orientation orientation = lookAtSolver.solve(position, lookAt, previousYaw);
        return new CameraSample(original.cinematicTimeSeconds(), original.replayTime(), position,
                orientation.quaternion(), orientation.yaw(), orientation.pitch(), orientation.roll(), original.fov(),
                lookAt, original.discontinuity() || orientation.degenerate(), original.collisionConstrained());
    }

    private static Vec3d rejectVectorOutlier(Vec3d previous, Vec3d current, Vec3d next,
                                             double previousTime, double currentTime, double nextTime,
                                             double distanceThreshold, double speedThreshold) {
        double incomingTime = currentTime - previousTime;
        double outgoingTime = nextTime - currentTime;
        double totalTime = nextTime - previousTime;
        if (incomingTime <= TIME_EPSILON || outgoingTime <= TIME_EPSILON || totalTime <= TIME_EPSILON) {
            return current;
        }

        Vec3d incoming = current.subtract(previous);
        Vec3d outgoing = next.subtract(current);
        double incomingDistance = incoming.length();
        double outgoingDistance = outgoing.length();
        if (incomingDistance / incomingTime < speedThreshold || outgoingDistance / outgoingTime < speedThreshold) {
            return current;
        }
        double reversal = incoming.normalizeOr(Vec3d.ZERO).dot(outgoing.normalizeOr(Vec3d.ZERO));
        if (reversal >= REVERSAL_DOT_LIMIT) return current;

        double amount = (currentTime - previousTime) / totalTime;
        Vec3d expected = previous.lerp(next, amount);
        return current.distanceTo(expected) >= distanceThreshold ? expected : current;
    }

    private record SmoothedFrame(Vec3d position, Vec3d lookAtPoint) {
    }

    private record CommonModeCorrection(Vec3d position, Vec3d lookAtPoint) {
    }

    /** Weighted local linear regression evaluated at the center time; unlike a sample average it preserves
     * constant-velocity motion when samples are irregularly spaced. */
    private static final class WeightedVec3Regression {
        private double weight;
        private double weightedTime;
        private double weightedTimeSquared;
        private Vec3d weightedValue = Vec3d.ZERO;
        private Vec3d weightedTimeValue = Vec3d.ZERO;

        void add(double relativeTime, Vec3d value, double sampleWeight) {
            weight += sampleWeight;
            weightedTime += sampleWeight * relativeTime;
            weightedTimeSquared += sampleWeight * relativeTime * relativeTime;
            weightedValue = weightedValue.add(value.multiply(sampleWeight));
            weightedTimeValue = weightedTimeValue.add(value.multiply(sampleWeight * relativeTime));
        }

        double totalWeight() {
            return weight;
        }

        Vec3d valueAtCenter(Vec3d fallback) {
            if (weight <= TIME_EPSILON) return fallback;
            double denominator = weight * weightedTimeSquared - weightedTime * weightedTime;
            if (Math.abs(denominator) <= TIME_EPSILON) return weightedValue.multiply(1.0 / weight);
            return weightedValue.multiply(weightedTimeSquared)
                    .subtract(weightedTimeValue.multiply(weightedTime))
                    .multiply(1.0 / denominator);
        }
    }

    private record SegmentMap(int[] starts, int[] ends) {
        static SegmentMap of(List<CameraSample> samples) {
            int size = samples.size();
            int[] starts = new int[size];
            int[] ends = new int[size];
            int segmentStart = 0;
            for (int index = 1; index <= size; index++) {
                boolean boundary = index == size || samples.get(index).discontinuity()
                        || samples.get(index - 1).collisionConstrained()
                        || (index < size && samples.get(index).collisionConstrained());
                if (!boundary) continue;
                int segmentEnd = index - 1;
                for (int cursor = segmentStart; cursor <= segmentEnd; cursor++) {
                    starts[cursor] = segmentStart;
                    ends[cursor] = segmentEnd;
                }
                segmentStart = index;
            }
            if (segmentStart < size) {
                for (int cursor = segmentStart; cursor < size; cursor++) {
                    starts[cursor] = segmentStart;
                    ends[cursor] = size - 1;
                }
            }
            return new SegmentMap(starts, ends);
        }

        int start(int index) {
            return starts[index];
        }

        int end(int index) {
            return ends[index];
        }

        boolean isAnchor(int index) {
            return index == starts[index] || index == ends[index];
        }
    }
}
