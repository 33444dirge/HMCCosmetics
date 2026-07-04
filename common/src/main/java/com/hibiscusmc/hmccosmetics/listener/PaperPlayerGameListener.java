package com.hibiscusmc.hmccosmetics.listener;

import com.hibiscusmc.hmccosmetics.cosmetic.CosmeticSlot;
import com.hibiscusmc.hmccosmetics.user.CosmeticUser;
import com.hibiscusmc.hmccosmetics.user.CosmeticUsers;
import com.destroystokyo.paper.event.player.PlayerArmorChangeEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

public class PaperPlayerGameListener implements Listener {

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerArmorEquip(PlayerArmorChangeEvent event) {
        CosmeticUser user = CosmeticUsers.getUser(event.getPlayer());
        if (user == null || user.isInWardrobe()) return;

        user.updateCosmetic(slotTypeToCosmeticType(event.getSlotType()));
    }

    private CosmeticSlot slotTypeToCosmeticType(PlayerArmorChangeEvent.SlotType slotType) {
        return switch (slotType) {
            case HEAD -> CosmeticSlot.HELMET;
            case CHEST -> CosmeticSlot.CHESTPLATE;
            case LEGS -> CosmeticSlot.LEGGINGS;
            case FEET -> CosmeticSlot.BOOTS;
        };
    }

}
