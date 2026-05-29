package io.github.steamwork;

import io.github.pylonmc.rebar.addon.RebarAddon;
import io.github.steamwork.content.machines.SteamArm;
import io.github.steamwork.content.machines.SteamPress;
import lombok.Getter;
import org.bukkit.Material;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;
import java.util.Set;

public final class Steamwork extends JavaPlugin implements RebarAddon {

    @Getter
    private static Steamwork instance;

    private static final String ANSI_RESET = "\u001B[0m";
    private static final String ANSI_CYAN = "\u001B[36m";
    private static final String ANSI_GOLD = "\u001B[33m";
    private static final String ANSI_GRAY = "\u001B[37m";

    @Override
    public void onEnable() {
        instance = this;

        printBanner();

        registerWithRebar();
        saveDefaultConfig();

        SteamworkFluids.initialize();
        SteamworkItems.initialize();
        SteamworkBlocks.initialize();

        // 注册需要全局监听的方块逻辑（必须在所有 block 注册完成后调用）。
        // 改为显式注册以替代 SteamArm 之前的 static {} 块注册方式。
        SteamArm.registerGlobalListeners();
        SteamPress.registerGlobalListeners();
        io.github.steamwork.content.machines.PneumaticCargoHub.registerGlobalListeners();
        getServer().getPluginManager().registerEvents(
                new io.github.steamwork.content.machines.upgrade.MachineUpgradeListener(), this);
        new io.github.steamwork.content.line.ProductionLineRegistry(this);
        getServer().getPluginManager().registerEvents(
                new io.github.steamwork.content.line.ProductionLineListener(), this);
        getServer().getPluginManager().registerEvents(
                new io.github.steamwork.content.line.PylonLineOutputBridge(), this);

        SteamworkResearches.initialize();
        SteamworkRecipes.initialize();
        SteamworkPages.initialize();

        getLogger().info("Steamwork " + getPluginMeta().getVersion() + " enabled");
    }

    @Override
    public void onDisable() {
        // Bukkit 在 plugin disable 时会自动 unregister 所有 listener 和 scheduled task。
        // 这里只做防御性清理：以防有外部代码持有旧引用，强制清理本插件相关的 handler。
        HandlerList.unregisterAll(this);
        getLogger().info("Steamwork " + getPluginMeta().getVersion() + " disabled");
    }

    private void printBanner() {
        String banner =
                ANSI_CYAN + " ________  _________  _______   ________  _____ ______   ___       __   ________  ________  ___  __       \n" +
                ANSI_CYAN + "|\\   ____\\|\\___   ___\\\\  ___ \\ |\\   __  \\|\\   _ \\  _   \\|\\  \\     |\\  \\|\\   __  \\|\\   __  \\|\\  \\|\\  \\     \n" +
                ANSI_CYAN + "\\ \\  \\___|||___ \\  \\_\\ \\   __/|\\ \\  \\|\\  \\ \\  \\\\__\\ \\  \\ \\  \\    \\ \\  \\ \\  \\|\\  \\ \\  \\|\\  \\ \\  \\/  /|_   \n" +
                ANSI_CYAN + " \\ \\_____  \\   \\ \\  \\ \\ \\  \\_|/_\\ \\   __  \\ \\  \\\\|__| \\  \\ \\  \\  __\\ \\  \\ \\  \\\\\\  \\ \\   _  _\\ \\   ___  \\  \n" +
                ANSI_CYAN + "  \\|____|\\  \\   \\ \\  \\ \\ \\  \\_|\\ \\ \\  \\ \\  \\ \\  \\    \\ \\  \\ \\  \\|\\__\\_\\  \\ \\  \\\\\\  \\ \\  \\\\  \\\\ \\  \\\\ \\  \\ \n" +
                ANSI_CYAN + "    ____\\_\\  \\   \\ \\__\\ \\ \\_______\\ \\__\\ \\__\\ \\__\\    \\ \\__\\ \\____________\\ \\_______\\ \\__\\\\ _\\\\ \\__\\\\ \\__\\\n" +
                ANSI_CYAN + "   |\\_________\\   \\|__|  \\|_______|\\|__|\\|__|\\|__|     \\|__|\\|____________|\\|_______|\\|__|\\|__|\\|__| \\|__|\n" +
                ANSI_CYAN + "   \\|_________|" +
                ANSI_RESET;

        String info =
                "  " + ANSI_GOLD + "Steam & Pressure Technology" + ANSI_RESET + "\n" +
                "  " + ANSI_GRAY + "author: sban66" + ANSI_RESET + "\n" +
                "  " + ANSI_GRAY + "version: " + getPluginMeta().getVersion() + ANSI_RESET;

        getLogger().info("\n" + banner + info);
    }

    @Override
    public @NotNull JavaPlugin getJavaPlugin() {
        return instance;
    }

    @Override
    public @NotNull Set<@NotNull Locale> getLanguages() {
        return Set.of(Locale.ENGLISH, Locale.SIMPLIFIED_CHINESE);
    }

    @Override
    public @NotNull Material getMaterial() {
        return Material.FURNACE;
    }
}
