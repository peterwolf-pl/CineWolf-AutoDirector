package pl.peterwolf.cinewolf.vehicle;

import org.junit.jupiter.api.Test;
import pl.peterwolf.cinewolf.TestFixtures;
import pl.peterwolf.cinewolf.model.TargetPose;
import pl.peterwolf.cinewolf.model.TargetReference;
import pl.peterwolf.cinewolf.model.Vec3d;
import pl.peterwolf.cinewolf.vehicle.integration.MinecartChainVehicleProvider;
import pl.peterwolf.cinewolf.vehicle.integration.PeterWolfPlanesVehicleProvider;
import pl.peterwolf.cinewolf.vehicle.integration.ZipLineVehicleProvider;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class VehicleProfileTest {
    @Test
    void minecartVanillaProfile() {
        VehicleProviderRegistry registry = VehicleProviderRegistry.createDefault();
        TargetReference target = new TargetReference(UUID.randomUUID(), "minecraft:minecart", "Cart");
        TargetPose pose = poseOf("minecraft:minecart");
        VehicleDescriptor descriptor = registry.requireOrGeneric(target, pose);
        assertEquals(VehicleCategory.MINECART, descriptor.category());
        VehicleProfile profile = VehicleProfile.fromDescriptor(descriptor, pose.velocity(), VehicleState.CRUISING);
        assertTrue(profile.anchor(VehicleAnchorKind.FRONT).isPresent());
    }

    @Test
    void boatAndHorse() {
        VehicleProviderRegistry registry = VehicleProviderRegistry.createDefault();
        assertEquals(VehicleCategory.BOAT,
                registry.requireOrGeneric(new TargetReference(UUID.randomUUID(), "minecraft:boat", "Boat"),
                        poseOf("minecraft:boat")).category());
        assertEquals(VehicleCategory.HORSE,
                registry.requireOrGeneric(new TargetReference(UUID.randomUUID(), "minecraft:horse", "Horse"),
                        poseOf("minecraft:horse")).category());
    }

    @Test
    void genericModdedFallbackNeverThrows() {
        VehicleProviderRegistry registry = VehicleProviderRegistry.createDefault();
        TargetReference target = new TargetReference(UUID.randomUUID(), "mod:unknown_vehicle", "Weird");
        VehicleDescriptor descriptor = registry.requireOrGeneric(target, poseOf("mod:unknown_vehicle"));
        assertNotNull(descriptor);
        assertFalse(descriptor.anchors().isEmpty());
    }

    @Test
    void trainAircraftZiplineProviders() {
        var train = new MinecartChainVehicleProvider();
        var plane = new PeterWolfPlanesVehicleProvider();
        var zip = new ZipLineVehicleProvider();
        TargetPose pose = poseOf("entity");
        TargetReference trainTarget = new TargetReference(UUID.randomUUID(), "peterwolf:minecart_chain_locomotive", "Loco");
        assertTrue(train.supports(trainTarget, pose));
        assertEquals(VehicleCategory.TRAIN, train.describe(trainTarget, pose).orElseThrow().category());
        TargetReference planeTarget = new TargetReference(UUID.randomUUID(), "simpleplanes:airplane", "Plane");
        assertTrue(plane.supports(planeTarget, pose));
        assertEquals(VehicleCategory.AIRCRAFT, plane.describe(planeTarget, pose).orElseThrow().category());
        TargetReference zipTarget = new TargetReference(UUID.randomUUID(), "mod:zipline_rider", "Zip");
        assertTrue(zip.supports(zipTarget, pose));
        assertEquals(VehicleCategory.ZIP_LINE, zip.describe(zipTarget, pose).orElseThrow().category());
    }

    private static TargetPose poseOf(String type) {
        TargetPose base = TestFixtures.pose(Vec3d.ZERO, new Vec3d(1, 0, 0), 0);
        return new TargetPose(base.position(), base.focusPosition(), base.boundingBox(), base.yaw(), base.pitch(),
                base.velocity(), type, true, base.dimension(), false);
    }
}
