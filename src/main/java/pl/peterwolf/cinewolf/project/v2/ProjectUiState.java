package pl.peterwolf.cinewolf.project.v2;

import java.util.Objects;

public record ProjectUiState(
        String activePanel,
        String selectedPresetId,
        String selectedStyleId,
        boolean showWeakEvents,
        boolean showDiagnostics
) {
    public ProjectUiState {
        activePanel = Objects.requireNonNullElse(activePanel, "montage");
        selectedPresetId = Objects.requireNonNullElse(selectedPresetId, "");
        selectedStyleId = Objects.requireNonNullElse(selectedStyleId, "clean_cinematic");
    }

    public static ProjectUiState defaults() {
        return new ProjectUiState("montage", "", "clean_cinematic", false, false);
    }
}
