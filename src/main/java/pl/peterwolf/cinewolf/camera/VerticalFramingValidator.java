package pl.peterwolf.cinewolf.camera;

import pl.peterwolf.cinewolf.api.TargetPoseResolver;
import pl.peterwolf.cinewolf.model.BoundingBox;
import pl.peterwolf.cinewolf.model.CameraSample;
import pl.peterwolf.cinewolf.model.TargetPose;
import pl.peterwolf.cinewolf.model.TargetReference;
import pl.peterwolf.cinewolf.model.Vec3d;

import java.util.List;
import java.util.Objects;

/** Projects the tracked subject bounds into an intended output frame without depending on window size. */
public final class VerticalFramingValidator {
    private static final double MIN_DEPTH = 1.0e-4;

    public Result validate(List<CameraSample> samples, TargetPoseResolver resolver, TargetReference target,
                           double widthToHeight, double safeFraction) {
        Objects.requireNonNull(samples, "samples");
        Objects.requireNonNull(resolver, "resolver");
        Objects.requireNonNull(target, "target");
        if (!Double.isFinite(widthToHeight) || widthToHeight <= 0.0) {
            throw new IllegalArgumentException("widthToHeight must be positive and finite");
        }
        if (!Double.isFinite(safeFraction) || safeFraction <= 0.0 || safeFraction > 1.0) {
            throw new IllegalArgumentException("safeFraction must be in (0, 1]");
        }

        int checked = 0;
        int outside = 0;
        int unavailable = 0;
        double maximumFill = 0.0;
        for (CameraSample sample : samples) {
            TargetPose pose = resolver.resolve(target, sample.replayTime()).orElse(null);
            if (pose == null || !pose.boundingBox().isFinite()) {
                unavailable++;
                continue;
            }
            Projection projection = project(sample, pose.boundingBox(), widthToHeight, safeFraction);
            checked++;
            maximumFill = Math.max(maximumFill, projection.maximumFill());
            if (!projection.inside()) outside++;
        }
        return new Result(checked, outside, unavailable, maximumFill);
    }

    private static Projection project(CameraSample sample, BoundingBox bounds, double aspect, double safeFraction) {
        Vec3d forward = sample.lookAtPoint().subtract(sample.position())
                .normalizeOr(new Vec3d(0.0, 0.0, 1.0));
        Vec3d referenceUp = Math.abs(forward.y()) > 0.995
                ? new Vec3d(0.0, 0.0, 1.0) : Vec3d.UP;
        Vec3d right = forward.cross(referenceUp).normalizeOr(new Vec3d(1.0, 0.0, 0.0));
        Vec3d up = right.cross(forward).normalizeOr(Vec3d.UP);
        double halfFov = Math.toRadians(Math.max(1.0, Math.min(179.0, sample.fov()))) * 0.5;
        double tangent = Math.tan(halfFov);
        double maximumFill = 0.0;
        boolean inside = true;

        for (double x : new double[]{bounds.min().x(), bounds.max().x()}) {
            for (double y : new double[]{bounds.min().y(), bounds.max().y()}) {
                for (double z : new double[]{bounds.min().z(), bounds.max().z()}) {
                    Vec3d relative = new Vec3d(x, y, z).subtract(sample.position());
                    double depth = relative.dot(forward);
                    if (depth <= MIN_DEPTH) {
                        inside = false;
                        maximumFill = Double.POSITIVE_INFINITY;
                        continue;
                    }
                    double verticalLimit = depth * tangent * safeFraction;
                    double horizontalLimit = verticalLimit * aspect;
                    double horizontalFill = Math.abs(relative.dot(right)) / Math.max(MIN_DEPTH, horizontalLimit);
                    double verticalFill = Math.abs(relative.dot(up)) / Math.max(MIN_DEPTH, verticalLimit);
                    double fill = Math.max(horizontalFill, verticalFill);
                    maximumFill = Math.max(maximumFill, fill);
                    if (fill > 1.0) inside = false;
                }
            }
        }
        return new Projection(inside, maximumFill);
    }

    public record Result(int checkedSamples, int outsideSamples, int unavailableSamples, double maximumFillRatio) {
        public boolean hasRisk() {
            return outsideSamples > 0;
        }

        public boolean incomplete() {
            return unavailableSamples > 0;
        }
    }

    private record Projection(boolean inside, double maximumFill) {
    }
}
