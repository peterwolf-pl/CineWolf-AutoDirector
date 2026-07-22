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

public final class StaticTrackingShotGenerator extends AbstractShotGenerator implements ShotGenerator {
    @Override
    public CameraPathPlan generate(ShotRequest request, ReplayContext context) {
        ShotValidationResult validation = validate(request, context);
        List<PathWarning> warnings = new ArrayList<>(validation.messages());
        if (!validation.isValid()) return finish(request, context, List.of(), warnings);

        long anchorTick = request.replayStartTime();
        TargetPose anchorPose = requiredPose(request, context, anchorTick);
        Vec3d forward = CameraMath.horizontalDirectionFromYaw(anchorPose.yaw());
        Vec3d right = Vec3d.UP.cross(forward).normalizeOr(new Vec3d(1.0, 0.0, 0.0));
        double side = request.options().sideOffset() != 0.0 ? request.options().sideOffset()
                : request.direction().sign() * Math.max(1.0, request.distance() * 0.35);
        Vec3d fixedCamera = anchorPose.position()
                .subtract(forward.multiply(request.distance()))
                .add(right.multiply(side))
                .add(Vec3d.UP.multiply(request.height()));

        List<Long> replayTicks = sampleReplayTicks(request, context);
        List<CameraSample> samples = new ArrayList<>(replayTicks.size());
        double previousYaw = Double.NaN;
        Vec3d camera = fixedCamera;
        for (int i = 0; i < replayTicks.size(); i++) {
            long replayTime = replayTicks.get(i);
            double delta = i == 0 ? request.durationSeconds() / Math.max(1, replayTicks.size() - 1)
                    : cinematicTimeAtTick(request, replayTime) - cinematicTimeAtTick(request, replayTicks.get(i - 1));
            TargetPose target = requiredPose(request, context, replayTime);
            if (request.options().allowLimitedRepositioning()) {
                double distance = camera.distanceTo(target.focusPosition());
                if (distance < request.options().minimumDistance() || distance > request.options().maximumDistance()) {
                    Vec3d desired = target.focusPosition().add(camera.subtract(target.focusPosition())
                            .normalizeOr(forward.multiply(-1.0)).multiply(request.distance()));
                    desired = fixedCamera.lerp(desired, 0.35);
                    if (desired.distanceTo(fixedCamera) > request.options().repositionRadius()) {
                        desired = fixedCamera.add(desired.subtract(fixedCamera).normalizeOr(Vec3d.ZERO)
                                .multiply(request.options().repositionRadius()));
                    }
                    camera = CameraSmoothing.exponential(camera, desired, request.cameraSpeed(), delta);
                }
            } else {
                camera = fixedCamera;
            }

            // Angular velocity limit via look-at previous yaw unwrap + clamp is handled in sample/lookAtSolver;
            // additionally damp wild swings when target is very close.
            if (camera.distanceTo(target.focusPosition()) < 1.25) {
                warnings.add(new PathWarning(PathWarning.Severity.INFO, "static_tracking.close_pass",
                        "Target passed very close to the static camera",
                        cinematicTimeAtTick(request, replayTime)));
            }
            CameraSample sample = sample(request, context, cinematicTimeAtTick(request, replayTime), replayTime,
                    camera, target, previousYaw);
            if (Double.isFinite(previousYaw)) {
                double turn = CameraMath.angleDifferenceDegrees(previousYaw, sample.yaw());
                double maxTurn = request.options().angularVelocityLimitDegreesPerSecond() * Math.max(1.0e-3, delta);
                if (turn > maxTurn) {
                    double limitedYaw = previousYaw + Math.copySign(maxTurn,
                            CameraMath.unwrapDegrees(previousYaw, sample.yaw()) - previousYaw);
                    sample = new CameraSample(sample.cinematicTimeSeconds(), sample.replayTime(), sample.position(),
                            sample.rotation(), limitedYaw, sample.pitch(), sample.roll(), sample.fov(),
                            sample.lookAtPoint(), sample.discontinuity(), sample.collisionConstrained());
                }
            }
            samples.add(sample);
            previousYaw = sample.yaw();
        }
        return finish(request, context, samples, warnings);
    }

    @Override
    public ShotValidationResult validate(ShotRequest request, ReplayContext context) {
        ShotValidationResult common = validateCommon(request, context);
        List<PathWarning> errors = new ArrayList<>();
        if (request.distance() <= 0.0) errors.add(error("distance", "Static tracking distance must be > 0"));
        return common.merge(new ShotValidationResult(errors));
    }

    @Override
    public Set<TargetKind> supportedTargetKinds() {
        return EnumSet.of(TargetKind.ENTITY, TargetKind.VEHICLE, TargetKind.GROUP, TargetKind.AREA);
    }

    @Override
    public Set<FramingType> supportedFramingTypes() {
        return EnumSet.of(FramingType.WIDE, FramingType.MEDIUM, FramingType.CLOSE);
    }

    @Override
    public ShotCapabilities capabilities() {
        return ShotCapabilities.full();
    }
}
