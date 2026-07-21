package pl.peterwolf.cinewolf.montage.plan;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MontageTimeMappingValidatorTest {
    private final MontageTimeMappingValidator validator = new MontageTimeMappingValidator();

    @Test
    void acceptsContiguousMonotonicMapping() {
        List<MontageTimeMapping> mappings = List.of(
                MontageTimeMapping.between(0.0, 5.0, 100, 200),
                MontageTimeMapping.between(5.0, 10.0, 200, 300));

        MontageTimeMappingValidator.ValidationResult result = validator.validate(mappings, 10.0, 0.5, 2.0, 1.0);

        assertTrue(result.valid(), () -> result.errors().toString());
    }

    @Test
    void rejectsBackwardSourceMapping() {
        List<MontageTimeMapping> mappings = List.of(
                MontageTimeMapping.between(0.0, 5.0, 200, 300),
                MontageTimeMapping.between(5.0, 10.0, 100, 200));

        MontageTimeMappingValidator.ValidationResult result = validator.validate(mappings, 10.0, 0.1, 4.0, 4.0);

        assertFalse(result.valid());
        assertTrue(result.errors().contains("montage.mapping.source_not_monotonic"));
    }

    @Test
    void rejectsDeclaredSpeedThatDoesNotMatchEndpoints() {
        MontageTimeMapping invalid = new MontageTimeMapping(0.0, 5.0, 0, 100, 2.0);

        MontageTimeMappingValidator.ValidationResult result = validator.validate(List.of(invalid), 5.0, 0.1, 4.0, 4.0);

        assertFalse(result.valid());
        assertTrue(result.errors().contains("montage.mapping.speed_mismatch"));
    }

    @Test
    void rejectsExcessiveAdjacentSpeedRamp() {
        List<MontageTimeMapping> mappings = List.of(
                MontageTimeMapping.between(0.0, 5.0, 0, 100),
                MontageTimeMapping.between(5.0, 10.0, 100, 500));

        MontageTimeMappingValidator.ValidationResult result = validator.validate(mappings, 10.0, 0.1, 5.0, 1.0);

        assertFalse(result.valid());
        assertTrue(result.errors().contains("montage.mapping.speed_change_too_large"));
    }
}
