package pl.peterwolf.cinewolf.camera;

import pl.peterwolf.cinewolf.model.BoundingBox;
import pl.peterwolf.cinewolf.model.TargetPose;
import pl.peterwolf.cinewolf.model.Vec3d;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Removes isolated target-position spikes from seek-sampled replay poses.
 * <p>
 * Flashback seek + client interpolation can occasionally yield a single sample that jumps several
 * blocks ahead of the true path and immediately returns. Follow/Chase then yank the camera after
 * that phantom pose. This pass rewrites only isolated outliers; sustained teleports and legitimate
 * high-speed flight remain.
 */
public final class TargetPoseSanitizer {
    private static final double MIN_SPIKE_BLOCKS = 1.25;
    private static final double MAX_SPIKE_BLOCKS = 256.0;
    private static final double CHORD_DOMINANCE = 1.75;
    private static final double OUT_AND_BACK_RATIO = 1.35;
    private static final double MAX_PLAUSIBLE_SPEED = 64.0;

    private TargetPoseSanitizer() {
    }

    public static Map<Long, TargetPose> sanitize(Map<Long, TargetPose> poses) {
        if (poses == null || poses.size() < 3) {
            return poses == null ? Map.of() : Map.copyOf(poses);
        }
        List<Long> ticks = new ArrayList<>(new TreeMap<>(poses).keySet());
        Map<Long, TargetPose> result = new LinkedHashMap<>(poses);
        boolean changed;
        // Two passes catch adjacent residual spikes after a neighbor is corrected.
        for (int pass = 0; pass < 2; pass++) {
            changed = false;
            for (int index = 1; index < ticks.size() - 1; index++) {
                long previousTick = ticks.get(index - 1);
                long currentTick = ticks.get(index);
                long nextTick = ticks.get(index + 1);
                TargetPose previous = result.get(previousTick);
                TargetPose current = result.get(currentTick);
                TargetPose next = result.get(nextTick);
                if (previous == null || current == null || next == null) continue;
                if (!sameDimension(previous, current, next)) continue;
                if (current.discontinuity() || previous.discontinuity() || next.discontinuity()) continue;

                if (isIsolatedSpike(previous, current, next, previousTick, currentTick, nextTick)) {
                    double amount = (currentTick - previousTick) / (double) (nextTick - previousTick);
                    amount = Math.max(0.0, Math.min(1.0, amount));
                    result.put(currentTick, lerpPose(previous, next, amount, false));
                    changed = true;
                }
            }
            if (!changed) break;
        }
        return Map.copyOf(result);
    }

    private static boolean isIsolatedSpike(TargetPose previous, TargetPose current, TargetPose next,
                                           long previousTick, long currentTick, long nextTick) {
        double chord = previous.position().distanceTo(next.position());
        double via = previous.position().distanceTo(current.position())
                + current.position().distanceTo(next.position());
        double residual = CameraMath.pointLineDistance(current.position(), previous.position(), next.position());
        if (residual < MIN_SPIKE_BLOCKS || residual > MAX_SPIKE_BLOCKS) return false;

        // Out-and-back fingerprint: path through current is much longer than direct chord.
        boolean outAndBack = chord > 1.0e-6 && via / Math.max(chord, 1.0e-6) >= OUT_AND_BACK_RATIO
                && residual >= MIN_SPIKE_BLOCKS;

        double prevResidualHint = residual;
        // Residual must dominate a linear path; for nearly collinear travel residual alone is enough if large.
        boolean residualSpike = residual >= Math.max(MIN_SPIKE_BLOCKS, chord * 0.35)
                && residual >= CHORD_DOMINANCE * Math.max(0.25, chord / Math.max(1.0, (nextTick - previousTick)));

        // Impossible instantaneous speed into or out of the sample.
        double inSpeed = speed(previous.position(), current.position(), currentTick - previousTick);
        double outSpeed = speed(current.position(), next.position(), nextTick - currentTick);
        boolean speedSpike = inSpeed > MAX_PLAUSIBLE_SPEED || outSpeed > MAX_PLAUSIBLE_SPEED;

        return outAndBack || (residualSpike && (speedSpike || residual >= 2.5));
    }

    private static double speed(Vec3d from, Vec3d to, long deltaTicks) {
        if (deltaTicks <= 0) return Double.POSITIVE_INFINITY;
        return from.distanceTo(to) * 20.0 / deltaTicks;
    }

    private static boolean sameDimension(TargetPose a, TargetPose b, TargetPose c) {
        return Objects.equals(a.dimension(), b.dimension()) && Objects.equals(b.dimension(), c.dimension());
    }

    private static TargetPose lerpPose(TargetPose left, TargetPose right, double amount, boolean discontinuity) {
        Vec3d position = left.position().lerp(right.position(), amount);
        Vec3d focus = left.focusPosition().lerp(right.focusPosition(), amount);
        double yaw = left.yaw() + (CameraMath.unwrapDegrees(left.yaw(), right.yaw()) - left.yaw()) * amount;
        double pitch = left.pitch() + (right.pitch() - left.pitch()) * amount;
        Vec3d velocity = right.position().subtract(left.position()); // caller re-estimates usually
        return new TargetPose(position, focus,
                new BoundingBox(left.boundingBox().min().lerp(right.boundingBox().min(), amount),
                        left.boundingBox().max().lerp(right.boundingBox().max(), amount)),
                yaw, pitch, velocity, left.entityType(), left.inVehicle() || right.inVehicle(),
                left.dimension(), discontinuity);
    }
}
