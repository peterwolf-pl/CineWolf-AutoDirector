package pl.peterwolf.cinewolf.vehicle;

import org.junit.jupiter.api.Test;
import pl.peterwolf.cinewolf.TestFixtures;
import pl.peterwolf.cinewolf.model.TargetPose;
import pl.peterwolf.cinewolf.model.TargetReference;
import pl.peterwolf.cinewolf.model.Vec3d;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class VehicleProviderRegistryTest {
    @Test
    void builtinRecognizesMinecart() {
        VehicleProviderRegistry registry = VehicleProviderRegistry.createDefault();
        TargetReference target = new TargetReference(UUID.randomUUID(), "minecraft:minecart", "Cart");
        TargetPose pose = TestFixtures.pose(Vec3d.ZERO, new Vec3d(1, 0, 0), 0);
        pose = new TargetPose(pose.position(), pose.focusPosition(), pose.boundingBox(), pose.yaw(), pose.pitch(),
                pose.velocity(), "minecraft:minecart", true, pose.dimension(), false);
        VehicleDescriptor descriptor = registry.requireOrGeneric(target, pose);
        assertEquals(VehicleCategory.MINECART, descriptor.category());
        assertFalse(descriptor.anchors().isEmpty());
        assertTrue(descriptor.anchor(VehicleAnchorKind.FRONT).isPresent());
    }

    @Test
    void softProviderRecognizesPlaneNamespace() {
        SoftModVehicleProvider provider = new SoftModVehicleProvider();
        TargetReference target = new TargetReference(UUID.randomUUID(), "peterwolf_planes:fighter", "Plane");
        TargetPose pose = TestFixtures.pose(Vec3d.ZERO, new Vec3d(0, 0, 1), 0);
        pose = new TargetPose(pose.position(), pose.focusPosition(), pose.boundingBox(), pose.yaw(), pose.pitch(),
                pose.velocity(), "peterwolf_planes:fighter", true, pose.dimension(), false);
        assertTrue(provider.supports(target, pose));
        assertEquals(VehicleCategory.AIRCRAFT, provider.describe(target, pose).orElseThrow().category());
    }
}
