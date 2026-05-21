package io.github.steamwork.recipes.registration;

import io.github.pylonmc.rebar.recipe.RecipeInput;
import io.github.steamwork.SteamworkItems;
import io.github.steamwork.recipes.SteamAssemblyRecipe;
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
                RecipeInput.of(new ItemStack(Material.IRON_INGOT), 4),
                RecipeInput.of(SteamworkItems.BRASS_GEAR),
                RecipeInput.of(SteamworkItems.BRASS_VALVE_CORE));

        register(steamworkKey("assemble_steam_pickaxe"),
                SteamworkItems.STEAM_PICKAXE,
                RecipeInput.of(SteamworkItems.BRASS_INGOT, 2),
                RecipeInput.of(new ItemStack(Material.IRON_INGOT), 3),
                RecipeInput.of(SteamworkItems.BRASS_GEAR),
                RecipeInput.of(SteamworkItems.BRASS_VALVE_CORE, 2));

        register(steamworkKey("assemble_steam_axe"),
                SteamworkItems.STEAM_AXE,
                RecipeInput.of(SteamworkItems.BRASS_INGOT),
                RecipeInput.of(new ItemStack(Material.IRON_INGOT), 6),
                RecipeInput.of(SteamworkItems.BRASS_GEAR),
                RecipeInput.of(SteamworkItems.BRASS_VALVE_CORE));

        register(steamworkKey("assemble_steam_shovel"),
                SteamworkItems.STEAM_SHOVEL,
                RecipeInput.of(new ItemStack(Material.IRON_INGOT), 6),
                RecipeInput.of(SteamworkItems.BRASS_GEAR),
                RecipeInput.of(SteamworkItems.BRASS_VALVE_CORE));

        register(steamworkKey("assemble_steam_hoe"),
                SteamworkItems.STEAM_HOE,
                RecipeInput.of(SteamworkItems.BRASS_INGOT, 2),
                RecipeInput.of(new ItemStack(Material.IRON_INGOT), 3),
                RecipeInput.of(SteamworkItems.BRASS_GEAR),
                RecipeInput.of(SteamworkItems.BRASS_VALVE_CORE, 2));

        // ===== 护甲（4 件） =====
        register(steamworkKey("assemble_steam_helmet"),
                SteamworkItems.STEAM_HELMET,
                RecipeInput.of(SteamworkItems.BRASS_INGOT, 2),
                RecipeInput.of(new ItemStack(Material.IRON_INGOT), 2),
                RecipeInput.of(SteamworkItems.BRASS_GEAR));

        register(steamworkKey("assemble_steam_chestplate"),
                SteamworkItems.STEAM_CHESTPLATE,
                RecipeInput.of(SteamworkItems.BRASS_INGOT, 4),
                RecipeInput.of(new ItemStack(Material.IRON_INGOT), 2),
                RecipeInput.of(SteamworkItems.BRASS_SEAL_RING, 5));

        register(steamworkKey("assemble_steam_leggings"),
                SteamworkItems.STEAM_LEGGINGS,
                RecipeInput.of(SteamworkItems.BRASS_INGOT, 2),
                RecipeInput.of(new ItemStack(Material.IRON_INGOT), 2),
                RecipeInput.of(SteamworkItems.BRASS_GEAR),
                RecipeInput.of(SteamworkItems.BRASS_VALVE_CORE, 2));

        register(steamworkKey("assemble_steam_boots"),
                SteamworkItems.STEAM_BOOTS,
                RecipeInput.of(SteamworkItems.BRASS_INGOT, 2),
                RecipeInput.of(new ItemStack(Material.IRON_INGOT), 2),
                RecipeInput.of(SteamworkItems.BRASS_GEAR));

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

        register(steamworkKey("assemble_canister_tungsten"),
                SteamworkItems.STEAM_CANISTER_TUNGSTEN,
                RecipeInput.of(SteamworkItems.TUNGSTEN_INGOT, 4),
                RecipeInput.of(SteamworkItems.BRASS_VALVE_CORE),
                RecipeInput.of(SteamworkItems.RUBBER_GASKET),
                RecipeInput.of(SteamworkItems.HEATING_COIL));
    }

    private static void register(
            org.bukkit.NamespacedKey key,
            ItemStack result,
            RecipeInput.Item... ingredients
    ) {
        SteamAssemblyRecipe.RECIPE_TYPE.addRecipe(new SteamAssemblyRecipe(key, List.of(ingredients), result));
    }
}
