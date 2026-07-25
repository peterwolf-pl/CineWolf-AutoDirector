package pl.peterwolf.cinewolf.montage.analysis;

import org.junit.jupiter.api.Test;
import pl.peterwolf.cinewolf.TestFixtures;
import pl.peterwolf.cinewolf.model.Vec3d;
import pl.peterwolf.cinewolf.montage.analysis.ReplayAnalysisRequest;
import pl.peterwolf.cinewolf.montage.analysis.ReplayAnalysisResult;
import pl.peterwolf.cinewolf.montage.analysis.ReplayAnalysisStatistics;
import pl.peterwolf.cinewolf.montage.analysis.ReplayEntitySnapshot;
import pl.peterwolf.cinewolf.montage.analysis.ReplaySample;
import pl.peterwolf.cinewolf.montage.analysis.SampleSelection;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IndoorSceneHeuristicsTest {
    @Test
    void detectsCompactRoomPathAsIndoor() {
        ReplayAnalysisResult analysis = analysis(
                new Vec3d(0, 64, 0),
                new Vec3d(3, 64.2, 1),
                new Vec3d(5, 64.1, 2));
        assertTrue(IndoorSceneHeuristics.isLikelyIndoor(analysis, TestFixtures.TARGET, 0, 40));
    }

    @Test
    void rejectsOpenFieldPathAsIndoor() {
        ReplayAnalysisResult analysis = analysis(
                new Vec3d(0, 64, 0),
                new Vec3d(40, 66, 10),
                new Vec3d(80, 70, 40));
        assertFalse(IndoorSceneHeuristics.isLikelyIndoor(analysis, TestFixtures.TARGET, 0, 40));
    }

    private static ReplayAnalysisResult analysis(Vec3d... positions) {
        ReplayEntitySnapshot[] entities = new ReplayEntitySnapshot[positions.length];
        List<ReplaySample> samples = new java.util.ArrayList<>();
        for (int index = 0; index < positions.length; index++) {
            long tick = index * 10L;
            entities[index] = ReplayEntitySnapshot.basic(TestFixtures.TARGET,
                    TestFixtures.pose(positions[index], Vec3d.ZERO, 0.0));
            samples.add(new ReplaySample(tick, Map.of(TestFixtures.TARGET, entities[index]), List.of(), List.of()));
        }
        ReplayAnalysisRequest request = ReplayAnalysisRequest.defaults(0, 100);
        SampleSelection selection = new SampleSelection(samples, List.of(), samples, List.of());
        ReplayAnalysisStatistics stats = new ReplayAnalysisStatistics(100, samples.size(), samples.size(),
                0, samples.size(), 1, 0, 0, 1, Map.of());
        return new ReplayAnalysisResult(request, selection, samples, Map.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), stats, List.of());
    }
}
