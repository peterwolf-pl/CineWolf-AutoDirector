package pl.peterwolf.cinewolf.camera;

import pl.peterwolf.cinewolf.model.CameraSample;
import pl.peterwolf.cinewolf.model.SamplingSettings;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

public final class CameraPathSimplifier {
    public List<CameraSample> simplify(List<CameraSample> samples, SamplingSettings settings) {
        if (samples.size() <= 2) return List.copyOf(samples);

        BitSet keep = new BitSet(samples.size());
        keep.set(0);
        keep.set(samples.size() - 1);
        rdp(samples, 0, samples.size() - 1, settings.positionTolerance(), keep);

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
            if (CameraMath.angleDifferenceDegrees(expectedYaw, current.yaw()) > settings.rotationToleranceDegrees()
                    || Math.abs(expectedPitch - current.pitch()) > settings.rotationToleranceDegrees()
                    || Math.abs(current.fov() - expectedFov) > settings.fovTolerance()
                    || current.discontinuity() || current.collisionConstrained()) {
                keep.set(i);
            }
        }

        int lastKept = 0;
        for (int i = 1; i < samples.size(); i++) {
            if (keep.get(i)) {
                while (samples.get(i).cinematicTimeSeconds() - samples.get(lastKept).cinematicTimeSeconds()
                        > settings.maximumKeyframeIntervalSeconds()) {
                    double target = samples.get(lastKept).cinematicTimeSeconds() + settings.maximumKeyframeIntervalSeconds();
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

    private static void rdp(List<CameraSample> samples, int start, int end, double tolerance, BitSet keep) {
        if (end <= start + 1) return;
        double maximum = -1.0;
        int index = -1;
        for (int i = start + 1; i < end; i++) {
            double distance = CameraMath.pointLineDistance(samples.get(i).position(), samples.get(start).position(), samples.get(end).position());
            if (distance > maximum) {
                maximum = distance;
                index = i;
            }
        }
        if (maximum > tolerance) {
            keep.set(index);
            rdp(samples, start, index, tolerance, keep);
            rdp(samples, index, end, tolerance, keep);
        }
    }
}
