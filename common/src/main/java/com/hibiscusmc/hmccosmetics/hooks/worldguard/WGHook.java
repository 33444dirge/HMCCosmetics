package com.hibiscusmc.hmccosmetics.hooks.worldguard;

import com.hibiscusmc.hmccosmetics.util.MessagesUtil;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.flags.StringFlag;
import com.sk89q.worldguard.protection.flags.registry.FlagConflictException;
import com.sk89q.worldguard.protection.flags.registry.FlagRegistry;

import java.util.logging.Level;

/**
 * A hook that integrates the plugin {@link com.sk89q.worldguard.WorldGuard WorldGuard}
 */
public class WGHook {
    /**
     * @implNote Please use {@link #getCosmeticEnableFlag()} instead
     */
    private static StateFlag COSMETIC_ENABLE_FLAG;

    private static StateFlag EMOTES_ENABLE_FLAG;

    /**
     * @implNote Please use {@link #getCosmeticWardrobeFlag()} instead
     */
    private static StringFlag COSMETIC_WARDROBE_FLAG;

    public WGHook() {
        FlagRegistry registry = WorldGuard.getInstance().getFlagRegistry();
        // WorldGuard never unregisters flags and locks its registry once it has enabled. On a PlugManX
        // reload the flags from the previous load are still there and registering again throws, so reuse them.
        COSMETIC_ENABLE_FLAG = registerFlag(registry, new StateFlag("cosmetic-enable", false), StateFlag.class);
        EMOTES_ENABLE_FLAG = registerFlag(registry, new StateFlag("emotes-enable", false), StateFlag.class);
        COSMETIC_WARDROBE_FLAG = registerFlag(registry, new StringFlag("cosmetic-wardrobe"), StringFlag.class);
    }

    /**
     * Whether the region flags are available. False when HMCCosmetics is loaded for the first time after
     * WorldGuard has already enabled (e.g. a first PlugManX load), since WorldGuard rejects new flags then.
     */
    public static boolean isHooked() {
        return COSMETIC_ENABLE_FLAG != null && COSMETIC_WARDROBE_FLAG != null;
    }

    private static <T extends Flag<?>> T registerFlag(FlagRegistry registry, T flag, Class<T> type) {
        Flag<?> existing = registry.get(flag.getName());
        if (existing == null) {
            try {
                registry.register(flag);
                return flag;
            } catch (FlagConflictException | IllegalStateException e) {
                existing = registry.get(flag.getName());
            }
        }
        if (type.isInstance(existing)) return type.cast(existing);
        // types don't match - this is bad news! some other plugin conflicts with you
        // hopefully this never actually happens
        MessagesUtil.sendDebugMessages("WorldGuard Unable to be hooked! Flag " + flag.getName() + " could not be registered.", Level.SEVERE);
        return null;
    }

    /**
     * Gets the cosmetic enable {@link StateFlag}
     * @return The cosmetic enable {@link StateFlag}
     */
    public static StateFlag getCosmeticEnableFlag() {
        return COSMETIC_ENABLE_FLAG;
    }

    /**
     * Gets the emotes enable {@link StateFlag}
     * @return The emotes enable {@link StateFlag}
     */
    public static StateFlag getEmotesEnableFlag() {
        return EMOTES_ENABLE_FLAG;
    }

    /**
     * Gets the cosmetic wardrobe {@link StateFlag}
     * @return The cosmetic wardrobe {@link StateFlag}
     */
    public static StringFlag getCosmeticWardrobeFlag() {
        return COSMETIC_WARDROBE_FLAG;
    }

}
