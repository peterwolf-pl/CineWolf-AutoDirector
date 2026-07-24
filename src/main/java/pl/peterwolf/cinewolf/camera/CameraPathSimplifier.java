package pl.peterwolf.cinewolf.camera;

import pl.peterwolf.cinewolf.model.CameraSample;
import pl.peterwolf.cinewolf.model.SamplingSettings;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

/**
 * Keyframe reduction that preserves cinematic motion.
 * RDP runs on both camera position and look-at path; samples are also kept when linear
 * interpolation would lose rotation, FOV, look-at, or sustained flight curvature.
 */
public final class CameraPathSimplifier {
    private static final double LOOK_AT_TOLERANCE_SCALE = 0.75;
    private static final double MIN_LOOK_AT_TOLERANCE = 0.04;
    private static final double ANGULAR_SPEED_KEEP_DEGREES_PER_SECOND = 35.0;

    public List<CameraSample> simplify(List<CameraSample> samples, SamplingSettings settings) {
        if (samples.size() <= 2) return List.copyOf(samples);

        BitSet keep = new BitSet(samples.size());
        keep.set(0);
        keep.set(samples.size() - 1);
        double positionTolerance = Math.max(0.02, settings.positionTolerance());
        double lookAtTolerance = Math.max(MIN_LOOK_AT_TOLERANCE,
                settings.positionTolerance() * LOOK_AT_TOLERANCE_SCALE);
        rdpPosition(samples, 0, samples.size() - 1, positionTolerance, keep);
        rdpLookAt(samples, 0, samples.size() - 1, lookAtTolerance, keep);

        for (int i = 1; i < samples.size() - 1; i++) {
            CameraSample previous = samples.get(i - 1);
            CameraSample current = samples.get(i);
            CameraSample next = samples.get(i + 1);
            double interval = next.cinematicTimeSeconds() - previous.cinematicTimeSeconds();
            double amount = interval <= 1.0e-9 ? 0.5
                    : (current.cinematicTimeSeconds() - previous.cinematicTimeSeconds()) / interval;
            amount = Math.max(0.0, Math.min(1.0, amount));
            double expectedYaw = previous.yaw() + (CameraMath.unwrapDegrees(previous.yaw(), next.yaw()) - previous.yaw())
                    * amount;
            double expectedPitch = previous.pitch() + (next.pitch() - previous.pitch()) * amount;
            double expectedFov = previous.fov() + (next.fov() - previous.fov()) * amount;
            double expectedLookAtError = CameraMath.pointLineDistance(current.lookAtPoint(),
                    previous.lookAtPoint(), next.lookAtPoint());

            double localDt = Math.max(1.0e-4,
                    Math.min(current.cinematicTimeSeconds() - previous.cinematicTimeSeconds(),
                            next.cinematicTimeSeconds() - current.cinematicTimeSeconds()));
            double yawSpeed = CameraMath.angleDifferenceDegrees(previous.yaw(), current.yaw()) / localDt;
            double pitchSpeed = Math.abs(current.pitch() - previous.pitch()) / localDt;
            boolean highAngularSpeed = yawSpeed > ANGULAR_SPEED_KEEP_DEGREES_PER_SECOND
                    || pitchSpeed > ANGULAR_SPEED_KEEP_DEGREES_PER_SECOND;

            if (CameraMath.angleDifferenceDegrees(expectedYaw, current.yaw()) > settings.rotationToleranceDegrees()
                    || Math.abs(expectedPitch - current.pitch()) > settings.rotationToleranceDegrees()
                    || Math.abs(current.fov() - expectedFov) > settings.fovTolerance()
                    || expectedLookAtError > lookAtTolerance
                    || highAngularSpeed
                    || current.discontinuity() || current.collisionConstrained()) {
                keep.set(i);
            }
        }

        // Keep curvature around collision-constrained anchors even when RDP skipped neighbors.
        for (int i = 1; i < samples.size() - 1; i++) {
            if (samples.get(i).collisionConstrained()) {
                keep.set(i - 1);
                keep.set(i);
                keep.set(i + 1);
            }
        }

        int lastKept = 0;
        for (int i = 1; i < samples.size(); i++) {
            if (keep.get(i)) {
                while (samples.get(i).cinematicTimeSeconds() - samples.get(lastKept).cinematicTimeSeconds()
                        > settings.maximumKeyframeIntervalSeconds()) {
                    double target = samples.get(lastKept).cinematicTimeSeconds()
                            + settings.maximumKeyframeIntervalSeconds();
                    int insert = lastKept + 1;
                    while (insert < i && samples.get(insert).cinematicTimeSeconds() < target) insert++;
                    keep.set(insert);
                    lastKept = insert;
                }
                lastKept = i;
            }
        }

        List<CameraSample> result = new ArrayList<>(keep.cardinality());
        for (int i = keep.nextSetBit(0); i >= 0; i = keep.nextSetBit(i + 1)) result.add(samples.get(i));
        return List.copyOf(result);
    }

    private static void rdpPosition(List<CameraSample> samples, int start, int end, double tolerance, BitSet keep) {
        if (end <= start + 1) return;
        double maximum = -1.0;
        int index = -1;
        for (int i = start + 1; i < end; i++) {
            double distance = CameraMath.pointLineDistance(samples.get(i).position(),
                    samples.get(start).position(), samples.get(end).position());
            if (distance > maximum) {
                maximum = distance;
                index = i;
            }
        }
        if (maximum > tolerance && index >= 0) {
            keep.set(index);
            rdpPosition(samples, start, index, tolerance, keep);
            rdpPosition(samples, index, end, tolerance, keep);
        }
    }

    private static void rdpLookAt(List<CameraSample> samples, int start, int end, double tolerance, BitSet keep) {
        if (end <= start + 1) return;
        double maximum = -1.0;
        int index = -1;
        for (int i = start + 1; i < end; i++) {
            double distance = CameraMath.pointLineDistance(samples.get(i).lookAtPoint(),
                    samples.get(start).lookAtPoint(), samples.get(end).lookAtPoint());
            if (distance > maximum) {
                maximum = distance;
                index = i;
            }
        }
        if (maximum > tolerance && index >= 0) {
            keep.set(index);
            rdpLookAt(samples, start, index, tolerance, keep);
            rdpLookAt(samples, index, end, tolerance, keep);
        }
    }
}
