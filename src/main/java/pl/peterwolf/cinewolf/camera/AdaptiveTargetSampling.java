package pl.peterwolf.cinewolf.camera;

import pl.peterwolf.cinewolf.model.TargetPose;
import pl.peterwolf.cinewolf.model.Vec3d;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/** Selects a bounded second sampling pass around motion that fixed-rate samples may underspecify. */
public final class AdaptiveTargetSampling {
    private static final double TURN_THRESHOLD_DEGREES = 12.0;
    private static final double ACCELERATION_THRESHOLD = 6.0;
    private static final double VERTICAL_ACCELERATION_THRESHOLD = 3.0;
    private static final double TELEPORT_DISTANCE = 16.0;

    public List<Long> selectAdditionalTicks(Map<Long, TargetPose> poses, List<Long> baseTicks, int maximumAdditional) {
        if (maximumAdditional <= 0 || baseTicks.size() < 2) return List.of();
        List<Long> ticks = baseTicks.stream().distinct().sorted().filter(poses::containsKey).toList();
        LinkedHashSet<Long> selected = new LinkedHashSet<>();

        for (int i = 1; i < ticks.size(); i++) {
            long previousTick = ticks.get(i - 1);
            long currentTick = ticks.get(i);
            TargetPose previous = poses.get(previousTick);
            TargetPose current = poses.get(currentTick);
            if (!previous.dimension().equals(current.dimension())
                    || previous.position().distanceTo(current.position()) > TELEPORT_DISTANCE
                    || previous.discontinuity() || current.discontinuity()) {
                addMidpoint(selected, previousTick, currentTick, maximumAdditional);
            }
        }

        for (int i = 1; i < ticks.size() - 1 && selected.size() < maximumAdditional; i++) {
            long firstTick = ticks.get(i - 1);
            long middleTick = ticks.get(i);
            long lastTick = ticks.get(i + 1);
            TargetPose first = poses.get(firstTick);
            TargetPose middle = poses.get(middleTick);
            TargetPose last = poses.get(lastTick);
            if (!first.dimension().equals(middle.dimension()) || !middle.dimension().equals(last.dimension())) continue;

            Vec3d incoming = velocity(first.position(), middle.position(), middleTick - firstTick);
            Vec3d outgoing = velocity(middle.position(), last.position(), lastTick - middleTick);
            double turn = horizontalAngleDegrees(incoming, outgoing);
            double acceleration = outgoing.subtract(incoming).length();
            double verticalAcceleration = Math.abs(outgoing.y() - incoming.y());
            if (turn >= TURN_THRESHOLD_DEGREES || acceleration >= ACCELERATION_THRESHOLD
                    || verticalAcceleration >= VERTICAL_ACCELERATION_THRESHOLD) {
                addMidpoint(selected, firstTick, middleTick, maximumAdditional);
                addMidpoint(selected, middleTick, lastTick, maximumAdditional);
            }
        }

        ArrayList<Long> result = new ArrayList<>(selected);
        result.removeAll(baseTicks);
        result.sort(Long::compareTo);
        if (result.size() > maximumAdditional) result.subList(maximumAdditional, result.size()).clear();
        return List.copyOf(result);
    }

    private static Vec3d velocity(Vec3d first, Vec3d second, long deltaTicks) {
        if (deltaTicks <= 0) return Vec3d.ZERO;
        return second.subtract(first).multiply(20.0 / deltaTicks);
    }

    private static double horizontalAngleDegrees(Vec3d first, Vec3d second) {
        Vec3d a = new Vec3d(first.x(), 0.0, first.z());
        Vec3d b = new Vec3d(second.x(), 0.0, second.z());
        if (a.lengthSquared() < 1.0e-8 || b.lengthSquared() < 1.0e-8) return 0.0;
        double cosine = a.dot(b) / Math.sqrt(a.lengthSquared() * b.lengthSquared());
        return Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, cosine))));
    }

    private static void addMidpoint(LinkedHashSet<Long> selected, long first, long second, int limit) {
        if (selected.size() >= limit || second - first <= 1) return;
        selected.add(first + (second - first) / 2L);
    }
}
