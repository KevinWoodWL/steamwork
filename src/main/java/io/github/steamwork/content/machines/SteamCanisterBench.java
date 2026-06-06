package io.github.steamwork.content.machines;

import io.github.pylonmc.rebar.block.RebarBlock;
import io.github.pylonmc.rebar.block.interfaces.GuiRebarBlock;
import io.github.pylonmc.rebar.block.interfaces.VirtualInventoryRebarBlock;
import io.github.pylonmc.rebar.block.context.BlockBreakContext;
import io.github.pylonmc.rebar.block.context.BlockCreateContext;
import io.github.pylonmc.rebar.item.RebarItem;
import io.github.pylonmc.rebar.item.builder.ItemStackBuilder;
import io.github.pylonmc.rebar.util.MachineUpdateReason;
import io.github.pylonmc.rebar.util.gui.GuiItems;
import io.github.steamwork.content.equipment.SteamArmorItem;
import io.github.steamwork.content.equipment.SteamCanisterType;
import io.github.steamwork.content.equipment.SteamToolItem;
import io.github.steamwork.util.SteamCharge;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.jetbrains.annotations.NotNull;
import xyz.xenondevs.invui.Click;
import xyz.xenondevs.invui.gui.Gui;
import xyz.xenondevs.invui.inventory.VirtualInventory;
import xyz.xenondevs.invui.item.AbstractItem;
import xyz.xenondevs.invui.item.ItemProvider;

import java.util.List;
import java.util.Map;

/**
 * 蒸汽装备改装台。
 *
 * <p>给蒸汽装备安装 / 拆卸蒸汽罐:</p>
 * <ul>
 *   <li><b>安装</b>:装备槽放未装罐的蒸汽装备 + 罐槽放蒸汽罐 → 点操作按钮,
 *       装备获得该罐容量(并转入罐内残余蒸汽),消耗一只罐。</li>
 *   <li><b>拆卸</b>:装备槽放已装罐装备 + 罐槽留空 → 点操作按钮,
 *       吐回一只对应空罐(装备残余蒸汽丢弃),装备回到未装罐态。</li>
 * </ul>
 */
public class SteamCanisterBench extends RebarBlock implements
        GuiRebarBlock,
        VirtualInventoryRebarBlock {

    private final VirtualInventory equipmentInventory = new VirtualInventory(1);
    private final VirtualInventory canisterInventory = new VirtualInventory(1);

    private final OperateItem operateItem = new OperateItem();

    // ===== 物品(指南占位)=====
    public static class Item extends RebarItem {
        public Item(@NotNull ItemStack stack) { super(stack); }
    }

    // ===== 构造 =====

    @SuppressWarnings("unused")
    public SteamCanisterBench(@NotNull Block block, @NotNull BlockCreateContext context) {
        super(block, context);
    }

    @SuppressWarnings("unused")
    public SteamCanisterBench(@NotNull Block block, @NotNull PersistentDataContainer pdc) {
        super(block, pdc);
    }

    // ===== GUI =====

    @Override
    public @NotNull Gui createGui() {
        return Gui.builder()
                .setStructure(
                        "# # # # # # # # #",
                        "# # e # > # c # #",
                        "# # # # # # # # #"
                )
                .addIngredient('#', GuiItems.background())
                .addIngredient('e', equipmentInventory)
                .addIngredient('c', canisterInventory)
                .addIngredient('>', operateItem)
                .build();
    }

    @Override
    public @NotNull Component getGuiTitle() {
        return noItalic(Component.translatable("steamwork.gui.steam_canister_bench.title"));
    }

    @Override
    public @NotNull Map<String, VirtualInventory> getVirtualInventories() {
        return Map.of("equipment", equipmentInventory, "canister", canisterInventory);
    }

    @Override
    public void onBlockBreak(@NotNull List<@NotNull ItemStack> drops, @NotNull BlockBreakContext context) {
        VirtualInventoryRebarBlock.super.onBlockBreak(drops, context);
    }

    // ===== 装/拆逻辑 =====

    private static boolean isSteamEquipment(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        RebarItem item = RebarItem.fromStack(stack);
        return item instanceof SteamToolItem || item instanceof SteamArmorItem;
    }

    private void operate(@NotNull Player player) {
        ItemStack equip = equipmentInventory.getItem(0);
        ItemStack canister = canisterInventory.getItem(0);
        MachineUpdateReason reason = new MachineUpdateReason();

        if (!isSteamEquipment(equip)) {
            feedback(player, "need_equipment", Sound.BLOCK_NOTE_BLOCK_BASS);
            return;
        }

        boolean alreadyHasCanister = SteamCharge.hasCanister(equip);
        SteamCanisterType insertType = SteamCanisterType.fromCanisterStack(canister);

        if (!alreadyHasCanister) {
            // 安装:需罐槽有罐
            if (insertType == null) {
                feedback(player, "need_canister", Sound.BLOCK_NOTE_BLOCK_BASS);
                return;
            }
            double transfer = SteamCharge.getAmount(canister); // 转入罐内残余蒸汽
            SteamCharge.installCanister(equip, insertType.socketKey, insertType.capacity, transfer);
            equipmentInventory.setItem(reason, 0, equip);
            canister.subtract(1);
            canisterInventory.setItem(reason, 0, canister.getAmount() > 0 ? canister : null);
            player.sendMessage(Component.translatable(
                    "steamwork.message.steam_canister_bench.installed",
                    io.github.pylonmc.rebar.i18n.RebarArgument.of("canister",
                            Component.translatable("steamwork.item.steam_canister_" + insertType.socketKey + ".name"))));
            player.playSound(player.getLocation(), Sound.BLOCK_PISTON_CONTRACT, 0.8f, 1.4f);
        } else {
            // 拆卸:需罐槽为空
            if (canister != null && !canister.isEmpty()) {
                feedback(player, "canister_slot_occupied", Sound.BLOCK_NOTE_BLOCK_BASS);
                return;
            }
            SteamCanisterType current = SteamCanisterType.fromSocketKey(SteamCharge.getSocket(equip));
            SteamCharge.clearCanister(equip);
            equipmentInventory.setItem(reason, 0, equip);
            if (current != null) {
                canisterInventory.setItem(reason, 0, current.makeCanisterItem());
            }
            player.sendMessage(Component.translatable(
                    "steamwork.message.steam_canister_bench.removed"));
            player.playSound(player.getLocation(), Sound.BLOCK_PISTON_EXTEND, 0.8f, 1.0f);
        }
        operateItem.notifyWindows();
    }

    private void feedback(@NotNull Player player, @NotNull String key, @NotNull Sound sound) {
        player.sendMessage(Component.translatable("steamwork.message.steam_canister_bench." + key));
        player.playSound(player.getLocation(), sound, 0.6f, 0.8f);
    }

    // ===== 操作按钮 =====

    private final class OperateItem extends AbstractItem {
        @Override
        public @NotNull ItemProvider getItemProvider(@NotNull Player viewer) {
            ItemStack equip = equipmentInventory.getItem(0);
            ItemStack canister = canisterInventory.getItem(0);
            boolean equipValid = isSteamEquipment(equip);
            boolean hasCanister = equipValid && SteamCharge.hasCanister(equip);

            Material mat;
            String stateKey;
            if (!equipValid) {
                mat = Material.GRAY_DYE;
                stateKey = "idle";
            } else if (!hasCanister && SteamCanisterType.fromCanisterStack(canister) != null) {
                mat = Material.LIME_DYE;
                stateKey = "ready_install";
            } else if (hasCanister) {
                mat = Material.ORANGE_DYE;
                stateKey = "ready_remove";
            } else {
                mat = Material.GRAY_DYE;
                stateKey = "idle";
            }

            return ItemStackBuilder.of(mat)
                    .name(noItalic(Component.translatable("steamwork.gui.steam_canister_bench.operate")))
                    .lore(List.of(noItalic(Component.translatable(
                            "steamwork.gui.steam_canister_bench.state." + stateKey))));
        }

        @Override
        public void handleClick(@NotNull ClickType clickType, @NotNull Player player, @NotNull Click click) {
            if (clickType.isLeftClick()) {
                operate(player);
            }
        }
    }

    private static @NotNull Component noItalic(@NotNull Component component) {
        return component.decoration(TextDecoration.ITALIC, false);
    }
}
