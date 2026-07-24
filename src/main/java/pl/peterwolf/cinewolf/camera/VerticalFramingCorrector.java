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
 */
public final class VerticalFramingCorrector {
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

        List<CameraSample> corrected = new ArrayList<>(samples.size());
        int adjusted = 0;
        for (CameraSample sample : samples) {
            CameraSample next = pullBackUntilSafe(sample, resolver, target, widthToHeight, safeFraction);
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

    private CameraSample pullBackUntilSafe(CameraSample sample, TargetPoseResolver resolver,
                                           TargetReference target, double aspect, double safeFraction) {
        TargetPose pose = resolver.resolve(target, sample.replayTime()).orElse(null);
        if (pose == null) return sample;
        Vec3d look = sample.lookAtPoint();
        Vec3d fromLook = sample.position().subtract(look);
        double distance = fromLook.length();
        if (distance < 1.0e-4) return sample;
        Vec3d direction = fromLook.multiply(1.0 / distance);

        CameraSample best = sample;
        for (int step = 1; step <= 6; step++) {
            double scale = 1.0 + step * 0.10;
            Vec3d position = look.add(direction.multiply(distance * scale));
            CameraLookAtSolver.Orientation orientation = lookAtSolver.solve(position, look, sample.yaw(),
                    sample.pitch(), 0.0);
            CameraSample candidate = new CameraSample(
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
            VerticalFramingValidator.Result result = validator.validate(
                    List.of(candidate), resolver, target, aspect, safeFraction);
            best = candidate;
            if (!result.hasRisk()) return candidate;
        }
        return best;
    }

    public record CorrectionResult(List<CameraSample> samples, int adjustedSamples, List<PathWarning> warnings) {
        public CorrectionResult {
            samples = List.copyOf(samples == null ? List.of() : samples);
            warnings = List.copyOf(warnings == null ? List.of() : warnings);
        }
    }
}
