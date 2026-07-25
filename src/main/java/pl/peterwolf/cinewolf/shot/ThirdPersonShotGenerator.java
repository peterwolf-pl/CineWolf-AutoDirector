package pl.peterwolf.cinewolf.shot;

import pl.peterwolf.cinewolf.api.ShotGenerator;
import pl.peterwolf.cinewolf.camera.CameraLookAtSolver;
import pl.peterwolf.cinewolf.camera.CameraMath;
import pl.peterwolf.cinewolf.camera.CameraPathSimplifier;
import pl.peterwolf.cinewolf.camera.CameraSmoothing;
import pl.peterwolf.cinewolf.model.CameraPathPlan;
import pl.peterwolf.cinewolf.model.CameraSample;
import pl.peterwolf.cinewolf.model.PathStatistics;
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

/**
 * Peer head-level view — as another player standing at the same height would see the subject.
 * <p>
 * Explicitly <strong>not</strong> Minecraft F5 (rear, elevated, looking down). Camera sits on the
 * subject's <em>side</em> at exact head/eye Y and looks horizontally at the head (pitch ≈ 0).
 * <p>
 * {@code request.height()} = small offset from eyes (0 = exact head). {@code request.distance()} =
 * horizontal separation from the subject.
 */
public final class ThirdPersonShotGenerator extends AbstractShotGenerator implements ShotGenerator {
    private static final double MAX_DIRECTION_TURN_DEGREES_PER_SECOND = 90.0;
    private static final double HIGH_SPEED_BLOCKS_PER_SECOND = 8.0;
    private static final double DEFAULT_DISTANCE = 3.5;
    private static final double MIN_DISTANCE = 1.75;
    private static final double MAX_DISTANCE = 10.0;
    /** Max |height| offset from eyes (blocks). Matches UI / MontageConfig.thirdPersonHeight. */
    private static final double MAX_EYE_OFFSET = 2.25;
    /**
     * How much of the offset is pure side vs slight behind.
     * High side fraction = peer standing beside you, not F5 glued behind.
     */
    private static final double SIDE_WEIGHT = 0.92;
    private static final double BEHIND_WEIGHT = 0.08;

    private final CameraPathSimplifier simplifier = new CameraPathSimplifier();

    @Override
    public CameraPathPlan generate(ShotRequest request, ReplayContext context) {
        ShotValidationResult validation = validate(request, context);
        List<PathWarning> warnings = new ArrayList<>(validation.messages());
        if (!validation.isValid()) {
            return new CameraPathPlan(request, List.of(), List.of(), warnings,
                    new PathStatistics(0, 0, 0.0, 0.0, 0.0));
        }

        List<Long> replayTicks = sampleReplayTicks(request, context);
        double defaultDelta = request.durationSeconds() / Math.max(1, replayTicks.size() - 1);
        List<CameraSample> samples = new ArrayList<>(replayTicks.size());
        Vec3d facing = new Vec3d(0.0, 0.0, 1.0);
        Vec3d smoothedXz = null;
        double previousYaw = Double.NaN;
        double previousPitch = 0.0;
        double distance = clampDistance(request.distance());
        double eyeOffset = eyeHeightOffset(request.height());
        double sideSign = request.direction().sign() >= 0 ? 1.0 : -1.0;
        if (request.options().sideOffset() != 0.0) {
            sideSign = Math.signum(request.options().sideOffset());
            if (sideSign == 0.0) sideSign = 1.0;
        }

        for (int i = 0; i < replayTicks.size(); i++) {
            long replayTime = replayTicks.get(i);
            double cinematic = cinematicTimeAtTick(request, replayTime);
            double delta = i == 0 ? defaultDelta
                    : cinematic - cinematicTimeAtTick(request, replayTicks.get(i - 1));
            delta = Math.max(1.0e-4, delta);

            TargetPose subject = requiredPose(request, context, replayTime);
            Vec3d measured = facingDirection(subject);
            double speed = subject.velocity().length();
            double responsiveness = speed > HIGH_SPEED_BLOCKS_PER_SECOND ? 3.0 : 5.5;
            double maxTurn = speed > HIGH_SPEED_BLOCKS_PER_SECOND ? 70.0 : MAX_DIRECTION_TURN_DEGREES_PER_SECOND;
            facing = CameraSmoothing.smoothDirectionRateLimited(facing, measured, responsiveness, delta, maxTurn);

            Vec3d head = headOf(subject);
            // Exact head height — never F5 lift above the head.
            double cameraY = head.y() + eyeOffset;

            Vec3d right = Vec3d.UP.cross(facing).normalizeOr(new Vec3d(1.0, 0.0, 0.0)).multiply(sideSign);
            // Almost pure lateral stand: another player beside the subject at head height.
            Vec3d offset = right.multiply(distance * SIDE_WEIGHT)
                    .subtract(facing.multiply(distance * BEHIND_WEIGHT));
            Vec3d desiredXz = new Vec3d(head.x() + offset.x(), 0.0, head.z() + offset.z());

            double maxStep = Math.max(request.cameraSpeed() * 1.4, speed + 5.0) * delta;
            if (smoothedXz == null) {
                smoothedXz = desiredXz;
            } else {
                Vec3d smoothed = CameraSmoothing.exponential(smoothedXz, desiredXz,
                        Math.max(1.0, request.cameraSpeed()), delta);
                Vec3d stepped = CameraSmoothing.clampStep(smoothedXz, smoothed, maxStep);
                smoothedXz = new Vec3d(stepped.x(), 0.0, stepped.z());
            }

            Vec3d camera = new Vec3d(smoothedXz.x(), cameraY, smoothedXz.z());
            // Look straight at the head at the same Y → pitch forced near zero (level peer gaze).
            Vec3d lookAt = new Vec3d(head.x(), cameraY, head.z());
            CameraSample sample = levelLookSample(request, cinematic, replayTime, camera, lookAt,
                    subject, previousYaw, previousPitch, delta);
            samples.add(sample);
            previousYaw = sample.yaw();
            previousPitch = sample.pitch();
        }

        warnings.add(new PathWarning(PathWarning.Severity.INFO, "third_person.player_level",
                String.format("peer head-level side view (eyeOffset=%.2f, distance=%.2f)", eyeOffset, distance),
                0.0));
        return finishPeerLevel(request, context, samples, warnings);
    }

    /**
     * Build orientation with hard-clamped pitch so motion / look-at never invents F5 downward gaze.
     */
    private CameraSample levelLookSample(ShotRequest request, double cinematicTime, long replayTime,
                                         Vec3d camera, Vec3d lookAt, TargetPose subject,
                                         double previousYaw, double previousPitch, double deltaSeconds) {
        CameraLookAtSolver.Orientation raw = lookAtSolver.solve(camera, lookAt, previousYaw,
                previousPitch, deltaSeconds, 140.0, 40.0);
        // Peer level: keep pitch tiny. Looking down at the subject is exactly the F5 feel we reject.
        double pitch = Math.max(-6.0, Math.min(6.0, raw.pitch()));
        org.joml.Quaternionf rotation = new org.joml.Quaternionf().rotationYXZ(
                (float) Math.toRadians(-raw.yaw()),
                (float) Math.toRadians(pitch),
                0.0f
        ).normalize();
        return new CameraSample(cinematicTime, replayTime, camera, rotation,
                raw.yaw(), pitch, 0.0, request.fov(), lookAt,
                subject.discontinuity() || raw.degenerate());
    }

    /**
     * Skip the shared motion limiter (it can drift Y / pitch). Only simplify keyframes; re-lock Y.
     */
    private CameraPathPlan finishPeerLevel(ShotRequest request, ReplayContext context,
                                           List<CameraSample> samples, List<PathWarning> warnings) {
        for (CameraSample sample : samples) {
            if (!sample.isFinite()) {
                warnings.add(new PathWarning(PathWarning.Severity.ERROR, "non_finite_path",
                        "Generated path contains an invalid camera sample", sample.cinematicTimeSeconds()));
                break;
            }
        }
        // Re-assert head-level Y on every sample (defensive against any drift).
        List<CameraSample> locked = new ArrayList<>(samples.size());
        for (CameraSample sample : samples) {
            double y = sample.lookAtPoint().y();
            Vec3d position = new Vec3d(sample.position().x(), y, sample.position().z());
            Vec3d lookAt = new Vec3d(sample.lookAtPoint().x(), y, sample.lookAtPoint().z());
            CameraLookAtSolver.Orientation orientation = lookAtSolver.solve(position, lookAt,
                    sample.yaw(), 0.0, 0.0, 180.0, 10.0);
            double pitch = Math.max(-6.0, Math.min(6.0, orientation.pitch()));
            org.joml.Quaternionf rotation = new org.joml.Quaternionf().rotationYXZ(
                    (float) Math.toRadians(-orientation.yaw()),
                    (float) Math.toRadians(pitch),
                    0.0f
            ).normalize();
            locked.add(new CameraSample(sample.cinematicTimeSeconds(), sample.replayTime(), position,
                    rotation, orientation.yaw(), pitch, 0.0, sample.fov(), lookAt,
                    sample.discontinuity(), sample.collisionConstrained()));
        }
        List<CameraSample> simplified = simplifier.simplify(locked, context.samplingSettings());
        if (simplified.size() > context.samplingSettings().maximumKeyframes()) {
            warnings.add(error("keyframe_limit", "Simplified path exceeds the configured safe keyframe limit"));
        }
        double length = 0.0;
        double maximumSpeed = 0.0;
        for (int i = 1; i < locked.size(); i++) {
            double distance = locked.get(i - 1).position().distanceTo(locked.get(i).position());
            double delta = locked.get(i).cinematicTimeSeconds() - locked.get(i - 1).cinematicTimeSeconds();
            length += distance;
            if (delta > 0.0) maximumSpeed = Math.max(maximumSpeed, distance / delta);
        }
        PathStatistics statistics = new PathStatistics(locked.size(), simplified.size(), length, maximumSpeed,
                request.revolutions());
        return new CameraPathPlan(request, locked, simplified, warnings, statistics);
    }

    private static Vec3d headOf(TargetPose subject) {
        Vec3d focus = subject.focusPosition();
        if (focus != null && focus.isFinite()) return focus;
        return subject.position().add(new Vec3d(0.0, 1.62, 0.0));
    }

    private static double clampDistance(double distance) {
        if (!Double.isFinite(distance) || distance <= 0.0) return DEFAULT_DISTANCE;
        return Math.max(MIN_DISTANCE, Math.min(MAX_DISTANCE, distance));
    }

    private static double eyeHeightOffset(double height) {
        if (!Double.isFinite(height)) return 0.0;
        return Math.max(-MAX_EYE_OFFSET, Math.min(MAX_EYE_OFFSET, height));
    }

    private static Vec3d facingDirection(TargetPose target) {
        Vec3d velocity = target.velocity();
        Vec3d fromYaw = CameraMath.horizontalDirectionFromYaw(target.yaw());
        if (velocity.lengthSquared() < 0.04) return fromYaw;
        Vec3d horizontal = new Vec3d(velocity.x(), 0.0, velocity.z());
        if (horizontal.lengthSquared() < 0.02) return fromYaw;
        Vec3d travel = horizontal.normalizeOr(fromYaw);
        return fromYaw.multiply(0.7).add(travel.multiply(0.3)).normalizeOr(fromYaw);
    }

    @Override
    public ShotValidationResult validate(ShotRequest request, ReplayContext context) {
        ShotValidationResult common = validateCommon(request, context);
        List<PathWarning> errors = new ArrayList<>();
        if (request.distance() <= 0.0) {
            errors.add(error("distance", "3rd person distance must be greater than zero"));
        }
        if (request.cameraSpeed() <= 0.0) {
            errors.add(error("camera_speed", "Camera speed must be greater than zero"));
        }
        return common.merge(new ShotValidationResult(errors));
    }

    @Override
    public Set<TargetKind> supportedTargetKinds() {
        return EnumSet.of(TargetKind.ENTITY, TargetKind.GROUP);
    }

    @Override
    public Set<FramingType> supportedFramingTypes() {
        return EnumSet.of(FramingType.MEDIUM, FramingType.CLOSE, FramingType.WIDE);
    }

    @Override
    public ShotCapabilities capabilities() {
        return ShotCapabilities.full();
    }
}
