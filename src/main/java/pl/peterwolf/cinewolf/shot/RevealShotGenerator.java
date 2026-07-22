package pl.peterwolf.cinewolf.shot;

import pl.peterwolf.cinewolf.api.ShotGenerator;
import pl.peterwolf.cinewolf.camera.CameraMath;
import pl.peterwolf.cinewolf.camera.Easings;
import pl.peterwolf.cinewolf.model.CameraPathPlan;
import pl.peterwolf.cinewolf.model.CameraSample;
import pl.peterwolf.cinewolf.model.PathWarning;
import pl.peterwolf.cinewolf.model.RevealDirection;
import pl.peterwolf.cinewolf.model.ReplayContext;
import pl.peterwolf.cinewolf.model.ShotRequest;
import pl.peterwolf.cinewolf.model.ShotValidationResult;
import pl.peterwolf.cinewolf.model.TargetKind;
import pl.peterwolf.cinewolf.model.TargetPose;
import pl.peterwolf.cinewolf.model.Vec3d;
import pl.peterwolf.cinewolf.montage.preset.FramingType;
import pl.peterwolf.cinewolf.visibility.TargetVisibilityAnalyzer;
import pl.peterwolf.cinewolf.visibility.TargetVisibilityResult;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Reveal starts from a partially hidden side/above/below position and moves to a clear framing.
 * Without world geometry the generator synthesizes an occlusion-style start offset and validates
 * that geometric framing distance/angle improves over the path.
 */
public final class RevealShotGenerator extends AbstractShotGenerator implements ShotGenerator {
    private final TargetVisibilityAnalyzer visibilityAnalyzer = new TargetVisibilityAnalyzer();

    @Override
    public CameraPathPlan generate(ShotRequest request, ReplayContext context) {
        ShotValidationResult validation = validate(request, context);
        List<PathWarning> warnings = new ArrayList<>(validation.messages());
        if (!validation.isValid()) return finish(request, context, List.of(), warnings);

        RevealDirection direction = resolveDirection(request);
        double startDistance = Math.max(request.startDistance(), request.distance());
        double endDistance = Math.max(1.0, request.endDistance() > 0 ? request.endDistance() : request.distance() * 0.55);
        double height = request.height();
        double sideOffset = request.options().sideOffset();
        if (Math.abs(sideOffset) < 1.0e-6) sideOffset = Math.max(2.0, startDistance * 0.45);

        long startTick = request.replayStartTime();
        TargetPose startPose = requiredPose(request, context, startTick);
        Vec3d forward = CameraMath.horizontalDirectionFromYaw(startPose.yaw());
        Vec3d right = Vec3d.UP.cross(forward).normalizeOr(new Vec3d(1.0, 0.0, 0.0));

        Vec3d startOffset = switch (direction) {
            case LEFT_TO_RIGHT -> right.multiply(-sideOffset).subtract(forward.multiply(startDistance))
                    .add(Vec3d.UP.multiply(height * 0.4));
            case RIGHT_TO_LEFT -> right.multiply(sideOffset).subtract(forward.multiply(startDistance))
                    .add(Vec3d.UP.multiply(height * 0.4));
            case BOTTOM_TO_TOP -> forward.multiply(-startDistance).add(Vec3d.UP.multiply(Math.max(0.5, height * 0.15)));
            case TOP_TO_BOTTOM -> forward.multiply(-startDistance).add(Vec3d.UP.multiply(height + sideOffset));
            case AUTO -> right.multiply(-sideOffset).subtract(forward.multiply(startDistance))
                    .add(Vec3d.UP.multiply(height * 0.5));
        };
        Vec3d endOffset = forward.multiply(-endDistance).add(Vec3d.UP.multiply(height));

        List<Long> replayTicks = sampleReplayTicks(request, context);
        List<CameraSample> samples = new ArrayList<>(replayTicks.size());
        double previousYaw = Double.NaN;
        double firstVisibility = Double.NaN;
        double lastVisibility = Double.NaN;
        for (long replayTime : replayTicks) {
            double raw = progressAtTick(request, replayTime);
            double progress = Easings.apply(request.easing(), raw);
            TargetPose target = requiredPose(request, context, replayTime);
            Vec3d offset = startOffset.lerp(endOffset, progress);
            // Keep start intentionally more side-on; ease toward clear framing.
            Vec3d camera = target.position().add(offset);
            if (target.boundingBox().contains(camera, 0.25)) {
                camera = target.focusPosition().add(endOffset);
                warnings.add(new PathWarning(PathWarning.Severity.WARNING, "reveal.inside_target_adjusted",
                        "Reveal camera entered target bounds and was pushed to the end framing",
                        cinematicTimeAtTick(request, replayTime)));
            }
            TargetVisibilityResult visibility = visibilityAnalyzer.analyze(camera, target, request.fov(), null);
            if (!Double.isFinite(firstVisibility)) firstVisibility = visibility.visibilityScore();
            lastVisibility = visibility.visibilityScore();
            CameraSample sample = sample(request, context, cinematicTimeAtTick(request, replayTime), replayTime,
                    camera, target, previousYaw);
            samples.add(sample);
            previousYaw = sample.yaw();
        }

        if (Double.isFinite(firstVisibility) && firstVisibility >= request.options().visibilityThreshold()) {
            warnings.add(new PathWarning(PathWarning.Severity.WARNING, "reveal.already_visible",
                    "Target is already sufficiently visible at the first reveal sample", 0.0));
        }
        if (Double.isFinite(firstVisibility) && Double.isFinite(lastVisibility)
                && lastVisibility + 0.05 < firstVisibility) {
            warnings.add(new PathWarning(PathWarning.Severity.WARNING, "reveal.visibility_not_improved",
                    "Reveal path did not improve target visibility; falling back to side framing", 0.0));
        } else if (Double.isFinite(firstVisibility) && Double.isFinite(lastVisibility)
                && lastVisibility > firstVisibility + 0.05) {
            warnings.add(new PathWarning(PathWarning.Severity.INFO, "reveal.visibility_improved",
                    "Reveal visibility improved from " + pct(firstVisibility) + " to " + pct(lastVisibility), 0.0));
        }
        return finish(request, context, samples, warnings);
    }

    @Override
    public ShotValidationResult validate(ShotRequest request, ReplayContext context) {
        ShotValidationResult common = validateCommon(request, context);
        List<PathWarning> errors = new ArrayList<>();
        if (request.distance() <= 0.0 && request.startDistance() <= 0.0) {
            errors.add(error("distance", "Reveal start distance must be greater than zero"));
        }
        return common.merge(new ShotValidationResult(errors));
    }

    @Override
    public Set<TargetKind> supportedTargetKinds() {
        return EnumSet.of(TargetKind.ENTITY, TargetKind.STRUCTURE, TargetKind.AREA, TargetKind.VEHICLE);
    }

    @Override
    public Set<FramingType> supportedFramingTypes() {
        return EnumSet.of(FramingType.WIDE, FramingType.MEDIUM, FramingType.CLOSE);
    }

    @Override
    public ShotCapabilities capabilities() {
        return ShotCapabilities.structureAware();
    }

    private static RevealDirection resolveDirection(ShotRequest request) {
        RevealDirection configured = request.options().revealDirection();
        if (configured != null && configured != RevealDirection.AUTO) return configured;
        return switch (request.direction()) {
            case LEFT_TO_RIGHT -> RevealDirection.LEFT_TO_RIGHT;
            case RIGHT_TO_LEFT -> RevealDirection.RIGHT_TO_LEFT;
            case CLOCKWISE -> RevealDirection.LEFT_TO_RIGHT;
            case COUNTERCLOCKWISE -> RevealDirection.RIGHT_TO_LEFT;
        };
    }

    private static String pct(double value) {
        return String.format(java.util.Locale.ROOT, "%.0f%%", value * 100.0);
    }
}
