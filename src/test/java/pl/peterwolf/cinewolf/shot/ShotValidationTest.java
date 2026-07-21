package pl.peterwolf.cinewolf.shot;

import org.junit.jupiter.api.Test;
import pl.peterwolf.cinewolf.TestFixtures;
import pl.peterwolf.cinewolf.model.EasingType;
import pl.peterwolf.cinewolf.model.RotationDirection;
import pl.peterwolf.cinewolf.model.ShotRequest;
import pl.peterwolf.cinewolf.model.ShotType;
import pl.peterwolf.cinewolf.model.Vec3d;

import static org.junit.jupiter.api.Assertions.*;

class ShotValidationTest {
    @Test
    void rejectsInvalidNumericValues() {
        ShotRequest base = TestFixtures.request(ShotType.ORBIT);
        ShotRequest invalid = new ShotRequest(base.target(), base.shotType(), Double.NaN, base.height(), base.distance(),
                base.startDistance(), base.endDistance(), base.rpm(), base.durationSeconds(), base.startAngleDegrees(),
                RotationDirection.CLOCKWISE, base.cameraSpeed(), base.fov(), EasingType.LINEAR,
                base.lookAheadSeconds(), base.replayStartTime(), base.replayEndTime());
        assertFalse(new OrbitShotGenerator().validate(invalid,
                TestFixtures.context(tick -> TestFixtures.pose(Vec3d.ZERO, Vec3d.ZERO, 0))).isValid());
    }

    @Test
    void rejectsMissingTargetSample() {
        ShotRequest request = TestFixtures.request(ShotType.ORBIT);
        var context = TestFixtures.context(tick -> tick == 100 ? null : TestFixtures.pose(Vec3d.ZERO, Vec3d.ZERO, 0));
        assertFalse(new OrbitShotGenerator().validate(request, context).isValid());
    }

    @Test
    void rejectsBackwardsTimeline() {
        ShotRequest base = TestFixtures.request(ShotType.ORBIT);
        ShotRequest invalid = new ShotRequest(base.target(), base.shotType(), base.diameter(), base.height(), base.distance(),
                base.startDistance(), base.endDistance(), base.rpm(), base.durationSeconds(), base.startAngleDegrees(),
                base.direction(), base.cameraSpeed(), base.fov(), base.easing(), base.lookAheadSeconds(), 100, 20);
        assertFalse(new OrbitShotGenerator().validate(invalid,
                TestFixtures.context(tick -> TestFixtures.pose(Vec3d.ZERO, Vec3d.ZERO, 0))).isValid());
    }
}
