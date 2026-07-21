package pl.peterwolf.cinewolf.montage.plan;

import pl.peterwolf.cinewolf.model.SamplingSettings;
import pl.peterwolf.cinewolf.model.ShotType;
import pl.peterwolf.cinewolf.config.ShotDiversityConfig;

import java.util.Objects;
import java.util.Set;

public record MontagePlanningContext(Set<ShotType> availableShotTypes, SamplingSettings samplingSettings,
                                     ShotDiversityProfile shotDiversity) {
    public MontagePlanningContext(Set<ShotType> availableShotTypes, SamplingSettings samplingSettings) {
        this(availableShotTypes, samplingSettings, ShotDiversityProfile.defaults());
    }

    public MontagePlanningContext(Set<ShotType> availableShotTypes, SamplingSettings samplingSettings,
                                  ShotDiversityConfig shotDiversity) {
        this(availableShotTypes, samplingSettings, ShotDiversityProfile.from(shotDiversity));
    }

    public MontagePlanningContext {
        availableShotTypes = Set.copyOf(Objects.requireNonNullElse(availableShotTypes, Set.of()));
        if (availableShotTypes.isEmpty()) throw new IllegalArgumentException("No shot generators are available");
        Objects.requireNonNull(samplingSettings, "samplingSettings");
        shotDiversity = Objects.requireNonNullElse(shotDiversity, ShotDiversityProfile.defaults());
    }
}
