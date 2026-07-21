package pl.peterwolf.cinewolf.shot;

import pl.peterwolf.cinewolf.camera.CameraLookAtSolver;
import pl.peterwolf.cinewolf.camera.CameraPathSimplifier;
import pl.peterwolf.cinewolf.model.CameraPathPlan;
import pl.peterwolf.cinewolf.model.CameraSample;
import pl.peterwolf.cinewolf.model.PathStatistics;
import pl.peterwolf.cinewolf.model.PathWarning;
import pl.peterwolf.cinewolf.model.ReplayContext;
import pl.peterwolf.cinewolf.model.SamplingSettings;
import pl.peterwolf.cinewolf.model.ShotRequest;
import pl.peterwolf.cinewolf.model.ShotValidationResult;
import pl.peterwolf.cinewolf.model.TargetPose;
import pl.peterwolf.cinewolf.model.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.TreeSet;

abstract class AbstractShotGenerator {
    protected final CameraLookAtSolver lookAtSolver = new CameraLookAtSolver();
    private final CameraPathSimplifier simplifier = new CameraPathSimplifier();

    protected ShotValidationResult validateCommon(ShotRequest request, ReplayContext context) {
        List<PathWarning> errors = new ArrayList<>();
        if (!Double.isFinite(request.durationSeconds()) || request.durationSeconds() <= 0.0) {
            errors.add(error("duration", "Duration must be greater than zero"));
        }
        if (request.replayEndTime() <= request.replayStartTime()) {
            errors.add(error("timeline", "Replay end time must be later than replay start time"));
        }
        if (!Double.isFinite(request.fov()) || request.fov() < 1.0 || request.fov() > 110.0) {
            errors.add(error("fov", "FOV must be between 1 and 110 degrees"));
        }
        if (!allFinite(request.diameter(), request.height(), request.distance(), request.startDistance(),
                request.endDistance(), request.rpm(), request.startAngleDegrees(), request.cameraSpeed(),
                request.lookAheadSeconds())) {
            errors.add(error("numeric", "All shot parameters must be finite numbers"));
        }

        int samples = sampleCount(request, context.samplingSettings());
        if (samples > context.samplingSettings().maximumSamples()) {
            errors.add(error("sample_limit", "Requested path exceeds the configured sample limit"));
        }
        if (errors.isEmpty()) {
            String dimension = null;
            for (int i = 0; i < samples; i++) {
                long tick = replayTick(request, i / (double) (samples - 1));
                Optional<TargetPose> pose = context.targetPoseResolver().resolve(request.target(), tick);
                if (pose.isEmpty()) {
                    errors.add(new PathWarning(PathWarning.Severity.ERROR, "missing_target",
                            "Target is unavailable at replay tick " + tick, cinematicTime(request, i, samples)));
                    break;
                }
                if (!pose.get().isFinite()) {
                    errors.add(new PathWarning(PathWarning.Severity.ERROR, "invalid_target",
                            "Target pose contains a non-finite value at replay tick " + tick, cinematicTime(request, i, samples)));
                    break;
                }
                if (dimension == null) dimension = pose.get().dimension();
                else if (!dimension.equals(pose.get().dimension())) {
                    errors.add(new PathWarning(PathWarning.Severity.ERROR, "dimension_change",
                            "Target changes dimension during the requested interval", cinematicTime(request, i, samples)));
                    break;
                }
            }
        }
        return new ShotValidationResult(errors);
    }

    protected CameraSample sample(ShotRequest request, ReplayContext context, double cinematicTime, long replayTime,
                                  Vec3d cameraPosition, TargetPose targetPose, double previousYaw) {
        long lookAheadTick = Math.min(request.replayEndTime(), replayTime + Math.round(request.lookAheadSeconds() * 20.0));
        Vec3d focus = context.targetPoseResolver().resolve(request.target(), lookAheadTick)
                .map(TargetPose::focusPosition).orElse(targetPose.focusPosition());
        CameraLookAtSolver.Orientation orientation = lookAtSolver.solve(cameraPosition, focus, previousYaw);
        return new CameraSample(cinematicTime, replayTime, cameraPosition, orientation.quaternion(),
                orientation.yaw(), orientation.pitch(), orientation.roll(), request.fov(), focus,
                targetPose.discontinuity() || orientation.degenerate());
    }

    protected CameraPathPlan finish(ShotRequest request, ReplayContext context, List<CameraSample> samples,
                                    List<PathWarning> warnings) {
        for (CameraSample sample : samples) {
            if (!sample.isFinite()) {
                warnings.add(new PathWarning(PathWarning.Severity.ERROR, "non_finite_path",
                        "Generated path contains a non-finite camera sample", sample.cinematicTimeSeconds()));
                break;
            }
        }
        List<CameraSample> simplified = simplifier.simplify(samples, context.samplingSettings());
        if (simplified.size() > context.samplingSettings().maximumKeyframes()) {
            warnings.add(error("keyframe_limit", "Simplified path exceeds the configured safe keyframe limit"));
        }

        double length = 0.0;
        double maximumSpeed = 0.0;
        for (int i = 1; i < samples.size(); i++) {
            double distance = samples.get(i - 1).position().distanceTo(samples.get(i).position());
            double delta = samples.get(i).cinematicTimeSeconds() - samples.get(i - 1).cinematicTimeSeconds();
            length += distance;
            if (delta > 0.0) maximumSpeed = Math.max(maximumSpeed, distance / delta);
        }
        PathStatistics statistics = new PathStatistics(samples.size(), simplified.size(), length, maximumSpeed,
                request.revolutions());
        return new CameraPathPlan(request, samples, simplified, warnings, statistics);
    }

    protected int sampleCount(ShotRequest request, SamplingSettings settings) {
        if (!Double.isFinite(request.durationSeconds()) || request.durationSeconds() <= 0.0) return 2;
        return Math.max(2, (int) Math.ceil(request.durationSeconds() * settings.samplesPerSecond()) + 1);
    }

    protected List<Long> sampleReplayTicks(ShotRequest request, ReplayContext context) {
        int count = sampleCount(request, context.samplingSettings());
        TreeSet<Long> ticks = new TreeSet<>();
        for (int i = 0; i < count; i++) ticks.add(replayTick(request, i / (double) (count - 1)));
        for (long tick : context.adaptiveReplayTicks()) {
            if (tick >= request.replayStartTime() && tick <= request.replayEndTime()
                    && ticks.size() < context.samplingSettings().maximumSamples()) {
                ticks.add(tick);
            }
        }
        return List.copyOf(ticks);
    }

    protected long replayTick(ShotRequest request, double progress) {
        return Math.round(request.replayStartTime() + (request.replayEndTime() - request.replayStartTime()) * progress);
    }

    protected double cinematicTime(ShotRequest request, int index, int count) {
        return request.durationSeconds() * index / (double) (count - 1);
    }

    protected double progressAtTick(ShotRequest request, long replayTick) {
        long interval = request.replayEndTime() - request.replayStartTime();
        if (interval <= 0) return 0.0;
        return (replayTick - request.replayStartTime()) / (double) interval;
    }

    protected double cinematicTimeAtTick(ShotRequest request, long replayTick) {
        return request.durationSeconds() * progressAtTick(request, replayTick);
    }

    protected TargetPose requiredPose(ShotRequest request, ReplayContext context, long replayTime) {
        return context.targetPoseResolver().resolve(request.target(), replayTime)
                .orElseThrow(() -> new IllegalStateException("Target pose missing at tick " + replayTime));
    }

    protected PathWarning error(String code, String message) {
        return new PathWarning(PathWarning.Severity.ERROR, code, message, 0.0);
    }

    private static boolean allFinite(double... values) {
        for (double value : values) if (!Double.isFinite(value)) return false;
        return true;
    }
}
