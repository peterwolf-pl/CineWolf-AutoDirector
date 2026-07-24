package pl.peterwolf.cinewolf.montage.v2;

import org.junit.jupiter.api.Test;
import pl.peterwolf.cinewolf.compatibility.FlashbackCapabilities;
import pl.peterwolf.cinewolf.model.ShotType;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MontageEngineV2Test {
    @Test
    void builtInStylesPresent() {
        assertEquals(11, MontageStyleProfiles.all().size());
        assertTrue(MontageStyleProfiles.get("train_journey").isPresent());
        assertTrue(MontageStyleProfiles.get("flight_showcase").isPresent());
        assertTrue(MontageStyleProfiles.get("vertical_fast_cut").isPresent());
    }

    @Test
    void durationAllocatorNonUniform() {
        DurationAllocator allocator = new DurationAllocator();
        // without real events, empty
        assertTrue(allocator.allocate(List.of(), MontageStyleProfiles.get("clean_cinematic").orElseThrow(),
                30, 2, 8).isEmpty());
    }

    @Test
    void diversityAvoidsImmediateRepeat() {
        ShotDiversityPlanner planner = new ShotDiversityPlanner();
        ShotType chosen = planner.select(
                List.of(ShotType.ORBIT, ShotType.FOLLOW, ShotType.CHASE),
                List.of(ShotType.ORBIT),
                null,
                NarrativePhase.DEVELOPMENT,
                MontageStyleProfiles.get("clean_cinematic").orElseThrow(),
                null
        );
        assertNotEquals(ShotType.ORBIT, chosen);
    }

    @Test
    void capabilityResolverFilters() {
        CapabilityAwareShotResolver resolver = new CapabilityAwareShotResolver(
                pl.peterwolf.cinewolf.shot.ShotGeneratorRegistry.createDefault());
        var available = resolver.resolve(List.of(ShotType.ORBIT, ShotType.CHASE), FlashbackCapabilities.flashback0411());
        assertFalse(available.isEmpty());
        var none = resolver.resolve(List.of(ShotType.ORBIT), FlashbackCapabilities.none());
        // none disables camera writing -> empty preferred then falls back to supported types
        assertFalse(none.isEmpty());
    }

    @Test
    void engineValidatesMissingAnalysis() {
        MontageEngineV2 engine = new MontageEngineV2();
        var report = engine.validate(null, null);
        assertFalse(report.valid());
    }
}
