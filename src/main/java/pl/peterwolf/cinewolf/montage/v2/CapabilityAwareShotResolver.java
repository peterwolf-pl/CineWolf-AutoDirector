package pl.peterwolf.cinewolf.montage.v2;

import pl.peterwolf.cinewolf.compatibility.FlashbackCapabilities;
import pl.peterwolf.cinewolf.model.ShotType;
import pl.peterwolf.cinewolf.shot.ShotGeneratorRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Filters shot candidates by generator registry and Flashback capabilities. */
public final class CapabilityAwareShotResolver {
    private final ShotGeneratorRegistry registry;

    public CapabilityAwareShotResolver(ShotGeneratorRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    public List<ShotType> resolve(List<ShotType> preferred, FlashbackCapabilities capabilities) {
        Set<ShotType> supported = registry.supportedTypes();
        List<ShotType> result = new ArrayList<>();
        for (ShotType type : preferred) {
            if (!supported.contains(type)) continue;
            if (capabilities != null && !capabilities.supportsCameraWriting()) continue;
            result.add(type);
        }
        if (result.isEmpty()) {
            for (ShotType type : supported) {
                result.add(type);
                if (result.size() >= 5) break;
            }
        }
        return List.copyOf(result);
    }

    public List<String> unsupportedReasons(List<ShotType> preferred, FlashbackCapabilities capabilities) {
        List<String> reasons = new ArrayList<>();
        Set<ShotType> supported = registry.supportedTypes();
        for (ShotType type : preferred) {
            if (!supported.contains(type)) {
                reasons.add("shot.generator.missing:" + type);
            }
        }
        if (capabilities != null && !capabilities.supportsCameraWriting()) {
            reasons.add("flashback.capability.camera_write_disabled");
        }
        if (capabilities != null && !capabilities.replayTimeKeyframes()) {
            reasons.add("flashback.capability.replay_time_disabled");
        }
        return reasons;
    }
}
