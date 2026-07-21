package pl.peterwolf.cinewolf.shot;

import pl.peterwolf.cinewolf.api.ShotGenerator;
import pl.peterwolf.cinewolf.camera.CameraMath;
import pl.peterwolf.cinewolf.camera.Easings;
import pl.peterwolf.cinewolf.model.CameraPathPlan;
import pl.peterwolf.cinewolf.model.CameraSample;
import pl.peterwolf.cinewolf.model.PathWarning;
import pl.peterwolf.cinewolf.model.ReplayContext;
import pl.peterwolf.cinewolf.model.ShotRequest;
import pl.peterwolf.cinewolf.model.ShotValidationResult;
import pl.peterwolf.cinewolf.model.TargetPose;
import pl.peterwolf.cinewolf.model.Vec3d;

import java.util.ArrayList;
import java.util.List;

public final class FlybyShotGenerator extends AbstractShotGenerator implements ShotGenerator {
    @Override
    public CameraPathPlan generate(ShotRequest request, ReplayContext context) {
        ShotValidationResult validation = validate(request, context);
        List<PathWarning> warnings = new ArrayList<>(validation.messages());
        if (!validation.isValid()) return finish(request, context, List.of(), warnings);

        long middleTick = replayTick(request, 0.5);
        TargetPose middleTarget = requiredPose(request, context, middleTick);
        Vec3d movement = new Vec3d(middleTarget.velocity().x(), 0.0, middleTarget.velocity().z());
        if (movement.lengthSquared() < 0.0025) movement = CameraMath.horizontalDirectionFromYaw(middleTarget.yaw());
        movement = movement.normalizeOr(new Vec3d(0.0, 0.0, 1.0));
        Vec3d across = Vec3d.UP.cross(movement).normalizeOr(new Vec3d(1.0, 0.0, 0.0))
                .multiply(request.direction().sign());
        double halfLength = Math.max(request.distance(), request.cameraSpeed() * request.durationSeconds() * 0.5);

        List<Long> replayTicks = sampleReplayTicks(request, context);
        List<CameraSample> samples = new ArrayList<>(replayTicks.size());
        double previousYaw = Double.NaN;
        for (long replayTime : replayTicks) {
            double rawProgress = progressAtTick(request, replayTime);
            double progress = Easings.apply(request.easing(), rawProgress);
            TargetPose target = requiredPose(request, context, replayTime);
            double along = (progress * 2.0 - 1.0) * halfLength;
            double shallowCurve = Math.sin(progress * Math.PI) * Math.min(2.0, request.distance() * 0.2);
            Vec3d camera = middleTarget.position().add(across.multiply(along))
                    .subtract(movement.multiply(request.distance() + shallowCurve))
                    .add(Vec3d.UP.multiply(request.height()));
            CameraSample sample = sample(request, context, cinematicTimeAtTick(request, replayTime), replayTime,
                    camera, target, previousYaw);
            samples.add(sample);
            previousYaw = sample.yaw();
        }
        return finish(request, context, samples, warnings);
    }

    @Override
    public ShotValidationResult validate(ShotRequest request, ReplayContext context) {
        ShotValidationResult common = validateCommon(request, context);
        List<PathWarning> errors = new ArrayList<>();
        if (request.distance() <= 0.0) errors.add(error("distance", "Flyby distance must be greater than zero"));
        if (request.cameraSpeed() <= 0.0) errors.add(error("camera_speed", "Camera speed must be greater than zero"));
        return common.merge(new ShotValidationResult(errors));
    }
}
