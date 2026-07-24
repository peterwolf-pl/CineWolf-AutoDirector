package pl.peterwolf.cinewolf.config;

import pl.peterwolf.cinewolf.model.ShotType;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * User overrides for which montage shot generators may run and how far/high the camera may be placed.
 * Gson-friendly mutable fields; empty {@link #enabledShotTypes} means “all registered types”.
 */
public final class MontageShotSettings {
    /** Canonical type names (e.g. {@code FOLLOW}). Empty/null = all types enabled. */
    public List<String> enabledShotTypes = new ArrayList<>();
    public double minimumDistance = 2.0;
    public double maximumDistance = 48.0;
    public double minimumHeight = 0.5;
    public double maximumHeight = 24.0;
    public double minimumOrbitDiameter = 3.0;
    public double maximumOrbitDiameter = 80.0;
    public double lookAheadSeconds = 0.2;

    public void normalize() {
        if (enabledShotTypes == null) enabledShotTypes = new ArrayList<>();
        LinkedHashSet<String> cleaned = new LinkedHashSet<>();
        for (String raw : enabledShotTypes) {
            if (raw == null || raw.isBlank()) continue;
            try {
                cleaned.add(ShotType.valueOf(raw.trim().toUpperCase(Locale.ROOT)).name());
            } catch (IllegalArgumentException ignored) {
                // drop unknown names from older configs
            }
        }
        enabledShotTypes = new ArrayList<>(cleaned);
        minimumDistance = clamp(minimumDistance, 2.0, 0.5, 128.0);
        maximumDistance = clamp(maximumDistance, 48.0, minimumDistance, 256.0);
        if (maximumDistance < minimumDistance) maximumDistance = minimumDistance;
        minimumHeight = clamp(minimumHeight, 0.5, -16.0, 64.0);
        maximumHeight = clamp(maximumHeight, 24.0, minimumHeight, 128.0);
        if (maximumHeight < minimumHeight) maximumHeight = minimumHeight;
        minimumOrbitDiameter = clamp(minimumOrbitDiameter, 3.0, 1.0, 160.0);
        maximumOrbitDiameter = clamp(maximumOrbitDiameter, 80.0, minimumOrbitDiameter, 320.0);
        if (maximumOrbitDiameter < minimumOrbitDiameter) maximumOrbitDiameter = minimumOrbitDiameter;
        lookAheadSeconds = clamp(lookAheadSeconds, 0.2, 0.0, 3.0);
    }

    public boolean isEnabled(ShotType type) {
        if (type == null) return false;
        if (enabledShotTypes == null || enabledShotTypes.isEmpty()) return true;
        return enabledShotTypes.contains(type.name());
    }

    public void setEnabled(ShotType type, boolean enabled) {
        Objects.requireNonNull(type, "type");
        normalize();
        if (enabledShotTypes.isEmpty() && enabled) {
            // already all-enabled
            return;
        }
        if (enabledShotTypes.isEmpty() && !enabled) {
            // materialize all except the disabled one
            for (ShotType value : ShotType.values()) {
                if (value != type) enabledShotTypes.add(value.name());
            }
            return;
        }
        if (enabled) {
            if (!enabledShotTypes.contains(type.name())) enabledShotTypes.add(type.name());
        } else {
            enabledShotTypes.remove(type.name());
            if (enabledShotTypes.isEmpty()) {
                // keep at least one type so planning never has an empty set
                enabledShotTypes.add(ShotType.FOLLOW.name());
            }
        }
    }

    public void enableAll() {
        enabledShotTypes = new ArrayList<>();
    }

    /**
     * Intersection of user-enabled types with generators currently registered.
     * Falls back to all available generators if the user disabled everything usable.
     */
    public Set<ShotType> resolvedAllowedTypes(Set<ShotType> registered) {
        normalize();
        Set<ShotType> available = registered == null || registered.isEmpty()
                ? EnumSet.allOf(ShotType.class) : EnumSet.copyOf(registered);
        if (enabledShotTypes.isEmpty()) return Set.copyOf(available);
        EnumSet<ShotType> selected = EnumSet.noneOf(ShotType.class);
        for (String name : enabledShotTypes) {
            try {
                ShotType type = ShotType.valueOf(name);
                if (available.contains(type)) selected.add(type);
            } catch (IllegalArgumentException ignored) {
            }
        }
        if (selected.isEmpty()) return Set.copyOf(available);
        return Set.copyOf(selected);
    }

    public MontageShotPreferences toPreferences(Set<ShotType> registered) {
        normalize();
        return new MontageShotPreferences(resolvedAllowedTypes(registered), minimumDistance, maximumDistance,
                minimumHeight, maximumHeight, minimumOrbitDiameter, maximumOrbitDiameter, lookAheadSeconds);
    }

    private static double clamp(double value, double fallback, double minimum, double maximum) {
        if (!Double.isFinite(value)) value = fallback;
        return Math.max(minimum, Math.min(maximum, value));
    }

    /** Immutable planning snapshot derived from {@link MontageShotSettings}. */
    public record MontageShotPreferences(
            Set<ShotType> allowedShotTypes,
            double minimumDistance,
            double maximumDistance,
            double minimumHeight,
            double maximumHeight,
            double minimumOrbitDiameter,
            double maximumOrbitDiameter,
            double lookAheadSeconds
    ) {
        public MontageShotPreferences {
            allowedShotTypes = Set.copyOf(Objects.requireNonNullElse(allowedShotTypes, Set.of()));
            if (allowedShotTypes.isEmpty()) {
                allowedShotTypes = Set.copyOf(EnumSet.allOf(ShotType.class));
            }
            minimumDistance = Math.max(0.5, minimumDistance);
            maximumDistance = Math.max(minimumDistance, maximumDistance);
            minimumHeight = Double.isFinite(minimumHeight) ? minimumHeight : 0.5;
            maximumHeight = Math.max(minimumHeight, maximumHeight);
            minimumOrbitDiameter = Math.max(1.0, minimumOrbitDiameter);
            maximumOrbitDiameter = Math.max(minimumOrbitDiameter, maximumOrbitDiameter);
            lookAheadSeconds = Math.max(0.0, Math.min(3.0, lookAheadSeconds));
        }

        public static MontageShotPreferences defaults() {
            return new MontageShotPreferences(EnumSet.allOf(ShotType.class), 2.0, 48.0, 0.5, 24.0,
                    3.0, 80.0, 0.2);
        }

        public double clampDistance(double value) {
            return Math.max(minimumDistance, Math.min(maximumDistance, value));
        }

        public double clampHeight(double value) {
            return Math.max(minimumHeight, Math.min(maximumHeight, value));
        }

        public double clampOrbitDiameter(double value) {
            return Math.max(minimumOrbitDiameter, Math.min(maximumOrbitDiameter, value));
        }
    }
}
