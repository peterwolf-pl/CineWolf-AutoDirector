package pl.peterwolf.cinewolf.shot;

import org.junit.jupiter.api.Test;
import pl.peterwolf.cinewolf.model.ShotType;

import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShotGeneratorRegistryTest {
    @Test
    void defaultRegistrySupportsEveryExistingShotType() {
        ShotGeneratorRegistry registry = ShotGeneratorRegistry.createDefault();

        assertEquals(EnumSet.allOf(ShotType.class), registry.supportedTypes());
        for (ShotType type : ShotType.values()) {
            assertTrue(registry.supports(type));
            assertNotNull(registry.require(type));
        }
        assertFalse(registry.supports(null));
    }

    @Test
    void supportedTypesIsAnImmutableSnapshot() {
        ShotGeneratorRegistry registry = new ShotGeneratorRegistry()
                .register(ShotType.ORBIT, new OrbitShotGenerator());
        Set<ShotType> snapshot = registry.supportedTypes();

        assertEquals(Set.of(ShotType.ORBIT), snapshot);
        assertThrows(UnsupportedOperationException.class, () -> snapshot.add(ShotType.FOLLOW));

        registry.register(ShotType.FOLLOW, new FollowShotGenerator());
        assertEquals(Set.of(ShotType.ORBIT), snapshot);
        assertEquals(EnumSet.of(ShotType.ORBIT, ShotType.FOLLOW), registry.supportedTypes());
    }

    @Test
    void emptyRegistryReportsNoSupportAndRequireStillFailsClearly() {
        ShotGeneratorRegistry registry = new ShotGeneratorRegistry();

        assertTrue(registry.supportedTypes().isEmpty());
        assertFalse(registry.supports(ShotType.FLYBY));
        assertThrows(IllegalArgumentException.class, () -> registry.require(ShotType.FLYBY));
    }
}
