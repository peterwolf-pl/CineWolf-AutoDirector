package pl.peterwolf.cinewolf.montage.preset;

public enum MontagePresetType {
    FIFTEEN_SECONDS("15_seconds"),
    THIRTY_SECONDS("30_seconds"),
    SIXTY_SECONDS("60_seconds"),
    TRAILER("trailer"),
    TIKTOK("tiktok"),
    YOUTUBE_SHORT("youtube_short"),
    CINEMATIC_SHOWCASE("cinematic_showcase");

    private final String id;

    MontagePresetType(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }
}
