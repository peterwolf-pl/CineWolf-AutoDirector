package pl.peterwolf.cinewolf.camera;

import pl.peterwolf.cinewolf.api.TargetPoseResolver;
import pl.peterwolf.cinewolf.model.TargetPose;
import pl.peterwolf.cinewolf.model.TargetReference;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Resolves poses for multiple tracked entities (subject + 3rd-person camera host). */
public final class MultiSampledTargetPoseResolver implements TargetPoseResolver {
    private final Map<UUID, SampledTargetPoseResolver> byTarget;

    public MultiSampledTargetPoseResolver(Map<UUID, Map<Long, TargetPose>> posesByTarget) {
        Map<UUID, SampledTargetPoseResolver> built = new HashMap<>();
        if (posesByTarget != null) {
            posesByTarget.forEach((uuid, poses) -> {
                if (uuid != null && poses != null && !poses.isEmpty()) {
                    built.put(uuid, new SampledTargetPoseResolver(poses));
                }
            });
        }
        this.byTarget = Map.copyOf(built);
    }

    @Override
    public Optional<TargetPose> resolve(TargetReference target, long replayTime) {
        if (target == null) return Optional.empty();
        SampledTargetPoseResolver resolver = byTarget.get(target.uuid());
        return resolver == null ? Optional.empty() : resolver.resolve(target, replayTime);
    }

    public boolean hasTarget(UUID uuid) {
        return uuid != null && byTarget.containsKey(uuid);
    }

    public int targetCount() {
        return byTarget.size();
    }

    public static MultiSampledTargetPoseResolver ofSingle(UUID uuid, Map<Long, TargetPose> poses) {
        Objects.requireNonNull(uuid, "uuid");
        return new MultiSampledTargetPoseResolver(Map.of(uuid, poses == null ? Map.of() : poses));
    }
}
