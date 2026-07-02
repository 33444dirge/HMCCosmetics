package com.hibiscusmc.hmccosmetics.database;

import com.hibiscusmc.hmccosmetics.cosmetic.Cosmetic;
import com.hibiscusmc.hmccosmetics.cosmetic.CosmeticSlot;
import com.hibiscusmc.hmccosmetics.user.CosmeticUser;
import lombok.Getter;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class UserData {

    @Getter
    private UUID owner;
    @Getter
    private Map<CosmeticSlot, Map.Entry<Cosmetic, Integer>> cosmetics;
    @Getter
    private final List<CosmeticUser.HiddenReason> hiddenReasons;

    public UserData(UUID owner) {
        this.owner = owner;
        this.cosmetics = new ConcurrentHashMap<>();
        this.hiddenReasons = new CopyOnWriteArrayList<>();
    }

    public void setCosmetics(Map<CosmeticSlot, Map.Entry<Cosmetic, Integer>> cosmetics) {
        this.cosmetics = new ConcurrentHashMap<>(cosmetics);
    }

    public void addCosmetic(CosmeticSlot slot, Cosmetic cosmetic, Integer color) {
        cosmetics.put(slot, Map.entry(cosmetic, color));
    }

    public void addHiddenReason(CosmeticUser.HiddenReason reason) {
        hiddenReasons.add(reason);
    }
}
