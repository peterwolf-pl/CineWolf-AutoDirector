package pl.peterwolf.cinewolf.montage.v2;

import pl.peterwolf.cinewolf.model.ShotType;
import pl.peterwolf.cinewolf.montage.preset.MontagePacing;

import java.util.Objects;
import java.util.Set;

public record MontageStyleProfile(
        String id,
        MontagePacing pacing,
        CameraMovementIntensity movementIntensity,
        ShotLengthProfile shotLengthProfile,
        FramingDistribution framingDistribution,
        Set<ShotType> preferredShots,
        Set<ShotType> restrictedShots,
        TransitionStyle transitionStyle,
        FovStyle fovStyle,
        CollisionPolicy collisionPolicy,
        boolean preferChronology,
        boolean allowReplaySpeedChanges
) {
    public MontageStyleProfile {
        id = Objects.requireNonNull(id, "id");
        pacing = pacing == null ? MontagePacing.MODERATE : pacing;
        movementIntensity = movementIntensity == null ? CameraMovementIntensity.MEDIUM : movementIntensity;
        shotLengthProfile = shotLengthProfile == null ? ShotLengthProfile.MIXED : shotLengthProfile;
        framingDistribution = framingDistribution == null ? FramingDistribution.BALANCED : framingDistribution;
        preferredShots = Set.copyOf(preferredShots == null ? Set.of() : preferredShots);
        restrictedShots = Set.copyOf(restrictedShots == null ? Set.of() : restrictedShots);
        transitionStyle = transitionStyle == null ? TransitionStyle.HARD_CUT_ONLY : transitionStyle;
        fovStyle = fovStyle == null ? FovStyle.STABLE : fovStyle;
        collisionPolicy = collisionPolicy == null ? CollisionPolicy.BALANCED : collisionPolicy;
    }
}
