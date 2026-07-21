package pl.peterwolf.cinewolf.model;

import java.util.List;
import java.util.Objects;

public record CameraPathPlan(
        ShotRequest request,
        List<CameraSample> samples,
        List<CameraSample> simplifiedSamples,
        List<PathWarning> warnings,
        PathStatistics statistics
) {
    public CameraPathPlan {
        Objects.requireNonNull(request, "request");
        samples = List.copyOf(samples);
        simplifiedSamples = List.copyOf(simplifiedSamples);
        warnings = List.copyOf(warnings);
        Objects.requireNonNull(statistics, "statistics");
    }

    public boolean valid() {
        return samples.size() >= 2 && warnings.stream().noneMatch(w -> w.severity() == PathWarning.Severity.ERROR);
    }
}
