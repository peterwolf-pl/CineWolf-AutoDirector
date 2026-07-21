package pl.peterwolf.cinewolf.shot;

import pl.peterwolf.cinewolf.api.ShotGenerator;
import pl.peterwolf.cinewolf.model.ShotType;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class ShotGeneratorRegistry {
    private final Map<ShotType, ShotGenerator> generators = new EnumMap<>(ShotType.class);

    public ShotGeneratorRegistry register(ShotType type, ShotGenerator generator) {
        generators.put(Objects.requireNonNull(type), Objects.requireNonNull(generator));
        return this;
    }

    public ShotGenerator require(ShotType type) {
        ShotGenerator generator = generators.get(type);
        if (generator == null) throw new IllegalArgumentException("No shot generator registered for " + type);
        return generator;
    }

    public boolean supports(ShotType type) {
        return type != null && generators.containsKey(type);
    }

    public Set<ShotType> supportedTypes() {
        if (generators.isEmpty()) return Set.of();
        return Collections.unmodifiableSet(EnumSet.copyOf(generators.keySet()));
    }

    public static ShotGeneratorRegistry createDefault() {
        return new ShotGeneratorRegistry()
                .register(ShotType.ORBIT, new OrbitShotGenerator())
                .register(ShotType.FOLLOW, new FollowShotGenerator())
                .register(ShotType.FLYBY, new FlybyShotGenerator())
                .register(ShotType.DOLLY_IN, new DollyInShotGenerator())
                .register(ShotType.DOLLY_OUT, new DollyOutShotGenerator());
    }
}
