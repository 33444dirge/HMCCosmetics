package com.hibiscusmc.hmccosmetics.listener;

import com.hibiscusmc.hmccosmetics.cosmetic.CosmeticSlot;
import com.hibiscusmc.hmccosmetics.user.CosmeticUser;
import com.hibiscusmc.hmccosmetics.user.CosmeticUsers;
import com.hibiscusmc.hmccosmetics.util.HMCCScheduler;
import io.papermc.paper.event.entity.EntityEquipmentChangedEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.EquipmentSlot;

public class PaperPlayerGameListener implements Listener {

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onEquipmentChange(EntityEquipmentChangedEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        CosmeticUser user = CosmeticUsers.getUser(player);
        if (user == null || user.isInWardrobe()) return;

        for (EquipmentSlot slot : event.getEquipmentChanges().keySet()) {
            CosmeticSlot cosmeticSlot = equipmentSlotToCosmeticType(slot);
            if (cosmeticSlot != null) user.updateCosmetic(cosmeticSlot);
        }

        HMCCScheduler.runEntityLater(player, player::updateInventory, 2);
    }

    private CosmeticSlot equipmentSlotToCosmeticType(EquipmentSlot equipmentSlot) {
        return switch (equipmentSlot) {
            case HEAD -> CosmeticSlot.HELMET;
            case FEET -> CosmeticSlot.BOOTS;
            case LEGS -> CosmeticSlot.LEGGINGS;
            case CHEST -> CosmeticSlot.CHESTPLATE;
            default -> null;
        };
    }

}
