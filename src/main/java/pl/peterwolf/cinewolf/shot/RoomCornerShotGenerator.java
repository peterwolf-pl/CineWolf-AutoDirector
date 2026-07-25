package pl.peterwolf.cinewolf.shot;

import pl.peterwolf.cinewolf.api.ShotGenerator;
import pl.peterwolf.cinewolf.model.CameraPathPlan;
import pl.peterwolf.cinewolf.model.CameraSample;
import pl.peterwolf.cinewolf.model.PathWarning;
import pl.peterwolf.cinewolf.model.ReplayContext;
import pl.peterwolf.cinewolf.model.ShotRequest;
import pl.peterwolf.cinewolf.model.ShotValidationResult;
import pl.peterwolf.cinewolf.model.TargetKind;
import pl.peterwolf.cinewolf.model.TargetPose;
import pl.peterwolf.cinewolf.model.Vec3d;
import pl.peterwolf.cinewolf.montage.preset.FramingType;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Closed-room style: camera sits in a room corner at the subject's eye height and tracks them.
 * The corner is estimated from the subject's path bounds (no world access in the generator);
 * the client world pass can refine placement against real walls.
 */
public final class RoomCornerShotGenerator extends AbstractShotGenerator implements ShotGenerator {
    private static final double MIN_ROOM_HALF = 2.0;
    private static final double CORNER_INSET = 0.45;

    @Override
    public CameraPathPlan generate(ShotRequest request, ReplayContext context) {
        ShotValidationResult validation = validate(request, context);
        List<PathWarning> warnings = new ArrayList<>(validation.messages());
        if (!validation.isValid()) return finish(request, context, List.of(), warnings);

        List<Long> replayTicks = sampleReplayTicks(request, context);
        List<TargetPose> poses = new ArrayList<>(replayTicks.size());
        for (long tick : replayTicks) {
            poses.add(requiredPose(request, context, tick));
        }

        Vec3d corner = chooseCorner(poses, request);
        double eyeY = eyeHeight(poses);
        Vec3d fixedCamera = new Vec3d(corner.x(), eyeY, corner.z());
        warnings.add(new PathWarning(PathWarning.Severity.INFO, "room_corner.placed",
                "Camera fixed in estimated room corner at eye height", 0.0));

        List<CameraSample> samples = new ArrayList<>(replayTicks.size());
        double previousYaw = Double.NaN;
        double previousPitch = Double.NaN;
        for (int i = 0; i < replayTicks.size(); i++) {
            long replayTime = replayTicks.get(i);
            double cinematic = cinematicTimeAtTick(request, replayTime);
            double delta = i == 0 ? request.durationSeconds() / Math.max(1, replayTicks.size() - 1)
                    : cinematic - cinematicTimeAtTick(request, replayTicks.get(i - 1));
            TargetPose target = poses.get(i);
            // Stay at eye height of the current frame so tall jumps/crouches still feel natural.
            Vec3d camera = new Vec3d(fixedCamera.x(), target.focusPosition().y(), fixedCamera.z());
            CameraSample sample = sample(request, context, cinematic, replayTime, camera, target,
                    previousYaw, previousPitch, Math.max(1.0e-3, delta));
            samples.add(sample);
            previousYaw = sample.yaw();
            previousPitch = sample.pitch();
        }
        return finish(request, context, samples, warnings);
    }

    /**
     * Picks one of the four horizontal corners of a box around the path (expanded by distance),
     * preferring the corner opposite the subject's early movement (entry into the room).
     */
    static Vec3d chooseCorner(List<TargetPose> poses, ShotRequest request) {
        double minX = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;
        for (TargetPose pose : poses) {
            Vec3d p = pose.position();
            minX = Math.min(minX, p.x());
            maxX = Math.max(maxX, p.x());
            minZ = Math.min(minZ, p.z());
            maxZ = Math.max(maxZ, p.z());
        }
        double pad = Math.max(MIN_ROOM_HALF, Math.max(2.0, request.distance() * 0.85));
        minX -= pad;
        maxX += pad;
        minZ -= pad;
        maxZ += pad;

        // Inset from the geometric corner so the camera sits slightly inside the room.
        Vec3d[] corners = {
                new Vec3d(minX + CORNER_INSET, 0.0, minZ + CORNER_INSET),
                new Vec3d(minX + CORNER_INSET, 0.0, maxZ - CORNER_INSET),
                new Vec3d(maxX - CORNER_INSET, 0.0, minZ + CORNER_INSET),
                new Vec3d(maxX - CORNER_INSET, 0.0, maxZ - CORNER_INSET)
        };

        Vec3d center = new Vec3d((minX + maxX) * 0.5, 0.0, (minZ + maxZ) * 0.5);
        Vec3d entry = poses.getFirst().position();
        Vec3d exit = poses.getLast().position();
        // Prefer corner farthest from the exit (behind the action) and from the entry path.
        int best = 0;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (int index = 0; index < corners.length; index++) {
            Vec3d corner = corners[index];
            double distExit = horizontalDistance(corner, exit);
            double distEntry = horizontalDistance(corner, entry);
            double distCenter = horizontalDistance(corner, center);
            // High score: far from exit (watch them leave), not too close to entry, decent room leverage.
            double score = distExit * 1.2 + distCenter * 0.35 - distEntry * 0.15;
            // Stable tie-break by index for determinism.
            score -= index * 1.0e-6;
            if (score > bestScore) {
                bestScore = score;
                best = index;
            }
        }
        return corners[best];
    }

    static double eyeHeight(List<TargetPose> poses) {
        double sum = 0.0;
        for (TargetPose pose : poses) sum += pose.focusPosition().y();
        return sum / poses.size();
    }

    private static double horizontalDistance(Vec3d a, Vec3d b) {
        double dx = a.x() - b.x();
        double dz = a.z() - b.z();
        return Math.sqrt(dx * dx + dz * dz);
    }

    @Override
    public ShotValidationResult validate(ShotRequest request, ReplayContext context) {
        ShotValidationResult common = validateCommon(request, context);
        List<PathWarning> errors = new ArrayList<>();
        if (request.distance() <= 0.0) {
            errors.add(error("distance", "Room corner distance/pad must be > 0"));
        }
        return common.merge(new ShotValidationResult(errors));
    }

    @Override
    public Set<TargetKind> supportedTargetKinds() {
        return EnumSet.of(TargetKind.ENTITY, TargetKind.VEHICLE, TargetKind.GROUP);
    }

    @Override
    public Set<FramingType> supportedFramingTypes() {
        return EnumSet.of(FramingType.WIDE, FramingType.MEDIUM, FramingType.CLOSE);
    }

    @Override
    public ShotCapabilities capabilities() {
        return ShotCapabilities.full();
    }
}
