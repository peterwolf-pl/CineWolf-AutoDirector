package pl.peterwolf.cinewolf.montage.highlight;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.slf4j.Logger;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Persists per-replay montage highlights (moments/fragments marked while watching).
 * Stored under the config directory so they survive restarts.
 */
public final class MontageHighlightStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type FILE_TYPE = new TypeToken<Map<String, List<StoredHighlight>>>() {}.getType();

    private final Path path;
    private final Logger logger;
    private final Map<String, CopyOnWriteArrayList<MontageHighlight>> byReplay = new LinkedHashMap<>();
    private Long pendingFragmentStartTick;
    private String activeReplayId = "";

    public MontageHighlightStore(Path path, Logger logger) {
        this.path = Objects.requireNonNull(path, "path");
        this.logger = Objects.requireNonNull(logger, "logger");
        load();
    }

    public synchronized void setActiveReplay(UUID replayId) {
        activeReplayId = replayId == null ? "" : replayId.toString();
    }

    public String activeReplayId() {
        return activeReplayId;
    }

    public List<MontageHighlight> highlightsForActiveReplay() {
        return highlightsFor(activeReplayId);
    }

    public List<MontageHighlight> highlightsFor(String replayId) {
        if (replayId == null || replayId.isBlank()) return List.of();
        CopyOnWriteArrayList<MontageHighlight> list = byReplay.get(replayId);
        if (list == null || list.isEmpty()) return List.of();
        return list.stream()
                .sorted(Comparator.comparingLong(MontageHighlight::startTick)
                        .thenComparingLong(MontageHighlight::endTick))
                .toList();
    }

    public synchronized MontageHighlight addMoment(long tick, String label, long paddingTicks) {
        requireReplay();
        MontageHighlight highlight = MontageHighlight.moment(tick, label, paddingTicks);
        listFor(activeReplayId).add(highlight);
        pendingFragmentStartTick = null;
        save();
        return highlight;
    }

    /**
     * First press starts a fragment; second press ends it and stores the highlight.
     * @return completed highlight, or empty when only the start was recorded
     */
    public synchronized java.util.Optional<MontageHighlight> toggleFragment(long tick, String label) {
        requireReplay();
        if (pendingFragmentStartTick == null) {
            pendingFragmentStartTick = tick;
            return java.util.Optional.empty();
        }
        long start = pendingFragmentStartTick;
        pendingFragmentStartTick = null;
        if (tick == start) tick = start + 1;
        MontageHighlight highlight = MontageHighlight.fragment(start, tick, label);
        listFor(activeReplayId).add(highlight);
        save();
        return java.util.Optional.of(highlight);
    }

    public synchronized boolean cancelPendingFragment() {
        if (pendingFragmentStartTick == null) return false;
        pendingFragmentStartTick = null;
        return true;
    }

    public Long pendingFragmentStartTick() {
        return pendingFragmentStartTick;
    }

    public synchronized boolean remove(UUID highlightId) {
        if (highlightId == null) return false;
        CopyOnWriteArrayList<MontageHighlight> list = byReplay.get(activeReplayId);
        if (list == null) return false;
        boolean removed = list.removeIf(h -> h.id().equals(highlightId));
        if (removed) save();
        return removed;
    }

    public synchronized void clearActive() {
        byReplay.remove(activeReplayId);
        pendingFragmentStartTick = null;
        save();
    }

    private void requireReplay() {
        if (activeReplayId == null || activeReplayId.isBlank()) {
            throw new IllegalStateException("No active replay for montage highlights");
        }
    }

    private CopyOnWriteArrayList<MontageHighlight> listFor(String replayId) {
        return byReplay.computeIfAbsent(replayId, ignored -> new CopyOnWriteArrayList<>());
    }

    private void load() {
        if (!Files.exists(path)) return;
        try {
            Map<String, List<StoredHighlight>> raw = GSON.fromJson(Files.readString(path), FILE_TYPE);
            if (raw == null) return;
            raw.forEach((replayId, highlights) -> {
                if (replayId == null || highlights == null) return;
                CopyOnWriteArrayList<MontageHighlight> list = new CopyOnWriteArrayList<>();
                for (StoredHighlight stored : highlights) {
                    try {
                        list.add(stored.toModel());
                    } catch (RuntimeException ignored) {
                        // skip corrupt entries
                    }
                }
                if (!list.isEmpty()) byReplay.put(replayId, list);
            });
        } catch (Exception exception) {
            logger.error("Unable to load montage highlights from {}", path, exception);
        }
    }

    private void save() {
        try {
            Files.createDirectories(path.getParent());
            Map<String, List<StoredHighlight>> raw = new LinkedHashMap<>();
            byReplay.forEach((replayId, highlights) -> {
                List<StoredHighlight> stored = new ArrayList<>();
                for (MontageHighlight highlight : highlights) {
                    stored.add(StoredHighlight.from(highlight));
                }
                if (!stored.isEmpty()) raw.put(replayId, stored);
            });
            Files.writeString(path, GSON.toJson(raw), StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.SYNC);
        } catch (IOException exception) {
            logger.error("Unable to save montage highlights to {}", path, exception);
        }
    }

    /** Gson-friendly DTO. */
    public static final class StoredHighlight {
        public String id;
        public long startTick;
        public long endTick;
        public String label;
        public String kind;

        public static StoredHighlight from(MontageHighlight highlight) {
            StoredHighlight stored = new StoredHighlight();
            stored.id = highlight.id().toString();
            stored.startTick = highlight.startTick();
            stored.endTick = highlight.endTick();
            stored.label = highlight.label();
            stored.kind = highlight.kind().name();
            return stored;
        }

        public MontageHighlight toModel() {
            UUID uuid = id == null || id.isBlank() ? UUID.randomUUID() : UUID.fromString(id);
            MontageHighlight.Kind resolved = MontageHighlight.Kind.MOMENT;
            if (kind != null) {
                try {
                    resolved = MontageHighlight.Kind.valueOf(kind);
                } catch (IllegalArgumentException ignored) {
                    // default
                }
            }
            return new MontageHighlight(uuid, startTick, endTick, label, resolved);
        }
    }
}
