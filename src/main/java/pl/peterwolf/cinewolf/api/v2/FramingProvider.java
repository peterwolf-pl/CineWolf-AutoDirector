package pl.peterwolf.cinewolf.api.v2;

import pl.peterwolf.cinewolf.model.TargetPose;
import pl.peterwolf.cinewolf.model.TargetReference;
import pl.peterwolf.cinewolf.model.Vec3d;
import pl.peterwolf.cinewolf.montage.preset.FramingType;

import java.util.Optional;

/** Custom framing rules for target-aware camera composition. */
public interface FramingProvider {
    String providerId();

    String displayName();

    int priority();

    Optional<FramingHint> resolve(
            TargetReference target,
            TargetPose pose,
            FramingType preferred
    );

    record FramingHint(
            Vec3d focus,
            double distance,
            double height,
            double fov,
            double leadSeconds
    ) {
        public FramingHint {
            distance = Math.max(0.5, Math.min(256.0, distance));
            height = Math.max(-64.0, Math.min(128.0, height));
            fov = Math.max(20.0, Math.min(120.0, fov));
            leadSeconds = Math.max(0.0, Math.min(5.0, leadSeconds));
        }
    }
}
