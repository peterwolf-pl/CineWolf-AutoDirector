package pl.peterwolf.cinewolf.model;

import pl.peterwolf.cinewolf.api.TargetPoseResolver;

import java.util.List;
import java.util.Objects;

public record ReplayContext(TargetPoseResolver targetPoseResolver, SamplingSettings samplingSettings,
                            List<Long> adaptiveReplayTicks) {
    public ReplayContext(TargetPoseResolver targetPoseResolver, SamplingSettings samplingSettings) {
        this(targetPoseResolver, samplingSettings, List.of());
    }

    public ReplayContext {
        Objects.requireNonNull(targetPoseResolver, "targetPoseResolver");
        samplingSettings = Objects.requireNonNullElseGet(samplingSettings, SamplingSettings::defaults);
        adaptiveReplayTicks = List.copyOf(Objects.requireNonNullElse(adaptiveReplayTicks, List.of()));
    }
}
