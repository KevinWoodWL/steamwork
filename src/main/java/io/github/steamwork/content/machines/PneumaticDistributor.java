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
import io.github.pylonmc.rebar.fluid.FluidPointType;
import io.github.pylonmc.rebar.i18n.RebarArgument;
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
import xyz.xenondevs.invui.Click;
import xyz.xenondevs.invui.gui.Gui;
import xyz.xenondevs.invui.inventory.VirtualInventory;
import xyz.xenondevs.invui.item.AbstractItem;
import xyz.xenondevs.invui.item.ItemProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static io.github.steamwork.util.SteamworkUtils.steamworkKey;

/**
 * 姘斿姩鍒嗗彂鍣?鈥斺€?娑堣€楀姞鍘嬭捀姹斤紝灏嗚緭鍏ユЫ鐗╁搧鎸夋柟鍚戝垎鍙戝埌鐩搁偦瀹瑰櫒鎴栨皵鍔ㄥ绠＄綉缁滅殑绔偣銆? *
 * <p>鏀寔 1鈥? 涓緭鍑烘柟鍚戯紙鍖?鍗?涓?瑗?涓?涓嬶級锛屾瘡涓柟鍚戝彲鍦?GUI 涓嫭绔嬪惎鐢ㄦ垨绂佺敤銆? * 鍚敤鐨勬柟鍚戣嫢鐩磋繛鍘熺増瀹瑰櫒鎴?Rebar 鏈哄櫒鐨?input 妲斤紝鍒欑洿鎺ユ帹閫侊紱
 * 鑻ユ柟鍚戣繛鎺ユ皵鍔ㄥ绠★紝鍒?BFS 鎼滅储鏁翠釜瀵肩缃戠粶锛屾帹閫佸埌绗竴涓彲鎺ユ敹鐨勭鐐广€? * 澶氫釜鍚敤鏂瑰悜涔嬮棿閲囩敤杞锛坮ound-robin锛夊潎琛″垎鍙戙€?/p>
 *
 * <p>GUI 甯冨眬锛?琛岋級锛? * <pre>
 * # N # S # E # W #   鈫?N/S/E/W 鏂瑰悜鏍囩锛堜氦閿欐帓鍒楋級
 * # 1 # 2 # 3 # 4 #   鈫?N/S/E/W 寮€鍏虫寜閽紙鍒楀彿涓庝笂鏂规爣绛剧浉鍚岋級
 * # # U # # # D # #   鈫?U/D 鏂瑰悜鏍囩锛堝眳涓級
 * # # 5 # # # 6 # #   鈫?U/D 寮€鍏虫寜閽? * # # # # # # # # #   鈫?鍒嗛殧琛? * g i i i i i i i a   鈫?钂告苯閲忚〃 | 7鏍艰緭鍏ユЫ | 鐘舵€? * </pre>
 * </p>
 */
public class PneumaticDistributor extends RebarBlock implements
        DirectionalRebarBlock,
        FluidBufferRebarBlock,
        GuiRebarBlock,
        TickingRebarBlock,
        VirtualInventoryRebarBlock {

    /** 6 涓緭鍑烘柟鍚戯紝绱㈠紩涓?GUI 鍒囨崲鎸夐挳瀵瑰簲锛?=鍖?1=鍗?2=涓?3=瑗?4=涓?5=涓?*/
    private static final BlockFace[] FACES = {
            BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST,
            BlockFace.WEST,  BlockFace.UP,    BlockFace.DOWN
    };

    /** enabledFacesMask 鐨勯粯璁ゅ€硷細6 浣嶅叏 1锛屽嵆鎵€鏈夋柟鍚戝垵濮嬪潎鍚敤 */
    private static final int DEFAULT_MASK = 0b111111;

    public static final int MIN_TICK_INTERVAL = 1;
    public static final int MAX_TICK_INTERVAL = 200;

    /** PDC 閿細6 浣?int 鎺╃爜锛宐it i 涓?1 琛ㄧず FACES[i] 鏂瑰悜宸插惎鐢?*/
    private static final NamespacedKey ENABLED_MASK_KEY  = steamworkKey("pdist_enabled_faces");
    private static final NamespacedKey TICK_INTERVAL_KEY = steamworkKey("pdist_tick_interval");

    private final int defaultTickInterval = getSettings().getOrThrow("tick-interval",      ConfigAdapter.INTEGER);
    private final double pressurizedBuffer = getSettings().getOrThrow("pressurized-buffer", ConfigAdapter.DOUBLE);
    private final double steamPerItem   = getSettings().getOrThrow("steam-per-item",    ConfigAdapter.DOUBLE);
    private final int itemsPerTick      = getSettings().getOrThrow("items-per-tick",    ConfigAdapter.INTEGER);

    /** 鍚敤鏂瑰悜鎺╃爜锛屽瓨鍏?PDC 浠ユ寔涔呭寲 */
    private int enabledFacesMask = DEFAULT_MASK;
    /** 宸ヤ綔 tick 闂撮殧锛?鈥?00锛孏UI 鍙皟锛堥粯璁ゅ彇閰嶇疆鏂囦欢鍊硷級銆?*/
    private int tickIntervalOverride;

    /** 涓婃鍒嗗彂鍒扮殑鐩爣鍒楄〃绱㈠紩锛坮ound-robin锛?*/
    private int roundRobinIndex = 0;
    /** 姣忎釜鍚敤闈㈠唴閮ㄧ殑绔偣杞娓告爣锛宬ey = face ordinal銆?*/
    private final int[] endpointCursor = new int[6];

    private boolean lastActive = false;
    /** 鐢ㄤ簬閬垮厤姣?tick 鏃犺皳鍒锋柊钂告苯閲忚〃 */
    private double lastSteamAmount = -1;

    private final VirtualInventory inputInventory = new VirtualInventory(7);
    private final StatusItem statusItem  = new StatusItem();
    private final GaugeItem  gaugeItem   = new GaugeItem();
    private final TickIntervalItem tickIntervalItem = new TickIntervalItem();
    /** 涓?FACES 涓€涓€瀵瑰簲鐨?6 涓柟鍚戝紑鍏虫寜閽?*/
    private final ToggleItem[] toggleItems = new ToggleItem[FACES.length];

    // 鈹€鈹€ 鐗╁搧鎻忚堪 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€

    public static class Item extends RebarItem {
        private final double pressurizedBuffer = getSettings().getOrThrow("pressurized-buffer", ConfigAdapter.DOUBLE);
        private final double steamPerItem      = getSettings().getOrThrow("steam-per-item",     ConfigAdapter.DOUBLE);
        private final int itemsPerTick         = getSettings().getOrThrow("items-per-tick",     ConfigAdapter.INTEGER);

        public Item(@NotNull ItemStack stack) { super(stack); }

        @Override
        public @NotNull List<RebarArgument> getPlaceholders() {
            return List.of(
                    RebarArgument.of("pressurized-buffer", UnitFormat.MILLIBUCKETS.format(pressurizedBuffer)),
                    RebarArgument.of("steam-per-item",     UnitFormat.MILLIBUCKETS.format(steamPerItem)),
                    RebarArgument.of("items-per-tick",     String.valueOf(itemsPerTick))
            );
        }
    }

    // 鈹€鈹€ 鏋勯€犲櫒 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€

    @SuppressWarnings("unused")
    public PneumaticDistributor(@NotNull Block block, @NotNull BlockCreateContext context) {
        super(block, context);
        setFacing(context.getFacing());
        tickIntervalOverride = defaultTickInterval;
        setTickInterval(tickIntervalOverride);
        createFluidPoint(FluidPointType.INPUT, BlockFace.NORTH, context, false);
        createFluidBuffer(SteamworkFluids.PRESSURIZED_STEAM, pressurizedBuffer, true, false);
        initToggleItems();
    }

    @SuppressWarnings("unused")
    public PneumaticDistributor(@NotNull Block block, @NotNull PersistentDataContainer pdc) {
        super(block, pdc);
        try { getFacing(); } catch (IllegalStateException e) { setFacing(BlockFace.SOUTH); }
        Integer mask = pdc.get(ENABLED_MASK_KEY, PersistentDataType.INTEGER);
        enabledFacesMask = (mask != null) ? mask : DEFAULT_MASK;
        tickIntervalOverride = Math.max(MIN_TICK_INTERVAL, Math.min(MAX_TICK_INTERVAL,
                pdc.getOrDefault(TICK_INTERVAL_KEY, PersistentDataType.INTEGER, defaultTickInterval)));
        setTickInterval(tickIntervalOverride);
        initToggleItems();
    }

    private void initToggleItems() {
        for (int i = 0; i < FACES.length; i++) {
            toggleItems[i] = new ToggleItem(i);
        }
    }

    @Override
    public void write(@NotNull PersistentDataContainer pdc) {
        pdc.set(ENABLED_MASK_KEY,  PersistentDataType.INTEGER, enabledFacesMask);
        pdc.set(TICK_INTERVAL_KEY, PersistentDataType.INTEGER, tickIntervalOverride);
    }

    @Override
    public void onBlockBreak(@NotNull List<@NotNull ItemStack> drops, @NotNull BlockBreakContext context) {
        FluidBufferRebarBlock.super.onBlockBreak(drops, context);
        VirtualInventoryRebarBlock.super.onBlockBreak(drops, context);
    }

    // 鈹€鈹€ 鏂瑰悜鎺╃爜宸ュ叿 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€

    private boolean isEnabled(int faceIndex) {
        return (enabledFacesMask & (1 << faceIndex)) != 0;
    }

    private void toggleDirection(int faceIndex) {
        enabledFacesMask ^= (1 << faceIndex);
    }

    private int enabledCount() {
        return Integer.bitCount(enabledFacesMask & 0x3F);
    }

    // 鈹€鈹€ tick 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€

    @Override
    public void tick() {
        PneumaticUtils.pullFromAdjacentHoppers(getBlock(), inputInventory);
        ItemStack probe = pickFirstItem();
        if (probe == null) {
            setActive(false);
            updateGauge();
            return;
        }

        // Stable list of enabled faces in FACES order.
        List<Integer> enabledFaceIndices = new ArrayList<>();
        for (int fi = 0; fi < FACES.length; fi++) {
            if (isEnabled(fi)) enabledFaceIndices.add(fi);
        }
        if (enabledFaceIndices.isEmpty()) {
            setActive(false);
            updateGauge();
            return;
        }

        int pushed = 0;
        for (int n = 0; n < itemsPerTick; n++) {
            if (fluidAmount(SteamworkFluids.PRESSURIZED_STEAM) < steamPerItem) break;

            ItemStack current = pickFirstItem();
            if (current == null) break;

            if (roundRobinIndex >= enabledFaceIndices.size()) roundRobinIndex = 0;

            boolean sent = false;
            // 鎹曡幏鏈疆璧峰娓告爣锛屽唴灞傚惊鐜笉淇敼 roundRobinIndex锛屽彧鍦ㄦ垚鍔熸椂鎺ㄨ繘
            int startCursor = roundRobinIndex;
            for (int attempt = 0; attempt < enabledFaceIndices.size(); attempt++) {
                int slot = (startCursor + attempt) % enabledFaceIndices.size();
                int faceIndex = enabledFaceIndices.get(slot);

                Block target = findTargetInDirection(FACES[faceIndex], current);
                if (target == null) continue;
                if (!PneumaticUtils.tryPushItem(target, current)) {
                    PneumaticDuct.notifyPassage(getBlock(), target, 1, false);
                    continue;
                }
                PneumaticDuct.notifyPassage(getBlock(), target, 1, true);

                consumeItem(current);
                removeFluid(SteamworkFluids.PRESSURIZED_STEAM, steamPerItem);
                roundRobinIndex = (slot + 1) % enabledFaceIndices.size();
                pushed++;
                sent = true;
                break;
            }

            if (!sent) break;
        }

        if (pushed > 0) spawnFx();
        setActive(pushed > 0);
        statusItem.notifyWindows();
        updateGauge();
    }

    /**
     * 鍦ㄦ寚瀹氭柟鍚戝鎵鹃涓 {@code item} 鏈夌┖闂寸殑鐩爣鏂瑰潡銆?     * <ul>
     *   <li>鐩磋繛瀹瑰櫒/Rebar 鏈哄櫒 鈫?鐩存帴杩斿洖</li>
     *   <li>杩炴帴姘斿姩瀵肩 鈫?BFS 鎼滅储绔偣锛岃繑鍥炵涓€涓湁绌洪棿鐨?/li>
     * </ul>
     */
    private @Nullable Block findTargetInDirection(@NotNull BlockFace face, @NotNull ItemStack item) {
        Block neighbor = getBlock().getRelative(face);
        int faceOrdinal = face.ordinal();

        // Direct pneumatic input endpoint.
        if (BlockStorage.get(neighbor) instanceof PneumaticInput
                && PneumaticUtils.hasSpace(neighbor, item)) {
            return neighbor;
        }

        // 鐩磋繛鏅€氬鍣?鏈哄櫒锛堥潪瀵肩銆侀潪杈撳嚭绔級
        if (!PneumaticDuct.isNetworkDuct(neighbor)
                && !(BlockStorage.get(neighbor) instanceof PneumaticInput)
                && !(BlockStorage.get(neighbor) instanceof PneumaticOutput)
                && PneumaticUtils.isItemTarget(neighbor)
                && PneumaticUtils.hasSpace(neighbor, item)) {
            return neighbor;
        }

        // Duct network: BFS collects reachable pneumatic inputs.
        if (PneumaticDuct.isNetworkDuct(neighbor)) {
            List<Block> endpoints = new ArrayList<>();
            for (Block ep : PneumaticDuct.findReachableEndpoints(neighbor)) {
                if (BlockStorage.get(ep) instanceof PneumaticInput
                        && PneumaticUtils.hasSpace(ep, item)) {
                    endpoints.add(ep);
                }
            }
            if (endpoints.isEmpty()) return null;
            int size = endpoints.size();
            int start = Math.floorMod(endpointCursor[faceOrdinal], size);
            for (int i = 0; i < size; i++) {
                int idx = (start + i) % size;
                Block candidate = endpoints.get(idx);
                if (PneumaticUtils.hasSpace(candidate, item)) {
                    endpointCursor[faceOrdinal] = Math.floorMod(idx + 1, size);
                    return candidate;
                }
            }
        }

        return null;
    }

    private @Nullable ItemStack pickFirstItem() {
        for (ItemStack s : inputInventory.getItems()) {
            if (s != null && !s.getType().isAir()) return s.clone().asQuantity(1);
        }
        return null;
    }

    private void consumeItem(@NotNull ItemStack one) {
        MachineUpdateReason reason = new MachineUpdateReason();
        ItemStack[] items = inputInventory.getItems();
        for (int i = 0; i < items.length; i++) {
            ItemStack s = items[i];
            if (s == null || s.getType().isAir() || !s.isSimilar(one)) continue;
            if (s.getAmount() <= 1) {
                inputInventory.setItem(reason, i, null);
            } else {
                ItemStack reduced = s.clone();
                reduced.setAmount(s.getAmount() - 1);
                inputInventory.setItem(reason, i, reduced);
            }
            return;
        }
    }

    private void spawnFx() {
        var loc = getBlock().getLocation().add(0.5, 0.5, 0.5);
        getBlock().getWorld().spawnParticle(Particle.CLOUD, loc, 3, 0.25, 0.1, 0.25, 0.04);
        if (Math.random() < 0.2) {
            getBlock().getWorld().playSound(getBlock().getLocation(),
                    Sound.BLOCK_PISTON_EXTEND, 0.15f, 0.8f);
        }
    }

    private void setActive(boolean active) {
        if (lastActive != active) {
            lastActive = active;
            refreshBlockTextureItem();
        }
    }

    private void updateGauge() {
        double current = fluidAmount(SteamworkFluids.PRESSURIZED_STEAM);
        if (current != lastSteamAmount) {
            lastSteamAmount = current;
            gaugeItem.notifyWindows();
        }
    }

    // 鈹€鈹€ GUI 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€

    @Override
    public @NotNull Gui createGui() {
        return Gui.builder()
                .setStructure(
                        "# N # S # E # W #",
                        "# 1 # 2 # 3 # 4 #",
                        "# # U # # # D # #",
                        "# # 5 # # # 6 # #",
                        "# # # # t # # # #",
                        "g i i i i i i i a"
                )
                .addIngredient('#', GuiItems.background())
                .addIngredient('i', inputInventory)
                .addIngredient('g', gaugeItem)
                .addIngredient('a', statusItem)
                .addIngredient('t', tickIntervalItem)
                // Direction labels.
                .addIngredient('N', dirLabel(Material.BLUE_STAINED_GLASS_PANE,
                        "steamwork.gui.pneumatic_distributor.direction.north"))
                .addIngredient('S', dirLabel(Material.RED_STAINED_GLASS_PANE,
                        "steamwork.gui.pneumatic_distributor.direction.south"))
                .addIngredient('E', dirLabel(Material.GREEN_STAINED_GLASS_PANE,
                        "steamwork.gui.pneumatic_distributor.direction.east"))
                .addIngredient('W', dirLabel(Material.WHITE_STAINED_GLASS_PANE,
                        "steamwork.gui.pneumatic_distributor.direction.west"))
                .addIngredient('U', dirLabel(Material.YELLOW_STAINED_GLASS_PANE,
                        "steamwork.gui.pneumatic_distributor.direction.up"))
                .addIngredient('D', dirLabel(Material.ORANGE_STAINED_GLASS_PANE,
                        "steamwork.gui.pneumatic_distributor.direction.down"))
                // Direction toggle buttons.
                .addIngredient('1', toggleItems[0])
                .addIngredient('2', toggleItems[1])
                .addIngredient('3', toggleItems[2])
                .addIngredient('4', toggleItems[3])
                .addIngredient('5', toggleItems[4])
                .addIngredient('6', toggleItems[5])
                .build();
    }

    @Override
    public @NotNull Component getGuiTitle() {
        return noItalic(Component.translatable("steamwork.gui.pneumatic_distributor.title"));
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
                RebarArgument.of("pressurized-bar", io.github.steamwork.util.SteamworkUtils.createFluidAmountBar(
                        fluidAmount(SteamworkFluids.PRESSURIZED_STEAM),
                        fluidCapacity(SteamworkFluids.PRESSURIZED_STEAM),
                        12, TextColor.fromHexString("#00cfff")
                )),
                RebarArgument.of("enabled-directions", String.valueOf(enabledCount())),
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
            return ItemStackBuilder.of(
                            lastActive ? Material.GREEN_STAINED_GLASS_PANE
                                       : Material.GRAY_STAINED_GLASS_PANE)
                    .name(noItalic(Component.translatable(
                            "steamwork.gui.pneumatic_distributor.status."
                                    + (lastActive ? "active" : "idle"))))
                    .lore(List.of(noItalic(Component.translatable(
                            "steamwork.gui.pneumatic_distributor.throughput",
                            RebarArgument.of("per-item",   UnitFormat.MILLIBUCKETS.format(steamPerItem).decimalPlaces(1)),
                            RebarArgument.of("max-items",  String.valueOf(itemsPerTick))
                    ))));
        }

        @Override
        public void handleClick(@NotNull ClickType clickType, @NotNull Player player,
                                @NotNull Click click) {}
    }

    private final class GaugeItem extends AbstractItem {
        @Override
        public @NotNull ItemProvider getItemProvider(@NotNull Player viewer) {
            double amount   = fluidAmount(SteamworkFluids.PRESSURIZED_STEAM);
            double capacity = fluidCapacity(SteamworkFluids.PRESSURIZED_STEAM);
            return ItemStackBuilder.of(Material.CYAN_STAINED_GLASS)
                    .name(noItalic(Component.translatable(
                            "steamwork.gui.pneumatic_distributor.pressurized_gauge")))
                    .lore(List.of(noItalic(Component.translatable(
                            "steamwork.gui.pneumatic_distributor.fluid_amount",
                            RebarArgument.of("amount",   UnitFormat.MILLIBUCKETS.format(amount).decimalPlaces(0)),
                            RebarArgument.of("capacity", UnitFormat.MILLIBUCKETS.format(capacity).decimalPlaces(0))
                    ))));
        }

        @Override
        public void handleClick(@NotNull ClickType clickType, @NotNull Player player,
                                @NotNull Click click) {}
    }

    /**
     * 鏂瑰悜鍚敤/绂佺敤鍒囨崲鎸夐挳銆?     * 缁胯壊鐜荤拑鏉?= 璇ユ柟鍚戝凡鍚敤锛堜細鍚戞闈㈠垎鍙戯級锛?     * 绾㈣壊鐜荤拑鏉?= 璇ユ柟鍚戝凡绂佺敤锛堣烦杩囨闈級銆?     * 宸﹂敭鐐瑰嚮鍒囨崲鐘舵€併€?     */
    private final class ToggleItem extends AbstractItem {
        private final int faceIndex;

        ToggleItem(int faceIndex) { this.faceIndex = faceIndex; }

        @Override
        public @NotNull ItemProvider getItemProvider(@NotNull Player viewer) {
            boolean enabled = isEnabled(faceIndex);
            return ItemStackBuilder.of(enabled
                            ? Material.LIME_STAINED_GLASS_PANE
                            : Material.RED_STAINED_GLASS_PANE)
                    .name(noItalic(Component.translatable(
                            "steamwork.gui.pneumatic_distributor.toggle."
                                    + (enabled ? "enabled" : "disabled"))))
                    .lore(List.of(noItalic(Component.translatable(
                            "steamwork.gui.pneumatic_distributor.toggle.hint"))));
        }

        @Override
        public void handleClick(@NotNull ClickType clickType, @NotNull Player player,
                                @NotNull Click click) {
            toggleDirection(faceIndex);
            // Refresh all toggles and the status item immediately.
            for (ToggleItem t : toggleItems) {
                if (t != null) t.notifyWindows();
            }
            statusItem.notifyWindows();
        }
    }

    private final class TickIntervalItem extends AbstractItem {
        @Override
        public @NotNull ItemProvider getItemProvider(@NotNull Player viewer) {
            List<Component> lore = new ArrayList<>();
            lore.add(noItalic(Component.translatable(
                    "steamwork.gui.pneumatic_distributor.tick_interval",
                    RebarArgument.of("interval", String.valueOf(tickIntervalOverride)),
                    RebarArgument.of("max",       String.valueOf(MAX_TICK_INTERVAL))
            )));
            lore.add(noItalic(Component.empty()));
            lore.add(noItalic(Component.translatable("steamwork.gui.pneumatic_distributor.tick_interval_increase")));
            lore.add(noItalic(Component.translatable("steamwork.gui.pneumatic_distributor.tick_interval_decrease")));
            return ItemStackBuilder.of(Material.CLOCK)
                    .name(noItalic(Component.translatable("steamwork.gui.pneumatic_distributor.tick_interval_title")))
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

    /** 鍒涘缓鏂瑰悜鏍囩闈欐€佺墿鍝侊紙涓嶅彲浜や簰锛屼粎鎻愪緵瑙嗚寮曞锛夈€?*/
    private static AbstractItem dirLabel(@NotNull Material mat, @NotNull String translationKey) {
        return new AbstractItem() {
            @Override
            public @NotNull ItemProvider getItemProvider(@NotNull Player viewer) {
                return ItemStackBuilder.of(mat)
                        .name(noItalic(Component.translatable(translationKey)));
            }

            @Override
            public void handleClick(@NotNull ClickType clickType, @NotNull Player player,
                                    @NotNull Click click) {}
        };
    }

    private static @NotNull Component noItalic(@NotNull Component c) {
        return c.decoration(TextDecoration.ITALIC, false);
    }
}
