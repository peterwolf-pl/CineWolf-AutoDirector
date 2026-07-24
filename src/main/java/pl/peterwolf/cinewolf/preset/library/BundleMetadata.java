package pl.peterwolf.cinewolf.preset.library;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public record BundleMetadata(
        List<String> tags,
        List<String> categories,
        String homepage,
        String notes,
        Map<String, String> attributes
) {
    public BundleMetadata {
        tags = List.copyOf(tags == null ? List.of() : tags);
        categories = List.copyOf(categories == null ? List.of() : categories);
        homepage = Objects.requireNonNullElse(homepage, "");
        notes = Objects.requireNonNullElse(notes, "");
        attributes = Map.copyOf(attributes == null ? Map.of() : attributes);
    }

    public static BundleMetadata empty() {
        return new BundleMetadata(List.of(), List.of(), "", "", Map.of());
    }
}
