package pl.peterwolf.cinewolf.visibility;

import pl.peterwolf.cinewolf.model.Vec3d;

import java.util.List;
import java.util.Objects;

public record TargetVisibilityResult(
        double visibilityScore,
        double occlusionRatio,
        boolean fullyVisible,
        boolean partiallyVisible,
        boolean occluded,
        double safeAreaScore,
        double leadSpaceScore,
        double groupVisibleRatio,
        List<Vec3d> occluderHints,
        List<String> diagnostics
) {
    public TargetVisibilityResult {
        visibilityScore = clamp01(visibilityScore);
        occlusionRatio = clamp01(occlusionRatio);
        safeAreaScore = clamp01(safeAreaScore);
        leadSpaceScore = clamp01(leadSpaceScore);
        groupVisibleRatio = clamp01(groupVisibleRatio);
        occluderHints = List.copyOf(Objects.requireNonNullElse(occluderHints, List.of()));
        diagnostics = List.copyOf(Objects.requireNonNullElse(diagnostics, List.of()));
    }

    public static TargetVisibilityResult open(double safeAreaScore, double leadSpaceScore) {
        return new TargetVisibilityResult(1.0, 0.0, true, false, false, safeAreaScore, leadSpaceScore, 1.0,
                List.of(), List.of("visibility.open"));
    }

    private static double clamp01(double value) {
        if (!Double.isFinite(value)) return 0.0;
        return Math.max(0.0, Math.min(1.0, value));
    }
}
