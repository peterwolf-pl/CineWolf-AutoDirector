package pl.peterwolf.cinewolf.montage.preset;

import pl.peterwolf.cinewolf.model.ShotType;
import pl.peterwolf.cinewolf.montage.event.ReplayEventType;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import static pl.peterwolf.cinewolf.model.ShotType.CHASE;
import static pl.peterwolf.cinewolf.model.ShotType.CLOSE_DETAIL;
import static pl.peterwolf.cinewolf.model.ShotType.CRANE_DOWN;
import static pl.peterwolf.cinewolf.model.ShotType.CRANE_UP;
import static pl.peterwolf.cinewolf.model.ShotType.DOLLY_IN;
import static pl.peterwolf.cinewolf.model.ShotType.DOLLY_OUT;
import static pl.peterwolf.cinewolf.model.ShotType.FLYBY;
import static pl.peterwolf.cinewolf.model.ShotType.FOLLOW;
import static pl.peterwolf.cinewolf.model.ShotType.ORBIT;
import static pl.peterwolf.cinewolf.model.ShotType.REVEAL;
import static pl.peterwolf.cinewolf.model.ShotType.SIDE_TRACKING;
import static pl.peterwolf.cinewolf.model.ShotType.SPIRAL;
import static pl.peterwolf.cinewolf.model.ShotType.STATIC_TRACKING;
import static pl.peterwolf.cinewolf.model.ShotType.VEHICLE_PROFILE;
import static pl.peterwolf.cinewolf.montage.event.ReplayEventType.ACCELERATION;
import static pl.peterwolf.cinewolf.montage.event.ReplayEventType.ALTITUDE_GAIN;
import static pl.peterwolf.cinewolf.montage.event.ReplayEventType.ALTITUDE_LOSS;
import static pl.peterwolf.cinewolf.montage.event.ReplayEventType.BLOCK_DESTRUCTION;
import static pl.peterwolf.cinewolf.montage.event.ReplayEventType.BLOCK_PLACEMENT;
import static pl.peterwolf.cinewolf.montage.event.ReplayEventType.COMBAT;
import static pl.peterwolf.cinewolf.montage.event.ReplayEventType.DAMAGE;
import static pl.peterwolf.cinewolf.montage.event.ReplayEventType.DEATH;
import static pl.peterwolf.cinewolf.montage.event.ReplayEventType.FLIGHT;
import static pl.peterwolf.cinewolf.montage.event.ReplayEventType.FLIGHT_START;
import static pl.peterwolf.cinewolf.montage.event.ReplayEventType.HIGH_SPEED;
import static pl.peterwolf.cinewolf.montage.event.ReplayEventType.LANDING;
import static pl.peterwolf.cinewolf.montage.event.ReplayEventType.PAUSE;
import static pl.peterwolf.cinewolf.montage.event.ReplayEventType.POSITION_CHANGE;
import static pl.peterwolf.cinewolf.montage.event.ReplayEventType.REPLAY_MARKER;
import static pl.peterwolf.cinewolf.montage.event.ReplayEventType.SHARP_TURN;
import static pl.peterwolf.cinewolf.montage.event.ReplayEventType.VEHICLE_ENTER;
import static pl.peterwolf.cinewolf.montage.event.ReplayEventType.VEHICLE_EXIT;
import static pl.peterwolf.cinewolf.montage.event.ReplayEventType.VEHICLE_MOVEMENT;

public final class MontagePresetRegistry {
    private static final List<BuiltInDefinition> BUILT_INS = List.of(
            builtIn(MontagePresetType.FIFTEEN_SECONDS, new MontagePreset(
                    MontagePresetType.FIFTEEN_SECONDS.id(), "cinewolf.montage.preset.15_seconds", 15.0,
                    OutputAspectRatio.LANDSCAPE_16_9, MontagePacing.FAST,
                    1.5, 5.0, 3, 5,
                    Set.of(DOLLY_IN, FOLLOW, FLYBY, DOLLY_OUT, CHASE, REVEAL),
                    weights(weight(POSITION_CHANGE, 0.5), weight(HIGH_SPEED, 1.9), weight(ACCELERATION, 1.5),
                            weight(SHARP_TURN, 1.4), weight(COMBAT, 2.1), weight(DAMAGE, 1.7), weight(DEATH, 2.4),
                            weight(FLIGHT_START, 1.8), weight(FLIGHT, 1.6), weight(LANDING, 1.8),
                            weight(BLOCK_PLACEMENT, 0.6), weight(BLOCK_DESTRUCTION, 0.8), weight(PAUSE, 0.2),
                            weight(REPLAY_MARKER, 2.2)),
                    template("quick_establishing", List.of(REVEAL, DOLLY_IN, ORBIT), FramingType.WIDE, 2.5, 0.55),
                    template("strong_finish", List.of(DOLLY_OUT, CRANE_UP, ORBIT), FramingType.WIDE, 3.0, 0.65),
                    style(0.85, 0.9, FramingType.MEDIUM, 1.0, 1.0, 1.0, 0.0,
                            false, true, false)
            )),
            builtIn(MontagePresetType.THIRTY_SECONDS, new MontagePreset(
                    MontagePresetType.THIRTY_SECONDS.id(), "cinewolf.montage.preset.30_seconds", 30.0,
                    OutputAspectRatio.LANDSCAPE_16_9, MontagePacing.MODERATE,
                    2.5, 7.0, 5, 8,
                    Set.of(ORBIT, FOLLOW, FLYBY, DOLLY_IN, DOLLY_OUT, SIDE_TRACKING, CHASE, REVEAL),
                    weights(weight(HIGH_SPEED, 1.6), weight(ACCELERATION, 1.4), weight(SHARP_TURN, 1.4),
                            weight(COMBAT, 1.8), weight(DAMAGE, 1.5), weight(DEATH, 2.1),
                            weight(VEHICLE_MOVEMENT, 1.5), weight(FLIGHT, 1.5), weight(LANDING, 1.6),
                            weight(BLOCK_PLACEMENT, 0.9), weight(BLOCK_DESTRUCTION, 1.0), weight(PAUSE, 0.5),
                            weight(REPLAY_MARKER, 1.9)),
                    template("compact_establishing", List.of(REVEAL, DOLLY_IN, ORBIT), FramingType.WIDE, 4.0, 0.45),
                    template("compact_final_wide", List.of(DOLLY_OUT, CRANE_UP, ORBIT), FramingType.WIDE, 5.0, 0.45),
                    style(0.65, 0.65, FramingType.MEDIUM, 1.0, 1.0, 1.0, 0.0,
                            false, true, false)
            )),
            builtIn(MontagePresetType.SIXTY_SECONDS, new MontagePreset(
                    MontagePresetType.SIXTY_SECONDS.id(), "cinewolf.montage.preset.60_seconds", 60.0,
                    OutputAspectRatio.LANDSCAPE_16_9, MontagePacing.NARRATIVE,
                    3.0, 10.0, 8, 14,
                    Set.of(ORBIT, FOLLOW, FLYBY, DOLLY_IN, DOLLY_OUT, CRANE_UP, CRANE_DOWN, SPIRAL, SIDE_TRACKING,
                            CHASE, REVEAL, STATIC_TRACKING, VEHICLE_PROFILE, CLOSE_DETAIL),
                    weights(weight(POSITION_CHANGE, 1.2), weight(HIGH_SPEED, 1.5), weight(ACCELERATION, 1.3),
                            weight(SHARP_TURN, 1.3), weight(ALTITUDE_GAIN, 1.3), weight(ALTITUDE_LOSS, 1.2),
                            weight(COMBAT, 1.7), weight(DAMAGE, 1.4), weight(DEATH, 2.0),
                            weight(VEHICLE_ENTER, 1.2), weight(VEHICLE_EXIT, 1.1), weight(VEHICLE_MOVEMENT, 1.5),
                            weight(FLIGHT_START, 1.5), weight(FLIGHT, 1.6), weight(LANDING, 1.6),
                            weight(BLOCK_PLACEMENT, 1.1), weight(BLOCK_DESTRUCTION, 1.1), weight(PAUSE, 0.8),
                            weight(REPLAY_MARKER, 1.8)),
                    template("narrative_location_intro", List.of(REVEAL, CRANE_DOWN, DOLLY_IN, ORBIT),
                            FramingType.EXTREME_WIDE, 6.0, 0.35),
                    template("narrative_final_reveal", List.of(CRANE_UP, DOLLY_OUT, SPIRAL, ORBIT),
                            FramingType.EXTREME_WIDE, 7.0, 0.4),
                    style(0.55, 0.45, FramingType.MEDIUM, 1.0, 1.0, 1.0, 0.0,
                            false, true, false)
            )),
            builtIn(MontagePresetType.TRAILER, new MontagePreset(
                    MontagePresetType.TRAILER.id(), "cinewolf.montage.preset.trailer", 45.0,
                    OutputAspectRatio.LANDSCAPE_16_9, MontagePacing.PROGRESSIVE,
                    1.5, 8.0, 7, 12,
                    Set.of(ORBIT, FOLLOW, FLYBY, DOLLY_IN, DOLLY_OUT, CHASE, SPIRAL, CRANE_UP, REVEAL, VEHICLE_PROFILE),
                    weights(weight(POSITION_CHANGE, 0.7), weight(HIGH_SPEED, 2.0), weight(ACCELERATION, 1.8),
                            weight(SHARP_TURN, 1.7), weight(ALTITUDE_GAIN, 1.5), weight(COMBAT, 2.2),
                            weight(DAMAGE, 1.8), weight(DEATH, 2.5), weight(VEHICLE_ENTER, 1.4),
                            weight(VEHICLE_MOVEMENT, 2.0), weight(FLIGHT_START, 2.0), weight(FLIGHT, 2.0),
                            weight(LANDING, 1.8), weight(BLOCK_PLACEMENT, 0.7), weight(PAUSE, 1.0),
                            weight(REPLAY_MARKER, 2.2)),
                    template("atmospheric_intro", List.of(REVEAL, DOLLY_IN, ORBIT), FramingType.EXTREME_WIDE, 6.0, 0.3),
                    template("trailer_hero_finish", List.of(SPIRAL, ORBIT, DOLLY_OUT), FramingType.WIDE, 6.0, 0.6),
                    style(0.9, 0.8, FramingType.MEDIUM, 1.0, 0.5, 2.0, 0.5,
                            true, false, false)
            )),
            builtIn(MontagePresetType.TIKTOK, new MontagePreset(
                    MontagePresetType.TIKTOK.id(), "cinewolf.montage.preset.tiktok", 30.0,
                    OutputAspectRatio.VERTICAL_9_16, MontagePacing.FAST,
                    1.0, 4.5, 7, 12,
                    Set.of(FOLLOW, FLYBY, ORBIT, DOLLY_IN, DOLLY_OUT, CHASE, CLOSE_DETAIL, SIDE_TRACKING),
                    weights(weight(POSITION_CHANGE, 0.4), weight(HIGH_SPEED, 2.2), weight(ACCELERATION, 1.9),
                            weight(SHARP_TURN, 1.8), weight(COMBAT, 2.1), weight(DAMAGE, 1.8), weight(DEATH, 2.3),
                            weight(VEHICLE_MOVEMENT, 2.0), weight(FLIGHT_START, 2.0), weight(FLIGHT, 2.0),
                            weight(LANDING, 1.9), weight(BLOCK_PLACEMENT, 0.6), weight(BLOCK_DESTRUCTION, 0.8),
                            weight(PAUSE, 0.1), weight(REPLAY_MARKER, 2.0)),
                    template("vertical_hook", List.of(CLOSE_DETAIL, FOLLOW, FLYBY), FramingType.CLOSE, 2.0, 0.95),
                    template("vertical_strong_finish", List.of(DOLLY_OUT, CHASE, ORBIT), FramingType.MEDIUM, 2.5, 0.8),
                    style(0.95, 0.95, FramingType.CLOSE, 1.0, 0.65, 1.75, 0.5,
                            true, true, true)
            )),
            builtIn(MontagePresetType.YOUTUBE_SHORT, new MontagePreset(
                    MontagePresetType.YOUTUBE_SHORT.id(), "cinewolf.montage.preset.youtube_short", 45.0,
                    OutputAspectRatio.VERTICAL_9_16, MontagePacing.MODERATE,
                    2.0, 7.5, 7, 12,
                    Set.of(FOLLOW, FLYBY, ORBIT, DOLLY_IN, DOLLY_OUT, CHASE, SIDE_TRACKING, REVEAL, CLOSE_DETAIL),
                    weights(weight(POSITION_CHANGE, 0.7), weight(HIGH_SPEED, 1.9), weight(ACCELERATION, 1.6),
                            weight(SHARP_TURN, 1.6), weight(COMBAT, 1.9), weight(DAMAGE, 1.6), weight(DEATH, 2.2),
                            weight(VEHICLE_MOVEMENT, 1.8), weight(FLIGHT_START, 1.8), weight(FLIGHT, 1.8),
                            weight(LANDING, 1.8), weight(BLOCK_PLACEMENT, 0.8), weight(BLOCK_DESTRUCTION, 0.9),
                            weight(PAUSE, 0.4), weight(REPLAY_MARKER, 2.0)),
                    template("short_opening_hook", List.of(REVEAL, FOLLOW, DOLLY_IN), FramingType.MEDIUM, 3.0, 0.8),
                    template("short_final_wide", List.of(DOLLY_OUT, CRANE_UP, ORBIT), FramingType.WIDE, 5.0, 0.55),
                    style(0.75, 0.7, FramingType.MEDIUM, 1.0, 0.7, 1.5, 0.4,
                            true, true, true)
            )),
            builtIn(MontagePresetType.CINEMATIC_SHOWCASE, new MontagePreset(
                    MontagePresetType.CINEMATIC_SHOWCASE.id(), "cinewolf.montage.preset.cinematic_showcase", 60.0,
                    OutputAspectRatio.LANDSCAPE_16_9, MontagePacing.CINEMATIC,
                    4.0, 15.0, 5, 12,
                    Set.of(ORBIT, DOLLY_IN, DOLLY_OUT, FLYBY, FOLLOW, REVEAL, CRANE_UP, CRANE_DOWN, SPIRAL,
                            STATIC_TRACKING, SIDE_TRACKING, VEHICLE_PROFILE, CLOSE_DETAIL),
                    weights(weight(POSITION_CHANGE, 1.1), weight(HIGH_SPEED, 0.8), weight(ACCELERATION, 0.7),
                            weight(SHARP_TURN, 1.0), weight(ALTITUDE_GAIN, 1.4), weight(ALTITUDE_LOSS, 1.2),
                            weight(COMBAT, 0.7), weight(DAMAGE, 0.5), weight(DEATH, 0.6),
                            weight(VEHICLE_MOVEMENT, 1.5), weight(FLIGHT, 1.5), weight(LANDING, 1.3),
                            weight(BLOCK_PLACEMENT, 2.0), weight(BLOCK_DESTRUCTION, 1.4), weight(PAUSE, 1.4),
                            weight(REPLAY_MARKER, 1.9)),
                    template("showcase_reveal", List.of(REVEAL, CRANE_DOWN, DOLLY_IN, ORBIT), FramingType.EXTREME_WIDE, 8.0, 0.25),
                    template("showcase_final_wide", List.of(CRANE_UP, SPIRAL, DOLLY_OUT, ORBIT), FramingType.EXTREME_WIDE, 10.0, 0.3),
                    style(0.35, 0.25, FramingType.WIDE, 1.0, 1.0, 1.0, 0.0,
                            false, true, false)
            ))
    );

    private final Map<String, MontagePreset> presets = new LinkedHashMap<>();
    private final Map<MontagePresetType, String> builtInIds = new EnumMap<>(MontagePresetType.class);

    public MontagePresetRegistry register(MontagePreset preset) {
        Objects.requireNonNull(preset, "preset");
        if (presets.putIfAbsent(preset.id(), preset) != null) {
            throw new IllegalArgumentException("A montage preset with id '" + preset.id() + "' is already registered");
        }
        return this;
    }

    public Optional<MontagePreset> get(String id) {
        if (id == null) return Optional.empty();
        return Optional.ofNullable(presets.get(id.trim()));
    }

    public Optional<MontagePreset> get(MontagePresetType type) {
        if (type == null) return Optional.empty();
        return get(builtInIds.get(type));
    }

    public List<MontagePreset> all() {
        return List.copyOf(presets.values());
    }

    public static MontagePresetRegistry createDefault() {
        MontagePresetRegistry registry = new MontagePresetRegistry();
        for (BuiltInDefinition definition : BUILT_INS) {
            registry.register(definition.preset());
            registry.builtInIds.put(definition.type(), definition.preset().id());
        }
        return registry;
    }

    private static BuiltInDefinition builtIn(MontagePresetType type, MontagePreset preset) {
        if (!type.id().equals(preset.id())) {
            throw new IllegalArgumentException("Built-in preset id must match " + type);
        }
        return new BuiltInDefinition(type, preset);
    }

    private static ShotTemplate template(String id, List<ShotType> types, FramingType framing,
                                         double durationSeconds, double movementIntensity) {
        return new ShotTemplate(id, types, framing, durationSeconds, movementIntensity);
    }

    private static MontageStyleSettings style(double movement, double cuts, FramingType framing,
                                               double preferredSpeed, double minimumSpeed, double maximumSpeed,
                                               double maximumSpeedChange, boolean allowSpeedChanges,
                                               boolean chronological, boolean centerSafe) {
        return new MontageStyleSettings(movement, cuts, framing, preferredSpeed, minimumSpeed, maximumSpeed,
                maximumSpeedChange, allowSpeedChanges, chronological, centerSafe);
    }

    private static Map<ReplayEventType, Double> weights(Weight... overrides) {
        EnumMap<ReplayEventType, Double> result = new EnumMap<>(ReplayEventType.class);
        for (ReplayEventType type : ReplayEventType.values()) result.put(type, 1.0);
        for (Weight override : overrides) result.put(override.type(), override.value());
        return result;
    }

    private static Weight weight(ReplayEventType type, double value) {
        return new Weight(type, value);
    }

    private record BuiltInDefinition(MontagePresetType type, MontagePreset preset) {
    }

    private record Weight(ReplayEventType type, double value) {
    }
}
