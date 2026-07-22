package pl.peterwolf.cinewolf.camera;

import org.junit.jupiter.api.Test;
import pl.peterwolf.cinewolf.model.Vec3d;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class CollisionStrategyResolverTest {
    private final CollisionStrategyResolver resolver = new CollisionStrategyResolver();

    @Test
    void returnsNoneWhenOriginalSafe() {
        var result = resolver.resolveSample(new Vec3d(0, 2, -5), Vec3d.ZERO, null, 0.2, position -> true);
        assertTrue(result.isPresent());
        assertEquals(CollisionStrategy.NONE, result.get().strategy());
    }

    @Test
    void lateralTranslationAvoidsBlockedOriginal() {
        Set<String> blocked = new HashSet<>();
        blocked.add(key(new Vec3d(0, 2, -5)));
        var result = resolver.resolveSample(new Vec3d(0, 2, -5), Vec3d.ZERO, new Vec3d(0, 2, -4), 0.2,
                position -> !blocked.contains(key(position)) && position.y() >= 0.0);
        assertTrue(result.isPresent());
        assertNotEquals(CollisionStrategy.NONE, result.get().strategy());
        assertTrue(result.get().score() > 0.0);
        assertFalse(result.get().diagnosis().isBlank());
    }

    @Test
    void radiusReductionAndRaiseAreAvailable() {
        var blockedCenter = new Vec3d(5, 2, 0);
        var result = resolver.resolveSample(blockedCenter, Vec3d.ZERO, new Vec3d(4.5, 2, 0), 0.2,
                position -> position.distanceTo(blockedCenter) > 0.5);
        assertTrue(result.isPresent());
        assertTrue(result.get().strategy() == CollisionStrategy.ORBIT_RADIUS_REDUCTION
                || result.get().strategy() == CollisionStrategy.RADIAL_PULL
                || result.get().strategy() == CollisionStrategy.RAISE
                || result.get().strategy() == CollisionStrategy.LATERAL_TRANSLATION
                || result.get().strategy() == CollisionStrategy.PATH_SHORTENING
                || result.get().strategy() == CollisionStrategy.INSERTED_CONTROL_POINTS);
    }

    private static String key(Vec3d value) {
        return String.format(java.util.Locale.ROOT, "%.3f,%.3f,%.3f", value.x(), value.y(), value.z());
    }
}
