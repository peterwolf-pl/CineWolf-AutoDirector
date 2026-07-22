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
        double previousYaw = Double.NaN;
        double previousSpeed = 0.0;
        for (int i = 0; i < replayTicks.size(); i++) {
            long replayTime = replayTicks.get(i);
            double delta = i == 0 ? request.durationSeconds() / Math.max(1, replayTicks.size() - 1)
                    : cinematicTimeAtTick(request, replayTime) - cinematicTimeAtTick(request, replayTicks.get(i - 1));
            TargetPose target = requiredPose(request, context, replayTime);
            Vec3d measured = new Vec3d(target.velocity().x(), 0.0, target.velocity().z());
            double speed = measured.length();
            if (measured.lengthSquared() < 0.0025) measured = CameraMath.horizontalDirectionFromYaw(target.yaw());
            direction = CameraSmoothing.smoothDirection(direction, measured.normalizeOr(direction), 3.2, delta);

            // Lateral lag during turns: compare previous direction via cross product magnitude.
            Vec3d right = Vec3d.UP.cross(direction).normalizeOr(new Vec3d(1.0, 0.0, 0.0));
            double turnRate = Math.min(1.0, Math.abs(speed - previousSpeed) * 0.15 + speed * 0.02);
            lateralLag = CameraSmoothing.exponential(lateralLag, right.multiply(turnRate * 1.2), 2.5, delta);
            previousSpeed = speed;

            double targetDistance = baseDistance + speed * velocityMultiplier;
            targetDistance = Math.max(minDistance, Math.min(maxDistance, targetDistance));
            long lookAheadTick = Math.min(request.replayEndTime(),
                    replayTime + Math.round(request.lookAheadSeconds() * 20.0 * (1.0 + speed * 0.05)));
            TargetPose predicted = context.targetPoseResolver().resolve(request.target(), lookAheadTick).orElse(target);
            Vec3d desired = predicted.position()
                    .subtract(direction.multiply(targetDistance))
                    .subtract(lateralLag)
                    .add(Vec3d.UP.multiply(request.height()));
            double responsiveness = Math.max(0.5, request.cameraSpeed());
            smoothedCamera = smoothedCamera == null ? desired
                    : CameraSmoothing.exponential(smoothedCamera, desired, responsiveness, delta);

            double fov = startFov;
            if (request.options().speedBasedFov()) {
                double speedFactor = Math.max(0.0, Math.min(1.0, speed / 12.0));
                fov = startFov + (endFov - startFov) * speedFactor;
            }
            CameraSample base = sample(request, context, cinematicTimeAtTick(request, replayTime), replayTime,
                    smoothedCamera, target, previousYaw);
            samples.add(new CameraSample(base.cinematicTimeSeconds(), base.replayTime(), base.position(),
                    base.rotation(), base.yaw(), base.pitch(), base.roll(), fov, base.lookAtPoint(),
                    base.discontinuity(), base.collisionConstrained()));
            previousYaw = base.yaw();
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
