package com.hibiscusmc.hmccosmetics.packets;

import com.hibiscusmc.hmccosmetics.config.Settings;
import com.hibiscusmc.hmccosmetics.cosmetic.Cosmetic;
import com.hibiscusmc.hmccosmetics.cosmetic.CosmeticSlot;
import com.hibiscusmc.hmccosmetics.cosmetic.types.CosmeticArmorType;
import com.hibiscusmc.hmccosmetics.cosmetic.types.CosmeticBackpackType;
import com.hibiscusmc.hmccosmetics.gui.Menu;
import com.hibiscusmc.hmccosmetics.user.CosmeticUser;
import com.hibiscusmc.hmccosmetics.user.CosmeticUsers;
import com.hibiscusmc.hmccosmetics.user.manager.UserBackpackManager;
import com.hibiscusmc.hmccosmetics.user.manager.UserWardrobeManager;
import com.hibiscusmc.hmccosmetics.listener.PlayerMovementListener;
import com.hibiscusmc.hmccosmetics.util.HMCCInventoryUtils;
import com.hibiscusmc.hmccosmetics.util.HMCCScheduler;
import com.hibiscusmc.hmccosmetics.util.MessagesUtil;
import com.hibiscusmc.hmccosmetics.util.packets.HMCCPacketManager;
import me.lojosho.hibiscuscommons.packets.PacketAction;
import me.lojosho.hibiscuscommons.packets.PacketInterface;
import me.lojosho.hibiscuscommons.packets.data.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class CosmeticPacketInterface implements PacketInterface {
    private static final Set<UUID> SCALE_REFRESHING = ConcurrentHashMap.newKeySet();

    @Override
    public @NotNull PacketAction writeContainerContent(@NotNull Player player, @NotNull ContainerContentWrapper wrapper) {
        int windowId = wrapper.getWindowId();
        MessagesUtil.sendDebugMessages("writeContainerContent (windowid: " + windowId + " )");
        if (windowId != 0) return PacketAction.NOTHING;
        List<ItemStack> slotData = wrapper.getSlotData();

        CosmeticUser user = CosmeticUsers.getUser(player);
        if (user == null) return PacketAction.NOTHING;

        HashMap<Integer, ItemStack> cosmeticItems = new HashMap<>();

        if (!user.isInWardrobe()) {
            for (Cosmetic cosmetic : user.getCosmetics()) {
                if (cosmetic instanceof CosmeticArmorType armorType) {
                    boolean requireEmpty = Settings.getSlotOption(armorType.getEquipSlot()).isRequireEmpty();
                    boolean isAir = user.getPlayer().getInventory().getItem(armorType.getEquipSlot()).getType().isAir();
                    MessagesUtil.sendDebugMessages("Menu Fired (Checks) - " + armorType.getId() + " - " + requireEmpty + " - " + isAir);
                    if (requireEmpty && !isAir) continue;
                    cosmeticItems.put(HMCCInventoryUtils.getPacketArmorSlot(armorType.getEquipSlot()), user.getUserCosmeticItem(armorType));
                }
            }
        }

        for (int slot = 0; slot < 46; slot++) {
            if ((slot >= 5 && slot <= 8) || slot == 45) {
                if (!cosmeticItems.containsKey(slot)) continue;
                slotData.set(slot, cosmeticItems.get(slot));
                if (Settings.isDebugMode()) MessagesUtil.sendDebugMessages("Set " + slot + " as " + cosmeticItems.get(slot));
            }
        }

        wrapper.setSlotData(slotData);
        MessagesUtil.sendDebugMessages("Menu Fired, updated cosmetics " + " on slotdata " + windowId + " with " + slotData.size());
        return PacketAction.CHANGED;
    }

    @Override
    public @NotNull PacketAction writeSlotContent(@NotNull Player player, @NotNull SlotContentWrapper wrapper) {
        int windowId = wrapper.getWindowId();
        int slot = wrapper.getSlot();
        ItemStack itemStack = wrapper.getItemStack();

        MessagesUtil.sendDebugMessages("SetSlot Initial ");
        if (windowId != 0) return PacketAction.NOTHING;

        CosmeticUser user = CosmeticUsers.getUser(player);
        if (user == null || user.isInWardrobe()) return PacketAction.NOTHING;

        MessagesUtil.sendDebugMessages("SetSlot Slot " + slot);
        CosmeticSlot cosmeticSlot = HMCCInventoryUtils.NMSCosmeticSlot(slot);
        EquipmentSlot equipmentSlot = HMCCInventoryUtils.getPacketArmorSlot(slot);
        if (cosmeticSlot == null || equipmentSlot == null) return PacketAction.NOTHING;
        if (!user.hasCosmeticInSlot(cosmeticSlot)) return PacketAction.NOTHING;
        if (Settings.getSlotOption(equipmentSlot).isRequireEmpty()) {
            if (!player.getInventory().getItem(equipmentSlot).getType().isAir()) return PacketAction.NOTHING;
        }
        wrapper.setItemStack(user.getUserCosmeticItem(cosmeticSlot));
        return PacketAction.CHANGED;
    }

    @Override
    public @NotNull PacketAction writeEquipmentContent(@NotNull Player player, @NotNull EntityEquipmentWrapper wrapper) {
        CosmeticUser user = CosmeticUsers.getUser(player);
        if (user == null || user.getEntityId() != wrapper.getEntityId()) return PacketAction.NOTHING;
        if (user.isInWardrobe()) return PacketAction.NOTHING;
        Map<EquipmentSlot, ItemStack> armor = wrapper.getArmor();

        for (Map.Entry<EquipmentSlot, ItemStack> armorSlot : armor.entrySet()) {
            EquipmentSlot slot = armorSlot.getKey();

            if (slot == EquipmentSlot.HAND) {
                if (user.getPlayer().getUniqueId() == player.getUniqueId())
                    continue; // When a player scrolls real fast, it messes up the mainhand. This fixes it
                if (user.getPlayer() != null && user.getPlayer().isInvisible())
                    continue; // Fixes integration with GSit still showing mainhand even when hidden
                armor.put(slot, player.getInventory().getItemInMainHand());
            } else {
                CosmeticSlot cosmeticSlot = HMCCInventoryUtils.BukkitCosmeticSlot(slot);
                if (cosmeticSlot == null) continue;
                if (Settings.getSlotOption(slot).isRequireEmpty() && player.getInventory().getItem(slot).getType().isAir())
                    continue;

                CosmeticArmorType cosmeticArmor = (CosmeticArmorType) user.getCosmetic(cosmeticSlot);
                if (cosmeticArmor == null) continue;
                ItemStack item = user.getUserCosmeticItem(cosmeticSlot);
                if (item == null) continue;
                armor.put(slot, item);
            }
        }

        wrapper.setArmor(armor);
        MessagesUtil.sendDebugMessages("Equipment for " + user.getPlayer().getName() + " has been updated for " + player.getName());
        return PacketAction.CHANGED;
    }

    @Override
    public @NotNull PacketAction writePassengerContent(@NotNull Player player, @NotNull PassengerWrapper wrapper) {
        if (!Settings.isBackpackInterceptPassengerPacket()) return PacketAction.NOTHING;

        CosmeticUser viewerUser = CosmeticUsers.getUser(player);
        if (viewerUser == null || viewerUser.isInWardrobe()) return PacketAction.NOTHING;

        int ownerId = wrapper.getOwner();

        CosmeticUser user = CosmeticUsers.getUser(ownerId);
        if (user == null) return PacketAction.NOTHING;
        if (user.isHidingBackpackPose()) return PacketAction.NOTHING;

        Cosmetic backpackCosmetic = user.getCosmetic(CosmeticSlot.BACKPACK);
        if (backpackCosmetic == null) return PacketAction.NOTHING;
        if (!(backpackCosmetic instanceof CosmeticBackpackType cosmeticBackpackType)) return PacketAction.NOTHING;
        // If a player is viewing their own backpack, don't do anything
        if (user.getUniqueId().equals(viewerUser.getUniqueId())) {
            if (cosmeticBackpackType.isFirstPersonCompadible()) return PacketAction.NOTHING;
        }

        if (user.getUserBackpackManager() == null) return PacketAction.NOTHING;

        List<Integer> originalPassengers = wrapper.getPassengers();
        List<Integer> passengers = new ArrayList<>(user.getUserBackpackManager().getFirstArmorStandId());
        passengers.addAll(originalPassengers);
        wrapper.setPassengers(passengers);
        return PacketAction.CHANGED;
    }

    @Override
    public PacketAction readPlayerScale(@NotNull Player player, @NotNull PlayerScaleWrapper wrapper) {
        int entityId = wrapper.getEntityId();
        CosmeticUser cosmeticUser = CosmeticUsers.getUser(entityId);
        if (cosmeticUser == null || cosmeticUser.isInWardrobe()) return PacketAction.NOTHING;

        UserBackpackManager backpack = cosmeticUser.getUserBackpackManager();
        if (backpack != null) {
            for (int cosmeticId : backpack.getEntityManager().getIds()) {
                HMCCPacketManager.sendEntityScalePacket(cosmeticId, wrapper.getScale(), Collections.singletonList(player));
            }
        }
        if (cosmeticUser.isBalloonSpawned()) {
            HMCCPacketManager.sendEntityScalePacket(cosmeticUser.getBalloonManager().getPufferfishBalloonId(), wrapper.getScale(), Collections.singletonList(player));
            HMCCPacketManager.sendEntityScalePacket(cosmeticUser.getBalloonManager().getModelId(), wrapper.getScale(), Collections.singletonList(player));
        }
        if (SCALE_REFRESHING.add(cosmeticUser.getUniqueId())) {
            Player changedPlayer = cosmeticUser.getPlayer();
            if (changedPlayer == null) {
                SCALE_REFRESHING.remove(cosmeticUser.getUniqueId());
                return PacketAction.NOTHING;
            }
            HMCCScheduler.runEntityLater(changedPlayer, () -> {
                try {
                    CosmeticUser user = CosmeticUsers.getUser(cosmeticUser.getUniqueId());
                    if (user == null || user.isInWardrobe()) return;
                    if (user.hasCosmeticInSlot(CosmeticSlot.BACKPACK)) user.respawnBackpack();
                    if (user.hasCosmeticInSlot(CosmeticSlot.BALLOON)) user.respawnBalloon();
                } finally {
                    SCALE_REFRESHING.remove(cosmeticUser.getUniqueId());
                }
            }, 1);
        }

        return PacketAction.NOTHING;
    }

    @Override
    public @NotNull PacketAction readPlayerPosition(@NotNull Player player, @NotNull PlayerPositionWrapper wrapper) {
        PlayerMovementListener.handlePositionPacket(player, wrapper);
        return PacketAction.NOTHING;
    }

    @Override
    public @NotNull PacketAction readPlayerRespawn(@NotNull Player player) {
        HMCCScheduler.runEntityLater(player, () -> {
            CosmeticUser user = CosmeticUsers.getUser(player);
            if (user == null || user.isInWardrobe()) return;
            if (user.hasCosmeticInSlot(CosmeticSlot.BACKPACK)) user.respawnBackpack();
        }, 4);
        return PacketAction.NOTHING;
    }

    @Override
    public @NotNull PacketAction readInventoryClick(@NotNull Player player, @NotNull InventoryClickWrapper wrapper) {
        int clickType = wrapper.getClickType();
        int slotNumber = wrapper.getSlotNumber();
        if (clickType != 0 || slotNumber == -999) return PacketAction.NOTHING;

        CosmeticUser user = CosmeticUsers.getUser(player);
        if (user == null || user.isInWardrobe()) return PacketAction.NOTHING;
        CosmeticSlot cosmeticSlot = HMCCInventoryUtils.NMSCosmeticSlot(slotNumber);
        if (cosmeticSlot == null || !user.hasCosmeticInSlot(cosmeticSlot)) return PacketAction.NOTHING;

        HMCCScheduler.runEntityLater(player, () -> user.updateCosmetic(cosmeticSlot), 1);
        MessagesUtil.sendDebugMessages("Packet fired, updated cosmetic " + cosmeticSlot);
        return PacketAction.NOTHING;
    }

    @Override
    public @NotNull PacketAction readPlayerAction(@NotNull Player player, @NotNull PlayerActionWrapper wrapper) {
        if (!Settings.isPreventOffhandSwapping()) return PacketAction.NOTHING;
        String actionType = wrapper.getActionType();
        // If it's not SWAP_ITEM_WITH_OFFHAND, ignore
        if (!actionType.equalsIgnoreCase("SWAP_ITEM_WITH_OFFHAND")) return PacketAction.NOTHING;

        CosmeticUser user = CosmeticUsers.getUser(player);
        if (user == null) {
            MessagesUtil.sendDebugMessages("EntityStatus User is null");
            return PacketAction.NOTHING;
        }
        MessagesUtil.sendDebugMessages("EntityStatus Initial " + user.getEntityId() + " - " + actionType);
        if (!user.hasCosmeticInSlot(CosmeticSlot.OFFHAND)) return PacketAction.NOTHING;
        return PacketAction.CANCELLED;
    }

    @Override
    public @NotNull PacketAction readPlayerArm(@NotNull Player player, @NotNull PlayerSwingWrapper wrapper) {
        CosmeticUser user = CosmeticUsers.getUser(player);
        if (user == null || !user.isInWardrobe() || !user.getWardrobeManager().getWardrobeStatus().equals(UserWardrobeManager.WardrobeStatus.RUNNING)) return PacketAction.NOTHING;

        Menu menu = user.getWardrobeManager().getLastOpenMenu();
        if (menu == null) return PacketAction.NOTHING;
        menu.openMenu(user);
        return PacketAction.CANCELLED;
    }

    @Override
    public @NotNull PacketAction readEntityHandle(@NotNull Player player, @NotNull PlayerInteractWrapper wrapper) {
        CosmeticUser user = CosmeticUsers.getUser(player);
        if (user == null || !user.isInWardrobe()) return PacketAction.NOTHING;
        else return PacketAction.CANCELLED;
    }
}
