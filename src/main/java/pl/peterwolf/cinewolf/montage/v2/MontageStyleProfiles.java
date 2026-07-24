package pl.peterwolf.cinewolf.montage.v2;

import pl.peterwolf.cinewolf.model.ShotType;
import pl.peterwolf.cinewolf.montage.preset.MontagePacing;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static pl.peterwolf.cinewolf.model.ShotType.*;

/** Built-in Montage Engine 2.0 style profiles. */
public final class MontageStyleProfiles {
    private static final Map<String, MontageStyleProfile> BUILT_INS = build();

    private MontageStyleProfiles() {
    }

    public static List<MontageStyleProfile> all() {
        return List.copyOf(BUILT_INS.values());
    }

    public static Optional<MontageStyleProfile> get(String id) {
        return Optional.ofNullable(BUILT_INS.get(id));
    }

    private static Map<String, MontageStyleProfile> build() {
        Map<String, MontageStyleProfile> map = new LinkedHashMap<>();
        put(map, profile("clean_cinematic", MontagePacing.MODERATE, CameraMovementIntensity.LOW,
                ShotLengthProfile.MEDIUM, FramingDistribution.BALANCED,
                Set.of(ORBIT, DOLLY_IN, DOLLY_OUT, REVEAL, STATIC_TRACKING),
                Set.of(SPIRAL), FovStyle.STABLE, CollisionPolicy.STRICT, true, false));
        put(map, profile("high_energy", MontagePacing.FAST, CameraMovementIntensity.HIGH,
                ShotLengthProfile.VERY_SHORT, FramingDistribution.CLOSE_HEAVY,
                Set.of(CHASE, FLYBY, SIDE_TRACKING, FOLLOW, SPIRAL),
                Set.of(), FovStyle.DRAMATIC, CollisionPolicy.CINEMATIC_PRIORITY, true, true));
        put(map, profile("documentary", MontagePacing.NARRATIVE, CameraMovementIntensity.MINIMAL,
                ShotLengthProfile.LONG, FramingDistribution.BALANCED,
                Set.of(STATIC_TRACKING, FOLLOW, SIDE_TRACKING, REVEAL),
                Set.of(SPIRAL, ORBIT), FovStyle.STABLE, CollisionPolicy.BALANCED, true, false));
        put(map, profile("vehicle_showcase", MontagePacing.MODERATE, CameraMovementIntensity.MEDIUM,
                ShotLengthProfile.MIXED, FramingDistribution.BALANCED,
                Set.of(VEHICLE_PROFILE, SIDE_TRACKING, CHASE, FLYBY, CLOSE_DETAIL),
                Set.of(), FovStyle.WIDE_BIAS, CollisionPolicy.BALANCED, true, false));
        put(map, profile("architecture_showcase", MontagePacing.CINEMATIC, CameraMovementIntensity.LOW,
                ShotLengthProfile.LONG, FramingDistribution.WIDE_HEAVY,
                Set.of(ORBIT, CRANE_UP, CRANE_DOWN, REVEAL, DOLLY_IN),
                Set.of(CHASE), FovStyle.WIDE_BIAS, CollisionPolicy.STRICT, true, false));
        put(map, profile("trailer", MontagePacing.FAST, CameraMovementIntensity.HIGH,
                ShotLengthProfile.SHORT, FramingDistribution.CLOSE_HEAVY,
                Set.of(FLYBY, CHASE, DOLLY_IN, CRANE_UP, REVEAL, CLOSE_DETAIL),
                Set.of(), FovStyle.DRAMATIC, CollisionPolicy.CINEMATIC_PRIORITY, false, true));
        put(map, profile("vertical_fast_cut", MontagePacing.FAST, CameraMovementIntensity.MEDIUM,
                ShotLengthProfile.VERY_SHORT, FramingDistribution.CLOSE_HEAVY,
                Set.of(FOLLOW, CHASE, CLOSE_DETAIL, SIDE_TRACKING, REVEAL),
                Set.of(ORBIT), FovStyle.TELEPHOTO_BIAS, CollisionPolicy.BALANCED, true, false));
        put(map, profile("slow_atmospheric", MontagePacing.CINEMATIC, CameraMovementIntensity.MINIMAL,
                ShotLengthProfile.LONG, FramingDistribution.WIDE_HEAVY,
                Set.of(CRANE_UP, DOLLY_OUT, ORBIT, STATIC_TRACKING, REVEAL),
                Set.of(CHASE, SPIRAL), FovStyle.STABLE, CollisionPolicy.STRICT, true, false));
        put(map, profile("action_tracking", MontagePacing.FAST, CameraMovementIntensity.HIGH,
                ShotLengthProfile.SHORT, FramingDistribution.BALANCED,
                Set.of(CHASE, FOLLOW, SIDE_TRACKING, FLYBY, STATIC_TRACKING),
                Set.of(), FovStyle.STABLE, CollisionPolicy.BALANCED, true, true));
        put(map, profile("train_journey", MontagePacing.MODERATE, CameraMovementIntensity.MEDIUM,
                ShotLengthProfile.MIXED, FramingDistribution.BALANCED,
                Set.of(SIDE_TRACKING, STATIC_TRACKING, VEHICLE_PROFILE, FLYBY, CLOSE_DETAIL, DOLLY_OUT),
                Set.of(SPIRAL), FovStyle.WIDE_BIAS, CollisionPolicy.BALANCED, true, false));
        put(map, profile("flight_showcase", MontagePacing.MODERATE, CameraMovementIntensity.HIGH,
                ShotLengthProfile.MIXED, FramingDistribution.WIDE_HEAVY,
                Set.of(CHASE, FLYBY, ORBIT, SPIRAL, CRANE_UP, VEHICLE_PROFILE, SIDE_TRACKING),
                Set.of(), FovStyle.WIDE_BIAS, CollisionPolicy.CINEMATIC_PRIORITY, true, false));
        return Map.copyOf(map);
    }

    private static void put(Map<String, MontageStyleProfile> map, MontageStyleProfile profile) {
        map.put(profile.id(), profile);
    }

    private static MontageStyleProfile profile(
            String id, MontagePacing pacing, CameraMovementIntensity intensity,
            ShotLengthProfile length, FramingDistribution framing,
            Set<ShotType> preferred, Set<ShotType> restricted,
            FovStyle fov, CollisionPolicy collision, boolean chronology, boolean speed
    ) {
        return new MontageStyleProfile(id, pacing, intensity, length, framing, preferred, restricted,
                TransitionStyle.HARD_CUT_ONLY, fov, collision, chronology, speed);
    }
}
