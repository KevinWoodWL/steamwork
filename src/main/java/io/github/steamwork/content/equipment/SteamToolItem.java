package io.github.steamwork.content.equipment;

import io.github.pylonmc.rebar.event.api.annotation.MultiHandler;
import io.github.pylonmc.rebar.item.base.RebarBlockInteractor;
import io.github.pylonmc.rebar.item.base.RebarInventoryTicker;
import io.github.pylonmc.rebar.item.base.RebarTool;
import io.github.pylonmc.rebar.item.base.RebarWeapon;
import io.github.steamwork.util.SteamCharge;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.TileState;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;

import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 蒸汽工具 / 武器，按材质分级提供不同的「蒸汽技能」与数值。
 *
 * <h3>剑命中技能</h3>
 * <ul>
 *   <li>黄铜：点燃 2 秒</li>
 *   <li>青铜：点燃 5 秒（强化燃烧）</li>
 *   <li>因瓦：缓慢 II 3 秒 + 点燃 2 秒（精密削弱）</li>
 *   <li>钨：击退周围敌人 + 点燃 2 秒（重型冲击）</li>
 * </ul>
 *
 * <h3>镐/斧/锹采掘技能</h3>
 * <ul>
 *   <li>黄铜：3×3 范围采掘</li>
 *   <li>青铜：3×3 + 掉落物自动冶炼（矿石→锭）</li>
 *   <li>因瓦：3×3 + 掉落物直接进背包</li>
 *   <li>钨：5×5 范围采掘</li>
 * </ul>
 *
 * <h3>锄耕地</h3>
 * <p>黄铜/青铜/因瓦 3×3，钨 5×5。</p>
 */
public class SteamToolItem extends SteamEquipment implements
        RebarTool,
        RebarWeapon,
        RebarBlockInteractor,
        RebarInventoryTicker {

    private static final double STEAM_PER_HIT = 12.0;
    private static final double STEAM_PER_BREAK = 20.0;
    private static final double STEAM_PER_EXTRA_RANGE_BLOCK = 3.0;
    private static final double STEAM_PER_TILL = 15.0;
    private static final double DEPLETED_DAMAGE_MULT = 0.60;
    private static final double SWEEP_DAMAGE_MULT = 0.28;

    /** 钨剑命中时对周围敌人的击退强度。 */
    private static final double TUNGSTEN_KNOCKBACK = 0.9;

    /** 重入守卫：标记当前正在施加横扫 / 喷射的内部伤害，防止其再次触发攻击处理器。 */
    private static boolean dealingInternalDamage = false;

    // ── 剑右键技能「蒸汽喷射」 ────────────────────────────────────────────────

    /** 蒸汽喷射基础耗汽（mB），按材质 toolSteamCostMultiplier 缩放。 */
    private static final double STEAM_BURST_COST = 35.0;

    /** 蒸汽喷射冷却（ms），所有蒸汽剑按玩家共享。 */
    private static final long STEAM_BURST_COOLDOWN_MS = 2500L;

    /** 蒸汽喷射前方有效距离（格）与锥形半角（度）。 */
    private static final double STEAM_BURST_REACH = 8.0;
    private static final double STEAM_BURST_CONE_DEG = 40.0;

    /** 每个玩家上次释放蒸汽喷射的时间戳（剑实例为单例，须按玩家区分冷却）。 */
    private static final Map<UUID, Long> STEAM_BURST_LAST_USE = new HashMap<>();

    /** 常见矿物冶炼映射表（青铜工具自动冶炼用）。 */
    private static final Map<Material, Material> SMELT_MAP = new EnumMap<>(Material.class);

    static {
        SMELT_MAP.put(Material.RAW_IRON,    Material.IRON_INGOT);
        SMELT_MAP.put(Material.RAW_COPPER,  Material.COPPER_INGOT);
        SMELT_MAP.put(Material.RAW_GOLD,    Material.GOLD_INGOT);
        SMELT_MAP.put(Material.ANCIENT_DEBRIS, Material.NETHERITE_SCRAP);
        SMELT_MAP.put(Material.SAND,        Material.GLASS);
        SMELT_MAP.put(Material.RED_SAND,    Material.GLASS);
        SMELT_MAP.put(Material.COBBLESTONE, Material.STONE);
        SMELT_MAP.put(Material.STONE,       Material.SMOOTH_STONE);
        SMELT_MAP.put(Material.NETHERRACK,  Material.NETHER_BRICK);
        SMELT_MAP.put(Material.CLAY_BALL,   Material.BRICK);
    }

    public SteamToolItem(@NotNull ItemStack stack) {
        super(stack);
    }

    // ── 武器：命中实体 ────────────────────────────────────────────────────────

    @Override
    @MultiHandler(priorities = {EventPriority.HIGH})
    public void onUsedToDamageEntity(@NotNull EntityDamageByEntityEvent event, @NotNull EventPriority priority) {
        if (priority != EventPriority.HIGH) return;
        // 重入守卫：横扫 / 蒸汽喷射造成的内部伤害不应再次触发本处理器（否则雪崩耗汽）
        if (dealingInternalDamage) return;
        if (!(event.getDamageSource().getCausingEntity() instanceof Player player)) return;

        SteamEquipmentProfile profile = SteamEquipmentProfile.fromKey(getKey());
        if (profile == null) return;
        ItemStack tool = player.getInventory().getItemInMainHand();
        if (SteamEquipmentProfile.fromStack(tool) != profile) return;

        double steamCost = STEAM_PER_HIT * profile.material().toolSteamCostMultiplier();
        if (SteamCharge.tryRemoveAmount(tool, steamCost)) {
            double poweredDamage = event.getDamage() * profile.material().poweredDamageMultiplier();
            event.setDamage(poweredDamage);
            player.getInventory().setItemInMainHand(tool);

            if (event.getEntity() instanceof LivingEntity living) {
                applyHitSkill(player, living, profile.material());
            }
            if (profile.part() == SteamEquipmentPart.SWORD) {
                sweepNearby(player, event.getEntity(), poweredDamage * SWEEP_DAMAGE_MULT, profile.material());
            }
            playHitEffects(player, event.getEntity(), profile);
        } else {
            event.setDamage(event.getDamage() * DEPLETED_DAMAGE_MULT);
        }
    }

    /**
     * 按材质对命中目标施加不同的附加技能效果。
     */
    private void applyHitSkill(@NotNull Player player, @NotNull LivingEntity target,
                               @NotNull SteamEquipmentMaterial material) {
        switch (material) {
            case BRASS -> target.setFireTicks(Math.max(target.getFireTicks(), 40));   // 点燃 2s
            case BRONZE -> target.setFireTicks(Math.max(target.getFireTicks(), 100)); // 点燃 5s（烈焰）
            case INVAR -> {
                // 精密削弱：缓慢 II 3s + 点燃 2s
                target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 1, true, false, true));
                target.setFireTicks(Math.max(target.getFireTicks(), 40));
            }
            case TUNGSTEN -> {
                // 重型冲击：击退目标 + 点燃 2s
                target.setFireTicks(Math.max(target.getFireTicks(), 40));
                Vector kb = target.getLocation().toVector()
                        .subtract(player.getLocation().toVector()).normalize();
                kb.setY(Math.max(kb.getY(), 0.35));
                target.setVelocity(target.getVelocity().add(kb.multiply(TUNGSTEN_KNOCKBACK)));
            }
        }
    }

    // ── 工具：破坏方块 ────────────────────────────────────────────────────────

    @Override
    @MultiHandler(priorities = {EventPriority.HIGH})
    public void onUsedToBreakBlock(@NotNull BlockBreakEvent event, @NotNull EventPriority priority) {
        if (priority != EventPriority.HIGH) return;
        Player player = event.getPlayer();
        ItemStack tool = player.getInventory().getItemInMainHand();
        SteamEquipmentProfile profile = SteamEquipmentProfile.fromKey(getKey());
        if (profile == null || SteamEquipmentProfile.fromStack(tool) != profile) return;

        double steamCost = STEAM_PER_BREAK * profile.material().toolSteamCostMultiplier();
        if (!SteamCharge.tryRemoveAmount(tool, steamCost)) {
            applyDepletedFatigue(player);
            return;
        }
        player.getInventory().setItemInMainHand(tool);

        SteamEquipmentMaterial mat = profile.material();

        // 青铜冶炼 / 因瓦入背包：连中心方块的掉落也统一接管，避免中心与周围表现不一致
        boolean customDrops = mat == SteamEquipmentMaterial.BRONZE || mat == SteamEquipmentMaterial.INVAR;
        if (customDrops) {
            event.setDropItems(false);
            handleDrops(player, event.getBlock(), tool, mat); // 中心方块掉落
        }

        // 斧子 → 伐木（顺着连通原木整棵砍）；其余范围工具 → 平面范围采掘
        int affected = profile.part() == SteamEquipmentPart.AXE
                ? fellTree(player, event.getBlock(), tool, profile, customDrops)
                : rangeMine(player, event.getBlock(), tool, profile, customDrops);
        player.getInventory().setItemInMainHand(tool);
        playBreakEffects(event.getBlock(), profile, affected);
    }

    // ── 右键交互：剑「蒸汽喷射」 / 锄「范围耕地」 ─────────────────────────────

    @Override
    @MultiHandler(priorities = {EventPriority.HIGH})
    public void onUsedToClickBlock(@NotNull PlayerInteractEvent event, @NotNull EventPriority priority) {
        if (priority != EventPriority.HIGH) return;
        Action action = event.getAction();
        boolean rightClick = action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK;
        if (!rightClick) return;

        ItemStack tool = event.getItem();
        if (tool == null || tool.isEmpty()) return;

        SteamEquipmentProfile profile = SteamEquipmentProfile.fromKey(getKey());
        if (profile == null || SteamEquipmentProfile.fromStack(tool) != profile) return;

        Player player = event.getPlayer();

        // 剑的右键技能改由 SteamWeaponSkillListener 处理（本回调收不到右键空气）。
        // 这里只保留锄的范围耕地。
        if (profile.part() != SteamEquipmentPart.HOE) return;
        if (action != Action.RIGHT_CLICK_BLOCK) return;
        Block clicked = event.getClickedBlock();
        if (clicked == null) return;

        int radius = profile.material().miningRadius();
        if (!hasTillableAround(clicked, radius)) return;

        double steamCost = STEAM_PER_TILL * profile.material().toolSteamCostMultiplier();
        if (!SteamCharge.tryRemoveAmount(tool, steamCost)) {
            applyDepletedFatigue(player);
            return;
        }

        int tilled = tillAround(clicked, radius);
        if (tilled == 0) return;

        setUsedHand(player, event.getHand(), tool);
        playBreakEffects(clicked, profile, tilled);
        event.setCancelled(true);
        event.setUseInteractedBlock(Event.Result.DENY);
        event.setUseItemInHand(Event.Result.DENY);
    }

    // ── 剑右键技能：蒸汽喷射 ──────────────────────────────────────────────────

    /**
     * 尝试释放蒸汽喷射：检查冷却 + 扣汽，成功则向视线方向喷射蒸汽冲击波。
     *
     * <p>由 {@link io.github.steamwork.content.equipment.SteamWeaponSkillListener} 在玩家右键
     * （空气或方块）持蒸汽剑时调用——因为 {@code RebarBlockInteractor.onUsedToClickBlock}
     * 只在右键方块时回调，收不到右键空气事件。</p>
     */
    public void tryExecuteSteamBurst(
            @NotNull Player player,
            @NotNull ItemStack tool,
            @NotNull SteamEquipmentProfile profile,
            @NotNull PlayerInteractEvent event
    ) {
        UUID id = player.getUniqueId();
        long now = System.currentTimeMillis();
        Long last = STEAM_BURST_LAST_USE.get(id);
        if (last != null && now - last < STEAM_BURST_COOLDOWN_MS) return;

        double cost = STEAM_BURST_COST * profile.material().toolSteamCostMultiplier();
        if (!SteamCharge.tryRemoveAmount(tool, cost)) return; // 蒸汽不足，静默
        setUsedHand(player, event.getHand(), tool);
        STEAM_BURST_LAST_USE.put(id, now);

        executeSteamBurst(player, profile.material());
        event.setCancelled(true);
        event.setUseItemInHand(Event.Result.DENY);
    }

    /**
     * 向玩家视线方向喷射蒸汽冲击波，命中前方锥形范围内的敌人，
     * 造成伤害并施加该材质对应的命中效果（点燃/缓慢/击退）。
     */
    private void executeSteamBurst(@NotNull Player player, @NotNull SteamEquipmentMaterial mat) {
        Location eye = player.getEyeLocation();
        Vector dir = eye.getDirection().normalize();
        World world = player.getWorld();

        double damage = switch (mat) {
            case BRASS -> 3.0;
            case BRONZE -> 3.0;
            case INVAR -> 2.0;
            case TUNGSTEN -> 5.0;
        };

        // 锥形命中判定（内部伤害，加重入守卫，避免触发攻击处理器二次耗汽）
        dealingInternalDamage = true;
        try {
            for (Entity entity : player.getNearbyEntities(STEAM_BURST_REACH, STEAM_BURST_REACH, STEAM_BURST_REACH)) {
                if (entity == player || !(entity instanceof LivingEntity living) || living.isDead()) continue;
                Vector toTarget = living.getEyeLocation().toVector().subtract(eye.toVector());
                if (toTarget.lengthSquared() > STEAM_BURST_REACH * STEAM_BURST_REACH) continue;
                if (toTarget.lengthSquared() < 1.0e-4) continue;
                if (Math.toDegrees(dir.angle(toTarget)) > STEAM_BURST_CONE_DEG) continue;
                living.damage(damage, player);
                applyHitSkill(player, living, mat);
            }
        } finally {
            dealingInternalDamage = false;
        }

        // 沿视线喷射蒸汽粒子
        for (double d = 0.6; d <= STEAM_BURST_REACH; d += 0.4) {
            Location p = eye.clone().add(dir.clone().multiply(d));
            world.spawnParticle(org.bukkit.Particle.CLOUD, p, 3, 0.12, 0.12, 0.12, 0.02);
            switch (mat) {
                case BRONZE -> world.spawnParticle(org.bukkit.Particle.FLAME, p, 2, 0.1, 0.1, 0.1, 0.01);
                case INVAR -> world.spawnParticle(org.bukkit.Particle.ELECTRIC_SPARK, p, 2, 0.1, 0.1, 0.1, 0.01);
                case TUNGSTEN -> world.spawnParticle(org.bukkit.Particle.WHITE_ASH, p, 2, 0.1, 0.1, 0.1, 0.01);
                default -> { }
            }
        }
        world.playSound(eye, org.bukkit.Sound.ENTITY_BREEZE_SHOOT, 0.85f, effectPitch(mat));

        // ActionBar 提示
        String skillName = switch (mat) {
            case BRASS -> "💨 蒸汽喷射";
            case BRONZE -> "💨 烈焰喷射";
            case INVAR -> "💨 凝滞喷射";
            case TUNGSTEN -> "💨 高压喷射";
        };
        TextColor color = switch (mat) {
            case BRONZE -> NamedTextColor.GOLD;
            case TUNGSTEN -> NamedTextColor.WHITE;
            default -> NamedTextColor.AQUA;
        };
        player.sendActionBar(Component.text(skillName, color));
    }

    // ── 横扫 ──────────────────────────────────────────────────────────────────

    /**
     * 剑横扫周围敌人。钨剑横扫范围更大并附带击退。
     */
    private void sweepNearby(@NotNull Player player, @NotNull Entity primaryTarget, double damage,
                             @NotNull SteamEquipmentMaterial material) {
        double range = material == SteamEquipmentMaterial.TUNGSTEN ? 3.2 : 2.4;
        Location center = primaryTarget.getLocation();
        dealingInternalDamage = true;
        try {
            for (Entity entity : primaryTarget.getWorld().getNearbyEntities(center, range, 0.9, range)) {
                if (entity == player || entity == primaryTarget) continue;
                if (!(entity instanceof LivingEntity living) || living.isDead()) continue;
                living.damage(Math.max(1.0, damage), player);
                living.setFireTicks(Math.max(living.getFireTicks(),
                        material == SteamEquipmentMaterial.BRONZE ? 60 : 20));
                if (material == SteamEquipmentMaterial.TUNGSTEN) {
                    Vector kb = living.getLocation().toVector()
                            .subtract(center.toVector()).normalize();
                    kb.setY(Math.max(kb.getY(), 0.3));
                    living.setVelocity(living.getVelocity().add(kb.multiply(TUNGSTEN_KNOCKBACK * 0.7)));
                }
            }
        } finally {
            dealingInternalDamage = false;
        }
    }

    // ── 范围采掘 ──────────────────────────────────────────────────────────────

    /**
     * 范围采掘，半径由材质决定（1 → 3×3，2 → 5×5），中心方块除外。
     *
     * @param customDrops 是否由本方法接管掉落（青铜冶炼 / 因瓦入背包）
     * @return 实际额外破坏的方块数
     */
    private int rangeMine(
            @NotNull Player player,
            @NotNull Block center,
            @NotNull ItemStack tool,
            @NotNull SteamEquipmentProfile profile,
            boolean customDrops
    ) {
        if (!profile.part().isRangeMiningTool()) return 0;

        int radius = profile.material().miningRadius();
        SteamEquipmentMaterial mat = profile.material();
        int affected = 0;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx == 0 && dz == 0) continue;
                Block block = center.getRelative(dx, 0, dz);
                if (!rangeBreakable(block, center, tool, profile.part())) continue;

                if (radius > 1) {
                    double extraCost = STEAM_PER_EXTRA_RANGE_BLOCK * mat.toolSteamCostMultiplier();
                    if (!SteamCharge.tryRemoveAmount(tool, extraCost)) {
                        return affected;
                    }
                }

                if (customDrops) {
                    handleDrops(player, block, tool, mat);
                    block.setType(Material.AIR);
                    affected++;
                } else if (block.breakNaturally(tool)) {
                    affected++;
                }
            }
        }
        return affected;
    }

    /**
     * 判断范围采掘是否应破坏某个相邻方块，按工具部位区分：
     * <ul>
     *   <li><b>铲</b>：只破坏与中心<b>同种</b>的方块（挖土只挖土、挖沙只挖沙）。</li>
     *   <li><b>镐</b>：只破坏<b>需要正确工具</b>且镐为优选工具的方块（石头/矿石等），不挖泥土/沙子。</li>
     *   <li>其它：沿用"镐为优选工具"判断。</li>
     * </ul>
     */
    private boolean rangeBreakable(
            @NotNull Block block,
            @NotNull Block center,
            @NotNull ItemStack tool,
            @NotNull SteamEquipmentPart part
    ) {
        if (block.getType().isAir() || block.isLiquid()) return false;
        if (block.getType() == Material.BEDROCK) return false;
        if (block.getState() instanceof TileState) return false;
        return switch (part) {
            case SHOVEL -> block.getType() == center.getType();
            case PICKAXE -> block.isPreferredTool(tool)
                    && block.getBlockData().requiresCorrectToolForDrops();
            default -> block.isPreferredTool(tool);
        };
    }

    /**
     * 蒸汽斧伐木：从被砍的原木出发，沿 3×3×3 邻接（含竖向与斜向）泛洪连通的原木，整棵砍掉。
     * 只作用于原木（{@link org.bukkit.Tag#LOGS}），不碰其它方块；非原木方块则只砍当前这一块。
     * 每根额外原木按范围采掘的单价扣汽，蒸汽不足即停。
     */
    private int fellTree(
            @NotNull Player player,
            @NotNull Block center,
            @NotNull ItemStack tool,
            @NotNull SteamEquipmentProfile profile,
            boolean customDrops
    ) {
        if (!org.bukkit.Tag.LOGS.isTagged(center.getType())) return 0;

        SteamEquipmentMaterial mat = profile.material();
        final int maxLogs = 128;
        java.util.Set<Block> visited = new java.util.HashSet<>();
        java.util.Deque<Block> queue = new java.util.ArrayDeque<>();
        visited.add(center);
        queue.add(center);
        int affected = 0;

        while (!queue.isEmpty()) {
            Block b = queue.poll();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) continue;
                        Block n = b.getRelative(dx, dy, dz);
                        if (visited.contains(n) || !org.bukkit.Tag.LOGS.isTagged(n.getType())) continue;
                        visited.add(n);

                        double extraCost = STEAM_PER_EXTRA_RANGE_BLOCK * mat.toolSteamCostMultiplier();
                        if (!SteamCharge.tryRemoveAmount(tool, extraCost)) return affected;

                        if (customDrops) {
                            handleDrops(player, n, tool, mat);
                            n.setType(Material.AIR);
                        } else {
                            n.breakNaturally(tool);
                        }
                        affected++;
                        if (affected >= maxLogs) return affected;
                        queue.add(n);
                    }
                }
            }
        }
        return affected;
    }

    /**
     * 接管单个方块的掉落：青铜冶炼后掉落，因瓦直接塞入玩家背包（满了则掉落）。
     */
    private void handleDrops(@NotNull Player player, @NotNull Block block,
                             @NotNull ItemStack tool, @NotNull SteamEquipmentMaterial mat) {
        Collection<ItemStack> drops = block.getDrops(tool, player);
        World world = block.getWorld();
        Location loc = block.getLocation().add(0.5, 0.5, 0.5);

        for (ItemStack drop : drops) {
            ItemStack result = drop;
            if (mat == SteamEquipmentMaterial.BRONZE) {
                result = smelt(drop);
            }
            if (mat == SteamEquipmentMaterial.INVAR) {
                // 直接进背包，溢出部分掉落地面
                Map<Integer, ItemStack> overflow = player.getInventory().addItem(result);
                for (ItemStack left : overflow.values()) {
                    world.dropItemNaturally(loc, left);
                }
            } else {
                world.dropItemNaturally(loc, result);
            }
        }
    }

    /** 按冶炼表把矿物原料转换为冶炼产物；无对应映射则原样返回。 */
    private @NotNull ItemStack smelt(@NotNull ItemStack drop) {
        Material smelted = SMELT_MAP.get(drop.getType());
        if (smelted == null) return drop;
        return new ItemStack(smelted, drop.getAmount());
    }

    // ── 耕地 ──────────────────────────────────────────────────────────────────

    private boolean hasTillableAround(@NotNull Block center, int radius) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (canTill(center.getRelative(dx, 0, dz))) return true;
            }
        }
        return false;
    }

    private int tillAround(@NotNull Block center, int radius) {
        int affected = 0;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                Block block = center.getRelative(dx, 0, dz);
                if (!canTill(block)) continue;
                block.setType(Material.FARMLAND);
                affected++;
            }
        }
        return affected;
    }

    private boolean canTill(@NotNull Block block) {
        if (!block.getRelative(BlockFace.UP).getType().isAir()) return false;
        return switch (block.getType()) {
            case COARSE_DIRT, DIRT, DIRT_PATH, GRASS_BLOCK, MYCELIUM, PODZOL, ROOTED_DIRT -> true;
            default -> false;
        };
    }

    // ── 特效 ──────────────────────────────────────────────────────────────────

    /**
     * 命中特效：按材质分级显示不同粒子与音效。
     */
    private void playHitEffects(
            @NotNull Player player,
            @NotNull Entity target,
            @NotNull SteamEquipmentProfile profile
    ) {
        Location location = target.getLocation().add(0.0, 0.7, 0.0);
        switch (profile.material()) {
            case BRASS -> {
                player.getWorld().spawnParticle(org.bukkit.Particle.CRIT,  location, 10, 0.35, 0.35, 0.35, 0.08);
                player.getWorld().spawnParticle(org.bukkit.Particle.CLOUD, location,  6, 0.25, 0.25, 0.25, 0.04);
                player.getWorld().spawnParticle(org.bukkit.Particle.FLAME, location,  4, 0.15, 0.20, 0.15, 0.02);
            }
            case BRONZE -> {
                player.getWorld().spawnParticle(org.bukkit.Particle.CRIT,  location,  8, 0.35, 0.35, 0.35, 0.06);
                player.getWorld().spawnParticle(org.bukkit.Particle.FLAME, location, 14, 0.30, 0.30, 0.30, 0.04);
                player.getWorld().spawnParticle(org.bukkit.Particle.CLOUD, location,  3, 0.20, 0.20, 0.20, 0.02);
            }
            case INVAR -> {
                player.getWorld().spawnParticle(org.bukkit.Particle.CRIT,           location, 12, 0.35, 0.35, 0.35, 0.10);
                player.getWorld().spawnParticle(org.bukkit.Particle.ELECTRIC_SPARK, location, 18, 0.30, 0.30, 0.30, 0.04);
            }
            case TUNGSTEN -> {
                player.getWorld().spawnParticle(org.bukkit.Particle.CRIT,      location, 6, 0.38, 0.38, 0.38, 0.10);
                player.getWorld().spawnParticle(org.bukkit.Particle.WHITE_ASH, location, 4, 0.32, 0.28, 0.32, 0.05);
                player.getWorld().spawnParticle(org.bukkit.Particle.CLOUD,     location, 3, 0.28, 0.28, 0.28, 0.04);
            }
        }
        player.getWorld().playSound(location, org.bukkit.Sound.ENTITY_BREEZE_SHOOT, 0.45f, effectPitch(profile.material()));
    }

    /**
     * 挖掘/耕地特效：按材质分级显示不同粒子。
     */
    private void playBreakEffects(@NotNull Block block, @NotNull SteamEquipmentProfile profile, int affected) {
        if (affected <= 0 && profile.part() != SteamEquipmentPart.HOE) return;
        Location location = block.getLocation().add(0.5, 0.5, 0.5);
        int base = 4 + affected * 2;
        switch (profile.material()) {
            case BRASS ->
                block.getWorld().spawnParticle(org.bukkit.Particle.CLOUD, location, base,
                        0.45, 0.35, 0.45, 0.035);
            case BRONZE -> {
                block.getWorld().spawnParticle(org.bukkit.Particle.CLOUD, location, base,
                        0.45, 0.35, 0.45, 0.030);
                block.getWorld().spawnParticle(org.bukkit.Particle.FLAME, location, base / 2,
                        0.30, 0.20, 0.30, 0.020);
            }
            case INVAR -> {
                block.getWorld().spawnParticle(org.bukkit.Particle.CLOUD,           location, base,
                        0.45, 0.35, 0.45, 0.030);
                block.getWorld().spawnParticle(org.bukkit.Particle.ELECTRIC_SPARK, location, base,
                        0.40, 0.30, 0.40, 0.025);
            }
            case TUNGSTEN -> {
                block.getWorld().spawnParticle(org.bukkit.Particle.CLOUD,     location, base + 4,
                        0.55, 0.45, 0.55, 0.040);
                block.getWorld().spawnParticle(org.bukkit.Particle.WHITE_ASH, location, base,
                        0.45, 0.35, 0.45, 0.035);
            }
        }
        block.getWorld().playSound(location, org.bukkit.Sound.BLOCK_PISTON_CONTRACT, 0.45f, effectPitch(profile.material()));
    }

    private float effectPitch(@NotNull SteamEquipmentMaterial material) {
        return switch (material) {
            case INVAR -> 1.45f;
            case TUNGSTEN -> 0.85f;
            case BRONZE -> 1.20f;
            case BRASS -> 1.30f;
        };
    }

    private void applyDepletedFatigue(@NotNull Player player) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, 30, 1, true, false, false));
    }

    private void setUsedHand(@NotNull Player player, EquipmentSlot hand, @NotNull ItemStack stack) {
        if (hand == EquipmentSlot.OFF_HAND) {
            player.getInventory().setItemInOffHand(stack);
        } else {
            player.getInventory().setItemInMainHand(stack);
        }
    }

    @Override
    public void onTick(@NotNull Player player) {
        // 手持有汽的蒸汽工具时给予被动增益：镐/铲 → 急迫；斧 → 力量（等级与时长随材质提升）。
        SteamEquipmentProfile profile = SteamEquipmentProfile.fromKey(getKey());
        if (profile == null) return;
        ItemStack held = player.getInventory().getItemInMainHand();
        if (SteamEquipmentProfile.fromStack(held) != profile) return;
        if (!SteamCharge.isPowered(held)) return;

        int tier = profile.material().ordinal();   // 0黄铜 1青铜 2因瓦 3钨
        int duration = 40 + tier * 15;             // 2s~4.25s，手持期间每 0.5s 续期
        switch (profile.part()) {
            case PICKAXE, SHOVEL ->
                    applyHoldBuff(player, PotionEffectType.HASTE, tier >= 2 ? tier - 1 : 0, duration);
            case AXE ->
                    applyHoldBuff(player, PotionEffectType.STRENGTH, tier >= 2 ? 1 : 0, duration);
            default -> { }
        }
    }

    private void applyHoldBuff(@NotNull Player player, @NotNull PotionEffectType type, int amplifier, int duration) {
        player.addPotionEffect(new PotionEffect(type, duration, amplifier, true, false, true));
    }

    @Override
    public long getBaseTickInterval() {
        return 1L;
    }
}
