package pl.peterwolf.cinewolf.montage.scene;

import pl.peterwolf.cinewolf.model.TargetReference;
import pl.peterwolf.cinewolf.model.Vec3d;
import pl.peterwolf.cinewolf.montage.event.ReplayEvent;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record ReplayScene(
        UUID sceneId,
        long startReplayTime,
        long endReplayTime,
        Set<TargetReference> primaryTargets,
        Vec3d center,
        double spatialRadius,
        List<ReplayEvent> events,
        SceneType type,
        double importanceScore
) {
    public ReplayScene {
        Objects.requireNonNull(sceneId, "sceneId");
        if (startReplayTime < 0 || endReplayTime < startReplayTime) {
            throw new IllegalArgumentException("Invalid replay scene interval");
        }
        primaryTargets = Set.copyOf(Objects.requireNonNullElse(primaryTargets, Set.of()));
        center = Objects.requireNonNullElse(center, Vec3d.ZERO);
        spatialRadius = Double.isFinite(spatialRadius) ? Math.max(0.0, spatialRadius) : 0.0;
        events = List.copyOf(Objects.requireNonNullElse(events, List.of()));
        Objects.requireNonNull(type, "type");
        importanceScore = Double.isFinite(importanceScore) ? Math.max(0.0, Math.min(1.0, importanceScore)) : 0.0;
    }

    public static ReplayScene create(long start, long end, Set<TargetReference> targets, Vec3d center,
                                     double radius, List<ReplayEvent> events, SceneType type, double importance) {
        List<TargetReference> ordered = new ArrayList<>(targets);
        ordered.sort(Comparator.comparing(target -> target.uuid().toString()));
        StringBuilder key = new StringBuilder(type.name()).append(':').append(start).append(':').append(end);
        ordered.forEach(target -> key.append(':').append(target.uuid()));
        UUID id = UUID.nameUUIDFromBytes(key.toString().getBytes(StandardCharsets.UTF_8));
        return new ReplayScene(id, start, end, targets, center, radius, events, type, importance);
    }
}
