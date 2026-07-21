package pl.peterwolf.cinewolf.montage.analysis;

import java.util.List;
import java.util.Objects;

public record SampleSelection(
        List<ReplaySample> coarseSamples,
        List<ReplaySample> detailedSamples,
        List<ReplaySample> combinedSamples,
        List<ReplayTimeWindow> detailedWindows
) {
    public SampleSelection {
        coarseSamples = List.copyOf(Objects.requireNonNullElse(coarseSamples, List.of()));
        detailedSamples = List.copyOf(Objects.requireNonNullElse(detailedSamples, List.of()));
        combinedSamples = List.copyOf(Objects.requireNonNullElse(combinedSamples, List.of()));
        detailedWindows = List.copyOf(Objects.requireNonNullElse(detailedWindows, List.of()));
    }
}
