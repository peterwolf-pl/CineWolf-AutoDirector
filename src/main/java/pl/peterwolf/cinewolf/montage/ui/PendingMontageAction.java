package pl.peterwolf.cinewolf.montage.ui;

import java.util.Objects;
import java.util.UUID;

/** Identifies the one UI action allowed to consume a specific montage generation. */
public record PendingMontageAction(
        Type type,
        UUID montageId,
        long generationId,
        UUID shotId
) {
    public PendingMontageAction {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(montageId, "montageId");
        if (generationId < 0L) throw new IllegalArgumentException("generationId cannot be negative");
        if (type == Type.PREVIEW_SHOT) Objects.requireNonNull(shotId, "shotId");
        else if (shotId != null) throw new IllegalArgumentException("shotId is only valid for shot preview");
    }

    public static PendingMontageAction preview(UUID montageId, long generationId) {
        return new PendingMontageAction(Type.PREVIEW_MONTAGE, montageId, generationId, null);
    }

    public static PendingMontageAction previewShot(UUID montageId, long generationId, UUID shotId) {
        return new PendingMontageAction(Type.PREVIEW_SHOT, montageId, generationId, shotId);
    }

    public static PendingMontageAction write(UUID montageId, long generationId) {
        return new PendingMontageAction(Type.WRITE, montageId, generationId, null);
    }

    public boolean matches(UUID candidateMontageId, long candidateGenerationId) {
        return montageId.equals(candidateMontageId) && generationId == candidateGenerationId;
    }

    public enum Type {
        PREVIEW_MONTAGE,
        PREVIEW_SHOT,
        WRITE
    }
}
