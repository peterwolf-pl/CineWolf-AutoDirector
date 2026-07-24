package pl.peterwolf.cinewolf.api.v2;

import pl.peterwolf.cinewolf.montage.preset.MontagePreset;

import java.util.List;

/** Supplies additional local montage presets (no network access). */
public interface PresetProvider {
    String providerId();

    String displayName();

    List<MontagePreset> presets();
}
