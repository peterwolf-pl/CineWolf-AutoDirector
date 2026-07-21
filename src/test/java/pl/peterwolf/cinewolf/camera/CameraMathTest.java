package pl.peterwolf.cinewolf.camera;

import org.junit.jupiter.api.Test;
import pl.peterwolf.cinewolf.model.Vec3d;

import static org.junit.jupiter.api.Assertions.*;

class CameraMathTest {
    private final CameraLookAtSolver solver = new CameraLookAtSolver();

    @Test
    void lookAtUsesMinecraftYawAndPitch() {
        var forward = solver.solve(Vec3d.ZERO, new Vec3d(0, 0, 1), Double.NaN);
        var rightAndUp = solver.solve(Vec3d.ZERO, new Vec3d(1, 1, 0), Double.NaN);
        assertEquals(0.0, forward.yaw(), 1.0e-6);
        assertEquals(0.0, forward.pitch(), 1.0e-6);
        assertEquals(-90.0, rightAndUp.yaw(), 1.0e-6);
        assertEquals(-45.0, rightAndUp.pitch(), 1.0e-6);
    }

    @Test
    void yawUnwrapsAcrossOneEighty() {
        double unwrapped = CameraMath.unwrapDegrees(179.0, -179.0);
        assertEquals(181.0, unwrapped, 1.0e-9);
        assertEquals(2.0, CameraMath.angleDifferenceDegrees(179.0, -179.0), 1.0e-9);
    }

    @Test
    void zeroDistanceIsStableAndFinite() {
        var orientation = solver.solve(Vec3d.ZERO, Vec3d.ZERO, 42.0);
        assertTrue(orientation.degenerate());
        assertEquals(42.0, orientation.yaw(), 1.0e-9);
        assertTrue(Float.isFinite(orientation.quaternion().w));
    }
}
