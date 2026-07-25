package pl.peterwolf.cinewolf.montage.plan;

import pl.peterwolf.cinewolf.model.SamplingSettings;
import pl.peterwolf.cinewolf.model.ShotType;
import pl.peterwolf.cinewolf.config.ShotDiversityConfig;

import java.util.Objects;
import java.util.Set;

public record MontagePlanningContext(Set<ShotType> availableShotTypes, SamplingSettings samplingSettings,
                                     ShotDiversityProfile shotDiversity,
                                     boolean thirdPersonTracking,
                                     double thirdPersonHeight) {
    public MontagePlanningContext(Set<ShotType> availableShotTypes, SamplingSettings samplingSettings) {
        this(availableShotTypes, samplingSettings, ShotDiversityProfile.defaults(), false, 0.0);
    }

    public MontagePlanningContext(Set<ShotType> availableShotTypes, SamplingSettings samplingSettings,
                                  ShotDiversityConfig shotDiversity) {
        this(availableShotTypes, samplingSettings, ShotDiversityProfile.from(shotDiversity), false, 0.0);
    }

    public MontagePlanningContext(Set<ShotType> availableShotTypes, SamplingSettings samplingSettings,
                                  ShotDiversityProfile shotDiversity) {
        this(availableShotTypes, samplingSettings, shotDiversity, false, 0.0);
    }

    /** Back-compat: tracking flag without height. */
    public MontagePlanningContext(Set<ShotType> availableShotTypes, SamplingSettings samplingSettings,
                                  ShotDiversityProfile shotDiversity, boolean thirdPersonTracking) {
        this(availableShotTypes, samplingSettings, shotDiversity, thirdPersonTracking, 0.0);
    }

    public MontagePlanningContext {
        availableShotTypes = Set.copyOf(Objects.requireNonNullElse(availableShotTypes, Set.of()));
        if (availableShotTypes.isEmpty()) throw new IllegalArgumentException("No shot generators are available");
        Objects.requireNonNull(samplingSettings, "samplingSettings");
        shotDiversity = Objects.requireNonNullElse(shotDiversity, ShotDiversityProfile.defaults());
        if (!Double.isFinite(thirdPersonHeight)) thirdPersonHeight = 0.0;
        thirdPersonHeight = Math.max(-2.25, Math.min(2.25, thirdPersonHeight));
    }

    public MontagePlanningContext withThirdPersonTracking(boolean enabled) {
        return new MontagePlanningContext(availableShotTypes, samplingSettings, shotDiversity, enabled,
                thirdPersonHeight);
    }

    public MontagePlanningContext withThirdPersonHeight(double height) {
        return new MontagePlanningContext(availableShotTypes, samplingSettings, shotDiversity,
                thirdPersonTracking, height);
    }
}
