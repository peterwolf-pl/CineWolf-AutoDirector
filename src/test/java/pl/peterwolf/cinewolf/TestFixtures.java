package pl.peterwolf.cinewolf;

import pl.peterwolf.cinewolf.api.TargetPoseResolver;
import pl.peterwolf.cinewolf.model.BoundingBox;
import pl.peterwolf.cinewolf.model.EasingType;
import pl.peterwolf.cinewolf.model.ReplayContext;
import pl.peterwolf.cinewolf.model.RotationDirection;
import pl.peterwolf.cinewolf.model.SamplingSettings;
import pl.peterwolf.cinewolf.model.ShotRequest;
import pl.peterwolf.cinewolf.model.ShotType;
import pl.peterwolf.cinewolf.model.TargetPose;
import pl.peterwolf.cinewolf.model.TargetReference;
import pl.peterwolf.cinewolf.model.Vec3d;

import java.util.Optional;
import java.util.UUID;
import java.util.function.LongFunction;

public final class TestFixtures {
    public static final TargetReference TARGET = new TargetReference(new UUID(1L, 2L), "minecraft:player", "CameraTarget");

    private TestFixtures() {
    }

    public static ShotRequest request(ShotType type) {
        return new ShotRequest(TARGET, type, 12.0, 3.0, 8.0, 16.0, 3.0, 0.5, 10.0,
                0.0, type == ShotType.FLYBY ? RotationDirection.LEFT_TO_RIGHT : RotationDirection.CLOCKWISE,
                4.0, 70.0, EasingType.LINEAR, 0.0, 0L, 200L);
    }

    public static ShotRequest request(ShotType type, double rpm, double duration, RotationDirection direction,
                                      long start, long end) {
        ShotRequest base = request(type);
        return new ShotRequest(base.target(), type, base.diameter(), base.height(), base.distance(),
                base.startDistance(), base.endDistance(), rpm, duration, base.startAngleDegrees(), direction,
                base.cameraSpeed(), base.fov(), base.easing(), base.lookAheadSeconds(), start, end);
    }

    public static ReplayContext context(LongFunction<TargetPose> poses) {
        TargetPoseResolver resolver = (target, tick) -> Optional.ofNullable(poses.apply(tick));
        return new ReplayContext(resolver, new SamplingSettings(12, 4096, 512, 0.08, 0.45, 0.1, 1.0));
    }

    public static TargetPose pose(Vec3d position, Vec3d velocity, double yaw) {
        Vec3d focus = position.add(new Vec3d(0.0, 1.62, 0.0));
        return new TargetPose(position, focus,
                new BoundingBox(position.add(new Vec3d(-0.3, 0.0, -0.3)), position.add(new Vec3d(0.3, 1.8, 0.3))),
                yaw, 0.0, velocity, "minecraft:player", false, "minecraft:overworld", false);
    }
}
