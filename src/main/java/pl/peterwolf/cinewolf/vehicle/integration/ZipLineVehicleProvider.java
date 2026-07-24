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

/** Soft integration for zip-line riders/cables. */
public final class ZipLineVehicleProvider implements VehicleProvider {
    @Override
    public String providerId() {
        return "peterwolf.zipline";
    }

    @Override
    public int priority() {
        return 50;
    }

    @Override
    public boolean supports(TargetReference target, TargetPose pose) {
        String type = (target.entityType() + " " + pose.entityType()).toLowerCase(Locale.ROOT);
        return type.contains("zipline") || type.contains("zip_line") || type.contains("zip-line")
                || type.contains("cablecar") || type.contains("trolley");
    }

    @Override
    public Optional<VehicleDescriptor> describe(TargetReference target, TargetPose pose) {
        if (!supports(target, pose)) return Optional.empty();
        Vec3d velocity = pose.velocity();
        Vec3d forward = velocity.length() > 0.05
                ? velocity.normalizeOr(CameraMath.horizontalDirectionFromYaw(pose.yaw()))
                : CameraMath.horizontalDirectionFromYaw(pose.yaw());
        Vec3d right = Vec3d.UP.cross(forward).normalizeOr(new Vec3d(1, 0, 0));
        BoundingBox box = pose.boundingBox();
        double length = Math.max(1.5, Math.max(box.max().x() - box.min().x(), box.max().z() - box.min().z()) * 2.0);
        double width = Math.max(0.8, Math.min(box.max().x() - box.min().x(), box.max().z() - box.min().z()));
        double height = Math.max(1.0, box.max().y() - box.min().y());
        Vec3d center = box.center();
        List<VehicleAnchor> anchors = new ArrayList<>();
        anchors.add(new VehicleAnchor(VehicleAnchorKind.DRIVER, pose.focusPosition())); // rider
        anchors.add(new VehicleAnchor(VehicleAnchorKind.FRONT, center.add(forward.multiply(length * 0.5)))); // toward end
        anchors.add(new VehicleAnchor(VehicleAnchorKind.REAR, center.subtract(forward.multiply(length * 0.5)))); // start
        anchors.add(new VehicleAnchor(VehicleAnchorKind.TOP, center.add(new Vec3d(0, height * 0.8, 0))));
        anchors.add(new VehicleAnchor(VehicleAnchorKind.SIDE_LEFT, center.subtract(right.multiply(width * 1.5))));
        anchors.add(new VehicleAnchor(VehicleAnchorKind.SIDE_RIGHT, center.add(right.multiply(width * 1.5))));
        anchors.add(new VehicleAnchor(VehicleAnchorKind.CENTER, center));
        return Optional.of(new VehicleDescriptor(target, List.of(), VehicleCategory.ZIP_LINE, providerId(),
                forward, Vec3d.UP, box, anchors, length, width, height));
    }
}
