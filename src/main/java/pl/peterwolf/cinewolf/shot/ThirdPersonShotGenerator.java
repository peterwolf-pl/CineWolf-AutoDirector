package pl.peterwolf.cinewolf.shot;

import pl.peterwolf.cinewolf.api.ShotGenerator;
import pl.peterwolf.cinewolf.camera.CameraMath;
import pl.peterwolf.cinewolf.model.CameraPathPlan;
import pl.peterwolf.cinewolf.model.CameraSample;
import pl.peterwolf.cinewolf.model.PathWarning;
import pl.peterwolf.cinewolf.model.ReplayContext;
import pl.peterwolf.cinewolf.model.ShotRequest;
import pl.peterwolf.cinewolf.model.ShotValidationResult;
import pl.peterwolf.cinewolf.model.TargetKind;
import pl.peterwolf.cinewolf.model.TargetPose;
import pl.peterwolf.cinewolf.model.TargetReference;
import pl.peterwolf.cinewolf.model.Vec3d;
import pl.peterwolf.cinewolf.montage.preset.FramingType;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * 3rd-person witness: camera sits at another player's head/eye height and looks at the subject.
 */
public final class ThirdPersonShotGenerator extends AbstractShotGenerator implements ShotGenerator {
    /** Slight pull-back from the host eyes so the host model does not fill the entire frame. */
    private static final double HOST_EYE_BACK_OFFSET = 0.35;

    @Override
    public CameraPathPlan generate(ShotRequest request, ReplayContext context) {
        ShotValidationResult validation = validate(request, context);
        List<PathWarning> warnings = new ArrayList<>(validation.messages());
        if (!validation.isValid()) return finish(request, context, List.of(), warnings);

        TargetReference host = resolveHostReference(request);
        if (host == null) {
            warnings.add(new PathWarning(PathWarning.Severity.ERROR, "third_person.no_host",
                    "No camera-host player is available for 3rd person", 0.0));
            return finish(request, context, List.of(), warnings);
        }

        List<Long> replayTicks = sampleReplayTicks(request, context);
        List<CameraSample> samples = new ArrayList<>(replayTicks.size());
        double previousYaw = Double.NaN;
        double previousPitch = Double.NaN;
        int missingHost = 0;
        for (int i = 0; i < replayTicks.size(); i++) {
            long replayTime = replayTicks.get(i);
            double cinematic = cinematicTimeAtTick(request, replayTime);
            double delta = i == 0 ? request.durationSeconds() / Math.max(1, replayTicks.size() - 1)
                    : cinematic - cinematicTimeAtTick(request, replayTicks.get(i - 1));
            TargetPose subject = requiredPose(request, context, replayTime);
            Optional<TargetPose> hostPose = context.targetPoseResolver().resolve(host, replayTime);
            if (hostPose.isEmpty() || !hostPose.get().isFinite()) {
                missingHost++;
                continue;
            }
            TargetPose hostBody = hostPose.get();
            // Head/eye height: prefer focus (eyes); fall back to position + ~eye offset.
            Vec3d eye = hostBody.focusPosition();
            if (!eye.isFinite()) {
                eye = hostBody.position().add(new Vec3d(0.0, 1.62, 0.0));
            }
            Vec3d lookDir = CameraMath.horizontalDirectionFromYaw(hostBody.yaw());
            // Sit just behind the host eyes so we are "with" the other player, not inside their skull.
            Vec3d camera = eye.subtract(lookDir.multiply(HOST_EYE_BACK_OFFSET));
            CameraSample sample = sample(request, context, cinematic, replayTime, camera, subject,
                    previousYaw, previousPitch, Math.max(1.0e-3, delta));
            samples.add(sample);
            previousYaw = sample.yaw();
            previousPitch = sample.pitch();
        }
        if (samples.isEmpty()) {
            warnings.add(new PathWarning(PathWarning.Severity.ERROR, "third_person.host_missing",
                    "Camera host has no samples in this interval", 0.0));
            return finish(request, context, List.of(), warnings);
        }
        if (missingHost > 0) {
            warnings.add(new PathWarning(PathWarning.Severity.WARNING, "third_person.host_gaps",
                    "Camera host missing for " + missingHost + " sample(s)", 0.0));
        }
        warnings.add(new PathWarning(PathWarning.Severity.INFO, "third_person.host",
                "3rd person host " + host.displayName() + " (" + host.shortIdentifier() + ")", 0.0));
        return finish(request, context, samples, warnings);
    }

    private static TargetReference resolveHostReference(ShotRequest request) {
        UUID hostId = request.options().cameraHostUuid();
        if (hostId == null) return null;
        return new TargetReference(hostId, "minecraft:player", "camera-host");
    }

    @Override
    public ShotValidationResult validate(ShotRequest request, ReplayContext context) {
        ShotValidationResult common = validateCommon(request, context);
        List<PathWarning> errors = new ArrayList<>();
        if (request.options().cameraHostUuid() == null) {
            errors.add(error("camera_host", "3rd person requires another player as camera host"));
        } else if (request.options().cameraHostUuid().equals(request.target().uuid())) {
            errors.add(error("camera_host", "Camera host must be a different entity than the subject"));
        }
        return common.merge(new ShotValidationResult(errors));
    }

    @Override
    public Set<TargetKind> supportedTargetKinds() {
        return EnumSet.of(TargetKind.ENTITY, TargetKind.GROUP);
    }

    @Override
    public Set<FramingType> supportedFramingTypes() {
        return EnumSet.of(FramingType.MEDIUM, FramingType.CLOSE, FramingType.WIDE);
    }

    @Override
    public ShotCapabilities capabilities() {
        return ShotCapabilities.full();
    }
}
