package io.github.steamwork.util;

import io.github.pylonmc.rebar.i18n.RebarArgument;
import io.github.pylonmc.rebar.item.builder.ItemStackBuilder;
import io.github.steamwork.SteamworkKeys;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.TooltipDisplay;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class SequencedWorkpiece {

    private SequencedWorkpiece() {
        throw new AssertionError("Utility class");
    }

    public static final String PALLADIUM_ALLOY = "palladium_alloy";

    private static final int MAX_DAMAGE = 1000;
    private static final NamespacedKey LINE_KEY = SteamworkUtils.steamworkKey("sequence_line");
    private static final NamespacedKey STEP_KEY = SteamworkUtils.steamworkKey("sequence_step");
    private static final NamespacedKey STEPS_KEY = SteamworkUtils.steamworkKey("sequence_steps");

    private static final String BASE_KEY = "steamwork.item.sequenced_workpiece";

    public static @NotNull ItemStack palladiumAlloy(int step) {
        return create(PALLADIUM_ALLOY, step, 4);
    }

    public static boolean is(@NotNull ItemStack stack, @NotNull String line, int step) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return false;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        String actualLine = pdc.get(LINE_KEY, PersistentDataType.STRING);
        Integer actualStep = pdc.get(STEP_KEY, PersistentDataType.INTEGER);
        return line.equals(actualLine) && actualStep != null && actualStep == step;
    }

    private static @NotNull ItemStack create(@NotNull String line, int step, int steps) {
        int clampedStep = Math.max(1, Math.min(step, steps));
        int pct = (int) Math.round(100.0 * clampedStep / Math.max(1, steps));
        int damage = Math.max(1, MAX_DAMAGE - (int) Math.round((MAX_DAMAGE - 1) * pct / 100.0));

        Component name = Component.translatable(BASE_KEY + "." + line + ".step_" + clampedStep + ".name")
                .decoration(TextDecoration.ITALIC, false);

        Component loreHeader = noItalic(Component.translatable(BASE_KEY + ".tooltip.header"));
        Component loreLine = noItalic(Component.translatable(BASE_KEY + ".tooltip.line",
                RebarArgument.of("line", Component.translatable(BASE_KEY + "." + line + ".line_name"))));
        Component loreStep = noItalic(Component.translatable(BASE_KEY + ".tooltip.step",
                RebarArgument.of("step", clampedStep),
                RebarArgument.of("steps", steps)));

        ItemStack stack = ItemStackBuilder.rebar(Material.RAW_IRON, SteamworkKeys.SEQUENCED_WORKPIECE)
                .name(name)
                .set(DataComponentTypes.MAX_DAMAGE, MAX_DAMAGE)
                .set(DataComponentTypes.DAMAGE, damage)
                .set(DataComponentTypes.MAX_STACK_SIZE, 1)
                .set(
                        DataComponentTypes.TOOLTIP_DISPLAY,
                        TooltipDisplay.tooltipDisplay()
                                .addHiddenComponents(DataComponentTypes.DAMAGE, DataComponentTypes.MAX_DAMAGE)
                )
                .lore(List.of(loreHeader, loreLine, loreStep))
                .build();

        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(LINE_KEY, PersistentDataType.STRING, line);
            pdc.set(STEP_KEY, PersistentDataType.INTEGER, clampedStep);
            pdc.set(STEPS_KEY, PersistentDataType.INTEGER, steps);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private static @NotNull Component noItalic(@NotNull Component component) {
        return component.decoration(TextDecoration.ITALIC, false);
    }
}
