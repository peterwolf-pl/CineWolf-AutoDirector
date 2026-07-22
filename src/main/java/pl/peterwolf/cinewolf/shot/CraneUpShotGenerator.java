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
import pl.peterwolf.cinewolf.model.TargetKind;
import pl.peterwolf.cinewolf.model.TargetPose;
import pl.peterwolf.cinewolf.model.Vec3d;
import pl.peterwolf.cinewolf.montage.preset.FramingType;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public class CraneUpShotGenerator extends AbstractShotGenerator implements ShotGenerator {
    @Override
    public CameraPathPlan generate(ShotRequest request, ReplayContext context) {
        return generateCrane(request, context, true);
    }

    CameraPathPlan generateCrane(ShotRequest request, ReplayContext context, boolean upward) {
        ShotValidationResult validation = validate(request, context);
        List<PathWarning> warnings = new ArrayList<>(validation.messages());
        if (!validation.isValid()) return finish(request, context, List.of(), warnings);

        double startHeight = request.options().resolvedStartHeight(upward ? Math.max(1.0, request.height() * 0.35)
                : Math.max(request.height(), request.startDistance()));
        double endHeight = request.options().resolvedEndHeight(upward
                ? Math.max(startHeight + 2.0, request.height())
                : Math.max(request.options().groundClearance(), request.endDistance() > 0 ? request.endDistance()
                : Math.max(1.0, request.height() * 0.35)));
        if (!upward && endHeight < request.options().groundClearance()) {
            endHeight = request.options().groundClearance();
            warnings.add(new PathWarning(PathWarning.Severity.WARNING, "crane.ground_clearance",
                    "Crane down end height raised to minimum ground clearance", 0.0));
        }
        double horizontal = Math.max(1.0, request.distance());
        double drift = request.options().sideOffset();
        double startFov = request.fov();
        double endFov = request.options().resolvedEndFov(startFov);
        if (Math.abs(endFov - startFov) > 25.0) {
            endFov = startFov + Math.copySign(25.0, endFov - startFov);
            warnings.add(new PathWarning(PathWarning.Severity.INFO, "crane.fov_clamped",
                    "Crane FOV change clamped to 25 degrees", 0.0));
        }

        List<Long> replayTicks = sampleReplayTicks(request, context);
        List<CameraSample> samples = new ArrayList<>(replayTicks.size());
        double previousYaw = Double.NaN;
        for (long replayTime : replayTicks) {
            double progress = Easings.apply(request.easing(), progressAtTick(request, replayTime));
            TargetPose target = requiredPose(request, context, replayTime);
            Vec3d forward = CameraMath.horizontalDirectionFromYaw(target.yaw());
            Vec3d right = Vec3d.UP.cross(forward).normalizeOr(new Vec3d(1.0, 0.0, 0.0));
            double height = startHeight + (endHeight - startHeight) * progress;
            double distance = horizontal;
            if (request.options().maintainTargetSize()) {
                // Pull back slightly as height increases to keep approximate subject size.
                distance = horizontal * (1.0 + 0.15 * progress);
            } else if (Math.abs(drift) > 1.0e-6) {
                distance = horizontal + drift * progress;
            }
            double orbit = request.startAngleDegrees() * Math.PI / 180.0 * progress * 0.15;
            Vec3d offset = forward.multiply(-distance).add(right.multiply(Math.sin(orbit) * Math.max(0.0, drift)))
                    .add(Vec3d.UP.multiply(height));
            Vec3d camera = target.position().add(offset);
            double fov = startFov + (endFov - startFov) * progress;
            if (request.options().maintainTargetSize()) {
                fov = startFov + (Math.min(endFov, startFov + 8.0) - startFov) * progress;
            }
            CameraSample base = sample(request, context, cinematicTimeAtTick(request, replayTime), replayTime,
                    camera, target, previousYaw);
            CameraSample sample = new CameraSample(base.cinematicTimeSeconds(), base.replayTime(), base.position(),
                    base.rotation(), base.yaw(), base.pitch(), base.roll(), fov, base.lookAtPoint(),
                    base.discontinuity(), base.collisionConstrained());
            samples.add(sample);
            previousYaw = sample.yaw();
        }
        return finish(request, context, samples, warnings);
    }

    @Override
    public ShotValidationResult validate(ShotRequest request, ReplayContext context) {
        ShotValidationResult common = validateCommon(request, context);
        List<PathWarning> errors = new ArrayList<>();
        if (request.distance() <= 0.0) errors.add(error("distance", "Crane horizontal distance must be > 0"));
        return common.merge(new ShotValidationResult(errors));
    }

    @Override
    public Set<TargetKind> supportedTargetKinds() {
        return EnumSet.of(TargetKind.ENTITY, TargetKind.STRUCTURE, TargetKind.AREA, TargetKind.VEHICLE, TargetKind.GROUP);
    }

    @Override
    public Set<FramingType> supportedFramingTypes() {
        return EnumSet.of(FramingType.EXTREME_WIDE, FramingType.WIDE, FramingType.MEDIUM);
    }

    @Override
    public ShotCapabilities capabilities() {
        return ShotCapabilities.full();
    }
}
