package pl.peterwolf.cinewolf.config;

import org.junit.jupiter.api.Test;
import pl.peterwolf.cinewolf.model.ShotType;
import pl.peterwolf.cinewolf.shot.ShotGeneratorRegistry;

import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class MontageShotSettingsTest {
    @Test
    void emptyListMeansAllRegisteredTypes() {
        MontageShotSettings settings = new MontageShotSettings();
        settings.normalize();
        Set<ShotType> allowed = settings.resolvedAllowedTypes(ShotGeneratorRegistry.createDefault().supportedTypes());
        assertEquals(ShotGeneratorRegistry.createDefault().supportedTypes(), allowed);
    }

    @Test
    void disablesTypesAndKeepsAtLeastOne() {
        MontageShotSettings settings = new MontageShotSettings();
        settings.setEnabled(ShotType.ORBIT, false);
        assertFalse(settings.isEnabled(ShotType.ORBIT));
        assertTrue(settings.isEnabled(ShotType.FOLLOW));
        settings.enableAll();
        for (ShotType type : ShotType.values()) {
            settings.setEnabled(type, false);
        }
        assertFalse(settings.enabledShotTypes.isEmpty());
        assertTrue(settings.isEnabled(ShotType.FOLLOW));
    }

    @Test
    void clampsGeometryLimits() {
        MontageShotSettings settings = new MontageShotSettings();
        settings.minimumDistance = 40;
        settings.maximumDistance = 10;
        settings.minimumHeight = 30;
        settings.maximumHeight = 1;
        settings.normalize();
        assertTrue(settings.maximumDistance >= settings.minimumDistance);
        assertTrue(settings.maximumHeight >= settings.minimumHeight);
        var prefs = settings.toPreferences(EnumSet.of(ShotType.FOLLOW, ShotType.ORBIT));
        assertEquals(2, prefs.allowedShotTypes().size());
        assertEquals(settings.minimumDistance, prefs.clampDistance(0.1), 1.0e-9);
        assertEquals(settings.maximumDistance, prefs.clampDistance(999), 1.0e-9);
    }
}
