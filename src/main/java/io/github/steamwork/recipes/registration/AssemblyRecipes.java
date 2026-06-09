package io.github.steamwork.recipes.registration;

import io.github.pylonmc.rebar.recipe.RecipeInput;
import io.github.pylonmc.pylon.PylonItems;
import io.github.steamwork.SteamworkItems;
import io.github.steamwork.recipes.SteamAssemblyRecipe;
import io.github.steamwork.util.SequencedWorkpiece;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.List;

import static io.github.steamwork.util.SteamworkUtils.steamworkKey;

/**
 * 蒸汽装配台配方（4c）：原本在原版工作台合成的 9 件装备 + 3 个罐子，全部搬到装配台。
 * 原料用量与旧合成台配方保持一致（避免改动平衡），但顺序无关，更友好。
 */
public final class AssemblyRecipes {

    private AssemblyRecipes() {
        throw new AssertionError("Utility class");
    }

    public static void register() {
        // ===== 武器 / 工具（5 件） =====
        register(steamworkKey("assemble_steam_sword"),
                SteamworkItems.STEAM_SWORD,
                RecipeInput.of(SteamworkItems.BRASS_INGOT, 2),
                RecipeInput.of(ItemStack.of(Material.IRON_INGOT), 4),
                RecipeInput.of(SteamworkItems.BRASS_GEAR),
                RecipeInput.of(SteamworkItems.BRASS_VALVE_CORE));

        register(steamworkKey("assemble_steam_pickaxe"),
                SteamworkItems.STEAM_PICKAXE,
                RecipeInput.of(SteamworkItems.BRASS_INGOT, 2),
                RecipeInput.of(ItemStack.of(Material.IRON_INGOT), 3),
                RecipeInput.of(SteamworkItems.BRASS_GEAR),
                RecipeInput.of(SteamworkItems.BRASS_VALVE_CORE, 2));

        register(steamworkKey("assemble_steam_axe"),
                SteamworkItems.STEAM_AXE,
                RecipeInput.of(SteamworkItems.BRASS_INGOT),
                RecipeInput.of(ItemStack.of(Material.IRON_INGOT), 6),
                RecipeInput.of(SteamworkItems.BRASS_GEAR),
                RecipeInput.of(SteamworkItems.BRASS_VALVE_CORE));

        register(steamworkKey("assemble_steam_shovel"),
                SteamworkItems.STEAM_SHOVEL,
                RecipeInput.of(ItemStack.of(Material.IRON_INGOT), 6),
                RecipeInput.of(SteamworkItems.BRASS_GEAR),
                RecipeInput.of(SteamworkItems.BRASS_VALVE_CORE));

        register(steamworkKey("assemble_steam_hoe"),
                SteamworkItems.STEAM_HOE,
                RecipeInput.of(SteamworkItems.BRASS_INGOT, 2),
                RecipeInput.of(ItemStack.of(Material.IRON_INGOT), 3),
                RecipeInput.of(SteamworkItems.BRASS_GEAR),
                RecipeInput.of(SteamworkItems.BRASS_VALVE_CORE, 2));

        // ===== 护甲（4 件） =====
        register(steamworkKey("assemble_steam_helmet"),
                SteamworkItems.STEAM_HELMET,
                RecipeInput.of(SteamworkItems.BRASS_INGOT, 2),
                RecipeInput.of(ItemStack.of(Material.IRON_INGOT), 2),
                RecipeInput.of(SteamworkItems.BRASS_GEAR));

        register(steamworkKey("assemble_steam_chestplate"),
                SteamworkItems.STEAM_CHESTPLATE,
                RecipeInput.of(SteamworkItems.BRASS_INGOT, 4),
                RecipeInput.of(ItemStack.of(Material.IRON_INGOT), 2),
                RecipeInput.of(SteamworkItems.BRASS_SEAL_RING, 5));

        register(steamworkKey("assemble_steam_leggings"),
                SteamworkItems.STEAM_LEGGINGS,
                RecipeInput.of(SteamworkItems.BRASS_INGOT, 2),
                RecipeInput.of(ItemStack.of(Material.IRON_INGOT), 2),
                RecipeInput.of(SteamworkItems.BRASS_GEAR),
                RecipeInput.of(SteamworkItems.BRASS_VALVE_CORE, 2));

        register(steamworkKey("assemble_steam_boots"),
                SteamworkItems.STEAM_BOOTS,
                RecipeInput.of(SteamworkItems.BRASS_INGOT, 2),
                RecipeInput.of(ItemStack.of(Material.IRON_INGOT), 2),
                RecipeInput.of(SteamworkItems.BRASS_GEAR));

        registerEquipmentSet("bronze", new EquipmentSet(
                SteamworkItems.STEAM_BRONZE_SWORD,
                SteamworkItems.STEAM_BRONZE_PICKAXE,
                SteamworkItems.STEAM_BRONZE_AXE,
                SteamworkItems.STEAM_BRONZE_SHOVEL,
                SteamworkItems.STEAM_BRONZE_HOE,
                SteamworkItems.STEAM_BRONZE_HELMET,
                SteamworkItems.STEAM_BRONZE_CHESTPLATE,
                SteamworkItems.STEAM_BRONZE_LEGGINGS,
                SteamworkItems.STEAM_BRONZE_BOOTS
        ), amount -> RecipeInput.of(PylonItems.BRONZE_INGOT, amount), false);

        registerEquipmentSet("invar", new EquipmentSet(
                SteamworkItems.STEAM_INVAR_SWORD,
                SteamworkItems.STEAM_INVAR_PICKAXE,
                SteamworkItems.STEAM_INVAR_AXE,
                SteamworkItems.STEAM_INVAR_SHOVEL,
                SteamworkItems.STEAM_INVAR_HOE,
                SteamworkItems.STEAM_INVAR_HELMET,
                SteamworkItems.STEAM_INVAR_CHESTPLATE,
                SteamworkItems.STEAM_INVAR_LEGGINGS,
                SteamworkItems.STEAM_INVAR_BOOTS
        ), amount -> RecipeInput.of(SteamworkItems.INVAR_INGOT, amount), false);

        // 钨套：工具/武器保持简单；护甲改为高难度——胸甲需要多工序的「蒸汽飞行核心」，
        // 头/腿/靴各需 1 个「喷射喷嘴」部件，让整套钨级飞行装备需要完整的多工序产线。
        registerEquipmentSet("tungsten", new EquipmentSet(
                SteamworkItems.STEAM_TUNGSTEN_SWORD,
                SteamworkItems.STEAM_TUNGSTEN_PICKAXE,
                SteamworkItems.STEAM_TUNGSTEN_AXE,
                SteamworkItems.STEAM_TUNGSTEN_SHOVEL,
                SteamworkItems.STEAM_TUNGSTEN_HOE,
                SteamworkItems.STEAM_TUNGSTEN_HELMET,
                SteamworkItems.STEAM_TUNGSTEN_CHESTPLATE,
                SteamworkItems.STEAM_TUNGSTEN_LEGGINGS,
                SteamworkItems.STEAM_TUNGSTEN_BOOTS
        ), amount -> RecipeInput.of(SteamworkItems.TUNGSTEN_INGOT, amount), true,
                RecipeInput.of(SteamworkItems.STEAM_FLIGHT_CORE),
                RecipeInput.of(SteamworkItems.JET_NOZZLE));

        // ===== 喷射喷嘴（飞行核心 / 钨护甲部件）=====
        register(steamworkKey("assemble_jet_nozzle"),
                SteamworkItems.JET_NOZZLE,
                RecipeInput.of(SteamworkItems.TUNGSTEN_INGOT, 3),
                RecipeInput.of(SteamworkItems.PRECISION_VALVE),
                RecipeInput.of(SteamworkItems.WEAR_PLATE),
                RecipeInput.of(SteamworkItems.HIGH_POLYMER));

        // ===== 蒸汽飞行核心工序链第 4 步（成品）=====
        SteamAssemblyRecipe.RECIPE_TYPE.addRecipe(new SteamAssemblyRecipe(
                steamworkKey("assemble_flight_core"),
                List.of(
                        RecipeInput.of(SequencedWorkpiece.flightCore(3)),
                        RecipeInput.of(SteamworkItems.JET_NOZZLE),
                        RecipeInput.of(SteamworkItems.PRECISION_BEARING, 2),
                        RecipeInput.of(SteamworkItems.HIGH_POLYMER)
                ),
                SteamworkItems.STEAM_FLIGHT_CORE.clone()));

        // ===== 蒸汽罐（3 件） =====
        register(steamworkKey("assemble_canister_brass"),
                SteamworkItems.STEAM_CANISTER_BRASS,
                RecipeInput.of(SteamworkItems.BRASS_INGOT, 5),
                RecipeInput.of(SteamworkItems.BRASS_VALVE_CORE),
                RecipeInput.of(SteamworkItems.RUBBER_GASKET));

        register(steamworkKey("assemble_canister_invar"),
                SteamworkItems.STEAM_CANISTER_INVAR,
                RecipeInput.of(SteamworkItems.INVAR_INGOT, 5),
                RecipeInput.of(SteamworkItems.BRASS_VALVE_CORE),
                RecipeInput.of(SteamworkItems.RUBBER_GASKET));

        // 钨罐（终局高压容器）：额外需要钯合金锭 + 高压法兰，匹配钨级强度
        register(steamworkKey("assemble_canister_tungsten"),
                SteamworkItems.STEAM_CANISTER_TUNGSTEN,
                RecipeInput.of(SteamworkItems.TUNGSTEN_INGOT, 4),
                RecipeInput.of(SteamworkItems.BRASS_VALVE_CORE),
                RecipeInput.of(SteamworkItems.RUBBER_GASKET),
                RecipeInput.of(SteamworkItems.HEATING_COIL),
                RecipeInput.of(SteamworkItems.PALLADIUM_ALLOY_INGOT),
                RecipeInput.of(SteamworkItems.HIGH_PRESSURE_FLANGE));

        // ===== 蒸汽装备改装台 =====
        register(steamworkKey("assemble_canister_bench"),
                SteamworkItems.STEAM_CANISTER_BENCH,
                RecipeInput.of(SteamworkItems.BRASS_INGOT, 3),
                RecipeInput.of(SteamworkItems.BRASS_GEAR, 2),
                RecipeInput.of(SteamworkItems.BRASS_VALVE_CORE, 2),
                RecipeInput.of(ItemStack.of(Material.SMITHING_TABLE)));

        // ===== 蒸汽充汽舱（主方块）===== 额外需要 2 个高压法兰（中期锻造件），加深但不卡早期
        register(steamworkKey("assemble_charging_chamber"),
                SteamworkItems.STEAM_CHARGING_CHAMBER,
                RecipeInput.of(SteamworkItems.BRASS_INGOT, 4),
                RecipeInput.of(SteamworkItems.STEAM_MOTOR),
                RecipeInput.of(SteamworkItems.BRASS_FLOW_VALVE, 2),
                RecipeInput.of(SteamworkItems.PRESSURE_GAUGE),
                RecipeInput.of(SteamworkItems.RUBBER_GASKET),
                RecipeInput.of(SteamworkItems.HIGH_PRESSURE_FLANGE, 2));

        // ===== 涡轮转子（精密蒸汽涡轮核心部件）=====
        register(steamworkKey("assemble_turbine_rotor"),
                SteamworkItems.TURBINE_ROTOR,
                RecipeInput.of(SteamworkItems.PALLADIUM_ALLOY_INGOT),
                RecipeInput.of(SteamworkItems.PRECISION_BEARING, 2),
                RecipeInput.of(SteamworkItems.WEAR_PLATE),
                RecipeInput.of(SteamworkItems.HIGH_PRESSURE_FLANGE));
    }

    private static void register(
            org.bukkit.NamespacedKey key,
            ItemStack result,
            RecipeInput.Item... ingredients
    ) {
        SteamAssemblyRecipe.RECIPE_TYPE.addRecipe(new SteamAssemblyRecipe(key, List.of(ingredients), result));
    }

    private static void registerEquipmentSet(
            String material,
            EquipmentSet set,
            Ingredient ingredient,
            boolean highHeat
    ) {
        registerEquipmentSet(material, set, ingredient, highHeat, null, null);
    }

    /**
     * @param chestplateGate 非空时，作为额外原料追加到该套胸甲（如「蒸汽飞行核心」）。
     * @param otherArmorGate 非空时，作为额外原料追加到头盔/护腿/靴子（如「喷射喷嘴」）。
     */
    private static void registerEquipmentSet(
            String material,
            EquipmentSet set,
            Ingredient ingredient,
            boolean highHeat,
            RecipeInput.Item chestplateGate,
            RecipeInput.Item otherArmorGate
    ) {
        RecipeInput.Item extra = highHeat
                ? RecipeInput.of(SteamworkItems.HEATING_COIL)
                : RecipeInput.of(SteamworkItems.BRASS_INGOT);

        register(steamworkKey("assemble_steam_" + material + "_sword"),
                set.sword,
                ingredient.of(4),
                RecipeInput.of(SteamworkItems.BRASS_GEAR),
                RecipeInput.of(SteamworkItems.BRASS_VALVE_CORE),
                extra);
        register(steamworkKey("assemble_steam_" + material + "_pickaxe"),
                set.pickaxe,
                ingredient.of(3),
                RecipeInput.of(SteamworkItems.BRASS_GEAR),
                RecipeInput.of(SteamworkItems.BRASS_VALVE_CORE, 2),
                extra);
        register(steamworkKey("assemble_steam_" + material + "_axe"),
                set.axe,
                ingredient.of(6),
                RecipeInput.of(SteamworkItems.BRASS_GEAR),
                RecipeInput.of(SteamworkItems.BRASS_VALVE_CORE),
                extra);
        register(steamworkKey("assemble_steam_" + material + "_shovel"),
                set.shovel,
                ingredient.of(6),
                RecipeInput.of(SteamworkItems.BRASS_GEAR),
                RecipeInput.of(SteamworkItems.BRASS_VALVE_CORE),
                extra);
        register(steamworkKey("assemble_steam_" + material + "_hoe"),
                set.hoe,
                ingredient.of(3),
                RecipeInput.of(SteamworkItems.BRASS_GEAR),
                RecipeInput.of(SteamworkItems.BRASS_VALVE_CORE, 2),
                extra);
        registerArmor(steamworkKey("assemble_steam_" + material + "_helmet"),
                set.helmet, otherArmorGate,
                ingredient.of(4),
                RecipeInput.of(SteamworkItems.BRASS_GEAR),
                extra);
        registerArmor(steamworkKey("assemble_steam_" + material + "_chestplate"),
                set.chestplate, chestplateGate,
                ingredient.of(6),
                RecipeInput.of(SteamworkItems.BRASS_SEAL_RING, 5),
                extra);
        registerArmor(steamworkKey("assemble_steam_" + material + "_leggings"),
                set.leggings, otherArmorGate,
                ingredient.of(5),
                RecipeInput.of(SteamworkItems.BRASS_GEAR),
                RecipeInput.of(SteamworkItems.BRASS_VALVE_CORE, 2),
                extra);
        registerArmor(steamworkKey("assemble_steam_" + material + "_boots"),
                set.boots, otherArmorGate,
                ingredient.of(4),
                RecipeInput.of(SteamworkItems.BRASS_GEAR),
                extra);
    }

    /** 注册一件护甲，并在 {@code gate} 非空时追加为额外原料（多工序产物门槛）。 */
    private static void registerArmor(
            org.bukkit.NamespacedKey key,
            ItemStack result,
            RecipeInput.Item gate,
            RecipeInput.Item... base
    ) {
        java.util.List<RecipeInput.Item> ingredients = new java.util.ArrayList<>(List.of(base));
        if (gate != null) {
            ingredients.add(gate);
        }
        SteamAssemblyRecipe.RECIPE_TYPE.addRecipe(
                new SteamAssemblyRecipe(key, ingredients, result));
    }

    @FunctionalInterface
    private interface Ingredient {
        RecipeInput.Item of(int amount);
    }

    private record EquipmentSet(
            ItemStack sword,
            ItemStack pickaxe,
            ItemStack axe,
            ItemStack shovel,
            ItemStack hoe,
            ItemStack helmet,
            ItemStack chestplate,
            ItemStack leggings,
            ItemStack boots
    ) {
    }
}
