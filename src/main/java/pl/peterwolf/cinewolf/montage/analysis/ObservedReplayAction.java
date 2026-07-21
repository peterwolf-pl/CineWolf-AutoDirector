package pl.peterwolf.cinewolf.montage.analysis;

import pl.peterwolf.cinewolf.model.TargetReference;
import pl.peterwolf.cinewolf.model.Vec3d;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public sealed interface ObservedReplayAction permits ObservedReplayAction.BlockPlaced,
        ObservedReplayAction.BlockDestroyed, ObservedReplayAction.ProjectileSignal,
        ObservedReplayAction.CombatSignal {
    long replayTime();

    Vec3d location();

    record BlockPlaced(long replayTime, Optional<TargetReference> actor, Vec3d location,
                       String blockType) implements ObservedReplayAction {
        public BlockPlaced {
            validateTimeAndLocation(replayTime, location);
            actor = Objects.requireNonNullElse(actor, Optional.empty());
            blockType = Objects.requireNonNullElse(blockType, "unknown");
        }
    }

    record BlockDestroyed(long replayTime, Optional<TargetReference> actor, Vec3d location,
                          String blockType) implements ObservedReplayAction {
        public BlockDestroyed {
            validateTimeAndLocation(replayTime, location);
            actor = Objects.requireNonNullElse(actor, Optional.empty());
            blockType = Objects.requireNonNullElse(blockType, "unknown");
        }
    }

    record ProjectileSignal(long replayTime, UUID projectileId, ProjectileSignalType signalType,
                            Optional<TargetReference> owner, Optional<TargetReference> target,
                            Vec3d location) implements ObservedReplayAction {
        public ProjectileSignal {
            validateTimeAndLocation(replayTime, location);
            Objects.requireNonNull(projectileId, "projectileId");
            Objects.requireNonNull(signalType, "signalType");
            owner = Objects.requireNonNullElse(owner, Optional.empty());
            target = Objects.requireNonNullElse(target, Optional.empty());
        }
    }

    record CombatSignal(long replayTime, CombatSignalType signalType,
                        Optional<TargetReference> attacker, Optional<TargetReference> victim,
                        Vec3d location, double magnitude) implements ObservedReplayAction {
        public CombatSignal {
            validateTimeAndLocation(replayTime, location);
            Objects.requireNonNull(signalType, "signalType");
            attacker = Objects.requireNonNullElse(attacker, Optional.empty());
            victim = Objects.requireNonNullElse(victim, Optional.empty());
            if (!Double.isFinite(magnitude) || magnitude < 0.0) magnitude = 0.0;
        }
    }

    enum ProjectileSignalType { SPAWN, HIT, DESPAWN }

    enum CombatSignalType { ATTACK, DAMAGE, DEATH }

    private static void validateTimeAndLocation(long replayTime, Vec3d location) {
        if (replayTime < 0) throw new IllegalArgumentException("Action replay time cannot be negative");
        Objects.requireNonNull(location, "location");
    }
}
