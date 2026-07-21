package pl.peterwolf.cinewolf.shot;

import pl.peterwolf.cinewolf.api.ShotGenerator;
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

public final class OrbitShotGenerator extends AbstractShotGenerator implements ShotGenerator {
    @Override
    public CameraPathPlan generate(ShotRequest request, ReplayContext context) {
        ShotValidationResult validation = validate(request, context);
        List<PathWarning> warnings = new ArrayList<>(validation.messages());
        if (!validation.isValid()) return finish(request, context, List.of(), warnings);

        List<Long> replayTicks = sampleReplayTicks(request, context);
        List<CameraSample> samples = new ArrayList<>(replayTicks.size());
        double radius = request.diameter() * 0.5;
        double previousYaw = Double.NaN;
        for (long replayTime : replayTicks) {
            double rawProgress = progressAtTick(request, replayTime);
            double easedTime = Easings.apply(request.easing(), rawProgress) * request.durationSeconds();
            TargetPose target = requiredPose(request, context, replayTime);
            double angle = Math.toRadians(request.startAngleDegrees())
                    + request.direction().sign() * (2.0 * Math.PI * request.rpm() / 60.0) * easedTime;
            Vec3d camera = target.position().add(new Vec3d(radius * Math.cos(angle), request.height(), radius * Math.sin(angle)));
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
        if (request.diameter() <= 0.0) {
            return common.merge(new ShotValidationResult(List.of(error("diameter", "Orbit diameter must be greater than zero"))));
        }
        if (request.rpm() < 0.0) {
            return common.merge(new ShotValidationResult(List.of(error("rpm", "Orbit RPM cannot be negative"))));
        }
        return common;
    }
}
