package pl.peterwolf.cinewolf.camera;

import pl.peterwolf.cinewolf.api.TargetPoseResolver;
import pl.peterwolf.cinewolf.model.CameraSample;
import pl.peterwolf.cinewolf.model.PathWarning;
import pl.peterwolf.cinewolf.model.TargetPose;
import pl.peterwolf.cinewolf.model.TargetReference;
import pl.peterwolf.cinewolf.model.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Pull-back of camera samples that leave the intended 9:16 safe area.
 * Moves the camera farther from the look-at point along the camera→subject axis.
 *
 * <p>Per-sample pull-back without temporal filtering causes continuous zoom breathing when the
 * subject bounds flicker (elytra/parachute, pose jitter). Scales are therefore max-filtered over a
 * short temporal window and rate-limited so framing expands smoothly and does not pump.</p>
 */
public final class VerticalFramingCorrector {
    private static final double MIN_DISTANCE = 1.0e-4;
    private static final double MAX_SCALE = 1.0 + 6 * 0.10;
    private static final double TEMPORAL_WINDOW_SECONDS = 0.45;
    /** Maximum scale change per second after the max-filter (cinematic zoom-out only). */
    private static final double MAX_SCALE_RATE_PER_SECOND = 0.55;

    private final VerticalFramingValidator validator = new VerticalFramingValidator();
    private final CameraLookAtSolver lookAtSolver = new CameraLookAtSolver();

    public CorrectionResult correct(List<CameraSample> samples, TargetPoseResolver resolver,
                                    TargetReference target, double widthToHeight, double safeFraction) {
        Objects.requireNonNull(samples, "samples");
        if (samples.isEmpty()) {
            return new CorrectionResult(samples, 0, List.of());
        }
        VerticalFramingValidator.Result initial = validator.validate(samples, resolver, target,
                widthToHeight, safeFraction);
        if (!initial.hasRisk()) {
            return new CorrectionResult(samples, 0, List.of());
        }

        double[] requiredScales = new double[samples.size()];
        for (int index = 0; index < samples.size(); index++) {
            requiredScales[index] = requiredPullBackScale(samples.get(index), resolver, target,
                    widthToHeight, safeFraction);
        }
        double[] smoothedScales = temporalStabilizeScales(samples, requiredScales);

        List<CameraSample> corrected = new ArrayList<>(samples.size());
        int adjusted = 0;
        for (int index = 0; index < samples.size(); index++) {
            CameraSample sample = samples.get(index);
            double scale = smoothedScales[index];
            if (scale <= 1.0 + 1.0e-6) {
                corrected.add(sample);
                continue;
            }
            CameraSample next = applyScale(sample, scale);
            if (next != sample) adjusted++;
            corrected.add(next);
        }
        List<PathWarning> warnings = new ArrayList<>();
        if (adjusted > 0) {
            warnings.add(new PathWarning(PathWarning.Severity.INFO, "vertical_framing_corrected",
                    "Pulled camera back on " + adjusted + " samples for 9:16 safe framing", 0.0));
        }
        VerticalFramingValidator.Result after = validator.validate(corrected, resolver, target,
                widthToHeight, safeFraction);
        if (after.hasRisk()) {
            warnings.add(new PathWarning(PathWarning.Severity.WARNING, "vertical_framing_risk",
                    "Target bounds leave the vertical safe area in " + after.outsideSamples()
                            + " camera samples after correction", 0.0));
        }
        if (after.incomplete()) {
            warnings.add(new PathWarning(PathWarning.Severity.WARNING, "vertical_framing_unverified",
                    "Vertical framing could not be verified in " + after.unavailableSamples()
                            + " camera samples", 0.0));
        }
        return new CorrectionResult(List.copyOf(corrected), adjusted, warnings);
    }

    private double requiredPullBackScale(CameraSample sample, TargetPoseResolver resolver,
                                         TargetReference target, double aspect, double safeFraction) {
        TargetPose pose = resolver.resolve(target, sample.replayTime()).orElse(null);
        if (pose == null) return 1.0;
        Vec3d look = sample.lookAtPoint();
        Vec3d fromLook = sample.position().subtract(look);
        double distance = fromLook.length();
        if (distance < MIN_DISTANCE) return 1.0;

        double bestSafeScale = MAX_SCALE;
        boolean foundSafe = false;
        for (int step = 0; step <= 6; step++) {
            double scale = 1.0 + step * 0.10;
            CameraSample candidate = applyScale(sample, scale);
            VerticalFramingValidator.Result result = validator.validate(
                    List.of(candidate), resolver, target, aspect, safeFraction);
            if (!result.hasRisk()) {
                bestSafeScale = scale;
                foundSafe = true;
                break;
            }
            bestSafeScale = scale;
        }
        return foundSafe || bestSafeScale > 1.0 ? bestSafeScale : 1.0;
    }

    /**
     * Expand framing only as far as nearby samples require, then rate-limit so the camera cannot
     * pump in and out every few frames when subject bounds flicker.
     */
    static double[] temporalStabilizeScales(List<CameraSample> samples, double[] requiredScales) {
        int size = samples.size();
        double[] windowMax = new double[size];
        for (int index = 0; index < size; index++) {
            double centerTime = samples.get(index).cinematicTimeSeconds();
            double maxScale = requiredScales[index];
            for (int neighbor = 0; neighbor < size; neighbor++) {
                if (samples.get(neighbor).discontinuity() && neighbor != index) {
                    // Do not borrow scales across hard cuts.
                    if (neighbor < index) continue;
                    break;
                }
                double timeDistance = Math.abs(samples.get(neighbor).cinematicTimeSeconds() - centerTime);
                if (timeDistance <= TEMPORAL_WINDOW_SECONDS) {
                    maxScale = Math.max(maxScale, requiredScales[neighbor]);
                }
            }
            windowMax[index] = maxScale;
        }

        double[] stabilized = new double[size];
        double previous = windowMax[0];
        stabilized[0] = previous;
        for (int index = 1; index < size; index++) {
            if (samples.get(index).discontinuity() || samples.get(index - 1).discontinuity()) {
                previous = windowMax[index];
                stabilized[index] = previous;
                continue;
            }
            double delta = Math.max(1.0e-4,
                    samples.get(index).cinematicTimeSeconds() - samples.get(index - 1).cinematicTimeSeconds());
            double maxStep = MAX_SCALE_RATE_PER_SECOND * delta;
            double desired = windowMax[index];
            // Prefer holding a wider framing once pulled out; only allow slow relaxation inward.
            if (desired < previous) {
                previous = Math.max(desired, previous - maxStep);
            } else if (desired > previous) {
                previous = Math.min(desired, previous + maxStep);
            }
            stabilized[index] = previous;
        }
        return stabilized;
    }

    private CameraSample applyScale(CameraSample sample, double scale) {
        if (scale <= 1.0 + 1.0e-9) return sample;
        Vec3d look = sample.lookAtPoint();
        Vec3d fromLook = sample.position().subtract(look);
        double distance = fromLook.length();
        if (distance < MIN_DISTANCE) return sample;
        Vec3d direction = fromLook.multiply(1.0 / distance);
        Vec3d position = look.add(direction.multiply(distance * scale));
        CameraLookAtSolver.Orientation orientation = lookAtSolver.solve(position, look, sample.yaw(),
                sample.pitch(), 0.0);
        return new CameraSample(
                sample.cinematicTimeSeconds(),
                sample.replayTime(),
                position,
                orientation.quaternion(),
                orientation.yaw(),
                orientation.pitch(),
                sample.roll(),
                sample.fov(),
                look,
                sample.discontinuity(),
                sample.collisionConstrained()
        );
    }

    public record CorrectionResult(List<CameraSample> samples, int adjustedSamples, List<PathWarning> warnings) {
        public CorrectionResult {
            samples = List.copyOf(samples == null ? List.of() : samples);
            warnings = List.copyOf(warnings == null ? List.of() : warnings);
        }
    }
}
