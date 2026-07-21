package pl.peterwolf.cinewolf.montage.analysis;

import pl.peterwolf.cinewolf.model.BoundingBox;
import pl.peterwolf.cinewolf.model.TargetPose;
import pl.peterwolf.cinewolf.model.TargetReference;
import pl.peterwolf.cinewolf.model.Vec3d;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class AnalysisTestFixtures {
    public static final TargetReference PLAYER = new TargetReference(
            UUID.fromString("00000000-0000-0000-0000-000000000001"), "minecraft:player", "Player");
    public static final TargetReference OTHER = new TargetReference(
            UUID.fromString("00000000-0000-0000-0000-000000000002"), "minecraft:zombie", "Zombie");
    public static final UUID VEHICLE = UUID.fromString("00000000-0000-0000-0000-000000000010");

    private AnalysisTestFixtures() {
    }

    public static ReplayEntitySnapshot snapshot(TargetReference target, double x, double y, double z) {
        return state(target, x, y, z, 20.0, 20.0, 0, false, false, true,
                true, 0.0, Optional.empty(), Optional.empty(), false, false, "minecraft:overworld", false);
    }

    public static ReplayEntitySnapshot state(TargetReference target, double x, double y, double z,
                                             double health, double maximumHealth, int hurtTime,
                                             boolean attacking, boolean swinging, boolean alive,
                                             boolean onGround, double groundProximity,
                                             Optional<UUID> vehicleUuid, Optional<String> vehicleType,
                                             boolean creativeFlying, boolean elytraFlying,
                                             String dimension, boolean discontinuity) {
        Vec3d position = new Vec3d(x, y, z);
        BoundingBox box = new BoundingBox(position.add(new Vec3d(-0.3, 0.0, -0.3)),
                position.add(new Vec3d(0.3, 1.8, 0.3)));
        TargetPose pose = new TargetPose(position, position.add(new Vec3d(0.0, 1.6, 0.0)), box,
                0.0, 0.0, Vec3d.ZERO, target.entityType(), vehicleUuid.isPresent(), dimension, discontinuity);
        return new ReplayEntitySnapshot(target, pose, health, maximumHealth, hurtTime, attacking, swinging,
                alive, onGround, groundProximity, vehicleUuid, vehicleType, creativeFlying, elytraFlying);
    }

    public static ReplaySample sample(long tick, ReplayEntitySnapshot... snapshots) {
        java.util.LinkedHashMap<TargetReference, ReplayEntitySnapshot> entities = new java.util.LinkedHashMap<>();
        for (ReplayEntitySnapshot snapshot : snapshots) entities.put(snapshot.target(), snapshot);
        return new ReplaySample(tick, entities, List.of(), List.of());
    }

    public static ReplaySample sample(long tick, Map<TargetReference, ReplayEntitySnapshot> entities,
                                      List<ReplayMarkerSnapshot> markers, List<ObservedReplayAction> actions) {
        return new ReplaySample(tick, entities, markers, actions);
    }
}
