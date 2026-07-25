package pl.peterwolf.cinewolf.compatibility;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class FlashbackCompatibilityRegistryTest {
    @Test
    void missingFlashback() {
        var assessment = FlashbackCompatibilityRegistry.assess(Optional.empty());
        assertEquals(CompatibilityLevel.MISSING, assessment.level());
        assertFalse(assessment.editorIntegrationEnabled());
        assertFalse(assessment.capabilities().supportsCameraWriting());
    }

    @Test
    void supportedVersion() {
        var assessment = FlashbackCompatibilityRegistry.assess(Optional.of("0.41.1"));
        assertEquals(CompatibilityLevel.SUPPORTED, assessment.level());
        assertTrue(assessment.editorIntegrationEnabled());
        assertTrue(assessment.capabilities().supportsMontageWriting());
        assertTrue(assessment.capabilities().entityTracking());
        assertTrue(assessment.capabilities().rollKeyframes());
    }

    @Test
    void experimentalNearbyPatch() {
        var assessment = FlashbackCompatibilityRegistry.assess(Optional.of("0.41.2"));
        assertEquals(CompatibilityLevel.EXPERIMENTAL, assessment.level());
        assertFalse(assessment.editorIntegrationEnabled());
    }

    @Test
    void unsupportedMajor() {
        var assessment = FlashbackCompatibilityRegistry.assess(Optional.of("0.50.0"));
        assertEquals(CompatibilityLevel.UNSUPPORTED, assessment.level());
        assertFalse(assessment.editorIntegrationEnabled());
        assertTrue(assessment.failureMessage().contains("0.50.0"));
    }

    @Test
    void versionRangeContains() {
        assertTrue(VersionRange.exact("0.41.1").contains("0.41.1"));
        assertFalse(VersionRange.exact("0.41.1").contains("0.41.2"));
        assertTrue(new VersionRange("0.40.0", "0.41.9").contains("0.41.1"));
    }
}
