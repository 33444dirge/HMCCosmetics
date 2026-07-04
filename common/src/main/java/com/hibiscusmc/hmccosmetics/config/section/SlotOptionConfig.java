package com.hibiscusmc.hmccosmetics.config.section;

import lombok.Getter;
import org.bukkit.inventory.EquipmentSlot;

import java.util.List;

public class SlotOptionConfig {

    @Getter
    private final EquipmentSlot slot;
    @Getter
    private final boolean addEnchantments;
    @Getter
    private final boolean requireEmpty;
    @Getter
    private final boolean addElytraComponent;
    @Getter
    private final boolean equipmentNamePassThrough;
    @Getter
    private final boolean equipmentLorePassThrough;
    @Getter
    private final boolean equipmentAttributesPassThrough;
    @Getter
    private final boolean itemDamagePassThrough;
    @Getter
    private final boolean equipmentExtraPassThrough;
    @Getter
    private final List<String> equipmentExtraComponents;

    public SlotOptionConfig(EquipmentSlot slot, boolean addEnchantments, boolean requireEmpty, boolean addElytraComponent, boolean equipmentNamePassThrough, boolean equipmentLorePassThrough, boolean equipmentAttributesPassThrough, boolean itemDamagePassThrough, boolean equipmentExtraPassThrough, List<String> equipmentExtraComponents) {
        this.slot = slot;
        this.addEnchantments = addEnchantments;
        this.requireEmpty = requireEmpty;
        this.addElytraComponent = addElytraComponent;
        this.equipmentNamePassThrough = equipmentNamePassThrough;
        this.equipmentLorePassThrough = equipmentLorePassThrough;
        this.equipmentAttributesPassThrough = equipmentAttributesPassThrough;
        this.itemDamagePassThrough = itemDamagePassThrough;
        this.equipmentExtraPassThrough = equipmentExtraPassThrough;
        this.equipmentExtraComponents = equipmentExtraComponents == null ? List.of() : List.copyOf(equipmentExtraComponents);
    }
}
