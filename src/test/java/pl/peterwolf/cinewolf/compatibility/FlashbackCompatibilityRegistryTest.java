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
    void supportedPinnedVersion() {
        var assessment = FlashbackCompatibilityRegistry.assess(Optional.of("0.43.4"));
        assertEquals(CompatibilityLevel.SUPPORTED, assessment.level());
        assertTrue(assessment.editorIntegrationEnabled());
        assertTrue(assessment.capabilities().supportsMontageWriting());
        assertTrue(assessment.capabilities().entityTracking());
        assertTrue(assessment.capabilities().rollKeyframes());
    }

    @Test
    void supportedLegacyValidatedPin() {
        var assessment = FlashbackCompatibilityRegistry.assess(Optional.of("0.42.1"));
        assertEquals(CompatibilityLevel.SUPPORTED, assessment.level());
        assertTrue(assessment.editorIntegrationEnabled());
        assertTrue(assessment.capabilities().supportsMontageWriting());
    }

    @Test
    void supportedNewerPatchesAndMinors() {
        for (String version : new String[]{"0.42.2", "0.43.0", "0.43.4", "0.43.5", "0.44.0"}) {
            var assessment = FlashbackCompatibilityRegistry.assess(Optional.of(version));
            assertEquals(CompatibilityLevel.SUPPORTED, assessment.level(), version);
            assertTrue(assessment.editorIntegrationEnabled(), version);
        }
    }

    @Test
    void experimentalNearbyPatch() {
        var assessment = FlashbackCompatibilityRegistry.assess(Optional.of("0.42.0"));
        assertEquals(CompatibilityLevel.EXPERIMENTAL, assessment.level());
        assertFalse(assessment.editorIntegrationEnabled());
    }

    @Test
    void legacy041LineIsExperimental() {
        var assessment = FlashbackCompatibilityRegistry.assess(Optional.of("0.41.1"));
        assertEquals(CompatibilityLevel.EXPERIMENTAL, assessment.level());
        assertFalse(assessment.editorIntegrationEnabled());
        assertTrue(assessment.capabilities().supportsCameraWriting());
    }

    @Test
    void unsupportedMajor() {
        var assessment = FlashbackCompatibilityRegistry.assess(Optional.of("1.0.0"));
        assertEquals(CompatibilityLevel.UNSUPPORTED, assessment.level());
        assertFalse(assessment.editorIntegrationEnabled());
        assertTrue(assessment.failureMessage().contains("1.0.0"));
        assertTrue(assessment.failureMessage().contains("0.42.1"));
        assertTrue(assessment.failureMessage().contains("0.43.4"));
    }

    @Test
    void versionRangeContains() {
        assertTrue(VersionRange.exact("0.42.1").contains("0.42.1"));
        assertFalse(VersionRange.exact("0.42.1").contains("0.42.0"));
        assertTrue(new VersionRange("0.41.0", "0.42.9").contains("0.42.1"));
        assertTrue(VersionRange.atLeast("0.42.1").contains("0.43.4"));
        assertTrue(VersionRange.atLeast("0.42.1").contains("0.44.0"));
        assertFalse(VersionRange.atLeast("0.42.1").contains("0.42.0"));
        assertEquals("0.42.1+", VersionRange.atLeast("0.42.1").display());
    }
}
