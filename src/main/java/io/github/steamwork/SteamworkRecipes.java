package io.github.steamwork;

import io.github.pylonmc.rebar.recipe.RecipeType;
import io.github.steamwork.content.machines.HeavyImpactCrusher;
import io.github.steamwork.content.machines.HydraulicForge;
import io.github.steamwork.content.machines.PrecisionCatalyticReactor;
import io.github.steamwork.content.machines.PrecisionCentrifuge;
import io.github.steamwork.content.machines.PrecisionCrystallizer;
import io.github.steamwork.content.machines.PrecisionFoundry;
import io.github.steamwork.content.machines.SteamGrinder;
import io.github.steamwork.content.machines.SteamPrecisionMill;
import io.github.steamwork.content.machines.SteamPress;
import io.github.steamwork.content.machines.SteamPressurizedFurnace;
import io.github.steamwork.content.machines.SteamSteepingVat;
import io.github.steamwork.content.machines.SteamSterilizer;
import io.github.steamwork.content.machines.SteamWashingTrough;
import io.github.steamwork.recipes.SteamAssemblyRecipe;
import io.github.steamwork.recipes.SteamCatalyticReactionRecipe;
import io.github.steamwork.recipes.SteamCentrifugationRecipe;
import io.github.steamwork.recipes.SteamCrushingRecipe;
import io.github.steamwork.recipes.SteamCrystallizingRecipe;
import io.github.steamwork.recipes.SteamDistillationRecipe;
import io.github.steamwork.recipes.SteamForgingRecipe;
import io.github.steamwork.recipes.SteamFoundryRecipe;
import io.github.steamwork.recipes.SteamGrindingRecipe;
import io.github.steamwork.recipes.SteamMillingRecipe;
import io.github.steamwork.recipes.SteamPressingRecipe;
import io.github.steamwork.recipes.SteamPressurizingRecipe;
import io.github.steamwork.recipes.SteamResearchRecipe;
import io.github.steamwork.recipes.SteamSteepingRecipe;
import io.github.steamwork.recipes.SteamSterilizingRecipe;
import io.github.steamwork.recipes.SteamWashingRecipe;
import io.github.steamwork.recipes.registration.AssemblyRecipes;
import io.github.steamwork.recipes.registration.CatalyticReactionRecipes;
import io.github.steamwork.recipes.registration.CookingRecipes;
import io.github.steamwork.recipes.registration.CraftingRecipes;
import io.github.steamwork.recipes.registration.DistillationRecipes;
import io.github.steamwork.recipes.registration.FoundryRecipes;
import io.github.steamwork.recipes.registration.GrindingRecipes;
import io.github.steamwork.recipes.registration.CentrifugationRecipes;
import io.github.steamwork.recipes.registration.CrushingRecipes;
import io.github.steamwork.recipes.registration.CrystallizingRecipes;
import io.github.steamwork.recipes.registration.ForgingRecipes;
import io.github.steamwork.recipes.registration.MillingRecipes;
import io.github.steamwork.recipes.registration.PressingRecipes;
import io.github.steamwork.recipes.registration.PressurizingRecipes;
import io.github.steamwork.recipes.registration.ResearchRecipes;
import io.github.steamwork.recipes.registration.SmelteryRecipes;
import io.github.steamwork.recipes.registration.SteepingRecipes;
import io.github.steamwork.recipes.registration.SterilizingRecipes;
import io.github.steamwork.recipes.registration.WashingRecipes;

public final class SteamworkRecipes {

    private SteamworkRecipes() {
        throw new AssertionError("Utility class");
    }

    private static volatile boolean initialized = false;
    private static volatile boolean asyncLoading = false;

    public static void initialize() {
        initializeAsync();
    }

    /** 同步初始化（保留兼容性）。 */
    public static void initializeSync() {
        if (initialized) return;
        synchronized (SteamworkRecipes.class) {
            if (initialized) return;
            doInitialize();
            initialized = true;
        }
    }

    /**
     * 异步初始化配方：在异步线程注册配方类型，主线程注册配方本体，
     * 启动时不阻塞主线程。
     */
    public static void initializeAsync() {
        if (initialized || asyncLoading) return;
        synchronized (SteamworkRecipes.class) {
            if (initialized || asyncLoading) return;
            asyncLoading = true;
        }

        Thread asyncThread = new Thread(() -> {
            try {
                registerCustomRecipeTypes();

                // 配方注册必须在主线程执行。
                Steamwork.getInstance().getServer().getScheduler().runTask(
                        Steamwork.getInstance(),
                        () -> {
                            try {
                                registerAllRecipes();
                                refreshMachineCaches();

                                synchronized (SteamworkRecipes.class) {
                                    initialized = true;
                                    asyncLoading = false;
                                }
                            } catch (Exception e) {
                                Steamwork.getInstance().getLogger().warning("注册配方时发生错误");
                                Steamwork.getInstance().getLogger().warning(e.getMessage());
                                synchronized (SteamworkRecipes.class) {
                                    initialized = false;
                                    asyncLoading = false;
                                }
                            }
                        }
                );
            } catch (Exception e) {
                Steamwork.getInstance().getLogger().warning("异步加载配方失败，回退到同步加载");
                Steamwork.getInstance().getLogger().warning(e.getMessage());
                synchronized (SteamworkRecipes.class) {
                    initialized = false;
                    asyncLoading = false;
                }
                initializeSync();
            }
        }, "Steamwork-Recipes-Loader");
        asyncThread.setPriority(Thread.MIN_PRIORITY);
        asyncThread.start();
    }

    /** 安全注册配方类型，避免重复注册导致异常。 */
    private static void safeRegisterRecipeType(RecipeType<?> type) {
        try {
            type.register();
        } catch (IllegalStateException ignored) {
            // 已经被其他线程注册，忽略
        }
    }

    /** 等待初始化完成；超时后回退同步加载。 */
    public static void waitForInitialization() {
        while (asyncLoading) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException ignored) {
                break;
            }
        }
        if (!initialized) {
            initializeSync();
        }
    }

    private static void doInitialize() {
        registerCustomRecipeTypes();
        registerAllRecipes();
        refreshMachineCaches();
    }

    private static void registerCustomRecipeTypes() {
        safeRegisterRecipeType(SteamPressurizingRecipe.RECIPE_TYPE);
        safeRegisterRecipeType(SteamSterilizingRecipe.RECIPE_TYPE);
        safeRegisterRecipeType(SteamSteepingRecipe.RECIPE_TYPE);
        safeRegisterRecipeType(SteamWashingRecipe.RECIPE_TYPE);
        safeRegisterRecipeType(SteamPressingRecipe.RECIPE_TYPE);
        safeRegisterRecipeType(SteamGrindingRecipe.RECIPE_TYPE);
        safeRegisterRecipeType(SteamMillingRecipe.RECIPE_TYPE);
        safeRegisterRecipeType(SteamFoundryRecipe.RECIPE_TYPE);
        safeRegisterRecipeType(SteamCatalyticReactionRecipe.RECIPE_TYPE);
        safeRegisterRecipeType(SteamAssemblyRecipe.RECIPE_TYPE);
        safeRegisterRecipeType(SteamResearchRecipe.RECIPE_TYPE);
        safeRegisterRecipeType(SteamDistillationRecipe.RECIPE_TYPE);
        safeRegisterRecipeType(SteamCrushingRecipe.RECIPE_TYPE);
        safeRegisterRecipeType(SteamForgingRecipe.RECIPE_TYPE);
        safeRegisterRecipeType(SteamCentrifugationRecipe.RECIPE_TYPE);
        safeRegisterRecipeType(SteamCrystallizingRecipe.RECIPE_TYPE);
    }

    private static void registerAllRecipes() {
        CraftingRecipes.register();
        CookingRecipes.register();
        SmelteryRecipes.register();
        PressurizingRecipes.register();
        SterilizingRecipes.register();
        SteepingRecipes.register();
        WashingRecipes.register();
        PressingRecipes.register();
        GrindingRecipes.register();
        CentrifugationRecipes.register();
        CrushingRecipes.register();
        CrystallizingRecipes.register();
        ForgingRecipes.register();
        MillingRecipes.register();
        FoundryRecipes.register();
        CatalyticReactionRecipes.register();
        AssemblyRecipes.register();
        ResearchRecipes.register();
        DistillationRecipes.register();
    }

    private static void refreshMachineCaches() {
        SteamPressurizedFurnace.refreshRecipeCache();
        SteamSterilizer.refreshRecipeCache();
        SteamSteepingVat.refreshRecipeCache();
        SteamWashingTrough.refreshRecipeCache();
        SteamPress.refreshRecipeCache();
        SteamGrinder.refreshRecipeCache();
        SteamPrecisionMill.refreshRecipeCache();
        PrecisionFoundry.refreshRecipeCache();
        PrecisionCatalyticReactor.refreshRecipeCache();
        HeavyImpactCrusher.refreshRecipeCache();
        HydraulicForge.refreshRecipeCache();
        PrecisionCrystallizer.refreshRecipeCache();
        PrecisionCentrifuge.refreshRecipeCache();
    }
}
