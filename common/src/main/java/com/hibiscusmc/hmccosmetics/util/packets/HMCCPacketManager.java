package com.hibiscusmc.hmccosmetics.util.packets;

import com.hibiscusmc.hmccosmetics.HMCCosmeticsPlugin;
import com.hibiscusmc.hmccosmetics.config.Settings;
import com.hibiscusmc.hmccosmetics.cosmetic.CosmeticSlot;
import com.hibiscusmc.hmccosmetics.user.CosmeticUser;
import com.hibiscusmc.hmccosmetics.util.HMCCInventoryUtils;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import me.lojosho.hibiscuscommons.nms.MinecraftVersion;
import me.lojosho.hibiscuscommons.nms.NMSHandlers;
import me.lojosho.hibiscuscommons.nms.NMSPacketBuilder;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class HMCCPacketManager {

    // The cloud effect map, in case it gets lost: Map<Integer, Number> dataValues = Map.of(0, (byte) 0x20, 8, 0f);
    private static final Map<Integer, Number> CLOUD_EFFECT_INVISIBLE_DATA_VALUES = Map.of(0, (byte) 0x20, 8, 0f); // For
    private static final Map<Integer, Number> GENERIC_INVISIBLE_DATA_VALUES = Map.of(0, (byte) 0x20);
    private static final List<CosmeticSlot> EQUIPMENT_SLOTS = List.of(CosmeticSlot.HELMET, CosmeticSlot.CHESTPLATE, CosmeticSlot.LEGGINGS, CosmeticSlot.BOOTS, CosmeticSlot.MAINHAND, CosmeticSlot.OFFHAND);

    public static void sendPacket(Object packet, List<Player> sendTo) {
        NMSHandlers.getHandler().getPacketSender().sendPacket(packet, sendTo);
    }

    public static void sendEntitySpawnPacket(
            final @NotNull Location location,
            final int entityId,
            final EntityType entityType,
            final UUID uuid,
            final @NotNull List<Player> sendTo
    ) {
        sendPacket(NMSHandlers.getHandler().getPacketBuilder().buildEntitySpawnPacket(entityId, uuid, entityType, location), sendTo);
    }

    public static void equipmentSlotUpdate(
            Player player,
            boolean empty,
            List<Player> sendTo
    ) {
        HashMap<EquipmentSlot, ItemStack> items = new HashMap<>();
        for (EquipmentSlot slot : HMCCInventoryUtils.getPlayerArmorSlots()) {
            ItemStack item = player.getInventory().getItem(slot);
            if (empty) item = new ItemStack(Material.AIR);
            items.put(slot, item);
        }
        sendPacket(NMSHandlers.getHandler().getPacketBuilder().buildEntityEquipmentSlotUpdatePacket(player.getEntityId(), items), sendTo);
    }

    public static void equipmentSlotUpdate(
            int entityId,
            CosmeticUser user,
            CosmeticSlot cosmeticSlot,
            List<Player> sendTo
    ) {
        if (!EQUIPMENT_SLOTS.contains(cosmeticSlot)) return;
        sendPacket(NMSHandlers.getHandler().getPacketBuilder().buildEntityEquipmentSlotUpdatePacket(entityId, Map.of(HMCCInventoryUtils.getEquipmentSlot(cosmeticSlot), user.getUserCosmeticItem(cosmeticSlot))), sendTo);
    }

    public static void equipmentSlotUpdate(
            int entityId,
            EquipmentSlot equipmentSlot,
            ItemStack itemStack,
            List<Player> sendTo
    ) {
        sendPacket(NMSHandlers.getHandler().getPacketBuilder().buildEntityEquipmentSlotUpdatePacket(entityId, Map.of(equipmentSlot, itemStack)), sendTo);
    }

    public static void sendInvisibilityPacket(
            int entityId,
            List<Player> sendTo
    ) {
        sendPacket(NMSHandlers.getHandler().getPacketBuilder().buildEntityMetadataPacket(entityId, Map.of(0, (byte) 0x20)), sendTo);
    }

    public static void spawnInvisibleEntity(
            int entityId,
            EntityType entityType,
            Location location,
            UUID uuid,
            List<Player> sendTo
    ) {
        NMSPacketBuilder packetBuilder = NMSHandlers.getHandler().getPacketBuilder();
        List<Object> packets = new ArrayList<>();
        packets.add(packetBuilder.buildEntitySpawnPacket(entityId, uuid, entityType, location));
        packets.add(packetBuilder.buildEntityMetadataPacket(entityId, GENERIC_INVISIBLE_DATA_VALUES));
        NMSHandlers.getHandler().getPacketSender().sendBundle(packets, sendTo);
    }

    public static List<Object> getCloudHandleEffect(
            int entityId,
            Location location,
            UUID uuid
    ) {
        NMSPacketBuilder packetBuilder = NMSHandlers.getHandler().getPacketBuilder();
        List<Object> packets = new ArrayList<>();
        packets.add(packetBuilder.buildEntitySpawnPacket(entityId, uuid, EntityType.AREA_EFFECT_CLOUD, location));
        packets.add(packetBuilder.buildEntityMetadataPacket(entityId, CLOUD_EFFECT_INVISIBLE_DATA_VALUES));
        return packets;
    }

    public static List<Object> getInvisibleArmorStand(
            int entityId,
            Location location,
            UUID uuid
    ) {
        NMSPacketBuilder packetBuilder = NMSHandlers.getHandler().getPacketBuilder();
        List<Object> packets = new ArrayList<>();
        packets.add(packetBuilder.buildEntitySpawnPacket(entityId, uuid, EntityType.ARMOR_STAND, location));
        packets.add(packetBuilder.buildEntityMetadataPacket(entityId, getInvisibleArmorStandData()));
        return packets;
        //NMSHandlers.getHandler().getPacketSender().sendBundle(packets, sendTo);
    }

    /**
     * This is just a normal meta data packet (non-bundled)
     * @param entityId
     * @param sendTo
     */
    public static void sendArmorstandMetadata(
            int entityId,
            List<Player> sendTo
    ) {
        Map<Integer, Number> dataValues = getInvisibleArmorStandData();
        sendPacket(NMSHandlers.getHandler().getPacketBuilder().buildEntityMetadataPacket(entityId, dataValues), sendTo);
    }

    public static Map<Integer, Number> getInvisibleArmorStandData() {
        return Map.of(0, getMask(), 15, (byte) 0x10);
    }

    private static byte getMask() {
        return (byte) (Settings.isBackpackPreventDarkness() ? 0x21 : 0x20);
    }

    public static void sendRotationPacket(
            int entityId,
            Location location,
            boolean onGround
    ) {
        sendRotationPacket(entityId, location, onGround, getViewers(location));
    }

    public static void sendRotationPacket(
            int entityId,
            @NotNull Location location,
            boolean onGround,
            @NotNull List<Player> sendTo
    ) {
        sendPacket(NMSHandlers.getHandler().getPacketBuilder().buildEntityRotatePacket(entityId, location.getYaw(), location.getPitch(), onGround), sendTo);
    }

    public static void sendRotationPacket(
            int entityId,
            int yaw,
            boolean onGround,
            @NotNull List<Player> sendTo
    ) {
        sendPacket(NMSHandlers.getHandler().getPacketBuilder().buildEntityRotatePacket(entityId, yaw, 0, onGround), sendTo);
    }

    public static void sendTeleportPacket(int entityId, Location location, boolean onGround, List<Player> sendTo) {
        sendPacket(NMSHandlers.getHandler().getPacketBuilder().buildEntityTeleportPacket(
                entityId,
                location.x(),
                location.y(),
                location.z(),
                location.getYaw(),
                location.getPitch(),
                onGround
        ), sendTo);
    }

    /**
     * Mostly to deal with backpacks, this deals with entities riding other entities.
     * @param mountId The entity that is the "mount", ex. a player
     * @param passengerId The entity that is riding the mount, ex. a armorstand for a backpack
     */
    public static void sendRidingPacket(
            final int mountId,
            final int passengerId,
            final Location location
    ) {
        sendRidingPacket(mountId, passengerId, getViewers(location));
    }

    /**
     * Mostly to deal with backpacks, this deals with entities riding other entities.
     * @param mountId The entity that is the "mount", ex. a player
     * @param passengerIds The entities that are riding the mount, ex. a armorstand for a backpack
     * @param sendTo Whom to send the packet to
     */
    public static void sendRidingPacket(
            final int mountId,
            final int[] passengerIds,
            final @NotNull List<Player> sendTo
    ) {
        sendPacket(NMSHandlers.getHandler().getPacketBuilder().buildEntityMountPacket(mountId, passengerIds), sendTo);
    }

    /**
     * Mostly to deal with backpacks, this deals with entities riding other entities.
     * @param mountId The entity that is the "mount", ex. a player
     * @param passengerId The entity that is riding the mount, ex. a armorstand for a backpack
     * @param sendTo Whom to send the packet to
     */
    public static void sendRidingPacket(
            final int mountId,
            final int passengerId,
            final @NotNull List<Player> sendTo
    ) {
        sendRidingPacket(mountId, new int[]{passengerId}, sendTo);
    }

    /**
     * Creates a fake player entity.
     * @param skinnedPlayer The original player it bases itself off of.
     * @param uuid UUID of the fake entity.
     * @param sendTo Whom to send the packet to
     */
    public static void sendFakePlayerInfoPacket(
            final Player skinnedPlayer,
            final int entityId,
            final UUID uuid,
            final String npcName,
            final List<Player> sendTo
    ) {
        sendPacket(NMSHandlers.getHandler().getPacketBuilder().buildPlayerInfoAddPacket(skinnedPlayer, entityId, uuid, npcName), sendTo);
    }

    /**
     * Generates the overlay packet for entities.
     * @param playerId The entity the packet is about
     * @param sendTo Whom is sent the packet.
     */
    public static void sendPlayerOverlayPacket(
            final int playerId,
            final @NotNull List<Player> sendTo
    ) {
        // https://minecraft.wiki/w/Java_Edition_protocol/Entity_metadata#Avatar
        if (NMSHandlers.getVersion().isLowerOrEqual(MinecraftVersion.v1_21_8)) sendPacket(NMSHandlers.getHandler().getPacketBuilder().buildEntityMetadataPacket(playerId, getPlayerOverlayMetaData()), sendTo);
        else sendPacket(NMSHandlers.getHandler().getPacketBuilder().buildEntityMetadataPacket(playerId, getPlayerOverlayMetaData()), sendTo);
    }

    public static Map<Integer, Number> getPlayerOverlayMetaData() {
        final byte mask = 0x01 | 0x02 | 0x04 | 0x08 | 0x010 | 0x020 | 0x40;
        if (NMSHandlers.getVersion().isLowerOrEqual(MinecraftVersion.v1_21_8)) return Map.of(17, mask);
        else return Map.of(16, mask);
    }

    /**
     * Removes a fake player from being seen by players.
     * @param player Which gameprofile to wrap for removing the player.
     * @param uuid What is the fake player UUID
     * @param sendTo Whom to send the packet to
     */
    @SuppressWarnings("deprecation")
    public static void sendRemovePlayerPacket(
            final Player player,
            final UUID uuid,
            final List<Player> sendTo
    ) {
        sendPacket(NMSHandlers.getHandler().getPacketBuilder().buildPlayerInfoRemovePacket(List.of(uuid)), sendTo);
    }

    public static void sendLeashPacket(
            final int leashedEntity,
            final int entityId,
            final Location location
    ) {
        sendPacket(NMSHandlers.getHandler().getPacketBuilder().buildEntityLeashPacket(leashedEntity, entityId), getViewers(location));
    }

    public static void sendLeashPacket(
            final int leashedEntity,
            final int entityId,
            final List<Player> sendTo
    ) {
        sendPacket(NMSHandlers.getHandler().getPacketBuilder().buildEntityLeashPacket(leashedEntity, entityId), sendTo);
    }

    /**
     * Sends a movement packet from one location to another
     * @param entityId Entity this will affect
     * @param from Previous location
     * @param to New location
     * @param onGround If the movement is on the ground
     * @param sendTo Whom to send the packet to
     */
    public static void sendMovePacket(
            final int entityId,
            final @NotNull Location from,
            final @NotNull Location to,
            final boolean onGround,
            @NotNull List<Player> sendTo
    ) {
        sendPacket(NMSHandlers.getHandler().getPacketBuilder().buildEntityMovePacket(entityId, from, to, onGround), sendTo);
    }

    public static void sendEntityScalePacket(
        int entityId,
        double scale,
        List<Player> sendTo
    ) {
        sendPacket(NMSHandlers.getHandler().getPacketBuilder().buildEntityAttributePacket(entityId, Attribute.SCALE, scale), sendTo);
    }

    public static void sendEntityDestroyPacket(int entityId, List<Player> sendTo) {
        sendPacket(NMSHandlers.getHandler().getPacketBuilder().buildEntityDestroyPacket(IntList.of(entityId)), sendTo);
    }

    public static void sendEntityDestroyPacket(List<Integer> entities, List<Player> sendTo) {
        sendPacket(NMSHandlers.getHandler().getPacketBuilder().buildEntityDestroyPacket(new IntArrayList(entities)), sendTo);
    }

    public static void sendRotateHeadPacket(int entityId, Location location, List<Player> sendTo) {
        sendPacket(NMSHandlers.getHandler().getPacketBuilder().buildEntityRotateHeadPacket(entityId, location.getYaw()), sendTo);
    }

    /*
    // For future transition to display entities
    public static void sendDisplayEntityMetadataPacket(
            int entityid,
            ItemStack backpackItem,
            List<Player> sendTo) {
        // TODO: Make the default values adjustable
        Vector3f translation = new Vector3f(0, 3, 0);
        Vector3f scale = new Vector3f(1, 1, 1);
        Quaternionf rotationLeft = new Quaternionf();
        Quaternionf rotationRight = new Quaternionf();
        Display.Billboard billboard = Display.Billboard.FIXED;
        int blockLight = 15;
        int skylight = 15;
        int viewRange = Settings.getViewDistance();
        int width = 0;
        int height = 0;
        ItemDisplay.ItemDisplayTransform transform = ItemDisplay.ItemDisplayTransform.HEAD;

        NMSHandlers.getHandler().getPacketHandler().sendItemDisplayMetadata(
                entityid,
                translation,
                scale,
                rotationLeft,
                rotationRight,
                billboard,
                blockLight,
                skylight,
                viewRange,
                width,
                height,
                transform,
                backpackItem,
                sendTo
        );
    }
    */

    @NotNull
    public static List<Player> getViewers(@NotNull Location location) {
        return new ArrayList<>(HMCCosmeticsPlugin.getInstance().getPlayerSearchManager().getPlayersInRange(location, Settings.getViewDistance()));
    }
}
