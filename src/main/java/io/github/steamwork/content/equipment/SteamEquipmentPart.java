package io.github.steamwork.content.equipment;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public enum SteamEquipmentPart {
    SWORD(false),
    PICKAXE(false),
    AXE(false),
    SHOVEL(false),
    HOE(false),
    HELMET(true),
    CHESTPLATE(true),
    LEGGINGS(true),
    BOOTS(true);

    private final boolean armor;

    SteamEquipmentPart(boolean armor) {
        this.armor = armor;
    }

    public boolean isArmor() {
        return armor;
    }

    public boolean isRangeMiningTool() {
        return this == PICKAXE || this == AXE || this == SHOVEL;
    }

    public @Nullable ItemStack getWorn(@NotNull PlayerInventory inventory) {
        return switch (this) {
            case HELMET -> inventory.getHelmet();
            case CHESTPLATE -> inventory.getChestplate();
            case LEGGINGS -> inventory.getLeggings();
            case BOOTS -> inventory.getBoots();
            default -> null;
        };
    }

    public void setWorn(@NotNull PlayerInventory inventory, @NotNull ItemStack stack) {
        switch (this) {
            case HELMET -> inventory.setHelmet(stack);
            case CHESTPLATE -> inventory.setChestplate(stack);
            case LEGGINGS -> inventory.setLeggings(stack);
            case BOOTS -> inventory.setBoots(stack);
            default -> {
            }
        }
    }
}
