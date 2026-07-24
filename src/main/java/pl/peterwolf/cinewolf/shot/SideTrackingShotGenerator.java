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
import pl.peterwolf.cinewolf.model.TrackingSide;
import pl.peterwolf.cinewolf.model.Vec3d;
import pl.peterwolf.cinewolf.montage.preset.FramingType;
import pl.peterwolf.cinewolf.vehicle.VehicleDescriptor;
import pl.peterwolf.cinewolf.vehicle.VehicleProviderRegistry;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public final class SideTrackingShotGenerator extends AbstractShotGenerator implements ShotGenerator {
    private final VehicleProviderRegistry vehicles = VehicleProviderRegistry.createDefault();

    @Override
    public CameraPathPlan generate(ShotRequest request, ReplayContext context) {
        ShotValidationResult validation = validate(request, context);
        List<PathWarning> warnings = new ArrayList<>(validation.messages());
        if (!validation.isValid()) return finish(request, context, List.of(), warnings);

        TrackingSide side = request.options().trackingSide() == TrackingSide.AUTO
                ? (request.direction().sign() >= 0 ? TrackingSide.RIGHT : TrackingSide.LEFT)
                : request.options().trackingSide();
        double sideSign = side == TrackingSide.LEFT ? -1.0 : 1.0;
        double distance = Math.max(1.0, request.distance());
        double forwardOffset = request.options().forwardOffset();

        List<Long> replayTicks = sampleReplayTicks(request, context);
        List<CameraSample> samples = new ArrayList<>(replayTicks.size());
        Vec3d direction = new Vec3d(0.0, 0.0, 1.0);
        Vec3d smoothedCamera = null;
        double previousYaw = Double.NaN;
        double previousPitch = Double.NaN;
        for (int i = 0; i < replayTicks.size(); i++) {
            long replayTime = replayTicks.get(i);
            double delta = i == 0 ? request.durationSeconds() / Math.max(1, replayTicks.size() - 1)
                    : cinematicTimeAtTick(request, replayTime) - cinematicTimeAtTick(request, replayTicks.get(i - 1));
            delta = Math.max(1.0e-4, delta);
            TargetPose target = requiredPose(request, context, replayTime);
            VehicleDescriptor vehicle = vehicles.requireOrGeneric(request.target(), target);
            Vec3d measured = new Vec3d(target.velocity().x(), 0.0, target.velocity().z());
            double speed = measured.length();
            if (measured.lengthSquared() < 0.0025) measured = vehicle.forward();
            if (measured.lengthSquared() < 0.0025) measured = CameraMath.horizontalDirectionFromYaw(target.yaw());
            double responsiveness = speed > 8.0 ? 2.8 : 4.0;
            double maxTurn = speed > 8.0 ? 70.0 : 100.0;
            direction = CameraSmoothing.smoothDirectionRateLimited(direction, measured.normalizeOr(direction),
                    responsiveness, delta, maxTurn);
            Vec3d right = Vec3d.UP.cross(direction).normalizeOr(new Vec3d(1.0, 0.0, 0.0));
            Vec3d desired = target.position()
                    .add(right.multiply(sideSign * distance))
                    .add(direction.multiply(forwardOffset))
                    .add(Vec3d.UP.multiply(request.height()));
            double maxStep = Math.max(request.cameraSpeed() * 1.5, speed + 6.0) * delta;
            if (smoothedCamera == null) {
                smoothedCamera = desired;
            } else {
                Vec3d smoothed = CameraSmoothing.exponential(smoothedCamera, desired, request.cameraSpeed(), delta);
                smoothedCamera = CameraSmoothing.clampStep(smoothedCamera, smoothed, maxStep);
            }
            CameraSample sample = sample(request, context, cinematicTimeAtTick(request, replayTime), replayTime,
                    smoothedCamera, target, previousYaw, previousPitch, delta);
            samples.add(sample);
            previousYaw = sample.yaw();
            previousPitch = sample.pitch();
        }
        return finish(request, context, samples, warnings);
    }

    @Override
    public ShotValidationResult validate(ShotRequest request, ReplayContext context) {
        ShotValidationResult common = validateCommon(request, context);
        List<PathWarning> errors = new ArrayList<>();
        if (request.distance() <= 0.0) errors.add(error("distance", "Side tracking distance must be > 0"));
        if (request.cameraSpeed() <= 0.0) errors.add(error("camera_speed", "Camera speed must be > 0"));
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
