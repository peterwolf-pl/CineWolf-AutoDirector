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
import pl.peterwolf.cinewolf.model.TargetKind;
import pl.peterwolf.cinewolf.model.TargetPose;
import pl.peterwolf.cinewolf.model.Vec3d;
import pl.peterwolf.cinewolf.montage.preset.FramingType;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public final class ChaseShotGenerator extends AbstractShotGenerator implements ShotGenerator {
    private static final double HIGH_SPEED_BLOCKS_PER_SECOND = 8.0;

    @Override
    public CameraPathPlan generate(ShotRequest request, ReplayContext context) {
        ShotValidationResult validation = validate(request, context);
        List<PathWarning> warnings = new ArrayList<>(validation.messages());
        if (!validation.isValid()) return finish(request, context, List.of(), warnings);

        double baseDistance = Math.max(1.0, request.distance());
        double minDistance = request.options().minimumDistance();
        double maxDistance = request.options().maximumDistance();
        double velocityMultiplier = request.options().velocityDistanceMultiplier();
        double startFov = request.fov();
        double endFov = request.options().resolvedEndFov(Math.min(110.0, startFov + 12.0));

        List<Long> replayTicks = sampleReplayTicks(request, context);
        List<CameraSample> samples = new ArrayList<>(replayTicks.size());
        Vec3d direction = new Vec3d(0.0, 0.0, 1.0);
        Vec3d lateralLag = Vec3d.ZERO;
        Vec3d smoothedCamera = null;
        double smoothedDistance = baseDistance;
        double previousYaw = Double.NaN;
        double previousPitch = Double.NaN;
        double previousSpeed = 0.0;
        for (int i = 0; i < replayTicks.size(); i++) {
            long replayTime = replayTicks.get(i);
            double delta = i == 0 ? request.durationSeconds() / Math.max(1, replayTicks.size() - 1)
                    : cinematicTimeAtTick(request, replayTime) - cinematicTimeAtTick(request, replayTicks.get(i - 1));
            delta = Math.max(1.0e-4, delta);
            TargetPose target = requiredPose(request, context, replayTime);
            Vec3d measured = new Vec3d(target.velocity().x(), 0.0, target.velocity().z());
            double speed = measured.length();
            if (measured.lengthSquared() < 0.0025) {
                measured = CameraMath.horizontalDirectionFromYaw(target.yaw());
            }
            double directionResponse = speed > HIGH_SPEED_BLOCKS_PER_SECOND ? 2.2 : 3.2;
            double maxTurn = speed > HIGH_SPEED_BLOCKS_PER_SECOND ? 65.0 : 90.0;
            direction = CameraSmoothing.smoothDirectionRateLimited(direction, measured.normalizeOr(direction),
                    directionResponse, delta, maxTurn);

            Vec3d right = Vec3d.UP.cross(direction).normalizeOr(new Vec3d(1.0, 0.0, 0.0));
            double turnRate = Math.min(0.75, Math.abs(speed - previousSpeed) * 0.1 + speed * 0.015);
            lateralLag = CameraSmoothing.exponential(lateralLag, right.multiply(turnRate * 0.9), 2.0, delta);
            // Bound lateral lag so high-speed turns cannot fling the camera through geometry in one sample.
            if (lateralLag.length() > 2.5) {
                lateralLag = lateralLag.normalizeOr(Vec3d.ZERO).multiply(2.5);
            }
            previousSpeed = speed;

            double targetDistance = baseDistance + speed * velocityMultiplier;
            targetDistance = Math.max(minDistance, Math.min(maxDistance, targetDistance));
            smoothedDistance = CameraSmoothing.exponential(smoothedDistance, targetDistance, 2.5, delta);

            long lookAheadTick = Math.min(request.replayEndTime(),
                    replayTime + Math.round(request.lookAheadSeconds() * 20.0 * (1.0 + Math.min(0.4, speed * 0.03))));
            TargetPose predicted = context.targetPoseResolver().resolve(request.target(), lookAheadTick).orElse(target);
            Vec3d desired = predicted.position()
                    .subtract(direction.multiply(smoothedDistance))
                    .subtract(lateralLag)
                    .add(Vec3d.UP.multiply(request.height()));
            double responsiveness = Math.max(0.8, Math.min(request.cameraSpeed(), speed > HIGH_SPEED_BLOCKS_PER_SECOND
                    ? request.cameraSpeed() * 0.75 : request.cameraSpeed()));
            double maxStep = Math.max(responsiveness * 1.4, speed + 8.0) * delta;
            if (smoothedCamera == null) {
                smoothedCamera = desired;
            } else {
                Vec3d smoothed = CameraSmoothing.exponential(smoothedCamera, desired, responsiveness, delta);
                smoothedCamera = CameraSmoothing.clampStep(smoothedCamera, smoothed, maxStep);
            }

            double fov = startFov;
            if (request.options().speedBasedFov()) {
                double speedFactor = Math.max(0.0, Math.min(1.0, speed / 12.0));
                double desiredFov = startFov + (endFov - startFov) * speedFactor;
                // FOV also rate-limited via sample FOV assignment smoothness (max 20 deg/s).
                double previousFov = samples.isEmpty() ? startFov : samples.getLast().fov();
                double maxFovStep = 20.0 * delta;
                fov = previousFov + Math.max(-maxFovStep, Math.min(maxFovStep, desiredFov - previousFov));
            }
            CameraSample base = sample(request, context, cinematicTimeAtTick(request, replayTime), replayTime,
                    smoothedCamera, target, previousYaw, previousPitch, delta);
            samples.add(new CameraSample(base.cinematicTimeSeconds(), base.replayTime(), base.position(),
                    base.rotation(), base.yaw(), base.pitch(), base.roll(), fov, base.lookAtPoint(),
                    base.discontinuity(), base.collisionConstrained()));
            previousYaw = base.yaw();
            previousPitch = base.pitch();
        }
        return finish(request, context, samples, warnings);
    }

    @Override
    public ShotValidationResult validate(ShotRequest request, ReplayContext context) {
        ShotValidationResult common = validateCommon(request, context);
        List<PathWarning> errors = new ArrayList<>();
        if (request.distance() <= 0.0) errors.add(error("distance", "Chase base distance must be > 0"));
        if (request.cameraSpeed() <= 0.0) errors.add(error("camera_speed", "Camera speed must be > 0"));
        if (request.options().maximumDistance() < request.options().minimumDistance()) {
            errors.add(error("distance_range", "Chase maximum distance must be >= minimum distance"));
        }
        return common.merge(new ShotValidationResult(errors));
    }

    @Override
    public Set<TargetKind> supportedTargetKinds() {
        return EnumSet.of(TargetKind.ENTITY, TargetKind.VEHICLE);
    }

    @Override
    public Set<FramingType> supportedFramingTypes() {
        return EnumSet.of(FramingType.MEDIUM, FramingType.CLOSE, FramingType.WIDE);
    }

    @Override
    public ShotCapabilities capabilities() {
        return ShotCapabilities.vehicleAware();
    }
}
