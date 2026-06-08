package io.github.steamwork.content.equipment;

import io.github.pylonmc.rebar.i18n.RebarArgument;
import io.github.steamwork.util.SteamCharge;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * 蒸汽套装技能监听器。
 *
 * <p>按 F（副手互换键）触发，每种材质技能完全不同：</p>
 * <ul>
 *   <li><b>BRASS  — 蒸汽冲刺：</b>向面朝方向爆发冲刺，附加速度 II 与吸收。</li>
 *   <li><b>BRONZE — 熔炉吐息：</b>以玩家为中心释放火焰环，点燃并伤害半径 5 格内所有生物。</li>
 *   <li><b>INVAR  — 蒸汽护盾：</b>激活 4 秒护盾，每受到 1 HP 伤害从胸甲扣蒸汽以抵消 60% 伤害。</li>
 *   <li><b>TUNGSTEN — 蒸汽地震：</b>以脚底为中心向外喷射冲击波，4 格内击退并造成伤害。</li>
 * </ul>
 */
public final class SteamSetBonusListener implements Listener {

    // ── 通用参数 ──────────────────────────────────────────────────────────────

    /** 每件盔甲的基础技能蒸汽消耗（mB）。 */
    private static final double SKILL_STEAM_COST = 200.0;

    /** 被实体攻击时，每 1 HP 伤害让每件已充汽蒸汽盔甲释放的蒸汽量（mB）。 */
    private static final double ARMOR_HIT_STEAM_PER_HP = 8.0;

    /** 每个玩家的上次技能触发时间（用于冷却）。 */
    private final Map<UUID, Long> lastUse = new HashMap<>();

    // ── INVAR 护盾参数 ────────────────────────────────────────────────────────

    /** 护盾持续时长（毫秒）。 */
    private static final long BARRIER_DURATION_MS   = 4_000L;

    /** 护盾每吸收 1 HP 伤害消耗的蒸汽量（mB）。 */
    private static final double BARRIER_STEAM_PER_HP = 20.0;

    /** 护盾伤害吸收比例（60%）。 */
    private static final double BARRIER_ABSORB_RATIO = 0.60;

    /**
     * 激活中的护盾过期时间戳（ms）。
     * 空 = 护盾未激活；非空 = 护盾有效期止此时刻。
     */
    private final Map<UUID, Long> barrierExpiry = new HashMap<>();

    // ── 技能触发（F 键）─────────────────────────────────────────────────────

    @EventHandler
    public void onSwapHands(@NotNull PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        PlayerInventory inv = player.getInventory();

        // ① 检测是否穿戴同材质全套蒸汽盔甲且均有蒸汽
        SteamEquipmentMaterial material = detectFullPoweredSet(inv);
        if (material == null) return;

        // ② 每件盔甲按材质倍率扣蒸汽
        double cost = SKILL_STEAM_COST * material.skillSteamCostMultiplier();
        if (!hasSteamOnAllPieces(inv, material, cost)) return;

        // ③ 冷却检测
        long now = System.currentTimeMillis();
        Long last = lastUse.get(player.getUniqueId());
        if (last != null && now - last < material.skillCooldownMillis()) {
            event.setCancelled(true);
            double remainingSeconds = (material.skillCooldownMillis() - (now - last)) / 1000.0;
            player.sendActionBar(Component.translatable(
                    "steamwork.message.steam_set.skill_cooldown",
                    RebarArgument.of("seconds", Component.text(
                            String.format(Locale.ROOT, "%.1fs", remainingSeconds),
                            NamedTextColor.AQUA))));
            return;
        }

        event.setCancelled(true); // 阻止实际互换副手
        lastUse.put(player.getUniqueId(), now);

        // ④ 按材质分派技能
        switch (material) {
            case BRASS    -> executeDash(player, inv, material, cost);
            case BRONZE   -> executeFireNova(player, inv, material, cost);
            case INVAR    -> activateSteamBarrier(player, inv, material, cost);
            case TUNGSTEN -> executeSteamQuake(player, inv, material, cost);
        }
    }

    // ── BRASS：蒸汽冲刺 ──────────────────────────────────────────────────────

    /**
     * 向面朝方向爆发冲刺，附加短暂速度 II 与吸收 II。
     */
    private void executeDash(
            @NotNull Player player,
            @NotNull PlayerInventory inv,
            @NotNull SteamEquipmentMaterial material,
            double cost
    ) {
        consumeFromAllPieces(inv, material, cost);

        Vector dir = player.getLocation().getDirection().normalize()
                .multiply(material.dashStrength());
        dir.setY(Math.max(0.35, dir.getY()));
        player.setVelocity(dir);
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED,      60, 1, true, false, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 200, 1, true, false, true));

        Location loc = player.getLocation();
        player.getWorld().spawnParticle(Particle.CLOUD, loc, 30, 0.3, 0.1, 0.3, 0.1);
        player.getWorld().playSound(loc, Sound.ENTITY_BREEZE_SHOOT, 1.0f, 1.2f);
        player.sendActionBar(Component.translatable("steamwork.message.steam_set.dash"));
    }

    // ── BRONZE：熔炉吐息 ──────────────────────────────────────────────────────

    /**
     * 以玩家为圆心释放环形火焰，点燃半径 5 格内所有生物并造成伤害。
     * 不移动玩家，纯 AOE。
     */
    private void executeFireNova(
            @NotNull Player player,
            @NotNull PlayerInventory inv,
            @NotNull SteamEquipmentMaterial material,
            double cost
    ) {
        consumeFromAllPieces(inv, material, cost);

        Location center = player.getLocation();

        // 点燃并伤害周围生物
        for (Entity entity : player.getNearbyEntities(5, 3, 5)) {
            if (!(entity instanceof LivingEntity le) || le.isDead()) continue;
            le.setFireTicks(Math.max(le.getFireTicks(), 120)); // 6 秒
            le.damage(4.0, player);
        }

        // 向外扩散的环形火焰粒子
        for (double r = 0.5; r <= 5.0; r += 0.5) {
            for (double angle = 0; angle < 360; angle += 12) {
                double x = Math.cos(Math.toRadians(angle)) * r;
                double z = Math.sin(Math.toRadians(angle)) * r;
                center.getWorld().spawnParticle(
                        Particle.FLAME, center.clone().add(x, 0.8, z),
                        1, 0.05, 0.15, 0.05, 0.015);
            }
        }
        center.getWorld().spawnParticle(Particle.FLAME,  center.clone().add(0, 1, 0), 40, 0.6, 0.5, 0.6, 0.08);
        center.getWorld().spawnParticle(Particle.CLOUD,  center.clone().add(0, 1, 0), 10, 0.4, 0.3, 0.4, 0.04);
        center.getWorld().playSound(center, Sound.ENTITY_BLAZE_SHOOT, 1.0f, 0.7f);

        player.sendActionBar(Component.translatable("steamwork.message.steam_set.fire_nova"));
    }

    // ── INVAR：蒸汽护盾 ───────────────────────────────────────────────────────

    /**
     * 激活 4 秒蒸汽护盾：护盾有效期间，每受到 1 HP 伤害扣除胸甲 20 mB 蒸汽，抵消 60% 伤害。
     * 蒸汽耗尽则护盾提前破碎。
     */
    private void activateSteamBarrier(
            @NotNull Player player,
            @NotNull PlayerInventory inv,
            @NotNull SteamEquipmentMaterial material,
            double cost
    ) {
        consumeFromAllPieces(inv, material, cost);

        long expiry = System.currentTimeMillis() + BARRIER_DURATION_MS;
        barrierExpiry.put(player.getUniqueId(), expiry);

        Location loc = player.getLocation().add(0, 1, 0);
        player.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, loc, 60, 0.5, 0.7, 0.5, 0.05);
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.8f, 1.3f);

        player.sendActionBar(Component.translatable("steamwork.message.steam_set.barrier_activate"));
    }

    /**
     * 护盾吸收逻辑：当玩家受伤时，若护盾有效，消耗胸甲蒸汽减伤。
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerDamage(@NotNull EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        double incoming = event.getDamage();
        if (incoming <= 0) return;

        if (event instanceof EntityDamageByEntityEvent) {
            consumeArmorHitSteam(player.getInventory(), incoming);
        }

        Long expiry = barrierExpiry.get(player.getUniqueId());
        if (expiry == null) return;

        if (System.currentTimeMillis() > expiry) {
            barrierExpiry.remove(player.getUniqueId());
            return;
        }

        ItemStack chest = player.getInventory().getChestplate();
        double steamNeeded = incoming * BARRIER_ABSORB_RATIO * BARRIER_STEAM_PER_HP;

        if (!SteamCharge.hasAtLeast(chest, steamNeeded)) {
            // 护盾蒸汽不足：直接破碎，本次伤害不吸收
            barrierExpiry.remove(player.getUniqueId());
            Location loc = player.getLocation().add(0, 1, 0);
            player.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, loc, 20, 0.5, 0.5, 0.5, 0.1);
            player.getWorld().playSound(player.getLocation(), Sound.BLOCK_GLASS_BREAK, 0.6f, 1.6f);
            player.sendActionBar(Component.translatable("steamwork.message.steam_set.barrier_broken"));
            return;
        }

        // 护盾吸收
        SteamCharge.tryRemoveAmount(chest, steamNeeded);
        player.getInventory().setChestplate(chest);
        event.setDamage(incoming * (1.0 - BARRIER_ABSORB_RATIO));

        Location loc = player.getLocation().add(0, 1, 0);
        player.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, loc, 12, 0.4, 0.4, 0.4, 0.05);
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_HIT, 0.5f, 1.4f);
        player.sendActionBar(Component.translatable(
                "steamwork.message.steam_set.barrier_absorb",
                RebarArgument.of("absorbed", String.format(Locale.ROOT, "%.1f", incoming * BARRIER_ABSORB_RATIO))));
    }

    // ── TUNGSTEN：蒸汽地震 ────────────────────────────────────────────────────

    /**
     * 以玩家脚下为中心，向外喷射冲击波：
     * 4 格球形范围内所有生物受到距离衰减伤害并被强力击退。
     */
    private void executeSteamQuake(
            @NotNull Player player,
            @NotNull PlayerInventory inv,
            @NotNull SteamEquipmentMaterial material,
            double cost
    ) {
        consumeFromAllPieces(inv, material, cost);

        Location center = player.getLocation();
        double radius = 4.5;

        for (Entity entity : player.getNearbyEntities(radius, radius * 0.75, radius)) {
            if (!(entity instanceof LivingEntity le) || le.isDead()) continue;

            double dist = entity.getLocation().distance(center);
            if (dist > radius) continue;

            // 距离衰减伤害：近处最高 18，边缘最低 6
            double damage = 18.0 * Math.max(0, 1.0 - dist / radius);
            le.damage(Math.max(6.0, damage), player);

            // 击退方向：远离玩家，带上扬（强度 1.3，原 1.8，收敛）
            Vector kb = entity.getLocation().toVector()
                    .subtract(center.toVector()).normalize();
            kb.setY(Math.max(kb.getY(), 0.45));
            entity.setVelocity(kb.multiply(1.55));
        }

        // 向外扩散的冲击波粒子环
        for (double r = 0.3; r <= radius; r += 0.4) {
            for (double angle = 0; angle < 360; angle += 8) {
                double x = Math.cos(Math.toRadians(angle)) * r;
                double z = Math.sin(Math.toRadians(angle)) * r;
                center.getWorld().spawnParticle(
                        Particle.WHITE_ASH, center.clone().add(x, 0.1, z),
                        1, 0.05, 0.03, 0.05, 0.01);
                if (r < 1.0) {
                    center.getWorld().spawnParticle(
                            Particle.CLOUD, center.clone().add(x, 0.2, z),
                            1, 0.05, 0.05, 0.05, 0.02);
                }
            }
        }
        center.getWorld().spawnParticle(Particle.CLOUD, center.clone().add(0, 1, 0),
                25, 0.6, 0.4, 0.6, 0.08);
        center.getWorld().playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 0.9f, 0.55f);
        center.getWorld().playSound(center, Sound.BLOCK_PISTON_CONTRACT, 0.7f, 0.6f);

        player.sendActionBar(Component.translatable("steamwork.message.steam_set.quake"));
    }

    // ── 通用辅助 ──────────────────────────────────────────────────────────────

    /**
     * 检测玩家是否穿戴同材质的全套有汽蒸汽盔甲，返回材质枚举；否则返回 null。
     */
    private @Nullable SteamEquipmentMaterial detectFullPoweredSet(@NotNull PlayerInventory inv) {
        SteamEquipmentMaterial material = SteamEquipmentProfile.poweredArmorMaterial(inv.getHelmet());
        if (material == null) return null;
        if (!SteamEquipmentProfile.isPoweredArmor(inv.getChestplate(), material, SteamEquipmentPart.CHESTPLATE)
                || !SteamEquipmentProfile.isPoweredArmor(inv.getLeggings(),   material, SteamEquipmentPart.LEGGINGS)
                || !SteamEquipmentProfile.isPoweredArmor(inv.getBoots(),      material, SteamEquipmentPart.BOOTS)) {
            return null;
        }
        return material;
    }

    /** 受实体攻击时才让穿戴中的蒸汽盔甲释放压力；待机增益不扣汽。 */
    private void consumeArmorHitSteam(@NotNull PlayerInventory inv, double incoming) {
        inv.setHelmet(consumeArmorHitSteam(inv.getHelmet(), SteamEquipmentPart.HELMET, incoming));
        inv.setChestplate(consumeArmorHitSteam(inv.getChestplate(), SteamEquipmentPart.CHESTPLATE, incoming));
        inv.setLeggings(consumeArmorHitSteam(inv.getLeggings(), SteamEquipmentPart.LEGGINGS, incoming));
        inv.setBoots(consumeArmorHitSteam(inv.getBoots(), SteamEquipmentPart.BOOTS, incoming));
    }

    private @Nullable ItemStack consumeArmorHitSteam(
            @Nullable ItemStack stack,
            @NotNull SteamEquipmentPart expectedPart,
            double incoming
    ) {
        if (stack == null || stack.isEmpty()) return stack;

        SteamEquipmentProfile profile = SteamEquipmentProfile.fromStack(stack);
        if (profile == null || profile.part() != expectedPart || !profile.part().isArmor()) return stack;
        if (!SteamCharge.isPowered(stack)) return stack;

        double cost = incoming * ARMOR_HIT_STEAM_PER_HP * profile.material().armorSteamCostMultiplier();
        SteamCharge.removeAmount(stack, cost);
        return stack;
    }

    /** 检查全套四件盔甲是否每件都至少有 {@code cost} mB 蒸汽。 */
    private boolean hasSteamOnAllPieces(
            @NotNull PlayerInventory inv,
            @NotNull SteamEquipmentMaterial material,
            double cost
    ) {
        ItemStack h = inv.getHelmet(), c = inv.getChestplate(),
                  l = inv.getLeggings(), b = inv.getBoots();
        return SteamCharge.hasAtLeast(h, cost)
                && SteamCharge.hasAtLeast(c, cost)
                && SteamCharge.hasAtLeast(l, cost)
                && SteamCharge.hasAtLeast(b, cost);
    }

    /** 从四件盔甲各扣除 {@code cost} mB 蒸汽，并写回背包。 */
    private void consumeFromAllPieces(
            @NotNull PlayerInventory inv,
            @NotNull SteamEquipmentMaterial material,
            double cost
    ) {
        ItemStack h = inv.getHelmet(),     c = inv.getChestplate(),
                  l = inv.getLeggings(),  b = inv.getBoots();
        SteamCharge.tryRemoveAmount(h, cost); inv.setHelmet(h);
        SteamCharge.tryRemoveAmount(c, cost); inv.setChestplate(c);
        SteamCharge.tryRemoveAmount(l, cost); inv.setLeggings(l);
        SteamCharge.tryRemoveAmount(b, cost); inv.setBoots(b);
    }
}
