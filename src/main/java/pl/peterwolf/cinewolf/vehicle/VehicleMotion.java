package pl.peterwolf.cinewolf.vehicle;

import pl.peterwolf.cinewolf.camera.CameraMath;
import pl.peterwolf.cinewolf.model.TargetPose;
import pl.peterwolf.cinewolf.model.TargetReference;
import pl.peterwolf.cinewolf.model.Vec3d;

import java.util.Locale;

/**
 * Shared travel-orientation helpers for vehicle-aware shots.
 *
 * <p>When the cinematic target is a <em>passenger</em>, free-look yaw/pitch of the player is almost
 * never the vehicle’s flight heading. Prefer actual travel velocity, then vehicle body yaw/pitch
 * (sampled from the mount), never raw player look for aircraft.</p>
 */
public final class VehicleMotion {
    private static final double VELOCITY_FORWARD_MIN = 0.25;
    private static final double STRONG_VELOCITY = 1.0;

    private VehicleMotion() {
    }

    /**
     * Unit forward for framing. 3D for aircraft / steep flight; mostly horizontal for ground craft.
     */
    public static Vec3d resolveForward(TargetReference target, TargetPose pose) {
        if (pose == null) return new Vec3d(0.0, 0.0, 1.0);
        boolean aircraft = isAircraftLike(target, pose);
        Vec3d velocity = pose.velocity();
        double speed = velocity == null ? 0.0 : velocity.length();

        if (speed >= VELOCITY_FORWARD_MIN && velocity.isFinite()) {
            if (aircraft || Math.abs(velocity.y()) > 0.35 || pose.inVehicle()) {
                // Full 3D travel — banked plane climbs/dives with the nose, not a flat yaw circle.
                return velocity.normalizeOr(bodyForward(pose, aircraft));
            }
            Vec3d horizontal = new Vec3d(velocity.x(), 0.0, velocity.z());
            if (horizontal.lengthSquared() > 1.0e-6) {
                return horizontal.normalizeOr(bodyForward(pose, false));
            }
        }
        return bodyForward(pose, aircraft);
    }

    /**
     * World-up biased by aircraft attitude so wing-level "up" follows pitch slightly.
     */
    public static Vec3d resolveUp(TargetReference target, TargetPose pose, Vec3d forward) {
        Vec3d bodyForward = forward == null || forward.lengthSquared() < 1.0e-8
                ? resolveForward(target, pose) : forward.normalizeOr(new Vec3d(0.0, 0.0, 1.0));
        if (!isAircraftLike(target, pose)) {
            return Vec3d.UP;
        }
        // Build an orthonormal frame from body forward: right = up × forward, up = forward × right.
        Vec3d right = Vec3d.UP.cross(bodyForward);
        if (right.lengthSquared() < 1.0e-6) {
            right = new Vec3d(1.0, 0.0, 0.0);
        } else {
            right = right.normalizeOr(new Vec3d(1.0, 0.0, 0.0));
        }
        return bodyForward.cross(right).normalizeOr(Vec3d.UP);
    }

    public static boolean isAircraftLike(TargetReference target, TargetPose pose) {
        String type = typeKey(target, pose);
        if (type.contains("plane") || type.contains("aircraft") || type.contains("helicopter")
                || type.contains("airship") || type.contains("biplane") || type.contains("glider")
                || type.contains("jet") || type.contains("elytra")) {
            return true;
        }
        // Passenger sampled with mount type still marked inVehicle — steep pitch ≈ flight body.
        if (pose != null && pose.inVehicle() && Math.abs(pose.pitch()) > 12.0) {
            return true;
        }
        if (pose != null && pose.velocity() != null) {
            double speed = pose.velocity().length();
            if (speed >= STRONG_VELOCITY && Math.abs(pose.velocity().y()) > 0.55) {
                return true;
            }
        }
        return false;
    }

    private static Vec3d bodyForward(TargetPose pose, boolean aircraft) {
        if (aircraft || (pose.inVehicle() && Math.abs(pose.pitch()) > 5.0)) {
            return CameraMath.directionFromYawPitch(pose.yaw(), pose.pitch());
        }
        return CameraMath.horizontalDirectionFromYaw(pose.yaw());
    }

    private static String typeKey(TargetReference target, TargetPose pose) {
        String targetType = target == null ? "" : target.entityType();
        String poseType = pose == null ? "" : pose.entityType();
        return (targetType + " " + poseType).toLowerCase(Locale.ROOT);
    }
}
