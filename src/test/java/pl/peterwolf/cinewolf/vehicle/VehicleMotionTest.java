package pl.peterwolf.cinewolf.vehicle;

import org.junit.jupiter.api.Test;
import pl.peterwolf.cinewolf.TestFixtures;
import pl.peterwolf.cinewolf.model.TargetPose;
import pl.peterwolf.cinewolf.model.TargetReference;
import pl.peterwolf.cinewolf.model.Vec3d;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class VehicleMotionTest {
    @Test
    void prefersTravelVelocityOverPassengerLookYaw() {
        // Player looking east (yaw 270) while plane flies north (+Z) at speed.
        TargetReference player = new TargetReference(UUID.randomUUID(), "minecraft:player", "Pilot");
        TargetPose pose = new TargetPose(
                new Vec3d(0, 80, 0),
                new Vec3d(0, 81.6, 0),
                TestFixtures.pose(Vec3d.ZERO, Vec3d.ZERO, 0).boundingBox(),
                270.0, // free-look sideways
                0.0,
                new Vec3d(0.0, 0.0, 12.0), // actual flight north
                "peterwolf_planes:fighter",
                true,
                "minecraft:overworld",
                false);

        Vec3d forward = VehicleMotion.resolveForward(player, pose);
        assertTrue(forward.z() > 0.9, "forward should follow flight +Z, got " + forward);
        assertTrue(Math.abs(forward.x()) < 0.25, "must not use free-look east, got " + forward);
    }

    @Test
    void aircraftUsesPitchInBodyForwardWhenNearlyStationary() {
        TargetReference plane = new TargetReference(UUID.randomUUID(), "simpleplanes:plane", "Plane");
        TargetPose pose = new TargetPose(
                new Vec3d(0, 90, 0),
                new Vec3d(0, 91, 0),
                TestFixtures.pose(Vec3d.ZERO, Vec3d.ZERO, 0).boundingBox(),
                0.0,
                -30.0, // nose up
                Vec3d.ZERO,
                "simpleplanes:plane",
                true,
                "minecraft:overworld",
                false);

        Vec3d forward = VehicleMotion.resolveForward(plane, pose);
        assertTrue(forward.y() > 0.4, "nose-up body forward should climb, got " + forward);
        assertTrue(VehicleMotion.isAircraftLike(plane, pose));
    }

    @Test
    void passengerWithPlanePoseTypeResolvesAsAircraft() {
        TargetReference player = new TargetReference(UUID.randomUUID(), "minecraft:player", "Pilot");
        TargetPose pose = new TargetPose(
                Vec3d.ZERO,
                new Vec3d(0, 1.6, 0),
                TestFixtures.pose(Vec3d.ZERO, Vec3d.ZERO, 0).boundingBox(),
                45.0,
                -10.0,
                new Vec3d(4.0, 1.0, 4.0),
                "peterwolf_planes:cessna",
                true,
                "minecraft:overworld",
                false);
        assertTrue(VehicleMotion.isAircraftLike(player, pose));
        VehicleDescriptor descriptor = VehicleProviderRegistry.createDefault().requireOrGeneric(player, pose);
        assertEquals(VehicleCategory.AIRCRAFT, descriptor.category());
        assertTrue(descriptor.forward().length() > 0.9);
        // Forward should align more with velocity (NE + climb) than pure yaw.
        assertTrue(descriptor.forward().y() > 0.05);
    }
}
