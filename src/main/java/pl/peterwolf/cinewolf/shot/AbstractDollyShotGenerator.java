package pl.peterwolf.cinewolf.shot;

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
import java.util.Optional;

abstract class AbstractDollyShotGenerator extends AbstractShotGenerator {
    private static final double TARGET_CLEARANCE = 0.25;
    private static final long DIRECTION_WINDOW_TICKS = 5L;
    private static final double MIN_WINDOW_TRAVEL_SQUARED = 0.1225;
    private static final double DIRECTION_RESPONSIVENESS = 2.0;
    private static final double MAX_DIRECTION_TURN_DEGREES_PER_SECOND = 90.0;

    protected CameraPathPlan generateDolly(ShotRequest request, ReplayContext context, boolean inward) {
        ShotValidationResult validation = validateDolly(request, context, inward);
        List<PathWarning> warnings = new ArrayList<>(validation.messages());
        if (!validation.isValid()) return finish(request, context, List.of(), warnings);

        List<Long> replayTicks = sampleReplayTicks(request, context);
        List<CameraSample> samples = new ArrayList<>(replayTicks.size());
        double previousYaw = Double.NaN;
        Vec3d direction = null;
        for (int index = 0; index < replayTicks.size(); index++) {
            long replayTime = replayTicks.get(index);
            double rawProgress = progressAtTick(request, replayTime);
            double progress = Easings.apply(request.easing(), rawProgress);
            TargetPose target = requiredPose(request, context, replayTime);
            Optional<Vec3d> measuredDirection = windowedTravelDirection(request, context, replayTime);
            if (target.discontinuity()) {
                direction = CameraMath.horizontalDirectionFromYaw(target.yaw());
            } else if (direction == null) {
                direction = measuredDirection.orElseGet(() -> CameraMath.horizontalDirectionFromYaw(target.yaw()));
            } else if (measuredDirection.isPresent()) {
                double deltaSeconds = cinematicTimeAtTick(request, replayTime)
                        - cinematicTimeAtTick(request, replayTicks.get(index - 1));
                direction = smoothHorizontalDirection(direction, measuredDirection.get(), deltaSeconds);
            }
            double start = inward ? request.startDistance() : request.endDistance();
            double end = inward ? request.endDistance() : request.startDistance();
            double distance = start + (end - start) * progress;
            Vec3d camera = target.position().subtract(direction.multiply(distance)).add(Vec3d.UP.multiply(request.height()));
            if (target.boundingBox().contains(camera, TARGET_CLEARANCE)
                    && warnings.stream().noneMatch(warning -> warning.code().equals("camera_inside_target"))) {
                warnings.add(new PathWarning(PathWarning.Severity.ERROR, "camera_inside_target",
                        "Dolly path enters the target bounding box; increase distance or height",
                        cinematicTimeAtTick(request, replayTime)));
            }
            CameraSample sample = sample(request, context, cinematicTimeAtTick(request, replayTime), replayTime,
                    camera, target, previousYaw);
            samples.add(sample);
            previousYaw = sample.yaw();
        }
        return finish(request, context, samples, warnings);
    }

    private static Optional<Vec3d> windowedTravelDirection(ShotRequest request, ReplayContext context,
                                                            long replayTime) {
        long beforeTick = Math.max(request.replayStartTime(), replayTime - DIRECTION_WINDOW_TICKS);
        long afterTick = Math.min(request.replayEndTime(), replayTime + DIRECTION_WINDOW_TICKS);
        Optional<TargetPose> before = context.targetPoseResolver().resolve(request.target(), beforeTick);
        Optional<TargetPose> after = context.targetPoseResolver().resolve(request.target(), afterTick);
        if (before.isPresent() && after.isPresent()
                && before.get().dimension().equals(after.get().dimension())
                && !before.get().discontinuity() && !after.get().discontinuity()) {
            Vec3d displacement = horizontal(after.get().position().subtract(before.get().position()));
            if (displacement.lengthSquared() >= MIN_WINDOW_TRAVEL_SQUARED) {
                return Optional.of(displacement.normalizeOr(Vec3d.ZERO));
            }
        }
        return Optional.empty();
    }

    private static Vec3d smoothHorizontalDirection(Vec3d previous, Vec3d measured, double deltaSeconds) {
        if (deltaSeconds <= 0.0) return previous;
        double previousYaw = directionYaw(previous);
        double measuredYaw = directionYaw(measured);
        double alpha = 1.0 - Math.exp(-DIRECTION_RESPONSIVENESS * deltaSeconds);
        double requestedTurn = (CameraMath.unwrapDegrees(previousYaw, measuredYaw) - previousYaw) * alpha;
        double maximumTurn = MAX_DIRECTION_TURN_DEGREES_PER_SECOND * deltaSeconds;
        double smoothedYaw = previousYaw + Math.max(-maximumTurn, Math.min(maximumTurn, requestedTurn));
        return CameraMath.horizontalDirectionFromYaw(smoothedYaw);
    }

    private static double directionYaw(Vec3d direction) {
        return -Math.toDegrees(Math.atan2(direction.x(), direction.z()));
    }

    private static Vec3d horizontal(Vec3d direction) {
        return new Vec3d(direction.x(), 0.0, direction.z());
    }

    protected ShotValidationResult validateDolly(ShotRequest request, ReplayContext context, boolean inward) {
        ShotValidationResult common = validateCommon(request, context);
        List<PathWarning> errors = new ArrayList<>();
        if (request.startDistance() < 1.0 || request.endDistance() < 1.0) {
            errors.add(error("safe_distance", "Dolly distances must be at least one block"));
        }
        if (request.startDistance() <= request.endDistance()) {
            errors.add(error("dolly_range", "Start distance must be greater than end distance"));
        }
        return common.merge(new ShotValidationResult(errors));
    }
}
