package pl.peterwolf.cinewolf.shot;

import pl.peterwolf.cinewolf.api.ShotGenerator;
import pl.peterwolf.cinewolf.camera.Easings;
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

public final class SpiralShotGenerator extends AbstractShotGenerator implements ShotGenerator {
    @Override
    public CameraPathPlan generate(ShotRequest request, ReplayContext context) {
        ShotValidationResult validation = validate(request, context);
        List<PathWarning> warnings = new ArrayList<>(validation.messages());
        if (!validation.isValid()) return finish(request, context, List.of(), warnings);

        double startRadius = Math.max(1.0, request.startDistance() > 0 ? request.startDistance()
                : request.diameter() * 0.5);
        double endRadius = Math.max(1.0, request.endDistance() > 0 ? request.endDistance()
                : Math.max(1.5, startRadius * 0.45));
        double startHeight = request.options().resolvedStartHeight(request.height() * 0.4);
        double endHeight = request.options().resolvedEndHeight(request.height());
        double revolutions = request.revolutions();
        double startFov = request.fov();
        double endFov = request.options().resolvedEndFov(startFov);

        List<Long> replayTicks = sampleReplayTicks(request, context);
        List<CameraSample> samples = new ArrayList<>(replayTicks.size());
        double previousYaw = Double.NaN;
        for (long replayTime : replayTicks) {
            double progress = Easings.apply(request.easing(), progressAtTick(request, replayTime));
            TargetPose target = requiredPose(request, context, replayTime);
            double radius = startRadius + (endRadius - startRadius) * progress;
            double height = startHeight + (endHeight - startHeight) * progress;
            if (request.options().maintainTargetSize() && radius > startRadius) {
                // Mild FOV compensation when spiraling out.
                endFov = Math.min(startFov + 12.0, endFov);
            }
            double angle = Math.toRadians(request.startAngleDegrees())
                    + request.direction().sign() * revolutions * 2.0 * Math.PI * progress;
            Vec3d camera = target.position().add(new Vec3d(
                    radius * Math.cos(angle),
                    height,
                    radius * Math.sin(angle)
            ));
            if (target.boundingBox().contains(camera, 0.25)) {
                warnings.add(new PathWarning(PathWarning.Severity.ERROR, "spiral.inside_target",
                        "Spiral path intersects target volume; increase radius",
                        cinematicTimeAtTick(request, replayTime)));
                camera = target.position().add(new Vec3d(Math.max(radius, 2.0) * Math.cos(angle),
                        Math.max(height, 1.5), Math.max(radius, 2.0) * Math.sin(angle)));
            }
            double fov = startFov + (endFov - startFov) * progress;
            CameraSample base = sample(request, context, cinematicTimeAtTick(request, replayTime), replayTime,
                    camera, target, previousYaw);
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
        double startRadius = request.startDistance() > 0 ? request.startDistance() : request.diameter() * 0.5;
        double endRadius = request.endDistance() > 0 ? request.endDistance() : startRadius;
        if (startRadius <= 0.0 || endRadius <= 0.0) {
            errors.add(error("radius", "Spiral radii must be greater than zero"));
        }
        if (request.revolutions() < 0.0) {
            errors.add(error("revolutions", "Spiral revolutions cannot be negative"));
        }
        return common.merge(new ShotValidationResult(errors));
    }

    @Override
    public Set<TargetKind> supportedTargetKinds() {
        return EnumSet.of(TargetKind.ENTITY, TargetKind.STRUCTURE, TargetKind.VEHICLE, TargetKind.GROUP);
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
