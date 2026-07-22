package pl.peterwolf.cinewolf.visibility;

import pl.peterwolf.cinewolf.model.PathWarning;
import pl.peterwolf.cinewolf.model.TargetPose;
import pl.peterwolf.cinewolf.model.Vec3d;
import pl.peterwolf.cinewolf.model.target.StructureTarget;
import pl.peterwolf.cinewolf.montage.preset.FramingType;
import pl.peterwolf.cinewolf.montage.preset.OutputAspectRatio;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Stronger framing validation for entity, group, structure, and vehicle subjects. */
public final class FramingValidator {
    private final TargetVisibilityAnalyzer visibilityAnalyzer = new TargetVisibilityAnalyzer();

    public List<PathWarning> validateSample(Vec3d camera, TargetPose target, double fov,
                                            FramingType framing, OutputAspectRatio aspect,
                                            TargetVisibilityResult visibility, double cinematicTime) {
        Objects.requireNonNull(camera, "camera");
        Objects.requireNonNull(target, "target");
        List<PathWarning> warnings = new ArrayList<>();
        double distance = camera.distanceTo(target.focusPosition());
        double size = maxDim(target.boundingBox().max().subtract(target.boundingBox().min()));
        double expected = expectedDistance(size, framing, aspect);
        if (distance < expected * 0.35) {
            warnings.add(new PathWarning(PathWarning.Severity.WARNING, "framing.too_close",
                    "Camera is closer than recommended for " + framing, cinematicTime));
        }
        if (distance > expected * 3.5) {
            warnings.add(new PathWarning(PathWarning.Severity.WARNING, "framing.too_far",
                    "Camera is farther than recommended for " + framing, cinematicTime));
        }
        if (visibility != null && visibility.visibilityScore() < 0.35) {
            warnings.add(new PathWarning(PathWarning.Severity.WARNING, "framing.low_visibility",
                    "Target visibility is low (" + String.format(java.util.Locale.ROOT, "%.0f%%",
                            visibility.visibilityScore() * 100.0) + ")", cinematicTime));
        }
        if (visibility != null && visibility.leadSpaceScore() < 0.25 && target.velocity().length() > 2.0) {
            warnings.add(new PathWarning(PathWarning.Severity.INFO, "framing.low_lead_space",
                    "Subject lead space is tight for current velocity", cinematicTime));
        }
        if (target.boundingBox().contains(camera, 0.2)) {
            warnings.add(new PathWarning(PathWarning.Severity.ERROR, "framing.inside_target",
                    "Camera is inside the target bounding volume", cinematicTime));
        }
        return warnings;
    }

    public double expectedDistance(double subjectSize, FramingType framing, OutputAspectRatio aspect) {
        double framingMultiplier = switch (framing == null ? FramingType.MEDIUM : framing) {
            case EXTREME_WIDE -> 6.5;
            case WIDE -> 4.5;
            case MEDIUM -> 3.0;
            case CLOSE -> 1.9;
            case EXTREME_CLOSE -> 1.2;
        };
        double vertical = aspect == OutputAspectRatio.VERTICAL_9_16 ? 1.2 : 1.0;
        return Math.max(1.0, subjectSize * framingMultiplier * vertical);
    }

    public double structureFramingDistance(StructureTarget structure, double fov) {
        return visibilityAnalyzer.structureFramingDistance(structure, fov);
    }

    private static double maxDim(Vec3d size) {
        return Math.max(0.5, Math.max(size.x(), Math.max(size.y(), size.z())));
    }
}
