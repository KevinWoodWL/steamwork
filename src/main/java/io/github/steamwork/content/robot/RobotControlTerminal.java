package io.github.steamwork.content.robot;

import io.github.pylonmc.rebar.block.RebarBlock;
import io.github.pylonmc.rebar.block.context.BlockBreakContext;
import io.github.pylonmc.rebar.block.context.BlockCreateContext;
import io.github.pylonmc.rebar.block.interfaces.GuiRebarBlock;
import io.github.pylonmc.rebar.block.interfaces.VirtualInventoryRebarBlock;
import io.github.pylonmc.rebar.config.adapter.ConfigAdapter;
import io.github.pylonmc.rebar.item.RebarItem;
import io.github.pylonmc.rebar.item.builder.ItemStackBuilder;
import io.github.pylonmc.rebar.util.gui.GuiItems;
import io.github.pylonmc.rebar.waila.WailaDisplay;
import io.github.steamwork.content.machines.SteamChargingChamber;
import io.github.steamwork.util.PneumaticEndpointSupport;
import io.github.steamwork.util.SteamworkBlockPrompt;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.entity.Entity;
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

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static io.github.steamwork.util.SteamworkUtils.steamworkKey;

/**
 * 机器人控制终端 —— 机器人工区的大脑（见 {@code docs/design/robot-work-zone.md}）。
 *
 * <p>管理一个矩形工区：用区域设定器划<b>区域</b>（两角，忽略 Y，上限 {@code region-max}×{@code region-max}），
 * 在区域内设<b>出货点</b>（容器）与<b>充汽点</b>（蒸汽充汽舱），并通过 GUI 槽位部署四种机器人。
 * 机器人按 {@code terminalId} 归属本终端、读其区域/出货/充汽配置。</p>
 *
 * <p><b>Phase A</b>：终端方块 + 区域/出货/充汽配置 + 持久化 + 注册表 + GUI。机器人部署（槽位）见 Phase B。</p>
 */
public class RobotControlTerminal extends RebarBlock implements GuiRebarBlock, VirtualInventoryRebarBlock,
        io.github.steamwork.content.machines.upgrade.UpgradeableMachine {

    // ===== 注册表：terminalId → 终端实例，供机器人 O(1) 找回 =====
    private static final Map<UUID, RobotControlTerminal> ACTIVE_TERMINALS = new ConcurrentHashMap<>();

    public static @Nullable RobotControlTerminal forTerminal(@NotNull UUID id) {
        RobotControlTerminal t = ACTIVE_TERMINALS.get(id);
        if (t == null) return null;
        Block b = t.getBlock();
        if (!PneumaticEndpointSupport.isChunkLoaded(b)) return null;
        return t;
    }

    // ===== PDC 键 =====
    private static final NamespacedKey K_ID  = steamworkKey("term_id");
    private static final NamespacedKey K_AX  = steamworkKey("term_ax");
    private static final NamespacedKey K_AZ  = steamworkKey("term_az");
    private static final NamespacedKey K_BX  = steamworkKey("term_bx");
    private static final NamespacedKey K_BZ  = steamworkKey("term_bz");
    private static final NamespacedKey K_DLV = steamworkKey("term_delivery"); // "x,y,z" 或缺省
    private static final NamespacedKey K_CHG = steamworkKey("term_charge");
    private static final NamespacedKey K_ROBOTS = steamworkKey("term_robots"); // 已部署机器人实体 UUID 列表

    private static final char[] ROBOT_SLOT_CHARS = {'1', '2', '3', '4', '5'};

    private final int regionMax = Math.max(4, getSettings().get("region-max", ConfigAdapter.INTEGER, 32));
    private final int baseMaxRobots = Math.max(1, getSettings().get("max-robots", ConfigAdapter.INTEGER, 3));
    private final int upgradeMaxRobots = Math.max(baseMaxRobots, getSettings().get("max-robots-upgraded", ConfigAdapter.INTEGER, 5));
    private final int upgradeSlots = Math.max(0, getSettings().get("upgrade-slots", ConfigAdapter.INTEGER, 2));

    // ===== 状态 =====
    private @NotNull UUID terminalId;
    private @Nullable Integer ax, az, bx, bz;       // 区域两角的 x/z（null = 未设区域）
    private @Nullable Location deliveryPoint;        // 出货点（容器）
    private @Nullable Location chargePoint;          // 充汽点（充汽舱）
    private final VirtualInventory robotSlots;
    /** 每个槽位对应的已部署机器人实体 UUID；null = 该槽未部署。 */
    private final UUID[] slotRobots;
    private boolean suppressSlotDeploy = false;      // 认领时抑制 PostUpdateHandler 重复部署
    private final @Nullable VirtualInventory upgradeInventory;
    private final RobotSlotItem[] robotSlotItems;
    private @Nullable StatusItem statusItem;

    @SuppressWarnings("unused")
    public RobotControlTerminal(@NotNull Block block, @NotNull BlockCreateContext context) {
        super(block, context);
        this.terminalId = UUID.randomUUID();
        this.robotSlots = new VirtualInventory(upgradeMaxRobots);
        this.slotRobots = new UUID[upgradeMaxRobots];
        this.upgradeInventory = upgradeSlots > 0 ? new VirtualInventory(upgradeSlots) : null;
        this.robotSlotItems = new RobotSlotItem[upgradeMaxRobots];
    }

    @SuppressWarnings("unused")
    public RobotControlTerminal(@NotNull Block block, @NotNull PersistentDataContainer pdc) {
        super(block, pdc);
        String id = pdc.get(K_ID, PersistentDataType.STRING);
        this.terminalId = (id != null) ? UUID.fromString(id) : UUID.randomUUID();
        this.ax = pdc.get(K_AX, PersistentDataType.INTEGER);
        this.az = pdc.get(K_AZ, PersistentDataType.INTEGER);
        this.bx = pdc.get(K_BX, PersistentDataType.INTEGER);
        this.bz = pdc.get(K_BZ, PersistentDataType.INTEGER);
        this.deliveryPoint = readPoint(pdc.get(K_DLV, PersistentDataType.STRING));
        this.chargePoint   = readPoint(pdc.get(K_CHG, PersistentDataType.STRING));
        this.robotSlots = new VirtualInventory(upgradeMaxRobots);
        this.slotRobots = new UUID[upgradeMaxRobots];
        this.upgradeInventory = upgradeSlots > 0 ? new VirtualInventory(upgradeSlots) : null;
        this.robotSlotItems = new RobotSlotItem[upgradeMaxRobots];
        ACTIVE_TERMINALS.put(terminalId, this);
        String robotsStr = pdc.get(K_ROBOTS, PersistentDataType.STRING);
        if (robotsStr != null && !robotsStr.isEmpty()) {
            for (String entry : robotsStr.split(";")) {
                int colon = entry.indexOf(':');
                if (colon >= 0) {
                    // 新格式：slot:uuid
                    try {
                        int slot = Integer.parseInt(entry.substring(0, colon));
                        UUID uid = UUID.fromString(entry.substring(colon + 1));
                        if (slot >= 0 && slot < slotRobots.length) slotRobots[slot] = uid;
                    } catch (IllegalArgumentException ignored) {}
                } else {
                    // 兼容旧格式：纯 uuid，按顺序填入空槽
                    try {
                        UUID uid = UUID.fromString(entry);
                        for (int i = 0; i < slotRobots.length; i++) {
                            if (slotRobots[i] == null) { slotRobots[i] = uid; break; }
                        }
                    } catch (IllegalArgumentException ignored) {}
                }
            }
        }
    }

    @Override
    public void write(@NotNull PersistentDataContainer pdc) {
        pdc.set(K_ID, PersistentDataType.STRING, terminalId.toString());
        setOrRemove(pdc, K_AX, ax); setOrRemove(pdc, K_AZ, az);
        setOrRemove(pdc, K_BX, bx); setOrRemove(pdc, K_BZ, bz);
        if (deliveryPoint != null) pdc.set(K_DLV, PersistentDataType.STRING, writePoint(deliveryPoint)); else pdc.remove(K_DLV);
        if (chargePoint   != null) pdc.set(K_CHG, PersistentDataType.STRING, writePoint(chargePoint)); else pdc.remove(K_CHG);
        StringBuilder robotsSb = new StringBuilder();
        for (int i = 0; i < slotRobots.length; i++) {
            if (slotRobots[i] != null) {
                if (!robotsSb.isEmpty()) robotsSb.append(';');
                robotsSb.append(i).append(':').append(slotRobots[i]);
            }
        }
        if (!robotsSb.isEmpty()) {
            pdc.set(K_ROBOTS, PersistentDataType.STRING, robotsSb.toString());
        } else {
            pdc.remove(K_ROBOTS);
        }
    }

    /** 当前实际机器人上限：有终端扩容模组则为满配上限，否则为基础值。 */
    int effectiveMaxRobots() {
        return hasCapacityUpgrade() ? upgradeMaxRobots : baseMaxRobots;
    }

    private boolean hasCapacityUpgrade() {
        if (upgradeInventory == null) return false;
        for (int i = 0; i < upgradeInventory.getSize(); i++) {
            ItemStack stack = upgradeInventory.getItem(i);
            if (stack != null && !stack.isEmpty()
                    && RebarItem.fromStack(stack) instanceof io.github.steamwork.content.machines.upgrade.UpgradeModule m
                    && m.getUpgradeType() == io.github.steamwork.content.machines.upgrade.UpgradeType.TERMINAL_CAPACITY) {
                return true;
            }
        }
        return false;
    }

    @Override
    public int upgradeSlotCount() { return upgradeSlots; }

    @Override
    public void openUpgradeGui(@NotNull Player player) {
        if (upgradeInventory == null) return;
        String middleRow = "# # # # u # # # #";
        xyz.xenondevs.invui.window.Window.builder()
                .setUpperGui(Gui.builder()
                        .setStructure("# # # # # # # # #", middleRow, "# # # # # # # # #")
                        .addIngredient('#', GuiItems.background())
                        .addIngredient('u', upgradeInventory)
                        .build())
                .setTitle(ni(Component.translatable("steamwork.gui.upgrade.title")))
                .setViewer(player)
                .build()
                .open();
    }

    @SuppressWarnings("unused")
    public void postInitialise() {
        ACTIVE_TERMINALS.put(terminalId, this);
        robotSlots.addPreUpdateHandler(event -> {
            ItemStack newItem = event.getNewItem();
            if (newItem == null || newItem.isEmpty()) return;
            // 锁定超出当前上限的槽位
            if (event.getSlot() >= effectiveMaxRobots()) {
                event.setCancelled(true);
                return;
            }
            if (!(RebarItem.fromStack(newItem) instanceof SteamRobotItem)) {
                event.setCancelled(true);
                return;
            }
            if (newItem.getAmount() > 1) {
                event.setCancelled(true);
            }
        });
        robotSlots.addPostUpdateHandler(event -> {
            if (suppressSlotDeploy) return;
            int slot = event.getSlot();
            if (slot < 0 || slot >= slotRobots.length) return;
            ItemStack prev = event.getPreviousItem();
            ItemStack next = event.getNewItem();
            boolean hadItem = prev != null && !prev.isEmpty();
            boolean hasItem = next != null && !next.isEmpty();
            if (hadItem && slotRobots[slot] != null) {
                recallRobot(slotRobots[slot]);
                slotRobots[slot] = null;
            }
            if (hasItem) {
                slotRobots[slot] = deployRobot(next);
            }
            refreshGuiItems();
        });
        // 升级槽：只接受终端扩容模组
        if (upgradeInventory != null) {
            upgradeInventory.addPreUpdateHandler(event -> {
                ItemStack newItem = event.getNewItem();
                if (newItem == null || newItem.isEmpty()) return;
                if (!(RebarItem.fromStack(newItem) instanceof io.github.steamwork.content.machines.upgrade.UpgradeModule m
                        && m.getUpgradeType() == io.github.steamwork.content.machines.upgrade.UpgradeType.TERMINAL_CAPACITY)) {
                    event.setCancelled(true);
                }
            });
            upgradeInventory.addPostUpdateHandler(event -> {
                // 移除升级后，超出新上限的槽位需召回机器人并弹出物品
                int eff = effectiveMaxRobots();
                for (int i = eff; i < slotRobots.length; i++) {
                    if (slotRobots[i] != null) {
                        recallRobot(slotRobots[i]);
                        slotRobots[i] = null;
                    }
                    ItemStack item = robotSlots.getItem(i);
                    if (item != null && !item.isEmpty()) {
                        Block block = getBlock();
                        block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 1.0, 0.5), item);
                        suppressSlotDeploy = true;
                        robotSlots.setItem(null, i, ItemStack.empty());
                        suppressSlotDeploy = false;
                    }
                }
                refreshGuiItems();
            });
        }
    }

    @Override
    public @NotNull Map<@NotNull String, @NotNull VirtualInventory> getVirtualInventories() {
        if (upgradeInventory != null) {
            return Map.of("robots", robotSlots, "upgrades", upgradeInventory);
        }
        return Map.of("robots", robotSlots);
    }

    @SuppressWarnings("unused")
    public void onBlockBreak(@NotNull List<@NotNull ItemStack> drops, @NotNull BlockBreakContext context) {
        ACTIVE_TERMINALS.remove(terminalId, this);
        recallAllRobots();
        VirtualInventoryRebarBlock.super.onBlockBreak(drops, context);
    }

    // ===== 部署 / 召回 =====

    private @Nullable UUID deployRobot(@NotNull ItemStack robotItem) {
        if (!hasRegion()) return null;
        RebarItem ri = RebarItem.fromStack(robotItem);
        if (!(ri instanceof SteamRobotItem sri)) return null;
        SteamRobot.RobotType type = switch (sri) {
            case SteamRobotItem.Mining  ignored -> SteamRobot.RobotType.MINE;
            case SteamRobotItem.Lumber  ignored -> SteamRobot.RobotType.CHOP;
            case SteamRobotItem.Haul    ignored -> SteamRobot.RobotType.HAUL;
            case SteamRobotItem.Picker  ignored -> SteamRobot.RobotType.PICK;
            case SteamRobotItem.Farmer  ignored -> SteamRobot.RobotType.FARM;
            case SteamRobotItem.Butcher ignored -> SteamRobot.RobotType.BUTCHER;
            default -> SteamRobot.RobotType.PATROL;
        };
        Location spawnLoc = findSafeSpawn();
        if (spawnLoc == null) return null;
        SteamRobot robot = SteamRobot.spawn(spawnLoc, type, terminalId);
        return robot.getEntity().getUniqueId();
    }

    private void recallRobot(@NotNull UUID entityId) {
        World world = getBlock().getWorld();
        Entity entity = world.getEntity(entityId);
        if (entity != null && !entity.isDead()) {
            if (io.github.pylonmc.rebar.entity.EntityStorage.get(entity) instanceof SteamRobot robot) {
                robot.dropStoredItems(getBlock().getLocation().add(0.5, 1.0, 0.5));
            }
            entity.remove();
        }
    }

    void recallAllRobots() {
        World world = getBlock().getWorld();
        Location dropLoc = getBlock().getLocation().add(0.5, 1.0, 0.5);
        for (int i = 0; i < slotRobots.length; i++) {
            if (slotRobots[i] == null) continue;
            Entity entity = world.getEntity(slotRobots[i]);
            if (entity != null && !entity.isDead()) {
                if (io.github.pylonmc.rebar.entity.EntityStorage.get(entity) instanceof SteamRobot robot) {
                    robot.dropStoredItems(dropLoc);
                }
                entity.remove();
            }
            slotRobots[i] = null;
        }
    }

    private @Nullable Location findSafeSpawn() {
        Block base = getBlock();
        World world = base.getWorld();
        // 在终端旁边找一个可站立的位置生成机器人
        int bx = base.getX(), by = base.getY(), bz = base.getZ();
        for (int[] offset : new int[][]{{1,0},{-1,0},{0,1},{0,-1},{1,1},{-1,1},{1,-1},{-1,-1}}) {
            Block feet = world.getBlockAt(bx + offset[0], by, bz + offset[1]);
            Block head = feet.getRelative(org.bukkit.block.BlockFace.UP);
            Block ground = feet.getRelative(org.bukkit.block.BlockFace.DOWN);
            if (!feet.getType().isSolid() && !head.getType().isSolid() && ground.getType().isSolid()) {
                return feet.getLocation().add(0.5, 0.0, 0.5);
            }
        }
        // fallback：终端正上方
        return base.getRelative(org.bukkit.block.BlockFace.UP).getLocation().add(0.5, 0.0, 0.5);
    }

    // ===== 区域 / 配置（供机器人与 GUI 读取） =====

    public @NotNull UUID getTerminalId() { return terminalId; }
    public boolean hasRegion() { return ax != null && az != null && bx != null && bz != null; }
    public @Nullable Location getDeliveryPoint() {
        if (deliveryPoint != null) {
            Block b = deliveryPoint.getBlock();
            if (PneumaticEndpointSupport.isChunkLoaded(b) && !(b.getState() instanceof Container)) {
                deliveryPoint = null;
            }
        }
        return deliveryPoint;
    }

    public @Nullable Location getChargePoint() {
        if (chargePoint != null) {
            Block b = chargePoint.getBlock();
            if (PneumaticEndpointSupport.isChunkLoaded(b)
                    && !(PneumaticEndpointSupport.loadedRebarBlock(b) instanceof SteamChargingChamber)) {
                chargePoint = null;
            }
        }
        return chargePoint;
    }

    private boolean isDeliveryValid() {
        if (deliveryPoint == null) return false;
        Block b = deliveryPoint.getBlock();
        return PneumaticEndpointSupport.isChunkLoaded(b) && b.getState() instanceof Container;
    }

    private boolean isChargeValid() {
        if (chargePoint == null) return false;
        Block b = chargePoint.getBlock();
        return PneumaticEndpointSupport.isChunkLoaded(b)
                && PneumaticEndpointSupport.loadedRebarBlock(b) instanceof SteamChargingChamber;
    }

    /** 区域 X 范围（min, max）；调用前须确认 {@link #hasRegion()}。 */
    public int regionMinX() { return Math.min(ax, bx); }
    public int regionMaxX() { return Math.max(ax, bx); }
    /** 区域 Z 范围（min, max）。 */
    public int regionMinZ() { return Math.min(az, bz); }
    public int regionMaxZ() { return Math.max(az, bz); }

    /** 某坐标是否在区域内（忽略 Y，同世界）。 */
    public boolean withinRegion(@NotNull Location loc) {
        if (!hasRegion() || loc.getWorld() != getBlock().getWorld()) return false;
        int x = loc.getBlockX(), z = loc.getBlockZ();
        return x >= Math.min(ax, bx) && x <= Math.max(ax, bx)
                && z >= Math.min(az, bz) && z <= Math.max(az, bz);
    }

    /** 设置区域两角，校验 ≤ regionMax×regionMax；越界则拒绝并返回 false。 */
    private boolean setRegion(@NotNull Block c1, @NotNull Block c2, @NotNull Player feedback) {
        if (c1.getWorld() != getBlock().getWorld() || c2.getWorld() != getBlock().getWorld()) {
            msg(feedback, "steamwork.message.robot_terminal.region_other_world", NamedTextColor.RED);
            return false;
        }
        int sizeX = Math.abs(c1.getX() - c2.getX()) + 1;
        int sizeZ = Math.abs(c1.getZ() - c2.getZ()) + 1;
        if (sizeX > regionMax || sizeZ > regionMax) {
            msg(feedback, "steamwork.message.robot_terminal.region_too_big", NamedTextColor.RED);
            return false;
        }
        ax = c1.getX(); az = c1.getZ(); bx = c2.getX(); bz = c2.getZ();
        // 区域变了：原出货/充汽点若不在新区域内则作废
        if (deliveryPoint != null && !withinRegion(deliveryPoint)) deliveryPoint = null;
        if (chargePoint   != null && !withinRegion(chargePoint))   chargePoint = null;
        msg(feedback, "steamwork.message.robot_terminal.region_set", NamedTextColor.GREEN);
        return true;
    }

    /** 设出货点（必须是区域内的容器）。 */
    private void setDeliveryPoint(@NotNull Block block, @NotNull Player feedback) {
        if (!hasRegion()) { msg(feedback, "steamwork.message.robot_terminal.need_region_first", NamedTextColor.RED); return; }
        if (!withinRegion(block.getLocation())) { msg(feedback, "steamwork.message.robot_terminal.point_out_of_region", NamedTextColor.RED); return; }
        if (!(block.getState() instanceof Container)) { msg(feedback, "steamwork.message.robot_terminal.delivery_invalid", NamedTextColor.RED); return; }
        deliveryPoint = block.getLocation();
        msg(feedback, "steamwork.message.robot_terminal.delivery_set", NamedTextColor.GREEN);
    }

    /** 设充汽点（必须是区域内的蒸汽充汽舱）。 */
    private void setChargePoint(@NotNull Block block, @NotNull Player feedback) {
        if (!hasRegion()) { msg(feedback, "steamwork.message.robot_terminal.need_region_first", NamedTextColor.RED); return; }
        if (!withinRegion(block.getLocation())) { msg(feedback, "steamwork.message.robot_terminal.point_out_of_region", NamedTextColor.RED); return; }
        if (!(PneumaticEndpointSupport.loadedRebarBlock(block) instanceof SteamChargingChamber)) { msg(feedback, "steamwork.message.robot_terminal.charge_invalid", NamedTextColor.RED); return; }
        chargePoint = block.getLocation();
        msg(feedback, "steamwork.message.robot_terminal.charge_set", NamedTextColor.GREEN);
    }

    // ===== GUI =====

    @Override
    public @NotNull Gui createGui() {
        int visibleRobotSlots = Math.min(upgradeMaxRobots, ROBOT_SLOT_CHARS.length);
        String robotRow = switch (visibleRobotSlots) {
            case 1 -> "# # # # 1 # # # #";
            case 2 -> "# # # 1 2 # # # #";
            case 3 -> "# # # 1 2 3 # # #";
            case 4 -> "# # 1 2 3 4 # # #";
            default -> "# # 1 2 3 4 5 # #";
        };
        statusItem = new StatusItem();
        var builder = Gui.builder()
                .setStructure(
                        "# # # # s # # # #",
                        "# r # d # c # a #",
                        "# # # # # # # # #",
                        robotRow)
                .addIngredient('#', GuiItems.background())
                .addIngredient('s', statusItem)
                .addIngredient('r', new RegionItem())
                .addIngredient('d', new BindItem(false))
                .addIngredient('c', new BindItem(true))
                .addIngredient('a', new ClaimItem());
        for (int i = 0; i < visibleRobotSlots; i++) {
            RobotSlotItem item = new RobotSlotItem(i);
            robotSlotItems[i] = item;
            builder.addIngredient(ROBOT_SLOT_CHARS[i], item);
        }
        return builder.build();
    }

    @Override
    public @Nullable WailaDisplay getWaila(@NotNull Player player) {
        Component status = hasRegion()
                ? Component.translatable("steamwork.gui.robot_terminal.region_size",
                        io.github.pylonmc.rebar.i18n.RebarArgument.of("x", String.valueOf(Math.abs(ax - bx) + 1)),
                        io.github.pylonmc.rebar.i18n.RebarArgument.of("z", String.valueOf(Math.abs(az - bz) + 1)))
                    .color(NamedTextColor.GREEN)
                : Component.translatable("steamwork.gui.robot_terminal.no_region").color(NamedTextColor.GRAY);
        return WailaDisplay.of(this, player).add(status);
    }

    /** 区域信息 / 点击划定区域（连点两角）。 */
    private final class RegionItem extends AbstractItem {
        @Override public @NotNull ItemProvider getItemProvider(@NotNull Player v) {
            if (hasRegion()) {
                Component size = Component.text(Math.abs(ax - bx) + 1 + " × " + (Math.abs(az - bz) + 1), NamedTextColor.GREEN);
                Component coords = Component.text("(" + ax + ", " + az + ") → (" + bx + ", " + bz + ")", NamedTextColor.GRAY);
                return ItemStackBuilder.of(Material.MAP)
                        .name(ni(Component.translatable("steamwork.gui.robot_terminal.set_region").color(NamedTextColor.AQUA)))
                        .lore(List.of(ni(size), ni(coords),
                                ni(Component.translatable("steamwork.gui.robot_terminal.set_region_hint").color(NamedTextColor.DARK_GRAY))));
            }
            return ItemStackBuilder.of(Material.MAP)
                    .name(ni(Component.translatable("steamwork.gui.robot_terminal.set_region").color(NamedTextColor.AQUA)))
                    .lore(List.of(ni(Component.translatable("steamwork.gui.robot_terminal.no_region").color(NamedTextColor.RED)),
                            ni(Component.translatable("steamwork.gui.robot_terminal.set_region_hint").color(NamedTextColor.DARK_GRAY))));
        }
        @Override public void handleClick(@NotNull ClickType t, @NotNull Player p, @NotNull Click c) {
            p.closeInventory();
            Location termLoc = getBlock().getLocation();
            TerminalParticleVisualizer.startSession(p, termLoc,
                    TerminalParticleVisualizer.SessionType.REGION, regionMax);
            msg(p, "steamwork.message.robot_terminal.click_corner1", NamedTextColor.YELLOW);
            SteamworkBlockPrompt.await(p, c1 -> {
                TerminalParticleVisualizer.setCorner1(p, c1.getLocation());
                msg(p, "steamwork.message.robot_terminal.click_corner2", NamedTextColor.YELLOW);
                SteamworkBlockPrompt.await(p, c2 -> {
                    TerminalParticleVisualizer.endSession(p);
                    setRegion(c1, c2, p);
                });
            });
        }
    }

    /** 出货点 / 充汽点绑定按钮（点击后下一次右键方块完成绑定）。 */
    private final class BindItem extends AbstractItem {
        private final boolean charge;
        BindItem(boolean charge) { this.charge = charge; }
        @Override public @NotNull ItemProvider getItemProvider(@NotNull Player v) {
            Location bound = charge ? chargePoint : deliveryPoint;
            Material icon = charge ? Material.BLAST_FURNACE : Material.CHEST;
            Component name = Component.translatable(charge
                    ? "steamwork.gui.robot_terminal.set_charge"
                    : "steamwork.gui.robot_terminal.set_delivery").color(NamedTextColor.AQUA);
            java.util.ArrayList<Component> lore = new java.util.ArrayList<>();
            if (bound == null) {
                lore.add(ni(Component.translatable("steamwork.gui.robot_terminal.unbound").color(NamedTextColor.RED)));
            } else {
                boolean valid = charge ? isChargeValid() : isDeliveryValid();
                Component coords = Component.text("(" + bound.getBlockX() + ", " + bound.getBlockY() + ", " + bound.getBlockZ() + ")");
                if (valid) {
                    lore.add(ni(coords.color(NamedTextColor.GREEN)));
                } else {
                    lore.add(ni(Component.translatable("steamwork.gui.robot_terminal.point_invalid").color(NamedTextColor.RED)));
                    lore.add(ni(coords.color(NamedTextColor.DARK_GRAY).decorate(TextDecoration.STRIKETHROUGH)));
                }
            }
            lore.add(ni(Component.translatable("steamwork.gui.robot_terminal.bind_hint").color(NamedTextColor.DARK_GRAY)));
            return ItemStackBuilder.of(icon).name(ni(name)).lore(lore);
        }
        @Override public void handleClick(@NotNull ClickType t, @NotNull Player p, @NotNull Click c) {
            p.closeInventory();
            Location termLoc = getBlock().getLocation();
            TerminalParticleVisualizer.startSession(p, termLoc,
                    charge ? TerminalParticleVisualizer.SessionType.CHARGE
                           : TerminalParticleVisualizer.SessionType.DELIVERY, regionMax);
            msg(p, charge ? "steamwork.message.robot_terminal.click_charge" : "steamwork.message.robot_terminal.click_delivery", NamedTextColor.YELLOW);
            SteamworkBlockPrompt.await(p, b -> {
                TerminalParticleVisualizer.endSession(p);
                if (charge) setChargePoint(b, p); else setDeliveryPoint(b, p);
            });
        }
    }

    /** 终端状态总览（区域、出货、充汽、机器人数量一目了然）。 */
    private void refreshGuiItems() {
        if (statusItem != null) statusItem.notifyWindows();
        for (RobotSlotItem item : robotSlotItems) {
            if (item != null) item.notifyWindows();
        }
    }

    private final class RobotSlotItem extends AbstractItem {
        private final int index;

        RobotSlotItem(int index) {
            this.index = index;
        }

        @Override
        public @NotNull ItemProvider getItemProvider(@NotNull Player viewer) {
            if (index >= effectiveMaxRobots()) {
                return ItemStackBuilder.of(Material.RED_STAINED_GLASS_PANE)
                        .name(ni(Component.translatable("steamwork.gui.robot_terminal.robot_slot_locked")
                                .color(NamedTextColor.RED)))
                        .lore(List.of(ni(Component.translatable("steamwork.gui.robot_terminal.robot_slot_locked_hint")
                                .color(NamedTextColor.DARK_GRAY))));
            }

            ItemStack stack = robotSlots.getItem(index);
            if (stack != null && !stack.isEmpty()) {
                return ItemStackBuilder.copyOf(stack);
            }

            return ItemStackBuilder.of(Material.LIGHT_GRAY_STAINED_GLASS_PANE)
                    .name(ni(Component.translatable("steamwork.gui.robot_terminal.robot_slot_empty")
                            .color(NamedTextColor.GRAY)))
                    .lore(List.of(ni(Component.translatable("steamwork.gui.robot_terminal.robot_slot_empty_hint")
                            .color(NamedTextColor.DARK_GRAY))));
        }

        @Override
        public void handleClick(@NotNull ClickType type, @NotNull Player player, @NotNull Click click) {
            if (index >= effectiveMaxRobots()) {
                refreshGuiItems();
                return;
            }

            ItemStack current = robotSlots.getItem(index);
            boolean occupied = current != null && !current.isEmpty();
            ItemStack cursor = player.getItemOnCursor();
            boolean hasCursor = cursor != null && !cursor.isEmpty();

            if (occupied) {
                if (!hasCursor) {
                    player.setItemOnCursor(current.clone());
                    robotSlots.setItem(null, index, ItemStack.empty());
                }
                refreshGuiItems();
                return;
            }

            if (!hasCursor || !(RebarItem.fromStack(cursor) instanceof SteamRobotItem)) {
                refreshGuiItems();
                return;
            }

            ItemStack placing = cursor.clone();
            placing.setAmount(1);
            robotSlots.setItem(null, index, placing);
            if (cursor.getAmount() <= 1) {
                player.setItemOnCursor(ItemStack.empty());
            } else {
                ItemStack remaining = cursor.clone();
                remaining.setAmount(cursor.getAmount() - 1);
                player.setItemOnCursor(remaining);
            }
            refreshGuiItems();
        }
    }

    private final class StatusItem extends AbstractItem {
        @Override public @NotNull ItemProvider getItemProvider(@NotNull Player v) {
            java.util.ArrayList<Component> lore = new java.util.ArrayList<>();
            // 区域
            if (hasRegion()) {
                lore.add(ni(Component.text("▸ ").color(NamedTextColor.DARK_GRAY)
                        .append(Component.translatable("steamwork.gui.robot_terminal.set_region").color(NamedTextColor.WHITE))
                        .append(Component.text("  " + (Math.abs(ax - bx) + 1) + " × " + (Math.abs(az - bz) + 1), NamedTextColor.GREEN))));
            } else {
                lore.add(ni(Component.text("▸ ").color(NamedTextColor.DARK_GRAY)
                        .append(Component.translatable("steamwork.gui.robot_terminal.set_region").color(NamedTextColor.WHITE))
                        .append(Component.text("  ").append(Component.translatable("steamwork.gui.robot_terminal.no_region").color(NamedTextColor.RED)))));
            }
            // 出货点
            addPointLine(lore, deliveryPoint, false);
            // 充汽点
            addPointLine(lore, chargePoint, true);
            // 机器人
            int deployed = 0;
            for (UUID u : slotRobots) if (u != null) deployed++;
            lore.add(ni(Component.text("▸ ").color(NamedTextColor.DARK_GRAY)
                    .append(Component.translatable("steamwork.gui.robot_terminal.status_robots").color(NamedTextColor.WHITE))
                    .append(Component.text("  " + deployed + " / " + effectiveMaxRobots(), deployed > 0 ? NamedTextColor.GREEN : NamedTextColor.GRAY))));
            return ItemStackBuilder.of(Material.COMPARATOR)
                    .name(ni(Component.translatable("steamwork.gui.robot_terminal.status_title").color(NamedTextColor.GOLD)))
                    .lore(lore);
        }
        private void addPointLine(java.util.ArrayList<Component> lore, @Nullable Location point, boolean charge) {
            String labelKey = charge ? "steamwork.gui.robot_terminal.set_charge" : "steamwork.gui.robot_terminal.set_delivery";
            Component base = Component.text("▸ ").color(NamedTextColor.DARK_GRAY)
                    .append(Component.translatable(labelKey).color(NamedTextColor.WHITE).append(Component.text("  ")));
            if (point == null) {
                lore.add(ni(base.append(Component.translatable("steamwork.gui.robot_terminal.unbound").color(NamedTextColor.GRAY))));
            } else {
                boolean valid = charge ? isChargeValid() : isDeliveryValid();
                if (valid) {
                    lore.add(ni(base.append(Component.text("(" + point.getBlockX() + ", " + point.getBlockY() + ", " + point.getBlockZ() + ")", NamedTextColor.GREEN))));
                } else {
                    lore.add(ni(base.append(Component.translatable("steamwork.gui.robot_terminal.point_invalid").color(NamedTextColor.RED))));
                }
            }
        }
        @Override public void handleClick(@NotNull ClickType t, @NotNull Player p, @NotNull Click c) {}
    }

    /** 认领区域内未绑定终端的机器人（绑定到本终端）。 */
    private final class ClaimItem extends AbstractItem {
        @Override public @NotNull ItemProvider getItemProvider(@NotNull Player v) {
            return ItemStackBuilder.of(Material.LEAD)
                    .name(ni(Component.translatable("steamwork.gui.robot_terminal.claim").color(NamedTextColor.AQUA)))
                    .lore(List.of(ni(Component.translatable("steamwork.gui.robot_terminal.claim_hint")
                            .color(NamedTextColor.DARK_GRAY))));
        }
        @Override public void handleClick(@NotNull ClickType t, @NotNull Player p, @NotNull Click c) {
            if (!hasRegion()) {
                msg(p, "steamwork.message.robot_terminal.need_region_first", NamedTextColor.RED);
                return;
            }
            int claimed = claimNearbyRobots();
            if (claimed > 0) {
                p.sendActionBar(Component.translatable("steamwork.message.robot_terminal.claimed",
                        io.github.pylonmc.rebar.i18n.RebarArgument.of("count", String.valueOf(claimed)))
                        .color(NamedTextColor.GREEN));
            } else {
                msg(p, "steamwork.message.robot_terminal.none_to_claim", NamedTextColor.GRAY);
            }
        }
    }

    private int claimNearbyRobots() {
        if (!hasRegion()) return 0;
        World world = getBlock().getWorld();
        int count = 0;
        suppressSlotDeploy = true;
        try {
            for (Entity entity : world.getEntities()) {
                if (!withinRegion(entity.getLocation())) continue;
                if (!(io.github.pylonmc.rebar.entity.EntityStorage.get(entity) instanceof SteamRobot robot)) continue;
                if (robot.getTerminalId() != null && robot.terminal() != null) continue;
                // 找第一个空槽（受升级上限约束）
                int emptySlot = -1;
                int eff = effectiveMaxRobots();
                for (int i = 0; i < eff; i++) {
                    if (slotRobots[i] == null) {
                        ItemStack si = robotSlots.getItem(i);
                        if (si == null || si.isEmpty()) { emptySlot = i; break; }
                    }
                }
                if (emptySlot < 0) break;
                robot.bindTerminal(terminalId);
                slotRobots[emptySlot] = entity.getUniqueId();
                ItemStack robotItem = robotItemForType(robot.getRobotType());
                if (robotItem != null) {
                    robotSlots.setItem(null, emptySlot, robotItem);
                }
                count++;
            }
        } finally {
            suppressSlotDeploy = false;
        }
        refreshGuiItems();
        return count;
    }

    private static @Nullable ItemStack robotItemForType(@NotNull SteamRobot.RobotType type) {
        return switch (type) {
            case MINE    -> io.github.steamwork.SteamworkItems.MINING_ROBOT.clone();
            case CHOP    -> io.github.steamwork.SteamworkItems.LUMBER_ROBOT.clone();
            case HAUL    -> io.github.steamwork.SteamworkItems.HAUL_ROBOT.clone();
            case PATROL  -> io.github.steamwork.SteamworkItems.PATROL_ROBOT.clone();
            case PICK    -> io.github.steamwork.SteamworkItems.PICKER_ROBOT.clone();
            case FARM    -> io.github.steamwork.SteamworkItems.FARMER_ROBOT.clone();
            case BUTCHER -> io.github.steamwork.SteamworkItems.BUTCHER_ROBOT.clone();
        };
    }

    // ===== 物品 =====
    public static class Item extends RebarItem {
        public Item(@NotNull ItemStack stack) { super(stack); }
    }

    // ===== 工具 =====
    private static void msg(@NotNull Player p, @NotNull String key, @NotNull NamedTextColor color) {
        p.sendActionBar(Component.translatable(key).color(color));
    }
    private static @NotNull Component ni(@NotNull Component c) { return c.decoration(TextDecoration.ITALIC, false); }
    private static void setOrRemove(@NotNull PersistentDataContainer pdc, @NotNull NamespacedKey key, @Nullable Integer v) {
        if (v != null) pdc.set(key, PersistentDataType.INTEGER, v); else pdc.remove(key);
    }
    private @Nullable Location readPoint(@Nullable String s) {
        if (s == null) return null;
        String[] p = s.split(",");
        if (p.length != 3) return null;
        try {
            return new Location(getBlock().getWorld(),
                    Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2]));
        } catch (NumberFormatException e) { return null; }
    }
    private static @NotNull String writePoint(@NotNull Location l) {
        return l.getBlockX() + "," + l.getBlockY() + "," + l.getBlockZ();
    }
}
