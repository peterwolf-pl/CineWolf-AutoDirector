package pl.peterwolf.cinewolf.montage.ui;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PendingMontageActionTest {
    private static final UUID MONTAGE_ID = UUID.fromString("00112233-4455-6677-8899-aabbccddeeff");
    private static final UUID SHOT_ID = UUID.fromString("ffeeddcc-bbaa-9988-7766-554433221100");

    @Test
    void scopesActionsToBothMontageAndGeneration() {
        PendingMontageAction action = PendingMontageAction.write(MONTAGE_ID, 17L);

        assertTrue(action.matches(MONTAGE_ID, 17L));
        assertFalse(action.matches(UUID.randomUUID(), 17L));
        assertFalse(action.matches(MONTAGE_ID, 18L));
    }

    @Test
    void carriesShotOnlyForShotPreview() {
        PendingMontageAction shot = PendingMontageAction.previewShot(MONTAGE_ID, 4L, SHOT_ID);
        PendingMontageAction montage = PendingMontageAction.preview(MONTAGE_ID, 4L);

        assertEquals(PendingMontageAction.Type.PREVIEW_SHOT, shot.type());
        assertEquals(SHOT_ID, shot.shotId());
        assertNull(montage.shotId());
        assertThrows(NullPointerException.class,
                () -> PendingMontageAction.previewShot(MONTAGE_ID, 4L, null));
        assertThrows(IllegalArgumentException.class,
                () -> new PendingMontageAction(PendingMontageAction.Type.WRITE, MONTAGE_ID, 4L, SHOT_ID));
    }
}
