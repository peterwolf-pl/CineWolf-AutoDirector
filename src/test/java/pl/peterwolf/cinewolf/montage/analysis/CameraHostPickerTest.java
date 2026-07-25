package pl.peterwolf.cinewolf.montage.analysis;

import org.junit.jupiter.api.Test;
import pl.peterwolf.cinewolf.TestFixtures;
import pl.peterwolf.cinewolf.model.TargetReference;
import pl.peterwolf.cinewolf.model.Vec3d;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CameraHostPickerTest {
    @Test
    void picksNearestOtherPlayer() {
        TargetReference subject = TestFixtures.TARGET;
        UUID hostId = UUID.nameUUIDFromBytes("other-player".getBytes());
        TargetReference host = new TargetReference(hostId, "minecraft:player", "Other");
        UUID farId = UUID.nameUUIDFromBytes("far-player".getBytes());
        TargetReference far = new TargetReference(farId, "minecraft:player", "Far");

        List<ReplaySample> samples = List.of(
                sample(0, subject, host, far, new Vec3d(0, 64, 0), new Vec3d(3, 64, 0), new Vec3d(40, 64, 0)),
                sample(20, subject, host, far, new Vec3d(0, 64, 0), new Vec3d(3, 64, 0), new Vec3d(40, 64, 0))
        );
        ReplayAnalysisResult analysis = analysis(samples);

        Optional<TargetReference> picked = CameraHostPicker.pick(analysis, subject, 0, 20);
        assertTrue(picked.isPresent());
        assertEquals(hostId, picked.get().uuid());
    }

    @Test
    void fallsBackToNonPlayerWhenNoPlayerHost() {
        TargetReference subject = TestFixtures.TARGET;
        UUID npcId = UUID.nameUUIDFromBytes("villager".getBytes());
        TargetReference npc = new TargetReference(npcId, "minecraft:villager", "Villager");

        List<ReplaySample> samples = List.of(
                sampleTwo(0, subject, npc, new Vec3d(0, 64, 0), new Vec3d(2, 64, 0)),
                sampleTwo(10, subject, npc, new Vec3d(0, 64, 0), new Vec3d(2, 64, 0))
        );
        ReplayAnalysisResult analysis = analysis(samples);

        Optional<TargetReference> picked = CameraHostPicker.pick(analysis, subject, 0, 10);
        assertTrue(picked.isPresent());
        assertEquals(npcId, picked.get().uuid());
    }

    private static ReplaySample sample(long tick, TargetReference subject, TargetReference host, TargetReference far,
                                       Vec3d subjectPos, Vec3d hostPos, Vec3d farPos) {
        ReplayEntitySnapshot s = ReplayEntitySnapshot.basic(subject, TestFixtures.pose(subjectPos, Vec3d.ZERO, 0));
        ReplayEntitySnapshot h = ReplayEntitySnapshot.basic(host, TestFixtures.pose(hostPos, Vec3d.ZERO, 0));
        ReplayEntitySnapshot f = ReplayEntitySnapshot.basic(far, TestFixtures.pose(farPos, Vec3d.ZERO, 0));
        return new ReplaySample(tick, Map.of(subject, s, host, h, far, f), List.of(), List.of());
    }

    private static ReplaySample sampleTwo(long tick, TargetReference subject, TargetReference other,
                                          Vec3d subjectPos, Vec3d otherPos) {
        ReplayEntitySnapshot s = ReplayEntitySnapshot.basic(subject, TestFixtures.pose(subjectPos, Vec3d.ZERO, 0));
        ReplayEntitySnapshot o = ReplayEntitySnapshot.basic(other, TestFixtures.pose(otherPos, Vec3d.ZERO, 0));
        return new ReplaySample(tick, Map.of(subject, s, other, o), List.of(), List.of());
    }

    private static ReplayAnalysisResult analysis(List<ReplaySample> samples) {
        ReplayAnalysisRequest request = ReplayAnalysisRequest.defaults(0, 100);
        SampleSelection selection = new SampleSelection(samples, List.of(), samples, List.of());
        ReplayAnalysisStatistics stats = new ReplayAnalysisStatistics(100, samples.size(), samples.size(),
                0, samples.size(), 1, 0, 0, 1, Map.of());
        return new ReplayAnalysisResult(request, selection, samples, Map.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), stats, List.of());
    }
}
