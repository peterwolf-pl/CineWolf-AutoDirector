package pl.peterwolf.cinewolf.vehicle;

import pl.peterwolf.cinewolf.camera.CameraMath;
import pl.peterwolf.cinewolf.model.BoundingBox;
import pl.peterwolf.cinewolf.model.TargetPose;
import pl.peterwolf.cinewolf.model.TargetReference;
import pl.peterwolf.cinewolf.model.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class BuiltinVehicleProvider implements VehicleProvider {
    @Override
    public String providerId() {
        return "builtin";
    }

    @Override
    public int priority() {
        return 0;
    }

    @Override
    public boolean supports(TargetReference target, TargetPose pose) {
        return categoryFor(target, pose) != null;
    }

    @Override
    public Optional<VehicleDescriptor> describe(TargetReference target, TargetPose pose) {
        VehicleCategory category = categoryFor(target, pose);
        if (category == null && !pose.inVehicle()) {
            // Generic vehicle-like entities still get soft support when the generator asks for it.
            category = VehicleCategory.GENERIC;
        } else if (category == null) {
            category = VehicleCategory.GENERIC;
        }
        Vec3d forward = CameraMath.horizontalDirectionFromYaw(pose.yaw());
        Vec3d right = Vec3d.UP.cross(forward).normalizeOr(new Vec3d(1.0, 0.0, 0.0));
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
        anchors.add(new VehicleAnchor(VehicleAnchorKind.SIDE_LEFT, center.subtract(right.multiply(width * 0.45))));
        anchors.add(new VehicleAnchor(VehicleAnchorKind.SIDE_RIGHT, center.add(right.multiply(width * 0.45))));
        anchors.add(new VehicleAnchor(VehicleAnchorKind.COCKPIT, pose.focusPosition()));
        anchors.add(new VehicleAnchor(VehicleAnchorKind.WING_LEFT,
                center.subtract(right.multiply(width * 0.7)).add(new Vec3d(0.0, height * 0.2, 0.0))));
        anchors.add(new VehicleAnchor(VehicleAnchorKind.WING_RIGHT,
                center.add(right.multiply(width * 0.7)).add(new Vec3d(0.0, height * 0.2, 0.0))));
        anchors.add(new VehicleAnchor(VehicleAnchorKind.WHEEL,
                pose.position().add(right.multiply(width * 0.3)).add(new Vec3d(0.0, 0.2, 0.0))));
        anchors.add(new VehicleAnchor(VehicleAnchorKind.COUPLING, center.subtract(forward.multiply(length * 0.5))));
        return Optional.of(new VehicleDescriptor(target, List.of(), category, providerId(), forward, Vec3d.UP, box,
                anchors, length, width, height));
    }

    private static VehicleCategory categoryFor(TargetReference target, TargetPose pose) {
        String type = (target.entityType() + " " + pose.entityType()).toLowerCase(Locale.ROOT);
        if (type.contains("minecart")) return VehicleCategory.MINECART;
        if (type.contains("boat")) return VehicleCategory.BOAT;
        if (type.contains("horse") || type.contains("camel") || type.contains("donkey") || type.contains("mule")) {
            return VehicleCategory.HORSE;
        }
        if (type.contains("train") || type.contains("locomotive") || type.contains("carriage")) {
            return VehicleCategory.TRAIN;
        }
        if (type.contains("plane") || type.contains("aircraft") || type.contains("helicopter")
                || type.contains("airship")) {
            return VehicleCategory.AIRCRAFT;
        }
        if (type.contains("zipline") || type.contains("zip_line") || type.contains("zip-line")) {
            return VehicleCategory.ZIPLINE;
        }
        if (pose.inVehicle()) return VehicleCategory.GENERIC;
        return null;
    }
}
