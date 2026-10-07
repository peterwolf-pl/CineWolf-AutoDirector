package pl.peterwolf.cinewolf.shot;

import pl.peterwolf.cinewolf.api.ShotGenerator;
import pl.peterwolf.cinewolf.camera.CameraMath;
import pl.peterwolf.cinewolf.camera.CameraSmoothing;
import pl.peterwolf.cinewolf.model.CameraPathPlan;
import pl.peterwolf.cinewolf.model.CameraSample;
import pl.peterwolf.cinewolf.model.PathWarning;
import pl.peterwolf.cinewolf.model.ReplayContext;
import pl.peterwolf.cinewolf.model.ShotRequest;
import pl.peterwolf.cinewolf.model.ShotValidationResult;
import pl.peterwolf.cinewolf.model.TargetPose;
import pl.peterwolf.cinewolf.model.Vec3d;
import pl.peterwolf.cinewolf.vehicle.VehicleMotion;

import java.util.ArrayList;
import java.util.List;

public final class FollowShotGenerator extends AbstractShotGenerator implements ShotGenerator {
    private static final double MAX_DIRECTION_TURN_DEGREES_PER_SECOND = 95.0;
    private static final double HIGH_SPEED_BLOCKS_PER_SECOND = 8.0;
    /** Soft lock on framing radius (blocks/s). Prevents free-space lag from breathing the zoom. */
    private static final double MAX_RADIUS_CHANGE_BLOCKS_PER_SECOND = 2.5;
    private static final double RADIUS_LOCK_RESPONSIVENESS = 5.5;

    @Override
    public CameraPathPlan generate(ShotRequest request, ReplayContext context) {
        ShotValidationResult validation = validate(request, context);
        List<PathWarning> warnings = new ArrayList<>(validation.messages());
        if (!validation.isValid()) return finish(request, context, List.of(), warnings);

        List<Long> replayTicks = sampleReplayTicks(request, context);
        double defaultDelta = request.durationSeconds() / Math.max(1, replayTicks.size() - 1);
        List<CameraSample> samples = new ArrayList<>(replayTicks.size());
        Vec3d direction = new Vec3d(0.0, 0.0, 1.0);
        Vec3d smoothedCamera = null;
        double previousYaw = Double.NaN;
        double previousPitch = Double.NaN;
        for (int i = 0; i < replayTicks.size(); i++) {
            long replayTime = replayTicks.get(i);
            double delta = i == 0 ? defaultDelta
                    : cinematicTimeAtTick(request, replayTime) - cinematicTimeAtTick(request, replayTicks.get(i - 1));
            delta = Math.max(1.0e-4, delta);
            TargetPose target = requiredPose(request, context, replayTime);
            Vec3d measured = travelDirection(target);
            double speed = target.velocity().length();
            double responsiveness = speed > HIGH_SPEED_BLOCKS_PER_SECOND ? 3.2 : 5.0;
            double maxTurn = speed > HIGH_SPEED_BLOCKS_PER_SECOND ? 70.0 : MAX_DIRECTION_TURN_DEGREES_PER_SECOND;
            direction = CameraSmoothing.smoothDirectionRateLimited(direction, measured, responsiveness, delta, maxTurn);
            Vec3d desired = target.position().subtract(direction.multiply(request.distance()))
                    .add(Vec3d.UP.multiply(request.height()));
            // Cap camera catch-up so high-speed flight does not produce one-sample teleports.
            double maxStep = Math.max(request.cameraSpeed() * 1.5, speed + 6.0) * delta;
            if (smoothedCamera == null) {
                smoothedCamera = desired;
            } else {
                Vec3d smoothed = CameraSmoothing.exponential(smoothedCamera, desired,
                        Math.max(0.8, request.cameraSpeed()), delta);
                smoothedCamera = CameraSmoothing.clampStep(smoothedCamera, smoothed, maxStep);
                // Reproject onto the cinematic framing sphere around the subject so lag only rotates
                // the rig instead of continuously zooming the player in and out.
                smoothedCamera = lockFocusDistance(smoothedCamera, desired, target.focusPosition(),
                        Math.max(1.0, request.distance()), delta);
            }
            CameraSample sample = sample(request, context, cinematicTimeAtTick(request, replayTime), replayTime,
                    smoothedCamera, target, previousYaw, previousPitch, delta);
            samples.add(sample);
            previousYaw = sample.yaw();
            previousPitch = sample.pitch();
        }
        return finish(request, context, samples, warnings);
    }

    /**
     * Softly keeps {@code camera} at the intended framing radius from {@code focus}. Direction of the
     * offset is preserved (angular lag stays); only distance is pulled back toward the desired sphere.
     */
    static Vec3d lockFocusDistance(Vec3d camera, Vec3d desired, Vec3d focus, double fallbackDistance,
                                   double deltaSeconds) {
        if (camera == null || !camera.isFinite() || focus == null || !focus.isFinite()) return camera;
        Vec3d offset = camera.subtract(focus);
        double currentDistance = offset.length();
        double targetDistance = desired != null && desired.isFinite()
                ? Math.max(0.5, desired.distanceTo(focus))
                : Math.max(0.5, fallbackDistance);
        if (currentDistance < 1.0e-6) {
            Vec3d fallbackOffset = desired != null && desired.isFinite()
                    ? desired.subtract(focus) : new Vec3d(0.0, 0.0, targetDistance);
            if (fallbackOffset.lengthSquared() < 1.0e-8) {
                fallbackOffset = new Vec3d(0.0, 0.0, targetDistance);
            }
            return focus.add(fallbackOffset.normalizeOr(new Vec3d(0.0, 0.0, 1.0)).multiply(targetDistance));
        }
        double blended = CameraSmoothing.exponential(currentDistance, targetDistance,
                RADIUS_LOCK_RESPONSIVENESS, deltaSeconds);
        double maxStep = MAX_RADIUS_CHANGE_BLOCKS_PER_SECOND * Math.max(1.0e-4, deltaSeconds);
        if (Math.abs(blended - currentDistance) > maxStep) {
            blended = currentDistance + Math.copySign(maxStep, blended - currentDistance);
        }
        return focus.add(offset.multiply(blended / currentDistance));
    }

    private static Vec3d travelDirection(TargetPose target) {
        // Vehicles / aircraft: follow the mount flight path (3D), not passenger free-look yaw.
        if (target.inVehicle() || VehicleMotion.isAircraftLike(null, target)) {
            return VehicleMotion.resolveForward(null, target);
        }
        Vec3d velocity = target.velocity();
        // Include vertical component when airborne so elytra/flight tracking does not yaw-flick on pitch changes.
        if (Math.abs(velocity.y()) > 0.35 || velocity.lengthSquared() > 0.01) {
            Vec3d full = velocity.normalizeOr(CameraMath.horizontalDirectionFromYaw(target.yaw()));
            // Keep a mostly horizontal follow frame; damp extreme pitch into the orbit plane.
            Vec3d horizontal = new Vec3d(full.x(), 0.0, full.z());
            if (horizontal.lengthSquared() < 0.05) {
                return CameraMath.horizontalDirectionFromYaw(target.yaw());
            }
            return horizontal.normalizeOr(CameraMath.horizontalDirectionFromYaw(target.yaw()));
        }
        return CameraMath.horizontalDirectionFromYaw(target.yaw());
    }

    @Override
    public ShotValidationResult validate(ShotRequest request, ReplayContext context) {
        ShotValidationResult common = validateCommon(request, context);
        List<PathWarning> errors = new ArrayList<>();
        if (request.distance() <= 0.0) errors.add(error("distance", "Follow distance must be greater than zero"));
        if (request.cameraSpeed() <= 0.0) errors.add(error("camera_speed", "Camera speed must be greater than zero"));
        return common.merge(new ShotValidationResult(errors));
    }
}
