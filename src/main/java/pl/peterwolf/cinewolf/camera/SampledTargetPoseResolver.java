package pl.peterwolf.cinewolf.camera;

import pl.peterwolf.cinewolf.api.TargetPoseResolver;
import pl.peterwolf.cinewolf.model.BoundingBox;
import pl.peterwolf.cinewolf.model.TargetPose;
import pl.peterwolf.cinewolf.model.TargetReference;
import pl.peterwolf.cinewolf.model.Vec3d;

import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.TreeMap;

public final class SampledTargetPoseResolver implements TargetPoseResolver {
    private static final double TELEPORT_DISTANCE = 16.0;
    private static final double MAX_VELOCITY = 64.0;
    private final NavigableMap<Long, TargetPose> poses;

    public SampledTargetPoseResolver(Map<Long, TargetPose> poses) {
        this.poses = new TreeMap<>(TargetPoseSanitizer.sanitize(poses));
    }

    @Override
    public Optional<TargetPose> resolve(TargetReference target, long replayTime) {
        TargetPose exact = poses.get(replayTime);
        if (exact != null) return Optional.of(withEstimatedVelocity(replayTime, exact));

        Map.Entry<Long, TargetPose> floor = poses.floorEntry(replayTime);
        Map.Entry<Long, TargetPose> ceil = poses.ceilingEntry(replayTime);
        if (floor == null || ceil == null || floor.getKey().equals(ceil.getKey())) return Optional.empty();
        TargetPose left = floor.getValue();
        TargetPose right = ceil.getValue();
        if (!left.dimension().equals(right.dimension())) return Optional.empty();

        double span = right.position().distanceTo(left.position());
        boolean discontinuity = left.discontinuity() || right.discontinuity() || span > TELEPORT_DISTANCE;
        // Do not lerp through teleports — hold the nearer side so chase/follow do not invent a far phantom.
        if (discontinuity && span > TELEPORT_DISTANCE) {
            long mid = (floor.getKey() + ceil.getKey()) / 2L;
            TargetPose held = replayTime <= mid ? left : right;
            return Optional.of(withEstimatedVelocity(replayTime, held, true));
        }

        double amount = (replayTime - floor.getKey()) / (double) (ceil.getKey() - floor.getKey());
        Vec3d position = left.position().lerp(right.position(), amount);
        Vec3d focus = left.focusPosition().lerp(right.focusPosition(), amount);
        Vec3d velocity = clampVelocity(right.position().subtract(left.position())
                .multiply(20.0 / (ceil.getKey() - floor.getKey())));
        return Optional.of(new TargetPose(
                position,
                focus,
                new BoundingBox(left.boundingBox().min().lerp(right.boundingBox().min(), amount),
                        left.boundingBox().max().lerp(right.boundingBox().max(), amount)),
                interpolateAngle(left.yaw(), right.yaw(), amount),
                left.pitch() + (right.pitch() - left.pitch()) * amount,
                velocity,
                left.entityType(),
                left.inVehicle() || right.inVehicle(),
                left.dimension(),
                discontinuity
        ));
    }

    private TargetPose withEstimatedVelocity(long replayTime, TargetPose pose) {
        return withEstimatedVelocity(replayTime, pose, pose.discontinuity());
    }

    private TargetPose withEstimatedVelocity(long replayTime, TargetPose pose, boolean discontinuity) {
        Map.Entry<Long, TargetPose> before = poses.lowerEntry(replayTime);
        Map.Entry<Long, TargetPose> after = poses.higherEntry(replayTime);
        boolean teleported = discontinuity
                || isTeleport(before, pose)
                || isTeleport(pose, after);
        Vec3d velocity = pose.velocity();
        if ((!teleported || velocity.lengthSquared() <= 1.0e-8) && before != null && after != null
                && before.getValue().dimension().equals(after.getValue().dimension())
                && !isTeleport(before, after.getValue())) {
            velocity = clampVelocity(after.getValue().position().subtract(before.getValue().position())
                    .multiply(20.0 / (after.getKey() - before.getKey())));
        } else if (velocity.lengthSquared() > 1.0e-8) {
            velocity = clampVelocity(velocity);
        } else {
            velocity = Vec3d.ZERO;
        }
        if (velocity.equals(pose.velocity()) && teleported == pose.discontinuity()) return pose;
        return new TargetPose(pose.position(), pose.focusPosition(), pose.boundingBox(), pose.yaw(), pose.pitch(),
                velocity, pose.entityType(), pose.inVehicle(), pose.dimension(), teleported);
    }

    private static boolean isTeleport(Map.Entry<Long, TargetPose> other, TargetPose pose) {
        return other != null && other.getValue().dimension().equals(pose.dimension())
                && other.getValue().position().distanceTo(pose.position()) > TELEPORT_DISTANCE;
    }

    private static boolean isTeleport(TargetPose pose, Map.Entry<Long, TargetPose> other) {
        return other != null && pose.dimension().equals(other.getValue().dimension())
                && pose.position().distanceTo(other.getValue().position()) > TELEPORT_DISTANCE;
    }

    private static Vec3d clampVelocity(Vec3d velocity) {
        double speed = velocity.length();
        if (!Double.isFinite(speed) || speed <= MAX_VELOCITY) return velocity;
        return velocity.normalizeOr(Vec3d.ZERO).multiply(MAX_VELOCITY);
    }

    private static double interpolateAngle(double first, double second, double amount) {
        return first + (CameraMath.unwrapDegrees(first, second) - first) * amount;
    }
}
