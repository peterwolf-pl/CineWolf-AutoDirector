package pl.peterwolf.cinewolf.preset.library;

import pl.peterwolf.cinewolf.montage.preset.MontagePreset;
import pl.peterwolf.cinewolf.montage.preset.OutputAspectRatio;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class PresetLibraryIndex {
    private final List<PresetLibraryEntry> entries = new ArrayList<>();

    public synchronized void clear() {
        entries.clear();
    }

    public synchronized void add(PresetLibraryEntry entry) {
        entries.add(Objects.requireNonNull(entry, "entry"));
    }

    public synchronized List<PresetLibraryEntry> all() {
        return List.copyOf(entries);
    }

    public synchronized Optional<PresetLibraryEntry> find(String id) {
        return entries.stream().filter(entry -> entry.id().equals(id)).findFirst();
    }

    public synchronized List<PresetLibraryEntry> search(PresetLibrarySearch query) {
        Objects.requireNonNull(query, "query");
        return entries.stream().filter(query::matches).toList();
    }
}
