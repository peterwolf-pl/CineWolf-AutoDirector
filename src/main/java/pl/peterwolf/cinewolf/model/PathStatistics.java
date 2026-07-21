package pl.peterwolf.cinewolf.model;

public record PathStatistics(
        int previewSamples,
        int simplifiedKeyframes,
        double pathLength,
        double maximumSpeed,
        double revolutions
) {
}
