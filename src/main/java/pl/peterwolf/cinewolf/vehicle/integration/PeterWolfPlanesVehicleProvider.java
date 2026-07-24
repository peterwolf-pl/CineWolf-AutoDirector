package pl.peterwolf.cinewolf.vehicle.integration;

import pl.peterwolf.cinewolf.camera.CameraMath;
import pl.peterwolf.cinewolf.model.BoundingBox;
import pl.peterwolf.cinewolf.model.TargetPose;
import pl.peterwolf.cinewolf.model.TargetReference;
import pl.peterwolf.cinewolf.model.Vec3d;
import pl.peterwolf.cinewolf.vehicle.VehicleAnchor;
import pl.peterwolf.cinewolf.vehicle.VehicleAnchorKind;
import pl.peterwolf.cinewolf.vehicle.VehicleCategory;
import pl.peterwolf.cinewolf.vehicle.VehicleDescriptor;
import pl.peterwolf.cinewolf.vehicle.VehicleProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Soft integration for PeterWolf's Planes / aircraft-like entities. */
public final class PeterWolfPlanesVehicleProvider implements VehicleProvider {
    @Override
    public String providerId() {
        return "peterwolf.planes";
    }

    @Override
    public int priority() {
        return 50;
    }

    @Override
    public boolean supports(TargetReference target, TargetPose pose) {
        String type = type(target, pose);
        return type.contains("peterwolf") && (type.contains("plane") || type.contains("aircraft"))
                || type.contains("simpleplanes")
                || type.contains("immersive_aircraft")
                || type.contains("airplane")
                || type.contains("biplane")
                || (type.contains("plane") && !type.contains("minecart"));
    }

    @Override
    public Optional<VehicleDescriptor> describe(TargetReference target, TargetPose pose) {
        if (!supports(target, pose)) return Optional.empty();
        Vec3d horizontal = CameraMath.horizontalDirectionFromYaw(pose.yaw());
        double pitchRad = Math.toRadians(pose.pitch());
        Vec3d forward = new Vec3d(
                horizontal.x() * Math.cos(pitchRad),
                -Math.sin(pitchRad),
                horizontal.z() * Math.cos(pitchRad)
        ).normalizeOr(horizontal);
        Vec3d right = Vec3d.UP.cross(forward).normalizeOr(new Vec3d(1, 0, 0));
        Vec3d up = forward.cross(right).normalizeOr(Vec3d.UP);
        BoundingBox box = pose.boundingBox();
        double length = Math.max(2.5, Math.max(box.max().x() - box.min().x(), box.max().z() - box.min().z()) * 1.4);
        double width = Math.max(2.0, Math.min(box.max().x() - box.min().x(), box.max().z() - box.min().z()) * 2.2);
        double height = Math.max(1.0, box.max().y() - box.min().y());
        Vec3d center = box.center();
        List<VehicleAnchor> anchors = new ArrayList<>();
        anchors.add(new VehicleAnchor(VehicleAnchorKind.FRONT, center.add(forward.multiply(length * 0.45)))); // nose
        anchors.add(new VehicleAnchor(VehicleAnchorKind.TAIL, center.subtract(forward.multiply(length * 0.45))));
        anchors.add(new VehicleAnchor(VehicleAnchorKind.WING_LEFT, center.subtract(right.multiply(width * 0.5))));
        anchors.add(new VehicleAnchor(VehicleAnchorKind.WING_RIGHT, center.add(right.multiply(width * 0.5))));
        anchors.add(new VehicleAnchor(VehicleAnchorKind.COCKPIT, pose.focusPosition()));
        anchors.add(new VehicleAnchor(VehicleAnchorKind.ENGINE, center.add(forward.multiply(length * 0.2))));
        anchors.add(new VehicleAnchor(VehicleAnchorKind.RUNWAY_FOCUS,
                center.subtract(up.multiply(height)).add(forward.multiply(length))));
        anchors.add(new VehicleAnchor(VehicleAnchorKind.BOTTOM, center.subtract(up.multiply(height * 0.4))));
        anchors.add(new VehicleAnchor(VehicleAnchorKind.CENTER, center));
        return Optional.of(new VehicleDescriptor(target, List.of(), VehicleCategory.AIRCRAFT, providerId(),
                forward, up, box, anchors, length, width, height));
    }

    private static String type(TargetReference target, TargetPose pose) {
        return (target.entityType() + " " + pose.entityType()).toLowerCase(Locale.ROOT);
    }
}
