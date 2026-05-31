package io.github.steamwork.util;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static io.github.steamwork.util.SteamworkUtils.steamworkKey;

/**
 * 蒸汽工坊科研学科系统。
 *
 * <p>每个学科对应独立的点数池，存储在玩家 PDC 中。
 * 学科点数通过蒸汽科研接口分析对应类型样本获得，
 * 用于在科研接口解锁学科专属研究（这些研究的 Rebar cost 为 null，
 * 无法通过全局研究点解锁，必须在科研接口消耗学科点数）。</p>
 *
 * <p>学科 ↔ 样本对应关系：<br>
 * {@link #MATERIAL}  ← 矿物分析样本<br>
 * {@link #BIOLOGY}   ← 有机分析样本<br>
 * {@link #PRECISION} ← 冶金分析样本<br>
 * {@link #CHEMISTRY} ← 流体分析样本</p>
 */
public enum SteamworkDiscipline {

    MATERIAL("material"),
    BIOLOGY("biology"),
    PRECISION("precision"),
    CHEMISTRY("chemistry");

    public final String key;
    private final NamespacedKey pdcKey;

    SteamworkDiscipline(@NotNull String key) {
        this.key = key;
        this.pdcKey = steamworkKey("discipline_" + key);
    }

    // ===== 点数操作 =====

    /** 返回玩家在本学科的累计点数。 */
    public long getPoints(@NotNull Player player) {
        Long val = player.getPersistentDataContainer().get(pdcKey, PersistentDataType.LONG);
        return val != null ? val : 0L;
    }

    /** 向玩家本学科点数池追加点数。 */
    public void addPoints(@NotNull Player player, long amount) {
        if (amount <= 0) return;
        long current = getPoints(player);
        player.getPersistentDataContainer().set(pdcKey, PersistentDataType.LONG, current + amount);
    }

    /**
     * 尝试从玩家本学科点数池扣除点数。
     *
     * @return 扣除成功返回 {@code true}；点数不足返回 {@code false}，不修改数据。
     */
    public boolean spendPoints(@NotNull Player player, long amount) {
        long current = getPoints(player);
        if (current < amount) return false;
        player.getPersistentDataContainer().set(pdcKey, PersistentDataType.LONG, current - amount);
        return true;
    }

    // ===== 工具 =====

    /** 根据 key 字符串查找对应枚举；找不到返回 {@code null}。 */
    public static @Nullable SteamworkDiscipline fromKey(@NotNull String key) {
        for (SteamworkDiscipline d : values()) {
            if (d.key.equals(key)) return d;
        }
        return null;
    }
}
