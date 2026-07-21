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

import java.util.ArrayList;
import java.util.List;

public final class FollowShotGenerator extends AbstractShotGenerator implements ShotGenerator {
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
        for (int i = 0; i < replayTicks.size(); i++) {
            long replayTime = replayTicks.get(i);
            double delta = i == 0 ? defaultDelta
                    : cinematicTimeAtTick(request, replayTime) - cinematicTimeAtTick(request, replayTicks.get(i - 1));
            TargetPose target = requiredPose(request, context, replayTime);
            Vec3d measured = new Vec3d(target.velocity().x(), 0.0, target.velocity().z());
            if (measured.lengthSquared() < 0.0025) measured = CameraMath.horizontalDirectionFromYaw(target.yaw());
            direction = CameraSmoothing.smoothDirection(direction, measured.normalizeOr(direction), 5.0, delta);
            Vec3d desired = target.position().subtract(direction.multiply(request.distance())).add(Vec3d.UP.multiply(request.height()));
            smoothedCamera = smoothedCamera == null ? desired
                    : CameraSmoothing.exponential(smoothedCamera, desired, request.cameraSpeed(), delta);
            CameraSample sample = sample(request, context, cinematicTimeAtTick(request, replayTime), replayTime,
                    smoothedCamera, target, previousYaw);
            samples.add(sample);
            previousYaw = sample.yaw();
        }
        return finish(request, context, samples, warnings);
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
