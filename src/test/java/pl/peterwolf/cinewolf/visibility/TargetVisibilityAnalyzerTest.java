package pl.peterwolf.cinewolf.visibility;

import org.junit.jupiter.api.Test;
import pl.peterwolf.cinewolf.TestFixtures;
import pl.peterwolf.cinewolf.model.BoundingBox;
import pl.peterwolf.cinewolf.model.GroupFocusMode;
import pl.peterwolf.cinewolf.model.TargetPose;
import pl.peterwolf.cinewolf.model.TargetReference;
import pl.peterwolf.cinewolf.model.Vec3d;
import pl.peterwolf.cinewolf.model.target.GroupTarget;
import pl.peterwolf.cinewolf.model.target.StructureTarget;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TargetVisibilityAnalyzerTest {
    private final TargetVisibilityAnalyzer analyzer = new TargetVisibilityAnalyzer();

    @Test
    void openLineOfSightScoresFullyVisible() {
        TargetPose pose = TestFixtures.pose(Vec3d.ZERO, Vec3d.ZERO, 0);
        TargetVisibilityResult result = analyzer.analyze(new Vec3d(0, 2, -8), pose, 70.0, (a, b) -> true);
        assertTrue(result.fullyVisible());
        assertEquals(1.0, result.visibilityScore(), 1.0e-9);
    }

    @Test
    void blockedLineOfSightLowersScore() {
        TargetPose pose = TestFixtures.pose(Vec3d.ZERO, Vec3d.ZERO, 0);
        TargetVisibilityResult result = analyzer.analyze(new Vec3d(0, 2, -8), pose, 70.0, (a, b) -> false);
        assertTrue(result.occluded());
        assertEquals(0.0, result.visibilityScore(), 1.0e-9);
    }

    @Test
    void groupVisibleRatioTracksMembers() {
        TargetPose a = TestFixtures.pose(new Vec3d(0, 0, 0), Vec3d.ZERO, 0);
        TargetPose b = TestFixtures.pose(new Vec3d(2, 0, 0), Vec3d.ZERO, 0);
        TargetVisibilityResult result = analyzer.analyzeGroup(new Vec3d(0, 2, -6), List.of(a, b), 70.0,
                (camera, probe) -> probe.x() < 1.0);
        assertTrue(result.groupVisibleRatio() > 0.0);
        assertTrue(result.groupVisibleRatio() < 1.0 || result.visibilityScore() > 0.0);
    }

    @Test
    void groupMetricsCombineBounds() {
        TargetReference one = new TargetReference(new UUID(1, 1), "minecraft:player", "A");
        TargetReference two = new TargetReference(new UUID(1, 2), "minecraft:player", "B");
        GroupTarget group = new GroupTarget(UUID.randomUUID(), List.of(one, two), GroupFocusMode.BOUNDING_BOX_CENTER, one);
        TargetPose a = TestFixtures.pose(new Vec3d(0, 0, 0), new Vec3d(1, 0, 0), 0);
        TargetPose b = TestFixtures.pose(new Vec3d(4, 0, 0), new Vec3d(1, 0, 0), 0);
        var metrics = analyzer.groupMetrics(group, List.of(a, b));
        assertTrue(metrics.spread() >= 4.0);
        assertTrue(metrics.radius() > 0.0);
    }

    @Test
    void structureFramingDistanceScalesWithSize() {
        StructureTarget small = new StructureTarget("small",
                new BoundingBox(new Vec3d(0, 0, 0), new Vec3d(2, 2, 2)), List.of(), null);
        StructureTarget large = new StructureTarget("large",
                new BoundingBox(new Vec3d(0, 0, 0), new Vec3d(40, 40, 40)), List.of(), null);
        // structureId-based targets
        assertTrue(analyzer.structureFramingDistance(large, 70.0)
                > analyzer.structureFramingDistance(small, 70.0));
    }

    @Test
    void vehicleLeadSpacePrefersForwardRoom() {
        TargetPose pose = TestFixtures.pose(Vec3d.ZERO, new Vec3d(0, 0, 1), 0);
        double forward = analyzer.vehicleLeadSpaceScore(new Vec3d(0, 2, -6), pose, new Vec3d(0, 0, 1));
        double behind = analyzer.vehicleLeadSpaceScore(new Vec3d(0, 2, 6), pose, new Vec3d(0, 0, 1));
        assertTrue(forward >= behind);
    }
}
