package io.github.steamwork.content.machines;

import io.github.pylonmc.rebar.block.RebarBlock;
import io.github.pylonmc.rebar.block.interfaces.DirectionalRebarBlock;
import io.github.pylonmc.rebar.block.interfaces.TickingRebarBlock;
import io.github.pylonmc.rebar.block.context.BlockCreateContext;
import io.github.pylonmc.rebar.config.adapter.ConfigAdapter;
import io.github.pylonmc.rebar.i18n.RebarArgument;
import io.github.pylonmc.rebar.item.RebarItem;
import io.github.pylonmc.rebar.waila.WailaDisplay;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.AxisAngle4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;
import java.util.UUID;

import static io.github.steamwork.util.SteamworkUtils.steamworkKey;

/**
 * 蒸汽弹射器 —— 气动货运站多方块结构的上方视觉组件。
 * 不持有任何流体或物品槽，仅负责播放抛物线弹射动画。
 * 蒸汽消耗、目标管理全部由下方的货运站承担。
 */
public class SteamCatapult extends RebarBlock implements
        DirectionalRebarBlock,
        TickingRebarBlock {

    private static final NamespacedKey MARKER_KEY = steamworkKey("catapult_projectile");

    private final int tickInterval = getSettings().getOrThrow("tick-interval", ConfigAdapter.INTEGER);

    private boolean lastActive = false;

    public static class Item extends RebarItem {
        public Item(@NotNull ItemStack stack) { super(stack); }

        @Override
        public @NotNull List<RebarArgument> getPlaceholders() {
            return List.of();
        }
    }

    @SuppressWarnings("unused")
    public SteamCatapult(@NotNull org.bukkit.block.Block block, @NotNull BlockCreateContext context) {
        super(block, context);
        setFacing(context.getFacing());
        setTickInterval(tickInterval);
    }

    @SuppressWarnings("unused")
    public SteamCatapult(@NotNull org.bukkit.block.Block block, @NotNull PersistentDataContainer pdc) {
        super(block, pdc);
        try { getFacing(); } catch (IllegalStateException e) { setFacing(BlockFace.SOUTH); }
    }

    @Override
    public void tick() {
        // 弹射器本身不做任何事，由下方货运站驱动
    }

    /**
     * 播放抛物线弹射动画；动画完成时执行 onLand 回调（主线程）。
     */
    public void launch(@NotNull Location from, @NotNull Location to,
                       @NotNull ItemStack item, int flightTicks, @NotNull Runnable onLand) {
        double dist = from.distance(to);

        setActive(true);

        from.getWorld().playSound(from, Sound.ENTITY_LLAMA_SPIT, 0.8f, 0.6f);
        from.getWorld().spawnParticle(Particle.CLOUD, from, 8, 0.15, 0.05, 0.15, 0.06);

        ItemDisplay display = from.getWorld().spawn(from, ItemDisplay.class, d -> {
            d.setItemStack(item);
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.GROUND);
            d.setGravity(false);
            d.setInvulnerable(true);
            d.setPersistent(false);
            d.setTransformation(new Transformation(
                    new Vector3f(-0.15f, -0.15f, -0.15f),
                    new Quaternionf(new AxisAngle4f(0, 0, 1, 0)),
                    new Vector3f(0.3f, 0.3f, 0.3f),
                    new Quaternionf(new AxisAngle4f(0, 0, 1, 0))
            ));
            d.getPersistentDataContainer().set(MARKER_KEY, PersistentDataType.BYTE, (byte) 1);
        });

        UUID displayId = display.getUniqueId();
        double peakHeight = Math.max(2.0, dist * 0.3);
        int total = flightTicks;

        new BukkitRunnable() {
            int tick = 0;

            @Override
            public void run() {
                Entity e = Bukkit.getEntity(displayId);
                if (e == null) { cancel(); return; }
                ItemDisplay d = (ItemDisplay) e;

                tick++;
                double t = (double) tick / total;
                double x = from.getX() + (to.getX() - from.getX()) * t;
                double z = from.getZ() + (to.getZ() - from.getZ()) * t;
                double baseY = from.getY() + (to.getY() - from.getY()) * t;
                double arcY = baseY + peakHeight * 4 * t * (1 - t);

                Location cur = new Location(from.getWorld(), x, arcY, z);
                d.teleport(cur);

                if (tick % 2 == 0) {
                    cur.getWorld().spawnParticle(Particle.CRIT, cur, 1, 0, 0, 0, 0);
                }

                if (tick >= total) {
                    d.remove();
                    cancel();
                    to.getWorld().playSound(to, Sound.BLOCK_DISPENSER_DISPENSE, 0.6f, 1.4f);
                    to.getWorld().spawnParticle(Particle.POOF, to, 6, 0.2, 0.2, 0.2, 0.02);
                    to.getWorld().spawnParticle(Particle.ITEM, to, 4, 0.15, 0.15, 0.15, 0.05, item);
                    onLand.run();
                    setActive(false);
                }
            }
        }.runTaskTimer(io.github.steamwork.Steamwork.getInstance(), 0L, 1L);
    }

    private void setActive(boolean active) {
        if (lastActive != active) {
            lastActive = active;
            refreshBlockTextureItem();
        }
    }

    @Override
    public @NotNull java.util.Map<String, kotlin.Pair<String, Integer>> getBlockTextureProperties() {
        var props = super.getBlockTextureProperties();
        props.put("active", new kotlin.Pair<>(Boolean.toString(lastActive), 2));
        return props;
    }

    @Override
    public @Nullable WailaDisplay getWaila(@NotNull Player player) {
        return WailaDisplay.of(getDefaultWailaTranslationKey().arguments(
                RebarArgument.of("state", Component.translatable(
                        "steamwork.state." + (lastActive ? "active" : "idle")))
        ));
    }
}
