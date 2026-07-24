package pl.peterwolf.cinewolf.montage.v2;

import pl.peterwolf.cinewolf.model.ShotType;
import pl.peterwolf.cinewolf.montage.event.ReplayEventType;
import pl.peterwolf.cinewolf.vehicle.VehicleCategory;
import pl.peterwolf.cinewolf.vehicle.VehicleProfile;
import pl.peterwolf.cinewolf.vehicle.VehicleState;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Vehicle-aware shot templates and event boosts for Montage Engine 2.0. */
public final class VehicleMontagePlanner {
    public List<ShotType> templateShots(VehicleProfile profile, VehicleState state) {
        Objects.requireNonNull(profile, "profile");
        VehicleCategory category = profile.category();
        VehicleState effective = state == null ? profile.state() : state;
        return switch (category) {
            case AIRCRAFT -> aircraftTemplate(effective);
            case TRAIN, CONVOY, MINECART -> trainTemplate(effective);
            case ZIPLINE, ZIP_LINE -> ziplineTemplate();
            case BOAT -> List.of(ShotType.SIDE_TRACKING, ShotType.FOLLOW, ShotType.FLYBY, ShotType.VEHICLE_PROFILE);
            case HORSE, MOUNT -> List.of(ShotType.FOLLOW, ShotType.SIDE_TRACKING, ShotType.CHASE, ShotType.CLOSE_DETAIL);
            default -> List.of(ShotType.VEHICLE_PROFILE, ShotType.SIDE_TRACKING, ShotType.CHASE, ShotType.FOLLOW);
        };
    }

    public double eventBoost(VehicleProfile profile, ReplayEventType type) {
        if (profile == null || type == null) return 1.0;
        return switch (profile.category()) {
            case AIRCRAFT -> switch (type) {
                case FLIGHT_START, FLIGHT, LANDING, ALTITUDE_GAIN, ALTITUDE_LOSS -> 1.45;
                case HIGH_SPEED, SHARP_TURN -> 1.25;
                default -> 1.0;
            };
            case TRAIN, CONVOY, MINECART -> switch (type) {
                case VEHICLE_MOVEMENT, VEHICLE_ENTER, VEHICLE_EXIT, HIGH_SPEED, ACCELERATION -> 1.35;
                default -> 1.0;
            };
            case ZIPLINE, ZIP_LINE -> switch (type) {
                case HIGH_SPEED, ALTITUDE_LOSS, VEHICLE_MOVEMENT -> 1.4;
                default -> 1.0;
            };
            default -> type.name().contains("VEHICLE") ? 1.2 : 1.0;
        };
    }

    public Optional<String> recommendedStyleId(VehicleProfile profile) {
        if (profile == null) return Optional.empty();
        return switch (profile.category()) {
            case AIRCRAFT -> Optional.of("flight_showcase");
            case TRAIN, CONVOY, MINECART -> Optional.of("train_journey");
            case ZIPLINE, ZIP_LINE, BOAT, HORSE, MOUNT, GROUND_VEHICLE -> Optional.of("vehicle_showcase");
            default -> Optional.of("vehicle_showcase");
        };
    }

    private static List<ShotType> aircraftTemplate(VehicleState state) {
        return switch (state) {
            case TAKEOFF, ACCELERATING, DEPARTING -> List.of(
                    ShotType.STATIC_TRACKING, ShotType.SIDE_TRACKING, ShotType.CHASE, ShotType.FLYBY, ShotType.CRANE_UP);
            case TURNING, DRIFTING -> List.of(
                    ShotType.VEHICLE_PROFILE, ShotType.SIDE_TRACKING, ShotType.ORBIT, ShotType.SPIRAL, ShotType.CHASE);
            case LANDING, ARRIVING, DESCENDING -> List.of(
                    ShotType.STATIC_TRACKING, ShotType.VEHICLE_PROFILE, ShotType.SIDE_TRACKING, ShotType.DOLLY_OUT);
            default -> List.of(ShotType.CHASE, ShotType.FLYBY, ShotType.ORBIT, ShotType.SIDE_TRACKING, ShotType.CRANE_UP);
        };
    }

    private static List<ShotType> trainTemplate(VehicleState state) {
        return switch (state) {
            case DEPARTING, ACCELERATING -> List.of(
                    ShotType.CLOSE_DETAIL, ShotType.STATIC_TRACKING, ShotType.SIDE_TRACKING, ShotType.FLYBY, ShotType.DOLLY_OUT);
            case ARRIVING, BRAKING -> List.of(
                    ShotType.STATIC_TRACKING, ShotType.SIDE_TRACKING, ShotType.CLOSE_DETAIL, ShotType.REVEAL, ShotType.STATIC_TRACKING);
            default -> List.of(ShotType.SIDE_TRACKING, ShotType.VEHICLE_PROFILE, ShotType.FLYBY, ShotType.FOLLOW, ShotType.CLOSE_DETAIL);
        };
    }

    private static List<ShotType> ziplineTemplate() {
        return List.of(ShotType.REVEAL, ShotType.SIDE_TRACKING, ShotType.FOLLOW, ShotType.CRANE_UP, ShotType.FLYBY);
    }

    public List<String> planningReasons(VehicleProfile profile) {
        List<String> reasons = new ArrayList<>();
        if (profile == null) {
            reasons.add("vehicle.profile.missing_fallback_entity");
            return reasons;
        }
        reasons.add("vehicle.profile.category:" + profile.category());
        reasons.add("vehicle.profile.state:" + profile.state());
        reasons.add("vehicle.profile.anchors:" + profile.anchors().size());
        return reasons;
    }
}
