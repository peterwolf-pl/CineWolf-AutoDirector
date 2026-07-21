package pl.peterwolf.cinewolf.montage.plan;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record MontageTransition(
        UUID fromShotId,
        UUID toShotId,
        MontageTransitionType type,
        double outputTimeSeconds,
        List<String> planningReasons
) {
    public MontageTransition {
        Objects.requireNonNull(fromShotId, "fromShotId");
        Objects.requireNonNull(toShotId, "toShotId");
        Objects.requireNonNull(type, "type");
        if (!Double.isFinite(outputTimeSeconds) || outputTimeSeconds < 0.0) {
            throw new IllegalArgumentException("outputTimeSeconds must be finite and non-negative");
        }
        planningReasons = List.copyOf(Objects.requireNonNullElse(planningReasons, List.of()));
    }
}
