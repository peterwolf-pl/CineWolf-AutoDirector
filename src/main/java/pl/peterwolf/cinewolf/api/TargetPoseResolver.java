package pl.peterwolf.cinewolf.api;

import pl.peterwolf.cinewolf.model.TargetPose;
import pl.peterwolf.cinewolf.model.TargetReference;

import java.util.Optional;

@FunctionalInterface
public interface TargetPoseResolver {
    Optional<TargetPose> resolve(TargetReference target, long replayTime);
}
