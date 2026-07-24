package pl.peterwolf.cinewolf.input;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

/** Client keybinds for marking montage-worthy moments while watching a replay. */
public final class CineWolfKeybinds {
    public static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath("cinewolf_autodirector", "main"));

    /** Drop a short highlight window around the current replay tick. */
    public static KeyMapping MARK_MOMENT;
    /** First press starts a fragment; second press ends and saves it. */
    public static KeyMapping MARK_FRAGMENT;
    /** Cancel an unfinished fragment start. */
    public static KeyMapping CANCEL_FRAGMENT;

    private CineWolfKeybinds() {
    }

    public static void register() {
        MARK_MOMENT = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.cinewolf.mark_moment",
                InputConstants.Type.KEYSYM,
                InputConstants.KEY_H,
                CATEGORY));
        MARK_FRAGMENT = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.cinewolf.mark_fragment",
                InputConstants.Type.KEYSYM,
                InputConstants.KEY_J,
                CATEGORY));
        CANCEL_FRAGMENT = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.cinewolf.cancel_fragment",
                InputConstants.Type.KEYSYM,
                InputConstants.KEY_K,
                CATEGORY));
    }
}
