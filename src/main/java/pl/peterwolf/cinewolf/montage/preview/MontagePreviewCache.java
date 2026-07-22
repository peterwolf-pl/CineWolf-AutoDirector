package pl.peterwolf.cinewolf.montage.preview;

import pl.peterwolf.cinewolf.model.CameraPathPlan;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class MontagePreviewCache {
    private final Map<UUID, CameraPathPlan> paths = new LinkedHashMap<>();

    public void put(UUID shotId, CameraPathPlan path) {
        if (shotId == null || path == null) return;
        paths.put(shotId, path);
    }

    public Optional<CameraPathPlan> get(UUID shotId) {
        return Optional.ofNullable(paths.get(shotId));
    }

    public void clear() {
        paths.clear();
    }

    public int size() {
        return paths.size();
    }
}
