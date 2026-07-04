package com.hibiscusmc.hmccosmetics.cosmetic.types;

import com.hibiscusmc.hmccosmetics.config.Settings;
import com.hibiscusmc.hmccosmetics.config.section.SlotOptionConfig;
import com.hibiscusmc.hmccosmetics.cosmetic.Cosmetic;
import com.hibiscusmc.hmccosmetics.cosmetic.behavior.CosmeticUpdateBehavior;
import com.hibiscusmc.hmccosmetics.user.CosmeticUser;
import com.hibiscusmc.hmccosmetics.util.HMCCInventoryUtils;
import com.hibiscusmc.hmccosmetics.util.packets.HMCCPacketManager;
import com.google.common.collect.Multimap;
import io.papermc.paper.datacomponent.DataComponentType;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.ItemAttributeModifiers;
import me.lojosho.hibiscuscommons.HibiscusCommonsPlugin;
import me.lojosho.hibiscuscommons.nms.MinecraftVersion;
import me.lojosho.hibiscuscommons.nms.NMSHandlers;
import me.lojosho.shaded.configurate.ConfigurationNode;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Entity;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class CosmeticArmorType extends Cosmetic implements CosmeticUpdateBehavior {
    private final EquipmentSlot equipSlot;

    public CosmeticArmorType(String id, ConfigurationNode config) {
        super(id, config);

        EquipmentSlot slot = HMCCInventoryUtils.getEquipmentSlot(getSlot());
        if (slot == null) {
            // Hypothetically it shouldn't be null, but it was happening on some random servers? Adding this just in case
            throw new IllegalArgumentException("Invalid slot for cosmetic armor type: " + getSlot() + " in " + id + " cosmetic config.");
        }
        this.equipSlot = slot;
    }

    @Override
    public void dispatchUpdate(@NotNull CosmeticUser user) {
        if (user.isInWardrobe()) return;
        Entity entity = Bukkit.getEntity(user.getUniqueId());
        if (entity == null) return;
        if (Settings.getSlotOption(equipSlot).isRequireEmpty() && entity instanceof HumanEntity humanEntity) {
            if (!humanEntity.getInventory().getItem(equipSlot).getType().isAir()) return;
        }
        ItemStack item = getItem(user);
        if (item == null) return;
        HMCCPacketManager.equipmentSlotUpdate(entity.getEntityId(), equipSlot, item, HMCCPacketManager.getViewers(entity.getLocation()));
    }

    public ItemStack getItem(@NotNull CosmeticUser user) {
        return user.getUserCosmeticItem(this);
    }

    public ItemStack getItem(@NotNull CosmeticUser user, ItemStack cosmeticItem) {
        if (cosmeticItem == null) return null;

        Player player = user.getPlayer();
        if (player == null) return null;

        ItemStack physicalEquippedItem = player.getInventory().getItem(equipSlot);
        if (physicalEquippedItem.getType().isAir()) return cosmeticItem;

        SlotOptionConfig slotOption = Settings.getSlotOption(equipSlot);
        if (slotOption.isAddEnchantments()) cosmeticItem.addUnsafeEnchantments(physicalEquippedItem.getEnchantments());
        if (slotOption.isEquipmentNamePassThrough() || slotOption.isEquipmentLorePassThrough()) {
            addEquipmentLore(cosmeticItem, physicalEquippedItem, slotOption.isEquipmentNamePassThrough(), slotOption.isEquipmentLorePassThrough());
        }
        if (slotOption.isEquipmentAttributesPassThrough()) addEquipmentAttributes(cosmeticItem, physicalEquippedItem);

        if (NMSHandlers.getVersion().isLower(MinecraftVersion.v1_21_4)) return cosmeticItem;
        // Past this point, we know the server is over 1.21.4
        if (slotOption.isAddElytraComponent()
                && HibiscusCommonsPlugin.isOnPaper()
                && physicalEquippedItem.hasData(DataComponentTypes.GLIDER)) {
            cosmeticItem.setData(DataComponentTypes.GLIDER);
        }
        if (slotOption.isItemDamagePassThrough() && HibiscusCommonsPlugin.isOnPaper()) {
            if (physicalEquippedItem.hasData(DataComponentTypes.MAX_DAMAGE))
                cosmeticItem.setData(DataComponentTypes.MAX_DAMAGE, physicalEquippedItem.getData(DataComponentTypes.MAX_DAMAGE));
            if (physicalEquippedItem.hasData(DataComponentTypes.DAMAGE))
                cosmeticItem.setData(DataComponentTypes.DAMAGE, physicalEquippedItem.getData(DataComponentTypes.DAMAGE));
        }
        if (slotOption.isEquipmentExtraPassThrough() && HibiscusCommonsPlugin.isOnPaper()) {
            addExtraComponents(cosmeticItem, physicalEquippedItem, slotOption.getEquipmentExtraComponents());
        }
        // Basically, if force offhand is off AND there is no item in an offhand slot, then the equipment packet to add the cosmetic
        return cosmeticItem;
    }

    private void addEquipmentLore(ItemStack cosmeticItem, ItemStack physicalEquippedItem, boolean includeName, boolean includeLore) {
        ItemMeta cosmeticMeta = cosmeticItem.getItemMeta();
        if (cosmeticMeta == null) return;

        ItemMeta physicalMeta = physicalEquippedItem.getItemMeta();
        List<Component> lore = new ArrayList<>();
        if (includeName) lore.addAll(formatEquipmentName(getEquipmentName(physicalEquippedItem, physicalMeta)));
        if (includeLore && physicalMeta != null && physicalMeta.hasLore() && physicalMeta.lore() != null) lore.addAll(physicalMeta.lore());
        if (cosmeticMeta.hasLore() && cosmeticMeta.lore() != null) lore.addAll(cosmeticMeta.lore());

        cosmeticMeta.lore(lore);
        cosmeticItem.setItemMeta(cosmeticMeta);
    }

    private Component getEquipmentName(ItemStack physicalEquippedItem, ItemMeta physicalMeta) {
        if (physicalMeta != null) {
            if (physicalMeta.hasCustomName()) return physicalMeta.customName();
            if (physicalMeta.hasDisplayName()) return physicalMeta.displayName();
            if (physicalMeta.hasItemName()) return physicalMeta.itemName();
        }
        return Component.translatable(physicalEquippedItem.translationKey());
    }

    private List<Component> formatEquipmentName(Component equipmentName) {
        List<Component> lines = new ArrayList<>();
        for (String line : Settings.getEquipmentNameFormat().split("(?i)<(?:br|newline)>", -1)) {
            lines.add(MiniMessage.miniMessage()
                    .deserialize(line, Placeholder.component("equipment_name", equipmentName))
                    .decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE));
        }
        return lines;
    }

    private void addEquipmentAttributes(ItemStack cosmeticItem, ItemStack physicalEquippedItem) {
        if (!HibiscusCommonsPlugin.isOnPaper() || NMSHandlers.getVersion().isLower(MinecraftVersion.v1_21_4)) {
            addLegacyEquipmentAttributes(cosmeticItem, physicalEquippedItem);
            return;
        }
        if (!physicalEquippedItem.hasData(DataComponentTypes.ATTRIBUTE_MODIFIERS)) return;

        ItemAttributeModifiers physicalModifiers = physicalEquippedItem.getData(DataComponentTypes.ATTRIBUTE_MODIFIERS);
        if (physicalModifiers == null || physicalModifiers.modifiers().isEmpty()) return;

        ItemAttributeModifiers cosmeticModifiers = cosmeticItem.hasData(DataComponentTypes.ATTRIBUTE_MODIFIERS)
                ? cosmeticItem.getData(DataComponentTypes.ATTRIBUTE_MODIFIERS)
                : null;
        ItemAttributeModifiers.Builder builder = ItemAttributeModifiers.itemAttributes();

        if (cosmeticModifiers != null) {
            for (ItemAttributeModifiers.Entry entry : cosmeticModifiers.modifiers()) addAttribute(builder, entry);
        }
        for (ItemAttributeModifiers.Entry entry : physicalModifiers.modifiers()) addAttribute(builder, entry);

        cosmeticItem.setData(DataComponentTypes.ATTRIBUTE_MODIFIERS, builder);
    }

    private void addAttribute(ItemAttributeModifiers.Builder builder, ItemAttributeModifiers.Entry entry) {
        EquipmentSlotGroup group = entry.getGroup();
        if (group == null) {
            builder.addModifier(entry.attribute(), entry.modifier());
        } else {
            builder.addModifier(entry.attribute(), entry.modifier(), group);
        }
    }

    private void addLegacyEquipmentAttributes(ItemStack cosmeticItem, ItemStack physicalEquippedItem) {
        ItemMeta physicalMeta = physicalEquippedItem.getItemMeta();
        if (physicalMeta == null || !physicalMeta.hasAttributeModifiers()) return;
        ItemMeta cosmeticMeta = cosmeticItem.getItemMeta();
        if (cosmeticMeta == null) return;

        Multimap<Attribute, AttributeModifier> attributeModifiers = physicalMeta.getAttributeModifiers();
        if (attributeModifiers == null) return;
        for (Map.Entry<Attribute, AttributeModifier> entry : attributeModifiers.entries()) {
            cosmeticMeta.addAttributeModifier(entry.getKey(), entry.getValue());
        }
        cosmeticItem.setItemMeta(cosmeticMeta);
    }

    private void addExtraComponents(ItemStack cosmeticItem, ItemStack physicalEquippedItem, List<String> componentNames) {
        if (componentNames.isEmpty()) return;

        Set<String> normalizedNames = new HashSet<>();
        for (String componentName : componentNames) normalizedNames.add(normalizeComponentName(componentName));
        cosmeticItem.copyDataFrom(physicalEquippedItem, componentType -> isExtraComponent(componentType, normalizedNames));
    }

    private boolean isExtraComponent(DataComponentType componentType, Set<String> componentNames) {
        String key = normalizeComponentName(componentType.getKey().getKey());
        String namespacedKey = normalizeComponentName(componentType.getKey().asString());
        return componentNames.contains(key) || componentNames.contains(namespacedKey);
    }

    private String normalizeComponentName(String componentName) {
        return componentName.toLowerCase(Locale.ROOT).replace('-', '_');
    }

    @NotNull
    public EquipmentSlot getEquipSlot() {
        return this.equipSlot;
    }
}
