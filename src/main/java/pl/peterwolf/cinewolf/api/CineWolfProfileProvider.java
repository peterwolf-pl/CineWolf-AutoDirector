package pl.peterwolf.cinewolf.api;

import pl.peterwolf.cinewolf.model.ReplayContext;
import pl.peterwolf.cinewolf.model.ShotRequest;

import java.util.List;

public interface CineWolfProfileProvider {
    String providerId();

    boolean isAvailable();

    List<ShotPreset> getPresets(ReplayContext context);

    record ShotPreset(String id, String displayName, ShotRequest request) {
    }
}
