package pl.peterwolf.cinewolf.camera;

import org.junit.jupiter.api.Test;
import pl.peterwolf.cinewolf.model.Vec3d;

import java.util.OptionalDouble;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CeilingClearanceClampTest {
    @Test
    void openSkyLeavesCameraUnchanged() {
        Vec3d camera = new Vec3d(1.0, 80.0, 2.0);
        assertEquals(camera, CeilingClearanceClamp.clamp(camera, OptionalDouble.empty()));
        assertTrue(CeilingClearanceClamp.maxCameraY(OptionalDouble.empty(), 0.35).isEmpty());
    }

    @Test
    void pullsCameraJustUnderCeiling() {
        OptionalDouble maxY = CeilingClearanceClamp.maxCameraY(OptionalDouble.of(70.0), 0.35);
        assertEquals(69.65, maxY.orElseThrow(), 1.0e-9);
        Vec3d clamped = CeilingClearanceClamp.clamp(new Vec3d(5.0, 75.0, 8.0), maxY);
        assertEquals(5.0, clamped.x(), 1.0e-9);
        assertEquals(69.65, clamped.y(), 1.0e-9);
        assertEquals(8.0, clamped.z(), 1.0e-9);
    }

    @Test
    void doesNotRaiseCameraThatIsAlreadyBelowCeiling() {
        Vec3d camera = new Vec3d(0.0, 64.0, 0.0);
        Vec3d clamped = CeilingClearanceClamp.clamp(camera, 69.65);
        assertEquals(camera, clamped);
    }
}
