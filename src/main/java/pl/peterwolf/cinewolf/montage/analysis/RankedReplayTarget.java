package pl.peterwolf.cinewolf.montage.analysis;

import pl.peterwolf.cinewolf.model.TargetReference;

import java.util.List;
import java.util.Objects;

public record RankedReplayTarget(
        TargetReference target,
        double score,
        long visibleDurationTicks,
        double movementDistance,
        int importantEventCount,
        List<String> scoringReasons
) {
    public RankedReplayTarget {
        Objects.requireNonNull(target, "target");
        score = Double.isFinite(score) ? Math.max(0.0, score) : 0.0;
        visibleDurationTicks = Math.max(0L, visibleDurationTicks);
        movementDistance = Double.isFinite(movementDistance) ? Math.max(0.0, movementDistance) : 0.0;
        importantEventCount = Math.max(0, importantEventCount);
        scoringReasons = List.copyOf(Objects.requireNonNullElse(scoringReasons, List.of()));
    }
}
