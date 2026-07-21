package pl.peterwolf.cinewolf.montage.timeline;

import pl.peterwolf.cinewolf.model.CameraPathPlan;

import java.util.Objects;

/** A generated camera path placed at a relative time in the montage output. */
public record MontageGeneratedShot(double outputStartSeconds, CameraPathPlan path) {
    public MontageGeneratedShot {
        Objects.requireNonNull(path, "path");
    }
}
