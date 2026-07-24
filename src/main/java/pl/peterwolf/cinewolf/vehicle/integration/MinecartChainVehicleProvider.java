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

/** Soft integration for Minecart Chain Train style entities (no hard dependency). */
public final class MinecartChainVehicleProvider implements VehicleProvider {
    @Override
    public String providerId() {
        return "peterwolf.minecart_chain";
    }

    @Override
    public int priority() {
        return 50;
    }

    @Override
    public boolean supports(TargetReference target, TargetPose pose) {
        String type = type(target, pose);
        return type.contains("minecartchain")
                || type.contains("minecart_chain")
                || type.contains("locomotive")
                || (type.contains("train") && type.contains("cart"));
    }

    @Override
    public Optional<VehicleDescriptor> describe(TargetReference target, TargetPose pose) {
        if (!supports(target, pose)) return Optional.empty();
        Vec3d forward = CameraMath.horizontalDirectionFromYaw(pose.yaw());
        Vec3d right = Vec3d.UP.cross(forward).normalizeOr(new Vec3d(1, 0, 0));
        BoundingBox box = pose.boundingBox();
        // Approximate multi-carriage length for train flyovers / trackside framing.
        double length = Math.max(4.0, Math.max(box.max().x() - box.min().x(), box.max().z() - box.min().z()) * 3.0);
        double width = Math.max(1.0, Math.min(box.max().x() - box.min().x(), box.max().z() - box.min().z()));
        double height = Math.max(1.2, box.max().y() - box.min().y());
        Vec3d center = box.center();
        List<VehicleAnchor> anchors = new ArrayList<>();
        anchors.add(new VehicleAnchor(VehicleAnchorKind.TRAIN_FRONT, center.add(forward.multiply(length * 0.45))));
        anchors.add(new VehicleAnchor(VehicleAnchorKind.TRAIN_REAR, center.subtract(forward.multiply(length * 0.45))));
        anchors.add(new VehicleAnchor(VehicleAnchorKind.CENTER, center));
        anchors.add(new VehicleAnchor(VehicleAnchorKind.COCKPIT, pose.focusPosition()));
        anchors.add(new VehicleAnchor(VehicleAnchorKind.COUPLING, center.subtract(forward.multiply(length * 0.15))));
        anchors.add(new VehicleAnchor(VehicleAnchorKind.CARRIAGE, center.add(forward.multiply(length * 0.1))));
        anchors.add(new VehicleAnchor(VehicleAnchorKind.TRACKSIDE_FOCUS,
                center.add(right.multiply(width * 2.5)).add(new Vec3d(0, height * 0.2, 0))));
        anchors.add(new VehicleAnchor(VehicleAnchorKind.SIDE_LEFT, center.subtract(right.multiply(width * 0.6))));
        anchors.add(new VehicleAnchor(VehicleAnchorKind.SIDE_RIGHT, center.add(right.multiply(width * 0.6))));
        return Optional.of(new VehicleDescriptor(target, List.of(), VehicleCategory.TRAIN, providerId(),
                forward, Vec3d.UP, expand(box, length, width, height), anchors, length, width, height));
    }

    private static BoundingBox expand(BoundingBox box, double length, double width, double height) {
        Vec3d c = box.center();
        return new BoundingBox(
                new Vec3d(c.x() - length * 0.5, c.y(), c.z() - width * 0.5),
                new Vec3d(c.x() + length * 0.5, c.y() + height, c.z() + width * 0.5)
        );
    }

    private static String type(TargetReference target, TargetPose pose) {
        return (target.entityType() + " " + pose.entityType()).toLowerCase(Locale.ROOT);
    }
}
