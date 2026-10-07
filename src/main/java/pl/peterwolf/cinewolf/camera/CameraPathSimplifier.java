package pl.peterwolf.cinewolf.camera;

import pl.peterwolf.cinewolf.model.CameraSample;
import pl.peterwolf.cinewolf.model.SamplingSettings;
import pl.peterwolf.cinewolf.model.Vec3d;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

/**
 * Builds sparse Flashback control points for a path that is already smoothed and rate-limited.
 *
 * <p>Playback between keys is {@code InterpolationType.SMOOTH} (centripetal Catmull-Rom), not a linear
 * chord and not a 12 Hz sample stream. A sample becomes a keyframe only when dropping it would make that
 * curve miss the intended camera by more than the cinematic tolerance.
 *
 * <p>When {@link SamplingSettings#cameraKeyframeIntervalSeconds()} is positive, that spacing wins:
 * one camera key is placed per interval, player steps between keys are not written, and Flashback's
 * smooth interpolant carries the motion. Discontinuities still force a hard cut.</p>
 */
public final class CameraPathSimplifier {
    /** Visual chord/spline error that still reads as smooth at typical framing distances. */
    private static final double VISUAL_TOLERANCE_DEGREES = 2.5;
    private static final double MIN_POSITION_TOLERANCE = 0.12;
    /** Wall slides stay on the curve; only a real avoidance bend forces a key. */
    private static final double COLLISION_POSITION_TOLERANCE = 0.12;

    public List<CameraSample> simplify(List<CameraSample> samples, SamplingSettings settings) {
        if (samples.size() <= 2) return List.copyOf(samples);

        double positionTolerance = Math.max(MIN_POSITION_TOLERANCE, settings.positionTolerance());
        double rotationTolerance = Math.max(0.05, settings.rotationToleranceDegrees());
        double fovTolerance = Math.max(0.05, settings.fovTolerance());

        BitSet keep = new BitSet(samples.size());
        keep.set(0);
        keep.set(samples.size() - 1);
        for (int index = 1; index < samples.size(); index++) {
            if (samples.get(index).discontinuity()) {
                keep.set(index - 1);
                keep.set(index);
            }
        }

        double spacing = settings.cameraKeyframeIntervalSeconds();
        if (spacing > 0.0) {
            // User spacing is the keyframe rate. Do not add a key for every step between marks.
            placeIntervalKeys(samples, keep, spacing);
        } else {
            boolean inserted = true;
            while (inserted) {
                inserted = false;
                int left = keep.nextSetBit(0);
                for (int right = keep.nextSetBit(left + 1); right >= 0; right = keep.nextSetBit(right + 1)) {
                    int worst = worstMiss(samples, keep, left, right, positionTolerance, rotationTolerance, fovTolerance);
                    if (worst >= 0) {
                        keep.set(worst);
                        inserted = true;
                    }
                    left = right;
                }
            }
            enforceInterval(samples, keep, settings.maximumKeyframeIntervalSeconds());
        }

        List<CameraSample> result = new ArrayList<>(keep.cardinality());
        for (int index = keep.nextSetBit(0); index >= 0; index = keep.nextSetBit(index + 1)) {
            result.add(samples.get(index));
        }
        return List.copyOf(result);
    }

    private static int worstMiss(List<CameraSample> samples, BitSet keep, int left, int right,
                                 double positionTolerance, double rotationTolerance, double fovTolerance) {
        if (right <= left + 1) return -1;
        int before = neighbourBefore(samples, keep, left);
        int after = neighbourAfter(samples, keep, right);
        CameraSample p0 = samples.get(before);
        CameraSample p1 = samples.get(left);
        CameraSample p2 = samples.get(right);
        CameraSample p3 = samples.get(after);
        int worst = -1;
        double worstError = 1.0;
        for (int index = left + 1; index < right; index++) {
            CameraSample sample = samples.get(index);
            double error = playbackError(p0, p1, p2, p3, sample, positionTolerance, rotationTolerance, fovTolerance);
            if (error > worstError) {
                worstError = error;
                worst = index;
            }
        }
        return worst;
    }

    private static double playbackError(CameraSample before, CameraSample start, CameraSample end, CameraSample after,
                                        CameraSample sample, double positionTolerance, double rotationTolerance,
                                        double fovTolerance) {
        double time = sample.cinematicTimeSeconds();
        Vec3d playedPosition = SmoothKeyframeCurve.position(before.position(), start.position(), end.position(),
                after.position(), before.cinematicTimeSeconds(), start.cinematicTimeSeconds(),
                end.cinematicTimeSeconds(), after.cinematicTimeSeconds(), time);
        double playedYaw = SmoothKeyframeCurve.degrees(before.yaw(), start.yaw(), end.yaw(), after.yaw(),
                before.cinematicTimeSeconds(), start.cinematicTimeSeconds(), end.cinematicTimeSeconds(),
                after.cinematicTimeSeconds(), time);
        double playedPitch = SmoothKeyframeCurve.degrees(before.pitch(), start.pitch(), end.pitch(), after.pitch(),
                before.cinematicTimeSeconds(), start.cinematicTimeSeconds(), end.cinematicTimeSeconds(),
                after.cinematicTimeSeconds(), time);
        double playedRoll = SmoothKeyframeCurve.degrees(before.roll(), start.roll(), end.roll(), after.roll(),
                before.cinematicTimeSeconds(), start.cinematicTimeSeconds(), end.cinematicTimeSeconds(),
                after.cinematicTimeSeconds(), time);
        double playedFov = SmoothKeyframeCurve.value(before.fov(), start.fov(), end.fov(), after.fov(),
                before.cinematicTimeSeconds(), start.cinematicTimeSeconds(), end.cinematicTimeSeconds(),
                after.cinematicTimeSeconds(), time);

        double allowedPosition = positionAllowance(sample, positionTolerance);
        double positionError = playedPosition.distanceTo(sample.position()) / allowedPosition;
        double yawError = CameraMath.angleDifferenceDegrees(playedYaw, sample.yaw()) / rotationTolerance;
        double pitchError = Math.abs(playedPitch - sample.pitch()) / rotationTolerance;
        double rollError = CameraMath.angleDifferenceDegrees(playedRoll, sample.roll()) / rotationTolerance;
        double fovError = Math.abs(playedFov - sample.fov()) / fovTolerance;
        return Math.max(positionError, Math.max(yawError, Math.max(pitchError, Math.max(rollError, fovError))));
    }

    /**
     * Far shots can tolerate a larger block error for the same visual drift. Close and collision-constrained
     * samples stay tighter so a wall dodge is not smoothed back into the obstacle.
     */
    private static double positionAllowance(CameraSample sample, double configured) {
        double focus = sample.position().distanceTo(sample.lookAtPoint());
        double visual = Double.isFinite(focus)
                ? focus * Math.tan(Math.toRadians(VISUAL_TOLERANCE_DEGREES))
                : configured;
        double allowed = Math.min(configured, Math.max(MIN_POSITION_TOLERANCE, visual));
        if (sample.collisionConstrained()) {
            allowed = Math.min(allowed, COLLISION_POSITION_TOLERANCE);
        }
        return Math.max(0.04, allowed);
    }

    private static int neighbourBefore(List<CameraSample> samples, BitSet keep, int left) {
        if (samples.get(left).discontinuity()) return left;
        int previous = keep.previousSetBit(left - 1);
        if (previous < 0 || samples.get(previous).discontinuity()) return left;
        return previous;
    }

    private static int neighbourAfter(List<CameraSample> samples, BitSet keep, int right) {
        int next = keep.nextSetBit(right + 1);
        if (next < 0 || samples.get(next).discontinuity()) return right;
        return next;
    }

    /** Keeps the sample nearest each spacing mark. Cuts already in {@code keep} stay. */
    private static void placeIntervalKeys(List<CameraSample> samples, BitSet keep, double intervalSeconds) {
        double start = samples.getFirst().cinematicTimeSeconds();
        double end = samples.getLast().cinematicTimeSeconds();
        double target = start + intervalSeconds;
        int cursor = 1;
        while (target < end - 1.0e-4) {
            while (cursor < samples.size() - 1
                    && samples.get(cursor).cinematicTimeSeconds() < target) {
                cursor++;
            }
            int candidate = cursor;
            if (cursor > 1) {
                double before = Math.abs(samples.get(cursor - 1).cinematicTimeSeconds() - target);
                double at = Math.abs(samples.get(cursor).cinematicTimeSeconds() - target);
                if (before <= at) candidate = cursor - 1;
            }
            if (candidate > 0 && candidate < samples.size() - 1) keep.set(candidate);
            target += intervalSeconds;
            if (cursor >= samples.size() - 1) break;
        }
    }

    private static void enforceInterval(List<CameraSample> samples, BitSet keep, double maximumIntervalSeconds) {
        if (!Double.isFinite(maximumIntervalSeconds) || maximumIntervalSeconds <= 0.0) return;
        int lastKept = 0;
        for (int index = 1; index < samples.size(); index++) {
            if (!keep.get(index)) continue;
            while (samples.get(index).cinematicTimeSeconds() - samples.get(lastKept).cinematicTimeSeconds()
                    > maximumIntervalSeconds) {
                double target = samples.get(lastKept).cinematicTimeSeconds() + maximumIntervalSeconds;
                int insert = lastKept + 1;
                while (insert < index && samples.get(insert).cinematicTimeSeconds() < target) insert++;
                if (insert >= index) break;
                keep.set(insert);
                lastKept = insert;
            }
            lastKept = index;
        }
    }
}
