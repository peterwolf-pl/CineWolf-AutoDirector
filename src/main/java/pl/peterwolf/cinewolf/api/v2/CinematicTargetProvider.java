package pl.peterwolf.cinewolf.api.v2;

import pl.peterwolf.cinewolf.model.TargetPose;
import pl.peterwolf.cinewolf.model.TargetReference;
import pl.peterwolf.cinewolf.model.target.CinematicTarget;

import java.util.List;
import java.util.Optional;

/** Provides custom cinematic targets derived from replay entities or structures. */
public interface CinematicTargetProvider {
    String providerId();

    String displayName();

    int priority();

    List<CinematicTarget> discover(TargetDiscoveryContext context);

    Optional<CinematicTarget> resolve(TargetReference reference, TargetPose pose);

    record TargetDiscoveryContext(
            long replayTime,
            List<TargetReference> entities,
            int maxTargets
    ) {
        public TargetDiscoveryContext {
            entities = List.copyOf(entities == null ? List.of() : entities);
            maxTargets = Math.max(1, Math.min(512, maxTargets));
        }
    }
}
