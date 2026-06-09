package io.github.steamwork.content.machines;

import io.github.pylonmc.pylon.util.PylonUtils;
import io.github.pylonmc.rebar.block.BlockStorage;
import io.github.pylonmc.rebar.block.RebarBlock;
import io.github.pylonmc.rebar.block.interfaces.DirectionalRebarBlock;
import io.github.pylonmc.rebar.block.interfaces.FluidBufferRebarBlock;
import io.github.pylonmc.rebar.block.interfaces.GuiRebarBlock;
import io.github.pylonmc.rebar.block.interfaces.TickingRebarBlock;
import io.github.pylonmc.rebar.block.interfaces.VirtualInventoryRebarBlock;
import io.github.pylonmc.rebar.block.context.BlockBreakContext;
import io.github.pylonmc.rebar.block.context.BlockCreateContext;
import io.github.pylonmc.rebar.config.adapter.ConfigAdapter;
import io.github.pylonmc.rebar.datatypes.RebarSerializers;
import io.github.pylonmc.rebar.fluid.FluidPointType;
import io.github.pylonmc.rebar.i18n.RebarArgument;
import io.github.pylonmc.rebar.item.ItemTypeWrapper;
import io.github.pylonmc.rebar.item.RebarItem;
import io.github.pylonmc.rebar.item.builder.ItemStackBuilder;
import io.github.pylonmc.rebar.util.MachineUpdateReason;
import io.github.pylonmc.rebar.util.gui.GuiItems;
import io.github.pylonmc.rebar.util.gui.unit.UnitFormat;
import io.github.pylonmc.rebar.waila.WailaDisplay;
import io.github.steamwork.SteamworkFluids;
import io.github.steamwork.util.PneumaticUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static io.github.steamwork.util.SteamworkUtils.steamworkKey;
import xyz.xenondevs.invui.Click;
import xyz.xenondevs.invui.gui.Gui;
import xyz.xenondevs.invui.inventory.VirtualInventory;
import xyz.xenondevs.invui.item.AbstractItem;
import xyz.xenondevs.invui.item.ItemProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 钂告苯鍘嬪姏鍒嗘嫞鏈?鈥斺€?娑堣€楄捀姹藉皢鍐呴儴杈撳叆妲界墿鍝佷富鍔ㄦ帹閫佸埌涓婁笅宸﹀彸鍓嶅悗鍏釜鐩搁偦瀹瑰櫒锛? * 鏍规嵁杩囨护妲介厤缃殑鐗╁搧绫诲瀷鍐冲畾鎺ㄩ€佺洰鏍囷紱鏃犲尮閰嶆椂杞绌轰綅鎶曢€併€? *
 * GUI 甯冨眬锛?琛?脳 9鍒楋級锛? *   琛?锛歔bg][N鏍嘳[N婊[S鏍嘳[S婊[E鏍嘳[E婊[W鏍嘳[W婊
 *   琛?锛歔bg][bg][bg][U鏍嘳[U婊[D鏍嘳[D婊[bg][bg]
 *   琛?锛氳儗鏅垎闅? *   琛?锛氳緭鍏ユЫ 脳 9锛堟弧琛岋級
 *   琛?锛氳捀姹介噺琛?| 鑳屾櫙 脳 7 | 鐘舵€? *
 * 杩囨护妲藉搴斿叧绯伙紙filterInventory 绱㈠紩涓?FACES 涓€鑷达級锛? *   0=鍖? 1=鍗? 2=涓? 3=瑗? 4=涓? 5=涓? */
public class SteamSorter extends RebarBlock implements
        DirectionalRebarBlock,
        FluidBufferRebarBlock,
        GuiRebarBlock,
        TickingRebarBlock,
        VirtualInventoryRebarBlock {

    private static final BlockFace[] FACES = {
            BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST,
            BlockFace.WEST, BlockFace.UP, BlockFace.DOWN
    };

    private final int defaultTickInterval = getSettings().getOrThrow("tick-interval", ConfigAdapter.INTEGER);
    private final double steamBuffer = getSettings().getOrThrow("pressurized-buffer", ConfigAdapter.DOUBLE);
    private final double steamPerItem = getSettings().getOrThrow("steam-per-item", ConfigAdapter.DOUBLE);
    private final int itemsPerTick = getSettings().getOrThrow("items-per-tick", ConfigAdapter.INTEGER);

    private boolean lastActive = false;
    /** 鐢ㄤ簬鍒ゆ柇钂告苯閲忔槸鍚﹀彉鍖栵紝閬垮厤 GUI 姣?tick 鏃犺皳鍒锋柊 */
    private double lastSteamAmount = -1;
    /** 杞娓告爣锛氭瘡娆℃垚鍔熷彂閫佸悗閫掑锛岃法 tick 鎸佺画锛屼繚璇佸鐩爣鍧囪　鍒嗗彂銆?*/
    private int roundRobinCursor = 0;

    public static final int MIN_TICK_INTERVAL = 1;
    public static final int MAX_TICK_INTERVAL = 200;

    private static final NamespacedKey TICK_INTERVAL_KEY = steamworkKey("ss_tick_interval");
    /** PDC 閿細姣忎釜闈㈢殑骞界伒杩囨护妲芥潗璐紙鏉愯川鍚嶇О瀛楃涓诧紝缂哄け = 鏃犺繃婊わ級銆?*/
    private static final NamespacedKey[] FILTER_KEYS = {
            steamworkKey("sf_n"), steamworkKey("sf_s"), steamworkKey("sf_e"),
            steamworkKey("sf_w"), steamworkKey("sf_u"), steamworkKey("sf_d")
    };

    /**
     * 杈撳叆妲斤紙7鏍硷級锛氬緟鍒嗘嫞鐨勭墿鍝佹斁鍦ㄨ繖閲岋紝鏈哄櫒涓诲姩鎺ㄩ€佸嚭鍘汇€?     *
     * <p>杩囨护妲斤紙骞界伒妲斤紝姣忛潰涓€鏍硷級锛氬乏閿敤鎵嬫寔/鍏夋爣鐗╁搧璁剧疆璇ラ潰鐨勮繃婊ょ被鍨嬶紱
     * 绌烘墜宸﹂敭娓呴櫎锛涗笉娑堣€楃帺瀹舵墜涓殑鐗╁搧銆傝繃婊や粎鎸?{@link Material} 绫诲瀷鍖归厤銆?/p>
     */
    private final VirtualInventory inputInventory = new VirtualInventory(7);
    /** 姣忎釜闈㈢殑骞界伒杩囨护鏉愯川锛宯ull = 鏃犺繃婊わ紙绱㈠紩涓?FACES 涓€涓€瀵瑰簲锛夈€?*/
    private final @Nullable ItemStack[] filterItems = new @Nullable ItemStack[6];
    /** 涓?filterMaterials 瀵瑰簲鐨?GUI 浜や簰鎸夐挳銆?*/
    private final FilterSlotItem[] filterSlotItems = new FilterSlotItem[6];
    /** 宸ヤ綔 tick 闂撮殧锛?鈥?00锛孏UI 鍙皟锛堥粯璁ゅ彇閰嶇疆鏂囦欢鍊硷級銆?*/
    private int tickIntervalOverride;

    private final StatusItem statusItem = new StatusItem();
    private final SteamGaugeItem steamGaugeItem = new SteamGaugeItem();
    private final TickIntervalItem tickIntervalItem = new TickIntervalItem();

    // 鈹€鈹€ 鐗╁搧鎻忚堪 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€

    public static class Item extends RebarItem {
        private final double steamBuffer = getSettings().getOrThrow("pressurized-buffer", ConfigAdapter.DOUBLE);
        private final double steamPerItem = getSettings().getOrThrow("steam-per-item", ConfigAdapter.DOUBLE);
        private final int itemsPerTick = getSettings().getOrThrow("items-per-tick", ConfigAdapter.INTEGER);

        public Item(@NotNull ItemStack stack) { super(stack); }

        @Override
        public @NotNull List<RebarArgument> getPlaceholders() {
            return List.of(
                    RebarArgument.of("steam-buffer", UnitFormat.MILLIBUCKETS.format(steamBuffer)),
                    RebarArgument.of("steam-per-item", UnitFormat.MILLIBUCKETS.format(steamPerItem)),
                    RebarArgument.of("items-per-tick", String.valueOf(itemsPerTick))
            );
        }
    }

    // 鈹€鈹€ 鏋勯€犲櫒 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€

    @SuppressWarnings("unused")
    public SteamSorter(@NotNull Block block, @NotNull BlockCreateContext context) {
        super(block, context);
        setFacing(context.getFacing());
        tickIntervalOverride = defaultTickInterval;
        setTickInterval(tickIntervalOverride);
        createFluidPoint(FluidPointType.INPUT, BlockFace.NORTH, context, false);
        createFluidBuffer(SteamworkFluids.PRESSURIZED_STEAM, steamBuffer, true, false);
        initFilterSlots();
    }

    @SuppressWarnings("unused")
    public SteamSorter(@NotNull Block block, @NotNull PersistentDataContainer pdc) {
        super(block, pdc);
        try { getFacing(); } catch (IllegalStateException e) { setFacing(BlockFace.SOUTH); }
        tickIntervalOverride = Math.max(MIN_TICK_INTERVAL, Math.min(MAX_TICK_INTERVAL,
                pdc.getOrDefault(TICK_INTERVAL_KEY, PersistentDataType.INTEGER, defaultTickInterval)));
        setTickInterval(tickIntervalOverride);
        for (int i = 0; i < 6; i++) {
            ItemStack saved = pdc.get(FILTER_KEYS[i], RebarSerializers.ITEM_STACK);
            if (saved != null && !saved.getType().isAir()) {
                filterItems[i] = saved.clone().asQuantity(1);
            } else {
                String matName = pdc.get(FILTER_KEYS[i], PersistentDataType.STRING);
                if (matName == null) continue;
                try { filterItems[i] = ItemStack.of(Material.valueOf(matName)); }
                catch (IllegalArgumentException ignored) {}
            }
        }
        initFilterSlots();
    }

    private void initFilterSlots() {
        for (int i = 0; i < 6; i++) filterSlotItems[i] = new FilterSlotItem(i);
    }

    @Override
    public void write(@NotNull PersistentDataContainer pdc) {
        pdc.set(TICK_INTERVAL_KEY, PersistentDataType.INTEGER, tickIntervalOverride);
        for (int i = 0; i < 6; i++) {
            if (filterItems[i] != null && !filterItems[i].getType().isAir()) {
                pdc.set(FILTER_KEYS[i], RebarSerializers.ITEM_STACK, filterItems[i].clone().asQuantity(1));
            } else {
                pdc.remove(FILTER_KEYS[i]);
            }
        }
    }

    @Override
    public void onBlockBreak(@NotNull List<@NotNull ItemStack> drops, @NotNull BlockBreakContext context) {
        FluidBufferRebarBlock.super.onBlockBreak(drops, context);
        VirtualInventoryRebarBlock.super.onBlockBreak(drops, context);
    }

    // 鈹€鈹€ tick 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€

    @Override
    public void tick() {
        PneumaticUtils.pullFromAdjacentHoppers(getBlock(), inputInventory);
        int pushed = 0;

        for (int n = 0; n < itemsPerTick; n++) {
            if (fluidAmount(SteamworkFluids.PRESSURIZED_STEAM) < steamPerItem) break;

            ItemStack[] inputs = inputInventory.getItems();
            boolean moved = false;

            for (int i = 0; i < inputs.length; i++) {
                ItemStack stack = inputs[i];
                if (stack == null || stack.getType().isAir()) continue;

                // Find a target that can accept this item.
                Block target = findTarget(stack);
                if (target == null) continue;

                // Push one item into the target.
                if (!PneumaticUtils.tryPushItem(target, stack)) {
                    PneumaticDuct.notifyPassage(getBlock(), target, 1, false);
                    continue;
                }
                PneumaticDuct.notifyPassage(getBlock(), target, 1, true);

                // Consume one item from the sorter input.
                MachineUpdateReason reason = new MachineUpdateReason();
                if (stack.getAmount() <= 1) {
                    inputInventory.setItem(reason, i, null);
                } else {
                    ItemStack reduced = stack.clone();
                    reduced.setAmount(stack.getAmount() - 1);
                    inputInventory.setItem(reason, i, reduced);
                }

                removeFluid(SteamworkFluids.PRESSURIZED_STEAM, steamPerItem);
                pushed++;
                moved = true;
                break;
            }

            if (!moved) break;
        }

        if (pushed > 0) spawnSortFx();
        setActive(pushed > 0);
        statusItem.notifyWindows();

        // 鍙湪钂告苯閲忓疄闄呭彉鍖栨椂鎵嶅埛鏂伴噺琛紝閬垮厤姣?tick 涓嶅繀瑕佸湴鏇存柊 GUI
        double currentSteam = fluidAmount(SteamworkFluids.PRESSURIZED_STEAM);
        if (currentSteam != lastSteamAmount) {
            lastSteamAmount = currentSteam;
            steamGaugeItem.notifyWindows();
        }
    }

    /**
     * 鏌ユ壘鎺ュ彈鎸囧畾鐗╁搧鐨勭洰鏍囨柟鍧椼€?     *
     * <p>鎼滅储椤哄簭锛堟瘡涓潰鐙珛锛夛細
     * <ol>
     *   <li>鐩存帴鐩搁偦鐨勫鍣?/ Rebar 鏈哄櫒 鈹€鈹€ 杩囨护鍖归厤鑰呬紭鍏堬紝鍚﹀垯鍔犲叆澶囬€夋睜</li>
     *   <li>璇ラ潰鏈夊绠℃椂锛孊FS 閬嶅巻鏁翠釜瀵肩缃戠粶锛屽姣忎釜绔偣鍚屾牱鍋氳繃婊ゅ尮閰?/li>
     * </ol>
     * 杩囨护妲界储寮曚笌 {@link #FACES} 涓€涓€瀵瑰簲锛?=鍖?1=鍗?2=涓?3=瑗?4=涓?5=涓嬨€?     * 鏌愪釜闈㈢殑杩囨护妲界暀绌猴紝鍒欒闈紙鍙婂叾瀵肩缃戠粶锛夋帴鍙椾换浣曠墿鍝侊紝杩涘叆澶囬€夋睜銆?/p>
     *
     * @param item 寰呮帹閫佺殑鐗╁搧
     * @return 鎵惧埌鐨勭洰鏍囨柟鍧楋紝鎵句笉鍒板垯 {@code null}
     */
    private @Nullable Block findTarget(@NotNull ItemStack item) {
        // 鍏堟寜杩囨护瑙勫垯鏀堕泦鎵€鏈夊€欓€夌洰鏍囷紙鏈夎繃婊?= 绮剧‘鍖归厤闈紝鏃犺繃婊?= 浠绘剰鏈夌┖闂寸殑闈級
        List<Block> filtered = new ArrayList<>();
        List<Block> unfiltered = new ArrayList<>();

        for (int fi = 0; fi < FACES.length; fi++) {
            Block neighbor = getBlock().getRelative(FACES[fi]);
            ItemStack filter = filterItems[fi];
            boolean filterSet = filter != null && !filter.getType().isAir();
            boolean typeMatch = filterSet && filterMatches(filter, item);

            // Direct pneumatic input endpoint.
            if (BlockStorage.get(neighbor) instanceof PneumaticInput) {
                if (typeMatch && PneumaticUtils.hasSpace(neighbor, item)) filtered.add(neighbor);
                else if (!filterSet && PneumaticUtils.hasSpace(neighbor, item)) unfiltered.add(neighbor);
                continue;
            }

            // 鐩磋繛鏅€氬鍣?鏈哄櫒
            if (!PneumaticDuct.isNetworkDuct(neighbor)) {
                if (!(BlockStorage.get(neighbor) instanceof PneumaticInput)
                        && !(BlockStorage.get(neighbor) instanceof PneumaticOutput)
                        && PneumaticUtils.isItemTarget(neighbor)) {
                    if (typeMatch && PneumaticUtils.hasSpace(neighbor, item)) filtered.add(neighbor);
                    else if (!filterSet && PneumaticUtils.hasSpace(neighbor, item)) unfiltered.add(neighbor);
                }
                continue;
            }

            // Duct network: BFS collects reachable pneumatic inputs.
            for (Block endpoint : PneumaticDuct.findReachableEndpoints(neighbor)) {
                if (!(BlockStorage.get(endpoint) instanceof PneumaticInput)) continue;
                if (typeMatch && PneumaticUtils.hasSpace(endpoint, item)) filtered.add(endpoint);
                else if (!filterSet && PneumaticUtils.hasSpace(endpoint, item)) unfiltered.add(endpoint);
            }
        }

        // Prefer filtered matches, otherwise use unfiltered candidates.
        List<Block> pool = !filtered.isEmpty() ? filtered : unfiltered;
        if (pool.isEmpty()) return null;

        int size = pool.size();
        int start = Math.floorMod(roundRobinCursor, size);
        for (int i = 0; i < size; i++) {
            int idx = (start + i) % size;
            Block candidate = pool.get(idx);
            if (PneumaticUtils.hasSpace(candidate, item)) {
                roundRobinCursor = Math.floorMod(idx + 1, size);
                return candidate;
            }
        }
        return null;
    }

    private boolean filterMatches(@NotNull ItemStack filter, @NotNull ItemStack item) {
        return ItemTypeWrapper.of(filter).equals(ItemTypeWrapper.of(item));
    }

    private void spawnSortFx() {
        var loc = getBlock().getLocation().add(0.5, 0.5, 0.5);
        getBlock().getWorld().spawnParticle(Particle.CRIT, loc, 4, 0.3, 0.1, 0.3, 0.04);
        getBlock().getWorld().spawnParticle(Particle.CLOUD, loc, 2, 0.2, 0.1, 0.2, 0.01);
        if (Math.random() < 0.15) {
            getBlock().getWorld().playSound(getBlock().getLocation(),
                    Sound.BLOCK_DISPENSER_DISPENSE, 0.2f, 1.5f);
        }
    }

    private void setActive(boolean active) {
        if (lastActive != active) {
            lastActive = active;
            refreshBlockTextureItem();
        }
    }

    // 鈹€鈹€ GUI 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€

    /**
     * 甯冨眬璇存槑锛?     * <pre>
     * # N # S # E # W #   鈫?N/S/E/W 鏂瑰悜鏍囩锛堜氦閿欐帓鍒楋級
     * # f # f # f # f #   鈫?瀵瑰簲杩囨护妲斤紙鍒楀彿涓庝笂鏂规爣绛剧浉鍚岋級
     * # # U # # # D # #   鈫?U/D 鏂瑰悜鏍囩锛堝眳涓級
     * # # f # # # f # #   鈫?U/D 杩囨护妲?     * # # # # # # # # #   鈫?鍒嗛殧琛?     * s i i i i i i i a   鈫?钂告苯閲忚〃 | 7鏍艰緭鍏ユЫ | 鐘舵€?     * </pre>
     * filterInventory 椤哄簭涓?FACES 涓€鑷达細0=鍖?1=鍗?2=涓?3=瑗?4=涓?5=涓?     */
    /**
     * 甯冨眬璇存槑锛?     * <pre>
     * # N # S # E # W #   鈫?N/S/E/W 鏂瑰悜鏍囩锛堜氦閿欐帓鍒楋級
     * # 0 # 1 # 2 # 3 #   鈫?鍖?鍗?涓?瑗?骞界伒杩囨护妲?     * # # U # # # D # #   鈫?U/D 鏂瑰悜鏍囩锛堝眳涓級
     * # # 4 # # # 5 # #   鈫?涓?涓?骞界伒杩囨护妲?     * # # # # # # # # #   鈫?鍒嗛殧琛?     * s i i i i i i i a   鈫?钂告苯閲忚〃 | 7鏍艰緭鍏ユЫ | 鐘舵€?     * </pre>
     * 杩囨护妲?0-5 涓?FACES锛堝寳/鍗?涓?瑗?涓?涓嬶級涓€涓€瀵瑰簲銆?     */
    @Override
    public @NotNull Gui createGui() {
        return Gui.builder()
                .setStructure(
                        "# N # S # E # W #",
                        "# 0 # 1 # 2 # 3 #",
                        "# # U # # # D # #",
                        "# # 4 # # # 5 # #",
                        "# # # # t # # # #",
                        "s i i i i i i i a"
                )
                .addIngredient('#', GuiItems.background())
                .addIngredient('i', inputInventory)
                .addIngredient('s', steamGaugeItem)
                .addIngredient('a', statusItem)
                .addIngredient('t', tickIntervalItem)
                // 骞界伒杩囨护妲斤紙0=鍖?1=鍗?2=涓?3=瑗?4=涓?5=涓嬶級
                .addIngredient('0', filterSlotItems[0])
                .addIngredient('1', filterSlotItems[1])
                .addIngredient('2', filterSlotItems[2])
                .addIngredient('3', filterSlotItems[3])
                .addIngredient('4', filterSlotItems[4])
                .addIngredient('5', filterSlotItems[5])
                // Direction labels.
                .addIngredient('N', dirLabel(Material.BLUE_STAINED_GLASS_PANE,
                        "steamwork.gui.steam_sorter.filter.north"))
                .addIngredient('S', dirLabel(Material.RED_STAINED_GLASS_PANE,
                        "steamwork.gui.steam_sorter.filter.south"))
                .addIngredient('E', dirLabel(Material.GREEN_STAINED_GLASS_PANE,
                        "steamwork.gui.steam_sorter.filter.east"))
                .addIngredient('W', dirLabel(Material.WHITE_STAINED_GLASS_PANE,
                        "steamwork.gui.steam_sorter.filter.west"))
                .addIngredient('U', dirLabel(Material.YELLOW_STAINED_GLASS_PANE,
                        "steamwork.gui.steam_sorter.filter.up"))
                .addIngredient('D', dirLabel(Material.ORANGE_STAINED_GLASS_PANE,
                        "steamwork.gui.steam_sorter.filter.down"))
                .build();
    }

    /**
     * 骞界伒杩囨护妲?鈥斺€?宸﹂敭鐢ㄥ厜鏍?鎵嬫寔鐗╁搧璁剧疆璇ラ潰鐨勮繃婊ょ被鍨嬶紱绌烘墜宸﹂敭娓呴櫎銆?     * 涓嶆秷鑰楃帺瀹舵墜涓殑浠讳綍鐗╁搧銆?     */
    private final class FilterSlotItem extends AbstractItem {
        private final int index;

        FilterSlotItem(int index) { this.index = index; }

        @Override
        public @NotNull ItemProvider getItemProvider(@NotNull Player viewer) {
            ItemStack filter = filterItems[index];
            if (filter == null || filter.getType().isAir()) {
                return ItemStackBuilder.of(Material.GRAY_STAINED_GLASS_PANE)
                        .name(noItalic(Component.translatable(
                                "steamwork.gui.steam_sorter.filter.empty")))
                        .lore(List.of(noItalic(Component.translatable(
                                "steamwork.gui.steam_sorter.filter.ghost_hint"))));
            }
            ItemStack display = filter.clone().asQuantity(1);
            return ItemStackBuilder.of(display)
                    .name(noItalic(Component.translatable(
                            "steamwork.gui.steam_sorter.filter.set",
                            RebarArgument.of("item", display.effectiveName()))))
                    .lore(List.of(noItalic(Component.translatable(
                            "steamwork.gui.steam_sorter.filter.ghost_hint_clear"))))
                    .amount(1);
        }

        @Override
        public void handleClick(@NotNull ClickType clickType, @NotNull Player player,
                                @NotNull Click click) {
            if (clickType.isRightClick()) {
                filterItems[index] = null;
                notifyWindows();
                return;
            }

            // Prefer the cursor stack, then the main hand.
            ItemStack cursor = player.getOpenInventory().getCursor();
            if (cursor != null && !cursor.getType().isAir()) {
                filterItems[index] = cursor.clone().asQuantity(1);
            } else {
                ItemStack hand = player.getInventory().getItemInMainHand();
                if (!hand.getType().isAir()) {
                    filterItems[index] = hand.clone().asQuantity(1);
                }
            }
            notifyWindows();
        }
    }

    private final class TickIntervalItem extends AbstractItem {
        @Override
        public @NotNull ItemProvider getItemProvider(@NotNull Player viewer) {
            List<Component> lore = new ArrayList<>();
            lore.add(noItalic(Component.translatable(
                    "steamwork.gui.steam_sorter.tick_interval",
                    RebarArgument.of("interval", String.valueOf(tickIntervalOverride)),
                    RebarArgument.of("max",       String.valueOf(MAX_TICK_INTERVAL))
            )));
            lore.add(noItalic(Component.empty()));
            lore.add(noItalic(Component.translatable("steamwork.gui.steam_sorter.tick_interval_increase")));
            lore.add(noItalic(Component.translatable("steamwork.gui.steam_sorter.tick_interval_decrease")));
            return ItemStackBuilder.of(Material.CLOCK)
                    .name(noItalic(Component.translatable("steamwork.gui.steam_sorter.tick_interval_title")))
                    .amount(Math.min(tickIntervalOverride, 64))
                    .lore(lore);
        }

        @Override
        public void handleClick(@NotNull ClickType clickType, @NotNull Player player,
                                @NotNull Click click) {
            int delta = 0;
            if (clickType == ClickType.LEFT)             delta =   1;
            else if (clickType == ClickType.SHIFT_LEFT)  delta =  10;
            else if (clickType == ClickType.RIGHT)       delta =  -1;
            else if (clickType == ClickType.SHIFT_RIGHT) delta = -10;
            if (delta == 0) return;
            int next = Math.max(MIN_TICK_INTERVAL, Math.min(MAX_TICK_INTERVAL, tickIntervalOverride + delta));
            if (next == tickIntervalOverride) return;
            tickIntervalOverride = next;
            setTickInterval(tickIntervalOverride);
            notifyWindows();
        }
    }

    /**
     * 鍒涘缓鏂瑰悜鏍囩闈欐€佺墿鍝侊紙涓嶅彲鐐瑰嚮锛屼粎浣滆瑙夊紩瀵硷級銆?     * 浣跨敤鍚勬柟鍚戝搴旂殑棰滆壊鐜荤拑鏉匡紝lore 鎻愮ず璇ユЫ浣嶇殑杩囨护瑙勫垯銆?     */
    private static AbstractItem dirLabel(Material mat, String translationKey) {
        return new AbstractItem() {
            @Override
            public @NotNull ItemProvider getItemProvider(@NotNull Player viewer) {
                return ItemStackBuilder.of(mat)
                        .name(noItalic(Component.translatable(translationKey)))
                        .lore(List.of(noItalic(Component.translatable(
                                "steamwork.gui.steam_sorter.filter.hint"))));
            }

            @Override
            public void handleClick(@NotNull ClickType clickType,
                                    @NotNull Player player,
                                    @NotNull Click click) {}
        };
    }

    @Override
    public @NotNull Component getGuiTitle() {
        return noItalic(Component.translatable("steamwork.gui.steam_sorter.title"));
    }

    @Override
    public @NotNull Map<String, kotlin.Pair<String, Integer>> getBlockTextureProperties() {
        var props = super.getBlockTextureProperties();
        props.put("active", new kotlin.Pair<>(Boolean.toString(lastActive), 2));
        return props;
    }

    @Override
    public @Nullable WailaDisplay getWaila(@NotNull Player player) {
        return WailaDisplay.of(getDefaultWailaTranslationKey().arguments(
                RebarArgument.of("steam-bar", io.github.steamwork.util.SteamworkUtils.createFluidAmountBar(
                        fluidAmount(SteamworkFluids.PRESSURIZED_STEAM),
                        fluidCapacity(SteamworkFluids.PRESSURIZED_STEAM),
                        12, TextColor.fromHexString("#d8edf0")
                )),
                RebarArgument.of("state", Component.translatable(
                        "steamwork.state." + (lastActive ? "active" : "idle")))
        ));
    }

    @Override
    public @NotNull Map<@NotNull String, @NotNull VirtualInventory> getVirtualInventories() {
        return Map.of("input", inputInventory);
    }

    // 鈹€鈹€ GUI 鐗╁搧 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€

    private final class StatusItem extends AbstractItem {
        @Override
        public @NotNull ItemProvider getItemProvider(@NotNull Player viewer) {
            return ItemStackBuilder.of(lastActive ? Material.GREEN_STAINED_GLASS_PANE : Material.GRAY_STAINED_GLASS_PANE)
                    .name(noItalic(Component.translatable("steamwork.gui.steam_sorter.status."
                            + (lastActive ? "active" : "idle"))))
                    .lore(List.of(noItalic(Component.translatable(
                            "steamwork.gui.steam_sorter.throughput",
                            RebarArgument.of("per-item", UnitFormat.MILLIBUCKETS.format(steamPerItem).decimalPlaces(1)),
                            RebarArgument.of("max-items", String.valueOf(itemsPerTick))
                    ))));
        }

        @Override
        public void handleClick(@NotNull ClickType clickType, @NotNull Player player, @NotNull Click click) {}
    }

    private final class SteamGaugeItem extends AbstractItem {
        @Override
        public @NotNull ItemProvider getItemProvider(@NotNull Player viewer) {
            double amount = fluidAmount(SteamworkFluids.PRESSURIZED_STEAM);
            double capacity = fluidCapacity(SteamworkFluids.PRESSURIZED_STEAM);
            return ItemStackBuilder.of(Material.LIGHT_BLUE_STAINED_GLASS)
                    .name(noItalic(Component.translatable("steamwork.gui.steam_sorter.steam_gauge")))
                    .lore(List.of(noItalic(Component.translatable(
                            "steamwork.gui.steam_sorter.fluid_amount",
                            RebarArgument.of("amount", UnitFormat.MILLIBUCKETS.format(amount).decimalPlaces(0)),
                            RebarArgument.of("capacity", UnitFormat.MILLIBUCKETS.format(capacity).decimalPlaces(0))
                    ))));
        }

        @Override
        public void handleClick(@NotNull ClickType clickType, @NotNull Player player, @NotNull Click click) {}
    }

    private static @NotNull Component noItalic(@NotNull Component c) {
        return c.decoration(TextDecoration.ITALIC, false);
    }
}
