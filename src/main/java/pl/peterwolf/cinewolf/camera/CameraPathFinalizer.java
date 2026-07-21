package pl.peterwolf.cinewolf.camera;

import pl.peterwolf.cinewolf.model.CameraPathPlan;
import pl.peterwolf.cinewolf.model.CameraSample;
import pl.peterwolf.cinewolf.model.PathStatistics;
import pl.peterwolf.cinewolf.model.PathWarning;
import pl.peterwolf.cinewolf.model.SamplingSettings;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Applies the final simplification pass after optional path post-processing such as collision avoidance. */
public final class CameraPathFinalizer {
    private final CameraPathSimplifier simplifier = new CameraPathSimplifier();

    public CameraPathPlan finalizePath(CameraPathPlan plan, SamplingSettings settings) {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(settings, "settings");
        List<CameraSample> simplified = simplifier.simplify(plan.samples(), settings);
        List<PathWarning> warnings = new ArrayList<>(plan.warnings().stream()
                .filter(warning -> !warning.code().equals("keyframe_limit"))
                .toList());
        if (simplified.size() > settings.maximumKeyframes()) {
            warnings.add(new PathWarning(PathWarning.Severity.ERROR, "keyframe_limit",
                    "Simplified path exceeds the configured safe keyframe limit", 0.0));
        }
        double length = 0.0;
        double maximumSpeed = 0.0;
        for (int index = 1; index < plan.samples().size(); index++) {
            CameraSample previous = plan.samples().get(index - 1);
            CameraSample current = plan.samples().get(index);
            double distance = previous.position().distanceTo(current.position());
            double delta = current.cinematicTimeSeconds() - previous.cinematicTimeSeconds();
            length += distance;
            if (delta > 0.0) maximumSpeed = Math.max(maximumSpeed, distance / delta);
        }
        PathStatistics statistics = new PathStatistics(plan.samples().size(), simplified.size(), length, maximumSpeed,
                plan.request().revolutions());
        return new CameraPathPlan(plan.request(), plan.samples(), simplified, warnings, statistics);
    }
}
