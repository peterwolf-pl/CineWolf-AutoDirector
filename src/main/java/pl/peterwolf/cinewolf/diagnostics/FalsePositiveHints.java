package pl.peterwolf.cinewolf.diagnostics;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class FalsePositiveHints {
    private FalsePositiveHints() {
    }

    public static List<String> forEvent(String eventType, double confidence) {
        List<String> hints = new ArrayList<>();
        String type = eventType == null ? "" : eventType.toUpperCase(Locale.ROOT);
        if (confidence < 0.45) {
            hints.add("weak_event_excluded_from_default_generation");
        }
        if (type.contains("HIGH_SPEED") || type.contains("ACCELERATION")) {
            hints.add("teleport_may_look_like_speed");
            hints.add("sampling_gap_may_look_like_acceleration");
        }
        if (type.contains("DEATH")) {
            hints.add("entity_unload_may_look_like_death");
        }
        if (type.contains("FLIGHT")) {
            hints.add("jump_may_look_like_flight");
        }
        if (type.contains("TURN")) {
            hints.add("stationary_yaw_noise_may_look_like_turn");
        }
        if (type.contains("ALTITUDE")) {
            hints.add("interpolation_noise_may_look_like_altitude_change");
        }
        if (type.contains("BLOCK")) {
            hints.add("block_update_burst_may_look_like_building");
        }
        if (type.contains("COMBAT") || type.contains("DAMAGE")) {
            hints.add("damage_animation_may_look_like_combat");
            hints.add("nearby_hostile_entity_is_not_combat_alone");
        }
        return List.copyOf(hints);
    }
}
