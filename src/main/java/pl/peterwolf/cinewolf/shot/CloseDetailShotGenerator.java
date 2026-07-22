package pl.peterwolf.cinewolf.shot;

import pl.peterwolf.cinewolf.api.ShotGenerator;
import pl.peterwolf.cinewolf.camera.CameraMath;
import pl.peterwolf.cinewolf.camera.Easings;
import pl.peterwolf.cinewolf.model.CameraPathPlan;
import pl.peterwolf.cinewolf.model.CameraSample;
import pl.peterwolf.cinewolf.model.DetailTargetType;
import pl.peterwolf.cinewolf.model.PathWarning;
import pl.peterwolf.cinewolf.model.ReplayContext;
import pl.peterwolf.cinewolf.model.ShotRequest;
import pl.peterwolf.cinewolf.model.ShotValidationResult;
import pl.peterwolf.cinewolf.model.TargetKind;
import pl.peterwolf.cinewolf.model.TargetPose;
import pl.peterwolf.cinewolf.model.Vec3d;
import pl.peterwolf.cinewolf.montage.preset.FramingType;
import pl.peterwolf.cinewolf.vehicle.VehicleAnchorKind;
import pl.peterwolf.cinewolf.vehicle.VehicleDescriptor;
import pl.peterwolf.cinewolf.vehicle.VehicleProviderRegistry;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public final class CloseDetailShotGenerator extends AbstractShotGenerator implements ShotGenerator {
    private final VehicleProviderRegistry vehicles = VehicleProviderRegistry.createDefault();

    @Override
    public CameraPathPlan generate(ShotRequest request, ReplayContext context) {
        ShotValidationResult validation = validate(request, context);
        List<PathWarning> warnings = new ArrayList<>(validation.messages());
        if (!validation.isValid()) return finish(request, context, List.of(), warnings);

        DetailTargetType detail = request.options().detailTargetType() == DetailTargetType.AUTO
                ? DetailTargetType.ENTITY_HEAD : request.options().detailTargetType();
        double distance = Math.max(0.6, request.distance() > 0 ? Math.min(request.distance(), 4.5) : 1.6);
        double microOrbit = request.options().microOrbitAmount();

        List<Long> replayTicks = sampleReplayTicks(request, context);
        List<CameraSample> samples = new ArrayList<>(replayTicks.size());
        double previousYaw = Double.NaN;
        for (long replayTime : replayTicks) {
            double progress = Easings.apply(request.easing(), progressAtTick(request, replayTime));
            TargetPose target = requiredPose(request, context, replayTime);
            Vec3d focus = resolveDetailFocus(request, target, detail);
            Vec3d forward = CameraMath.horizontalDirectionFromYaw(target.yaw());
            Vec3d right = Vec3d.UP.cross(forward).normalizeOr(new Vec3d(1.0, 0.0, 0.0));
            double angle = request.startAngleDegrees() * Math.PI / 180.0
                    + microOrbit * Math.sin(progress * Math.PI * 2.0);
            double depth = distance * (1.0 - 0.12 * Math.sin(progress * Math.PI));
            Vec3d camera = focus
                    .subtract(forward.multiply(depth * Math.cos(angle * 0.25)))
                    .add(right.multiply(request.options().sideOffset() + depth * Math.sin(angle) * 0.35))
                    .add(Vec3d.UP.multiply(request.height() * 0.25 + request.options().forwardOffset() * 0.05));
            if (target.boundingBox().contains(camera, 0.1)) {
                camera = focus.subtract(forward.multiply(Math.max(1.0, distance)));
            }
            // Override look-at to the detail focus by building sample with temporary target focus.
            TargetPose detailPose = new TargetPose(target.position(), focus, target.boundingBox(), target.yaw(),
                    target.pitch(), target.velocity(), target.entityType(), target.inVehicle(), target.dimension(),
                    target.discontinuity());
            CameraSample sample = sample(request, context, cinematicTimeAtTick(request, replayTime), replayTime,
                    camera, detailPose, previousYaw);
            samples.add(sample);
            previousYaw = sample.yaw();
        }
        return finish(request, context, samples, warnings);
    }

    private Vec3d resolveDetailFocus(ShotRequest request, TargetPose target, DetailTargetType detail) {
        VehicleDescriptor vehicle = vehicles.requireOrGeneric(request.target(), target);
        return switch (detail) {
            case ENTITY_HEAD, AUTO -> target.focusPosition();
            case ENTITY_HAND, HELD_ITEM -> target.focusPosition()
                    .add(CameraMath.horizontalDirectionFromYaw(target.yaw()).multiply(0.35))
                    .add(new Vec3d(0.0, -0.35, 0.0));
            case VEHICLE_FRONT -> vehicle.anchor(VehicleAnchorKind.FRONT).map(a -> a.worldPosition())
                    .orElse(target.position().add(vehicle.forward().multiply(vehicle.length() * 0.4)));
            case VEHICLE_REAR -> vehicle.anchor(VehicleAnchorKind.REAR).map(a -> a.worldPosition())
                    .orElse(target.position().subtract(vehicle.forward().multiply(vehicle.length() * 0.4)));
            case VEHICLE_SIDE -> vehicle.anchor(VehicleAnchorKind.SIDE_RIGHT).map(a -> a.worldPosition())
                    .orElse(target.focusPosition());
            case VEHICLE_COCKPIT -> vehicle.anchor(VehicleAnchorKind.COCKPIT).map(a -> a.worldPosition())
                    .orElse(target.focusPosition());
            case VEHICLE_WING -> vehicle.anchor(VehicleAnchorKind.WING_LEFT).map(a -> a.worldPosition())
                    .orElse(target.focusPosition());
            case STRUCTURE_POINT, CUSTOM_POINT -> target.focusPosition();
        };
    }

    @Override
    public ShotValidationResult validate(ShotRequest request, ReplayContext context) {
        ShotValidationResult common = validateCommon(request, context);
        List<PathWarning> errors = new ArrayList<>();
        if (request.distance() < 0.0) errors.add(error("distance", "Close detail distance cannot be negative"));
        return common.merge(new ShotValidationResult(errors));
    }

    @Override
    public Set<TargetKind> supportedTargetKinds() {
        return EnumSet.of(TargetKind.ENTITY, TargetKind.VEHICLE, TargetKind.DETAIL, TargetKind.STRUCTURE);
    }

    @Override
    public Set<FramingType> supportedFramingTypes() {
        return EnumSet.of(FramingType.CLOSE, FramingType.EXTREME_CLOSE);
    }

    @Override
    public ShotCapabilities capabilities() {
        return ShotCapabilities.full();
    }
}
