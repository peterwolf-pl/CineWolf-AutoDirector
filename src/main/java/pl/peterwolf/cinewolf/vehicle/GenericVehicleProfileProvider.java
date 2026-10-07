package pl.peterwolf.cinewolf.vehicle;

import pl.peterwolf.cinewolf.model.BoundingBox;
import pl.peterwolf.cinewolf.model.TargetPose;
import pl.peterwolf.cinewolf.model.TargetReference;
import pl.peterwolf.cinewolf.model.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Infer a usable vehicle profile for unknown/modded vehicles. Never throws. */
public final class GenericVehicleProfileProvider implements VehicleProvider {
    @Override
    public String providerId() {
        return "generic";
    }

    @Override
    public int priority() {
        return -100;
    }

    @Override
    public boolean supports(TargetReference target, TargetPose pose) {
        return true;
    }

    @Override
    public Optional<VehicleDescriptor> describe(TargetReference target, TargetPose pose) {
        try {
            Vec3d forward = VehicleMotion.resolveForward(target, pose);
            Vec3d up = VehicleMotion.resolveUp(target, pose, forward);
            Vec3d right = up.cross(forward).normalizeOr(new Vec3d(1, 0, 0));
            BoundingBox box = pose.boundingBox();
            double length = Math.max(0.8, Math.max(box.max().x() - box.min().x(), box.max().z() - box.min().z()));
            double width = Math.max(0.6, Math.min(box.max().x() - box.min().x(), box.max().z() - box.min().z()));
            double height = Math.max(0.6, box.max().y() - box.min().y());
            Vec3d center = box.center();
            List<VehicleAnchor> anchors = new ArrayList<>();
            anchors.add(new VehicleAnchor(VehicleAnchorKind.CENTER, center));
            anchors.add(new VehicleAnchor(VehicleAnchorKind.ROOT, pose.position()));
            anchors.add(new VehicleAnchor(VehicleAnchorKind.FRONT, center.add(forward.multiply(length * 0.45))));
            anchors.add(new VehicleAnchor(VehicleAnchorKind.REAR, center.subtract(forward.multiply(length * 0.45))));
            anchors.add(new VehicleAnchor(VehicleAnchorKind.LEFT, center.subtract(right.multiply(width * 0.45))));
            anchors.add(new VehicleAnchor(VehicleAnchorKind.RIGHT, center.add(right.multiply(width * 0.45))));
            anchors.add(new VehicleAnchor(VehicleAnchorKind.TOP, center.add(up.multiply(height * 0.5))));
            anchors.add(new VehicleAnchor(VehicleAnchorKind.BOTTOM, pose.position()));
            anchors.add(new VehicleAnchor(VehicleAnchorKind.DRIVER, pose.focusPosition()));
            VehicleCategory category = VehicleMotion.isAircraftLike(target, pose)
                    ? VehicleCategory.AIRCRAFT : VehicleCategory.GENERIC;
            return Optional.of(new VehicleDescriptor(target, List.of(), category, providerId(), forward, up,
                    box, anchors, length, width, height));
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }
}
