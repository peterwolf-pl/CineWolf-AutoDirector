package pl.peterwolf.cinewolf.shot;

import pl.peterwolf.cinewolf.api.ShotGenerator;
import pl.peterwolf.cinewolf.camera.CameraSmoothing;
import pl.peterwolf.cinewolf.camera.Easings;
import pl.peterwolf.cinewolf.model.CameraPathPlan;
import pl.peterwolf.cinewolf.model.CameraSample;
import pl.peterwolf.cinewolf.model.PathWarning;
import pl.peterwolf.cinewolf.model.ReplayContext;
import pl.peterwolf.cinewolf.model.ShotRequest;
import pl.peterwolf.cinewolf.model.ShotValidationResult;
import pl.peterwolf.cinewolf.model.TargetKind;
import pl.peterwolf.cinewolf.model.TargetPose;
import pl.peterwolf.cinewolf.model.VehicleProfileStyle;
import pl.peterwolf.cinewolf.model.Vec3d;
import pl.peterwolf.cinewolf.montage.preset.FramingType;
import pl.peterwolf.cinewolf.vehicle.VehicleCategory;
import pl.peterwolf.cinewolf.vehicle.VehicleDescriptor;
import pl.peterwolf.cinewolf.vehicle.VehicleProviderRegistry;
import pl.peterwolf.cinewolf.visibility.TargetVisibilityAnalyzer;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public final class VehicleProfileShotGenerator extends AbstractShotGenerator implements ShotGenerator {
    private final VehicleProviderRegistry vehicles = VehicleProviderRegistry.createDefault();
    private final TargetVisibilityAnalyzer visibilityAnalyzer = new TargetVisibilityAnalyzer();

    @Override
    public CameraPathPlan generate(ShotRequest request, ReplayContext context) {
        ShotValidationResult validation = validate(request, context);
        List<PathWarning> warnings = new ArrayList<>(validation.messages());
        if (!validation.isValid()) return finish(request, context, List.of(), warnings);

        List<Long> replayTicks = sampleReplayTicks(request, context);
        List<CameraSample> samples = new ArrayList<>(replayTicks.size());
        Vec3d smoothedCamera = null;
        double previousYaw = Double.NaN;
        for (int i = 0; i < replayTicks.size(); i++) {
            long replayTime = replayTicks.get(i);
            double progress = Easings.apply(request.easing(), progressAtTick(request, replayTime));
            double delta = i == 0 ? request.durationSeconds() / Math.max(1, replayTicks.size() - 1)
                    : cinematicTimeAtTick(request, replayTime) - cinematicTimeAtTick(request, replayTicks.get(i - 1));
            TargetPose target = requiredPose(request, context, replayTime);
            VehicleDescriptor vehicle = vehicles.requireOrGeneric(request.target(), target);
            VehicleProfileStyle style = resolveStyle(request, vehicle);
            Vec3d desired = profilePosition(request, target, vehicle, style, progress);
            if (request.cameraSpeed() > 0.0) {
                smoothedCamera = smoothedCamera == null ? desired
                        : CameraSmoothing.exponential(smoothedCamera, desired, request.cameraSpeed(), delta);
            } else {
                smoothedCamera = desired;
            }
            double lead = visibilityAnalyzer.vehicleLeadSpaceScore(smoothedCamera, target, vehicle.forward());
            if (lead < 0.3) {
                // Bias slightly forward for lead space.
                smoothedCamera = smoothedCamera.add(vehicle.forward().multiply(Math.max(0.5, request.distance() * 0.08)));
            }
            CameraSample sample = sample(request, context, cinematicTimeAtTick(request, replayTime), replayTime,
                    smoothedCamera, target, previousYaw);
            samples.add(sample);
            previousYaw = sample.yaw();
        }
        return finish(request, context, samples, warnings);
    }

    private static VehicleProfileStyle resolveStyle(ShotRequest request, VehicleDescriptor vehicle) {
        VehicleProfileStyle style = request.options().vehicleProfileStyle();
        if (style != null && style != VehicleProfileStyle.AUTO) return style;
        return switch (vehicle.category()) {
            case TRAIN -> VehicleProfileStyle.LOCOMOTIVE_FRONT;
            case AIRCRAFT -> VehicleProfileStyle.WING_VIEW;
            case MINECART -> VehicleProfileStyle.TRACKSIDE;
            case BOAT, HORSE -> VehicleProfileStyle.SIDE_PROFILE;
            case ZIPLINE -> VehicleProfileStyle.SIDE_PROFILE;
            case GENERIC -> VehicleProfileStyle.FRONT_THREE_QUARTER;
        };
    }

    private static Vec3d profilePosition(ShotRequest request, TargetPose target, VehicleDescriptor vehicle,
                                         VehicleProfileStyle style, double progress) {
        Vec3d forward = vehicle.forward().normalizeOr(new Vec3d(0, 0, 1));
        Vec3d right = vehicle.up().cross(forward).normalizeOr(new Vec3d(1, 0, 0));
        double distance = Math.max(2.0, request.distance());
        double height = request.height();
        double side = request.options().sideOffset() != 0.0 ? request.options().sideOffset()
                : request.direction().sign() * Math.max(1.0, vehicle.width());
        double forwardOffset = request.options().forwardOffset();
        // Mild travel matching: slide along vehicle motion.
        double travel = progress * Math.min(vehicle.length() * 0.5, request.cameraSpeed() * request.durationSeconds() * 0.15);
        Vec3d center = vehicle.center().add(forward.multiply(travel + forwardOffset));
        return switch (style) {
            case FRONT_THREE_QUARTER -> center.add(forward.multiply(distance * 0.55)).add(right.multiply(side * 0.7))
                    .add(Vec3d.UP.multiply(height));
            case REAR_THREE_QUARTER -> center.subtract(forward.multiply(distance * 0.55)).add(right.multiply(side * 0.7))
                    .add(Vec3d.UP.multiply(height));
            case SIDE_PROFILE, TRACKSIDE -> center.add(right.multiply(side >= 0 ? distance : -distance))
                    .add(Vec3d.UP.multiply(height));
            case LOW_ANGLE_SIDE -> center.add(right.multiply(side >= 0 ? distance : -distance))
                    .add(Vec3d.UP.multiply(Math.max(0.4, height * 0.35)));
            case HIGH_ANGLE_SIDE -> center.add(right.multiply(side >= 0 ? distance : -distance))
                    .add(Vec3d.UP.multiply(height + vehicle.height() * 0.8));
            case WING_VIEW -> center.add(right.multiply(distance * 0.8)).add(forward.multiply(-distance * 0.2))
                    .add(Vec3d.UP.multiply(height * 0.7));
            case LOCOMOTIVE_FRONT -> center.add(forward.multiply(distance * 0.85))
                    .add(right.multiply(side * 0.25)).add(Vec3d.UP.multiply(height * 0.6));
            case TRAIN_LENGTH -> center.add(right.multiply(distance)).add(forward.multiply(-vehicle.length() * 0.2))
                    .add(Vec3d.UP.multiply(height + 1.0));
            case RUNWAY -> center.add(right.multiply(distance * 1.2)).add(forward.multiply(distance * 0.3))
                    .add(Vec3d.UP.multiply(Math.max(1.0, height * 0.5)));
            case AUTO -> center.add(forward.multiply(distance * 0.4)).add(right.multiply(distance * 0.6))
                    .add(Vec3d.UP.multiply(height));
        };
    }

    @Override
    public ShotValidationResult validate(ShotRequest request, ReplayContext context) {
        ShotValidationResult common = validateCommon(request, context);
        List<PathWarning> errors = new ArrayList<>();
        if (request.distance() <= 0.0) errors.add(error("distance", "Vehicle profile distance must be > 0"));
        return common.merge(new ShotValidationResult(errors));
    }

    @Override
    public Set<TargetKind> supportedTargetKinds() {
        return EnumSet.of(TargetKind.VEHICLE, TargetKind.ENTITY);
    }

    @Override
    public Set<FramingType> supportedFramingTypes() {
        return EnumSet.of(FramingType.WIDE, FramingType.MEDIUM, FramingType.CLOSE);
    }

    @Override
    public ShotCapabilities capabilities() {
        return ShotCapabilities.vehicleAware();
    }
}
