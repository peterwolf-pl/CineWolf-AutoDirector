package pl.peterwolf.cinewolf.montage.timeline;

import pl.peterwolf.cinewolf.montage.plan.MontageTimeMapping;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record MontageTimelineWriteRequest(
        UUID montageId,
        long absoluteOutputStartTick,
        List<MontageGeneratedShot> generatedShots,
        List<MontageTimeMapping> timeMappings,
        int keyframeLimit
) {
    public MontageTimelineWriteRequest {
        Objects.requireNonNull(montageId, "montageId");
        generatedShots = List.copyOf(Objects.requireNonNullElse(generatedShots, List.of()));
        timeMappings = List.copyOf(Objects.requireNonNullElse(timeMappings, List.of()));
    }
}
