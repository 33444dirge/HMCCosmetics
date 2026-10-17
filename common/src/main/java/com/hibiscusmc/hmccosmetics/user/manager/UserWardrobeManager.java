package com.hibiscusmc.hmccosmetics.user.manager;

import com.hibiscusmc.hmccosmetics.HMCCosmeticsPlugin;
import com.hibiscusmc.hmccosmetics.config.WardrobeSettings;
import com.hibiscusmc.hmccosmetics.config.section.Wardrobe;
import com.hibiscusmc.hmccosmetics.config.section.WardrobeLocation;
import com.hibiscusmc.hmccosmetics.cosmetic.Cosmetic;
import com.hibiscusmc.hmccosmetics.cosmetic.CosmeticSlot;
import com.hibiscusmc.hmccosmetics.cosmetic.types.CosmeticBalloonType;
import com.hibiscusmc.hmccosmetics.gui.Menu;
import com.hibiscusmc.hmccosmetics.gui.Menus;
import com.hibiscusmc.hmccosmetics.user.CosmeticUser;
import com.hibiscusmc.hmccosmetics.util.HMCCInventoryUtils;
import com.hibiscusmc.hmccosmetics.util.HMCCScheduler;
import com.hibiscusmc.hmccosmetics.util.HMCCServerUtils;
import com.hibiscusmc.hmccosmetics.util.MessagesUtil;
import com.hibiscusmc.hmccosmetics.util.packets.HMCCPacketManager;
import lombok.Getter;
import lombok.Setter;
import me.lojosho.hibiscuscommons.nms.NMSHandlers;
import me.lojosho.hibiscuscommons.nms.NMSPacketBuilder;
import me.lojosho.hibiscuscommons.nms.NMSPacketSender;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;

public class UserWardrobeManager {

    private static final int MANUAL_MIN_PITCH = -45;
    private static final int MANUAL_MAX_PITCH = 45;

    @Getter
    private final int NPC_ID;
    @Getter
    private final int ARMORSTAND_ID;
    @Getter
    private final UUID WARDROBE_UUID;
    @Getter
    private String npcName;
    @Getter
    private GameMode originalGamemode;
    @Getter
    private final CosmeticUser user;
    @Getter
    private final Wardrobe wardrobe;
    @Getter
    private final WardrobeLocation wardrobeLocation;
    @Getter
    private final Location viewingLocation;
    @Getter
    private final Location npcLocation;
    @Getter
    private Location exitLocation;
    @Getter
    private BossBar bossBar;
    @Getter
    private boolean active;
    @Setter
    @Getter
    private WardrobeStatus wardrobeStatus;
    @Getter
    @Setter
    private Menu lastOpenMenu;
    private int npcYaw;
    private int npcPitch;
    private ManualRotationAxis manualRotationAxis = ManualRotationAxis.HORIZONTAL;
    private HMCCScheduler.TaskHandle transitionTask;
    private HMCCScheduler.TaskHandle updateTask;
    private boolean started;
    private boolean finished;
    private boolean scoreboardTeamCreated;
    private boolean originalAllowFlight;
    private boolean originalFlying;
    private boolean originalInvisible;

    private NMSPacketBuilder packetBuilder = NMSHandlers.getHandler().getPacketBuilder();
    private NMSPacketSender packetSender = NMSHandlers.getHandler().getPacketSender();

    public UserWardrobeManager(CosmeticUser user, Wardrobe wardrobe) {
        World world = user.getEntity().getWorld();
        NPC_ID = me.lojosho.hibiscuscommons.util.ServerUtils.getNextEntityId(world);
        ARMORSTAND_ID = me.lojosho.hibiscuscommons.util.ServerUtils.getNextEntityId(world);
        WARDROBE_UUID = UUID.randomUUID();
        this.user = user;

        this.wardrobe = wardrobe;
        this.wardrobeLocation = wardrobe.getLocation();

        Location leaveLocation = wardrobeLocation.getLeaveLocation();
        this.exitLocation = leaveLocation == null ? null : leaveLocation.clone();
        this.viewingLocation = wardrobeLocation.getViewerLocation().clone();
        this.npcLocation = wardrobeLocation.getNpcLocation().clone();
        this.npcYaw = Math.round(this.npcLocation.getYaw());
        this.npcPitch = Math.round(this.npcLocation.getPitch());

        String defaultMenu = wardrobe.getDefaultMenu();
        if (defaultMenu != null) {
            // User has defined a custom menu in the wardrobe config
            Menu menu = Menus.getMenu(defaultMenu);
            if (menu != null) {
                // User provided a good, valid menu
                this.lastOpenMenu = Menus.getMenu(defaultMenu);
            } else {
                // User provided a menu that does not exist in HMCC
                this.lastOpenMenu = Menus.getDefaultMenu();
                MessagesUtil.sendDebugMessages("Unable to set menu (" + defaultMenu + ") in wardrobe " + getWardrobe().getId() + ". Defaulting to default menu defined in config.yml", Level.WARNING);
                if (this.lastOpenMenu == null) {
                    // That means that even the default menu is null in the config.
                    MessagesUtil.sendDebugMessages("Unable to set any menu in wardrobe " + getWardrobe().getId() + " as the fallback default menu (defined in config.yml) is invalid.", Level.WARNING);
                }
            }
        }

        wardrobeStatus = WardrobeStatus.SETUP;
    }

    public void start() {
        setWardrobeStatus(WardrobeStatus.STARTING);
        Player player = user.getPlayer();

        started = true;
        this.originalGamemode = player.getGameMode();
        this.originalAllowFlight = player.getAllowFlight();
        this.originalFlying = player.isFlying();
        this.originalInvisible = player.isInvisible();
        if (WardrobeSettings.isReturnLastLocation() || this.exitLocation == null) {
            this.exitLocation = player.getLocation().clone();
        }

        user.hidePlayer();
        if (!Bukkit.getServer().getAllowFlight()) player.setAllowFlight(true);
        List<Player> viewer = Collections.singletonList(player);

        MessagesUtil.sendMessage(player, "opened-wardrobe");

        Runnable run = () -> {
            transitionTask = null;
            if (finished || wardrobeStatus != WardrobeStatus.STARTING
                || !HMCCosmeticsPlugin.getInstance().isAcceptingWork()) return;
            if (!player.isOnline()) {
                endImmediately();
                return;
            }

            List<Object> viewerPackets = new ArrayList<>();

            // Armorstand
            viewerPackets.add(packetBuilder.buildEntitySpawnPacket(ARMORSTAND_ID, UUID.randomUUID(), EntityType.ARMOR_STAND, viewingLocation));
            viewerPackets.add(packetBuilder.buildEntityMetadataPacket(ARMORSTAND_ID, HMCCPacketManager.getInvisibleArmorStandData()));
            viewerPackets.add(packetBuilder.buildEntityTeleportPacket(ARMORSTAND_ID, viewingLocation.getX(), viewingLocation.getY(), viewingLocation.getZ(), viewingLocation.getYaw(), viewingLocation.getPitch(), false));
            viewerPackets.add(packetBuilder.buildEntityRotateHeadPacket(ARMORSTAND_ID, viewingLocation));

            // Player
            HMCCScheduler.teleportAsync(player, viewingLocation);
            player.setInvisible(true);
            player.getInventory().setHeldItemSlot(4);
            ItemStack mainHandItem = player.getInventory().getItemInMainHand();
            if (mainHandItem.getType().isAir()) {
                ItemStack dummyItem = new ItemStack(Material.DIRT);
                ItemMeta meta = dummyItem.getItemMeta();
                if (meta != null) {
                    meta.getPersistentDataContainer().set(HMCCServerUtils.getWardrobeDummyItemKey(), PersistentDataType.BYTE, (byte) 1);
                    dummyItem.setItemMeta(meta);
                }
                player.getInventory().setItemInMainHand(dummyItem);
            }
            viewerPackets.add(packetBuilder.buildPlayerGamemodeChangePacket(GameMode.SPECTATOR));
            viewerPackets.add(packetBuilder.buildEntityCameraPacket(ARMORSTAND_ID));

            // NPC
            npcName = "Mannequin";
            if (npcName.length() >= 16) {
                npcName = npcName.substring(0, 15);
            }
            viewerPackets.add(packetBuilder.buildPlayerInfoAddPacket(player, NPC_ID, WARDROBE_UUID, npcName));
            viewerPackets.add(packetBuilder.buildEntitySpawnPacket(NPC_ID, WARDROBE_UUID, EntityType.PLAYER, npcLocation));
            viewerPackets.add(packetBuilder.buildEntityMetadataPacket(NPC_ID, HMCCPacketManager.getPlayerOverlayMetaData()));
            viewerPackets.add(packetBuilder.buildPlayerScoreboardCreatePacket(player, npcName));
            viewerPackets.add(packetBuilder.buildPlayerScoreboardAddPlayersPacket(player, npcName));
            viewerPackets.add(packetBuilder.buildEntityAttributePacket(NPC_ID, Attribute.SCALE, 1.0));

            // Location
            viewerPackets.add(packetBuilder.buildEntityRotateHeadPacket(NPC_ID, npcLocation));
            viewerPackets.add(packetBuilder.buildEntityRotatePacket(NPC_ID, npcLocation, true));

            // Misc
            if (user.hasCosmeticInSlot(CosmeticSlot.BACKPACK)) {
                // Maybe null as backpack maybe despawned before entering
                if (user.getUserBackpackManager() == null) user.respawnBackpack();
                if (user.isBackpackSpawned()) {
                    user.getUserBackpackManager().getEntityManager().teleport(npcLocation.clone().add(0, 2, 0));

                    viewerPackets.add(packetBuilder.buildEntityEquipmentSlotUpdatePacket(user.getUserBackpackManager().getFirstArmorStandId(), Map.of(EquipmentSlot.HEAD, user.getUserCosmeticItem(user.getCosmetic(CosmeticSlot.BACKPACK)))));
                    viewerPackets.add(packetBuilder.buildEntityMountPacket(NPC_ID, new int[]{user.getUserBackpackManager().getFirstArmorStandId()}));
                }
            }

            packetSender.sendBundle(viewerPackets, viewer);
            scoreboardTeamCreated = true;

            if (user.hasCosmeticInSlot(CosmeticSlot.BALLOON)) {
                if (user.getBalloonManager() == null) user.respawnBalloon();
                if (user.isBalloonSpawned()) {
                    CosmeticBalloonType cosmetic = (CosmeticBalloonType) user.getCosmetic(CosmeticSlot.BALLOON);
                    user.getBalloonManager().sendRemoveLeashPacket(viewer);
                    user.getBalloonManager().sendLeashPacket(NPC_ID, viewer);
                    //PacketManager.sendLeashPacket(VIEWER.getBalloonEntity().getModelId(), NPC_ID, viewer);

                    Location balloonLocation = npcLocation.clone().add(cosmetic.getScaledBalloonOffset(user));
                    HMCCPacketManager.sendTeleportPacket(user.getBalloonManager().getPufferfishBalloonId(), balloonLocation, false, viewer);
                    user.getBalloonManager().setLocation(balloonLocation);
                }
            }

            if (WardrobeSettings.isEnabledBossbar()) {
                float progress = WardrobeSettings.getBossbarProgress();
                Component message = MessagesUtil.processStringNoKey(player, WardrobeSettings.getBossbarMessage());

                bossBar = BossBar.bossBar(message, progress, WardrobeSettings.getBossbarColor(), WardrobeSettings.getBossbarOverlay());
                //Audience target = BukkitAudiences.create(HMCCosmeticsPlugin.getInstance()).player(player);

                player.showBossBar(bossBar);
            }

            if (WardrobeSettings.isEnterOpenMenu()) {
                Menu menu = Menus.getDefaultMenu();
                if (menu != null) menu.openMenu(user);
            }

            if (WardrobeSettings.getRotationMode() == WardrobeSettings.RotationMode.MANUAL) {
                player.getInventory().setHeldItemSlot(4);
            }

            this.active = true;
            update();
            setWardrobeStatus(WardrobeStatus.RUNNING);
        };


        if (WardrobeSettings.isEnabledTransition()) {
            MessagesUtil.sendTitle(
                    user.getPlayer(),
                    WardrobeSettings.getTransitionText(),
                    WardrobeSettings.getTransitionFadeIn(),
                    WardrobeSettings.getTransitionStay(),
                    WardrobeSettings.getTransitionFadeOut()
            );
            transitionTask = HMCCScheduler.runEntityLater(player, run, WardrobeSettings.getTransitionDelay());
        } else {
            run.run();
        }

    }

    public void setTransitionTask(HMCCScheduler.TaskHandle task) {
        transitionTask = task;
    }

    public void end() {
        finish(false);
    }

    /** Restore a wardrobe even when its entrance or exit transition has not completed. */
    public void endImmediately() {
        finish(true);
    }

    private void finish(boolean shuttingDown) {
        if (finished) return;
        finished = true;
        setWardrobeStatus(WardrobeStatus.STOPPING);
        boolean hadDisplay = active;
        active = false;
        if (transitionTask != null) transitionTask.cancel();
        if (updateTask != null) updateTask.cancel();
        Player player = user.getPlayer();
        if (player == null || !started) return;

        List<Player> viewer = Collections.singletonList(player);
        player.setAllowFlight(originalAllowFlight);
        if (originalAllowFlight) player.setFlying(originalFlying);
        MessagesUtil.sendMessage(player, "closed-wardrobe");

        Runnable run = () -> {
            if (scoreboardTeamCreated) {
                HMCCPacketManager.sendPacket(packetBuilder.buildPlayerScoreboardRemovePacket(player, npcName), viewer);
                scoreboardTeamCreated = false;
            }

            // For Wardrobe Temp Cosmetics
            for (Cosmetic cosmetic : user.getCosmetics()) {
                MessagesUtil.sendDebugMessages("Checking... " + cosmetic.getId());
                if (!user.canEquipCosmetic(cosmetic)) {
                    MessagesUtil.sendDebugMessages("Unable to keep " + cosmetic.getId());
                    user.removeCosmeticSlot(cosmetic.getSlot());
                }
            }

            // NPC
            if (hadDisplay) {
                if (user.isBalloonSpawned()) user.getBalloonManager().sendRemoveLeashPacket(viewer);
                HMCCPacketManager.sendEntityDestroyPacket(NPC_ID, viewer);
                HMCCPacketManager.sendRemovePlayerPacket(player, WARDROBE_UUID, viewer);
                HMCCPacketManager.sendPacket(packetBuilder.buildEntityCameraPacket(player.getEntityId()), viewer);
                HMCCPacketManager.sendEntityDestroyPacket(ARMORSTAND_ID, viewer);
            }

            // Player
            player.setInvisible(originalInvisible);
            ItemStack mainHandItem = player.getInventory().getItemInMainHand();
            if (mainHandItem.hasItemMeta()) {
                ItemMeta meta = mainHandItem.getItemMeta();
                if (meta != null && meta.getPersistentDataContainer().has(HMCCServerUtils.getWardrobeDummyItemKey(), PersistentDataType.BYTE)) {
                    player.getInventory().setItemInMainHand(null);
                }
            }

            //PacketManager.sendEntityDestroyPacket(player.getEntityId(), viewer); // Success
            if (hadDisplay && WardrobeSettings.isForceExitGamemode()) {
                MessagesUtil.sendDebugMessages("Force Exit Gamemode " + WardrobeSettings.getExitGamemode());
                player.setGameMode(WardrobeSettings.getExitGamemode());
                HMCCPacketManager.sendPacket(packetBuilder.buildPlayerGamemodeChangePacket(WardrobeSettings.getExitGamemode()), viewer);
            } else if (hadDisplay) {
                MessagesUtil.sendDebugMessages("Original Gamemode " + this.originalGamemode);
                player.setGameMode(this.originalGamemode);
                HMCCPacketManager.sendPacket(packetBuilder.buildPlayerGamemodeChangePacket(this.originalGamemode), viewer);
            }
            if (!shuttingDown) user.showPlayer();

            if (!shuttingDown && user.hasCosmeticInSlot(CosmeticSlot.BACKPACK)) {
                user.respawnBackpack();
                //PacketManager.ridingMountPacket(player.getEntityId(), VIEWER.getBackpackEntity().getEntityId(), viewer);
            }

            if (user.hasCosmeticInSlot(CosmeticSlot.BALLOON)) {
                //user.respawnBalloon();
                //PacketManager.sendLeashPacket(VIEWER.getBalloonEntity().getPufferfishBalloonId(), player.getEntityId(), viewer);
            }

            if (hadDisplay) HMCCScheduler.teleportAsync(player, getExitLocation(player));

            HashMap<EquipmentSlot, ItemStack> items = new HashMap<>();
            for (EquipmentSlot slot : HMCCInventoryUtils.getPlayerArmorSlots()) {
                ItemStack item = player.getInventory().getItem(slot);
                items.put(slot, item);
            }
            /*
            if (WardrobeSettings.isEquipPumpkin()) {
                items.put(EquipmentSlot.HEAD, player.getInventory().getHelmet());
            }
             */
            if (hadDisplay) HMCCPacketManager.sendPacket(packetBuilder.buildEntityEquipmentSlotUpdatePacket(player.getEntityId(), items), viewer);

            if (bossBar != null) {
                //Audience target = BukkitAudiences.create(HMCCosmeticsPlugin.getInstance()).player(player);
                player.hideBossBar(bossBar);
            }

            if (!shuttingDown) user.updateCosmetic();
        };
        run.run();
    }

    public void toggleManualRotationAxis() {
        if (!active || WardrobeSettings.getRotationMode() != WardrobeSettings.RotationMode.MANUAL) return;
        manualRotationAxis = manualRotationAxis == ManualRotationAxis.HORIZONTAL ? ManualRotationAxis.VERTICAL : ManualRotationAxis.HORIZONTAL;
        Player player = user.getPlayer();
        if (player == null) return;
        MessagesUtil.sendMessage(player, manualRotationAxis == ManualRotationAxis.HORIZONTAL ? "wardrobe-rotation-horizontal" : "wardrobe-rotation-vertical");
    }

    public void rotateNpcByScroll(boolean reverse) {
        if (WardrobeSettings.getRotationMode() == WardrobeSettings.RotationMode.MANUAL && manualRotationAxis == ManualRotationAxis.HORIZONTAL) {
            rotateNpc(reverse);
        } else {
            rotateNpcPitch(reverse);
        }
    }

    private void rotateNpc(boolean left) {
        if (!active || WardrobeSettings.getRotationMode() != WardrobeSettings.RotationMode.MANUAL) return;
        int amount = WardrobeSettings.getRotationSpeed();
        if (amount <= 0) return;
        Player player = user.getPlayer();
        if (player == null) return;
        npcYaw = normalizeYaw(npcYaw + amount * (left ? -1 : 1));
        sendNpcRotation(Collections.singletonList(player));
        playRotationSound(player);
    }

    private void rotateNpcPitch(boolean up) {
        if (!active) return;
        int amount = WardrobeSettings.getPitchStep();
        if (amount <= 0) return;
        Player player = user.getPlayer();
        if (player == null) return;
        npcPitch = clampPitch(npcPitch + amount * (up ? -1 : 1));
        sendNpcRotation(Collections.singletonList(player));
        playRotationSound(player);
    }

    private void playRotationSound(Player player) {
        String sound = WardrobeSettings.getManualRotationSound();
        if (sound == null || sound.isBlank() || WardrobeSettings.getManualRotationSoundVolume() <= 0) return;
        player.playSound(
                player.getLocation(),
                sound,
                WardrobeSettings.getManualRotationSoundType(),
                WardrobeSettings.getManualRotationSoundVolume(),
                WardrobeSettings.getManualRotationSoundPitch()
        );
    }

    private Location getExitLocation(Player player) {
        Location configuredLeaveLocation = wardrobeLocation.getLeaveLocation();
        if (!WardrobeSettings.isReturnLastLocation() && configuredLeaveLocation != null) {
            return configuredLeaveLocation.clone();
        }

        Location currentLocation = player.getLocation();
        if (configuredLeaveLocation == null && !isViewingLocation(currentLocation)) {
            return currentLocation.clone();
        }

        return Objects.requireNonNullElseGet(exitLocation, () -> player.getWorld().getSpawnLocation()).clone();
    }

    private boolean isViewingLocation(Location location) {
        if (location.getWorld() == null || viewingLocation.getWorld() == null) return false;
        return location.getWorld().equals(viewingLocation.getWorld()) && location.distanceSquared(viewingLocation) < 4;
    }

    private void sendNpcRotation(List<Player> viewer) {
        if (viewer.isEmpty() || viewer.get(0) == null) return;
        npcLocation.setYaw(npcYaw);
        npcLocation.setPitch(npcPitch);
        HMCCPacketManager.sendRotateHeadPacket(NPC_ID, npcLocation, viewer);
        HMCCPacketManager.sendPacket(packetBuilder.buildEntityRotatePacket(NPC_ID, npcYaw, npcPitch, false), viewer);
    }

    private int normalizeYaw(int yaw) {
        while (yaw > 179) yaw -= 360;
        while (yaw < -180) yaw += 360;
        return yaw;
    }

    private int clampPitch(int pitch) {
        return Math.max(MANUAL_MIN_PITCH, Math.min(MANUAL_MAX_PITCH, pitch));
    }

    private void update() {
        final AtomicInteger data = new AtomicInteger(npcYaw);

        AtomicReference<HMCCScheduler.TaskHandle> task = new AtomicReference<>();
        task.set(HMCCScheduler.runEntityTimer(user.getEntity(), () -> {
                Player player = user.getPlayer();
                if (!active || player == null) {
                    MessagesUtil.sendDebugMessages("WardrobeEnd[user=" + user.getUniqueId() + ",reason=Active is false]");
                    task.get().cancel();
                    return;
                }
                MessagesUtil.sendDebugMessages("WardrobeUpdate[user=" + user.getUniqueId() + ",status=" + getWardrobeStatus() + "]");
                List<Player> viewer = Collections.singletonList(player);
                List<Player> outsideViewers = Collections.emptyList();

                Location location = npcLocation;
                user.hidePlayer();
                int nextyaw = npcYaw;

                if (WardrobeSettings.getRotationMode() == WardrobeSettings.RotationMode.AUTOMATIC) {
                    int yaw = data.get();
                    location.setYaw(yaw);
                    location.setPitch(npcPitch);
                    HMCCPacketManager.sendRotateHeadPacket(NPC_ID, location, viewer);
                    int rotationSpeed = WardrobeSettings.getRotationSpeed();
                    int newYaw = HMCCServerUtils.getNextYaw(yaw - 30, rotationSpeed);
                    location.setYaw(newYaw);
                    HMCCPacketManager.sendPacket(packetBuilder.buildEntityRotatePacket(NPC_ID, newYaw, npcPitch, false), viewer);
                    nextyaw = HMCCServerUtils.getNextYaw(yaw, rotationSpeed);
                    npcYaw = nextyaw;
                    data.set(nextyaw);
                } else {
                    nextyaw = npcYaw;
                    sendNpcRotation(viewer);
                }

                for (CosmeticSlot slot : CosmeticSlot.values().values()) {
                    HMCCPacketManager.equipmentSlotUpdate(NPC_ID, user, slot, viewer);
                }

                if (user.hasCosmeticInSlot(CosmeticSlot.BACKPACK) && user.getUserBackpackManager() != null) {
                    HMCCPacketManager.sendTeleportPacket(user.getUserBackpackManager().getFirstArmorStandId(), location, false, viewer);
                    HMCCPacketManager.sendPacket(packetBuilder.buildEntityMountPacket(NPC_ID, new int[]{user.getUserBackpackManager().getFirstArmorStandId()}), viewer);
                    user.getUserBackpackManager().getEntityManager().setRotation(nextyaw);
                    HMCCPacketManager.sendEntityDestroyPacket(user.getUserBackpackManager().getFirstArmorStandId(), outsideViewers);
                }

                if (user.hasCosmeticInSlot(CosmeticSlot.BALLOON) && user.isBalloonSpawned()) {
                    // The two lines below broke, solved by listening to PlayerCosmeticPostEquipEvent
                    //PacketManager.sendTeleportPacket(user.getBalloonManager().getPufferfishBalloonId(), npcLocation.add(Settings.getBalloonOffset()), false, viewer);
                    //user.getBalloonManager().getModelEntity().teleport(npcLocation.add(Settings.getBalloonOffset()));
                    user.getBalloonManager().sendRemoveLeashPacket(outsideViewers);
                    if (user.getBalloonManager().getBalloonType() != UserBalloonManager.BalloonType.MODELENGINE) {
                        HMCCPacketManager.sendEntityDestroyPacket(user.getBalloonManager().getModelId(), outsideViewers);
                    }
                    user.getBalloonManager().sendLeashPacket(NPC_ID, viewer);
                }

                if (WardrobeSettings.isEquipPumpkin()) {
                    HMCCPacketManager.equipmentSlotUpdate(user.getPlayer().getEntityId(), EquipmentSlot.HEAD, new ItemStack(Material.CARVED_PUMPKIN), viewer);
                } else {
                    HMCCPacketManager.equipmentSlotUpdate(user.getPlayer(), true, viewer); // Optifine dumbassery
                }
        }, 1, 2));
        updateTask = task.get();
    }

    public enum WardrobeStatus {
        SETUP,
        STARTING,
        RUNNING,
        STOPPING,
    }

    private enum ManualRotationAxis {
        HORIZONTAL,
        VERTICAL
    }

}
