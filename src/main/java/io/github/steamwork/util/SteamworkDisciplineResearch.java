package io.github.steamwork.util;

import io.github.pylonmc.rebar.item.research.Research;
import io.github.steamwork.SteamworkResearches;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 学科门控研究映射表。
 *
 * <p>定义哪些研究必须通过蒸汽科研接口用学科点数解锁。
 * 这些研究的 {@link Research#getCost()} 为 {@code null}，
 * 无法通过全局研究点在 Rebar 指南中解锁，
 * 必须在蒸汽科研接口 GUI 中消耗对应学科点数。</p>
 *
 * <p>需求量设计依据（样本每次产 5-7 点）：<br>
 * 生物 30pt ≈ 6 次有机样本<br>
 * 化学 40-100pt ≈ 7-17 次流体样本<br>
 * 材料 60-80pt ≈ 12-16 次矿物样本<br>
 * 精密 150pt ≈ 21 次冶金样本</p>
 */
public final class SteamworkDisciplineResearch {

    private SteamworkDisciplineResearch() {
        throw new AssertionError("Utility class");
    }

    /** 一项学科研究的解锁需求。 */
    public record Requirement(
            @NotNull SteamworkDiscipline discipline,
            int points
    ) {}

    /**
     * 有序映射：研究 → 学科需求（顺序决定科研接口中的显示顺序）。
     * 延迟初始化，{@link SteamworkResearches} 注册完成后才能引用。
     */
    private static volatile Map<Research, Requirement> requirements;

    public static @NotNull Map<Research, Requirement> getRequirements() {
        if (requirements == null) {
            synchronized (SteamworkDisciplineResearch.class) {
                if (requirements == null) {
                    requirements = buildRequirements();
                }
            }
        }
        return requirements;
    }

    private static @NotNull Map<Research, Requirement> buildRequirements() {
        Map<Research, Requirement> map = new LinkedHashMap<>();
        // ── 化学 ────────────────────────────────────────────────────────────
        // 注：CHEMISTRY_BASIC_RESEARCH（解锁科研接口本身）不在此列，
        //     必须保持全局研究点可解锁，否则玩家无法建造接口来获取学科点数。
        map.put(SteamworkResearches.CHEMISTRY_HEATING_CHAMBER,
                new Requirement(SteamworkDiscipline.CHEMISTRY, 60));
        map.put(SteamworkResearches.CHEMISTRY_DISTILLATION,
                new Requirement(SteamworkDiscipline.CHEMISTRY, 100));
        // ── 材料学 ──────────────────────────────────────────────────────────
        map.put(SteamworkResearches.MATERIAL_ADVANCED_INGOTS,
                new Requirement(SteamworkDiscipline.MATERIAL, 80));
        map.put(SteamworkResearches.MATERIAL_ADVANCED_BOILERS,
                new Requirement(SteamworkDiscipline.MATERIAL, 60));
        // ── 精密工程 ─────────────────────────────────────────────────────────
        map.put(SteamworkResearches.PRECISION_ADVANCED_AUTOMATION_1,
                new Requirement(SteamworkDiscipline.PRECISION, 150));
        return map;
    }

    /** 返回某研究的学科需求；若无学科门控则返回 {@code null}。 */
    public static @Nullable Requirement requirementFor(@NotNull Research research) {
        return getRequirements().get(research);
    }

    /** 检查玩家是否满足解锁条件（有足够学科点数且尚未解锁）。 */
    public static boolean canUnlock(@NotNull Player player, @NotNull Research research) {
        if (research.isResearchedBy(player)) return false;
        Requirement req = requirementFor(research);
        if (req == null) return false;
        return req.discipline().getPoints(player) >= req.points();
    }

    /**
     * 解锁研究并扣除对应学科点数。
     *
     * @return 解锁成功返回 {@code true}；已解锁或点数不足返回 {@code false}。
     */
    public static boolean unlock(@NotNull Player player, @NotNull Research research) {
        if (research.isResearchedBy(player)) return false;
        Requirement req = requirementFor(research);
        if (req == null) return false;
        if (!req.discipline().spendPoints(player, req.points())) return false;
        research.addTo(player, true, true);
        return true;
    }
}
