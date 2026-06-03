package io.github.steamwork.content.equipment;

import io.github.pylonmc.rebar.item.RebarItem;
import io.github.steamwork.SteamworkKeys;
import io.github.steamwork.util.SteamCharge;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

public enum SteamEquipmentProfile {
    BRASS_SWORD(SteamworkKeys.STEAM_SWORD, SteamEquipmentMaterial.BRASS, SteamEquipmentPart.SWORD),
    BRASS_PICKAXE(SteamworkKeys.STEAM_PICKAXE, SteamEquipmentMaterial.BRASS, SteamEquipmentPart.PICKAXE),
    BRASS_AXE(SteamworkKeys.STEAM_AXE, SteamEquipmentMaterial.BRASS, SteamEquipmentPart.AXE),
    BRASS_SHOVEL(SteamworkKeys.STEAM_SHOVEL, SteamEquipmentMaterial.BRASS, SteamEquipmentPart.SHOVEL),
    BRASS_HOE(SteamworkKeys.STEAM_HOE, SteamEquipmentMaterial.BRASS, SteamEquipmentPart.HOE),
    BRASS_HELMET(SteamworkKeys.STEAM_HELMET, SteamEquipmentMaterial.BRASS, SteamEquipmentPart.HELMET),
    BRASS_CHESTPLATE(SteamworkKeys.STEAM_CHESTPLATE, SteamEquipmentMaterial.BRASS, SteamEquipmentPart.CHESTPLATE),
    BRASS_LEGGINGS(SteamworkKeys.STEAM_LEGGINGS, SteamEquipmentMaterial.BRASS, SteamEquipmentPart.LEGGINGS),
    BRASS_BOOTS(SteamworkKeys.STEAM_BOOTS, SteamEquipmentMaterial.BRASS, SteamEquipmentPart.BOOTS),

    BRONZE_SWORD(SteamworkKeys.STEAM_BRONZE_SWORD, SteamEquipmentMaterial.BRONZE, SteamEquipmentPart.SWORD),
    BRONZE_PICKAXE(SteamworkKeys.STEAM_BRONZE_PICKAXE, SteamEquipmentMaterial.BRONZE, SteamEquipmentPart.PICKAXE),
    BRONZE_AXE(SteamworkKeys.STEAM_BRONZE_AXE, SteamEquipmentMaterial.BRONZE, SteamEquipmentPart.AXE),
    BRONZE_SHOVEL(SteamworkKeys.STEAM_BRONZE_SHOVEL, SteamEquipmentMaterial.BRONZE, SteamEquipmentPart.SHOVEL),
    BRONZE_HOE(SteamworkKeys.STEAM_BRONZE_HOE, SteamEquipmentMaterial.BRONZE, SteamEquipmentPart.HOE),
    BRONZE_HELMET(SteamworkKeys.STEAM_BRONZE_HELMET, SteamEquipmentMaterial.BRONZE, SteamEquipmentPart.HELMET),
    BRONZE_CHESTPLATE(SteamworkKeys.STEAM_BRONZE_CHESTPLATE, SteamEquipmentMaterial.BRONZE, SteamEquipmentPart.CHESTPLATE),
    BRONZE_LEGGINGS(SteamworkKeys.STEAM_BRONZE_LEGGINGS, SteamEquipmentMaterial.BRONZE, SteamEquipmentPart.LEGGINGS),
    BRONZE_BOOTS(SteamworkKeys.STEAM_BRONZE_BOOTS, SteamEquipmentMaterial.BRONZE, SteamEquipmentPart.BOOTS),

    INVAR_SWORD(SteamworkKeys.STEAM_INVAR_SWORD, SteamEquipmentMaterial.INVAR, SteamEquipmentPart.SWORD),
    INVAR_PICKAXE(SteamworkKeys.STEAM_INVAR_PICKAXE, SteamEquipmentMaterial.INVAR, SteamEquipmentPart.PICKAXE),
    INVAR_AXE(SteamworkKeys.STEAM_INVAR_AXE, SteamEquipmentMaterial.INVAR, SteamEquipmentPart.AXE),
    INVAR_SHOVEL(SteamworkKeys.STEAM_INVAR_SHOVEL, SteamEquipmentMaterial.INVAR, SteamEquipmentPart.SHOVEL),
    INVAR_HOE(SteamworkKeys.STEAM_INVAR_HOE, SteamEquipmentMaterial.INVAR, SteamEquipmentPart.HOE),
    INVAR_HELMET(SteamworkKeys.STEAM_INVAR_HELMET, SteamEquipmentMaterial.INVAR, SteamEquipmentPart.HELMET),
    INVAR_CHESTPLATE(SteamworkKeys.STEAM_INVAR_CHESTPLATE, SteamEquipmentMaterial.INVAR, SteamEquipmentPart.CHESTPLATE),
    INVAR_LEGGINGS(SteamworkKeys.STEAM_INVAR_LEGGINGS, SteamEquipmentMaterial.INVAR, SteamEquipmentPart.LEGGINGS),
    INVAR_BOOTS(SteamworkKeys.STEAM_INVAR_BOOTS, SteamEquipmentMaterial.INVAR, SteamEquipmentPart.BOOTS),

    TUNGSTEN_SWORD(SteamworkKeys.STEAM_TUNGSTEN_SWORD, SteamEquipmentMaterial.TUNGSTEN, SteamEquipmentPart.SWORD),
    TUNGSTEN_PICKAXE(SteamworkKeys.STEAM_TUNGSTEN_PICKAXE, SteamEquipmentMaterial.TUNGSTEN, SteamEquipmentPart.PICKAXE),
    TUNGSTEN_AXE(SteamworkKeys.STEAM_TUNGSTEN_AXE, SteamEquipmentMaterial.TUNGSTEN, SteamEquipmentPart.AXE),
    TUNGSTEN_SHOVEL(SteamworkKeys.STEAM_TUNGSTEN_SHOVEL, SteamEquipmentMaterial.TUNGSTEN, SteamEquipmentPart.SHOVEL),
    TUNGSTEN_HOE(SteamworkKeys.STEAM_TUNGSTEN_HOE, SteamEquipmentMaterial.TUNGSTEN, SteamEquipmentPart.HOE),
    TUNGSTEN_HELMET(SteamworkKeys.STEAM_TUNGSTEN_HELMET, SteamEquipmentMaterial.TUNGSTEN, SteamEquipmentPart.HELMET),
    TUNGSTEN_CHESTPLATE(SteamworkKeys.STEAM_TUNGSTEN_CHESTPLATE, SteamEquipmentMaterial.TUNGSTEN, SteamEquipmentPart.CHESTPLATE),
    TUNGSTEN_LEGGINGS(SteamworkKeys.STEAM_TUNGSTEN_LEGGINGS, SteamEquipmentMaterial.TUNGSTEN, SteamEquipmentPart.LEGGINGS),
    TUNGSTEN_BOOTS(SteamworkKeys.STEAM_TUNGSTEN_BOOTS, SteamEquipmentMaterial.TUNGSTEN, SteamEquipmentPart.BOOTS);

    private static final Map<NamespacedKey, SteamEquipmentProfile> BY_KEY = new HashMap<>();

    static {
        for (SteamEquipmentProfile profile : values()) {
            BY_KEY.put(profile.key, profile);
        }
    }

    private final NamespacedKey key;
    private final SteamEquipmentMaterial material;
    private final SteamEquipmentPart part;

    SteamEquipmentProfile(
            @NotNull NamespacedKey key,
            @NotNull SteamEquipmentMaterial material,
            @NotNull SteamEquipmentPart part
    ) {
        this.key = key;
        this.material = material;
        this.part = part;
    }

    public @NotNull NamespacedKey key() {
        return key;
    }

    public @NotNull SteamEquipmentMaterial material() {
        return material;
    }

    public @NotNull SteamEquipmentPart part() {
        return part;
    }

    public static @Nullable SteamEquipmentProfile fromKey(@NotNull NamespacedKey key) {
        return BY_KEY.get(key);
    }

    public static @Nullable SteamEquipmentProfile fromStack(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        RebarItem item = RebarItem.fromStack(stack);
        if (item == null) return null;
        return fromKey(item.getKey());
    }

    public static boolean isPoweredArmor(
            @Nullable ItemStack stack,
            @NotNull SteamEquipmentMaterial material,
            @NotNull SteamEquipmentPart part
    ) {
        SteamEquipmentProfile profile = fromStack(stack);
        return profile != null
                && profile.part == part
                && profile.material == material
                && SteamCharge.isPowered(stack);
    }

    public static @Nullable SteamEquipmentMaterial poweredArmorMaterial(@Nullable ItemStack stack) {
        SteamEquipmentProfile profile = fromStack(stack);
        if (profile == null || !profile.part.isArmor()) return null;
        return SteamCharge.isPowered(stack) ? profile.material : null;
    }
}
