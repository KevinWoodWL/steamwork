package io.github.steamwork.content.machines;

/**
 * 标识机器属于 Steamwork 精密加工系列，可被精密加工涡轮专门加速。
 * <p>
 * 继承自 {@link SteamBoostable}，因此精密机器同时也能被基础加工涡轮识别，
 * 但精密加工涡轮只扫描实现本接口的机器，实现更精准的分工。
 * <p>
 * 精密系列包括：精密磨坊、精密铸造炉、精密催化反应釜、精密结晶塔、
 * 精密离心机、重型冲击破碎机、液压锻造机、蒸汽装配台。
 */
public interface PrecisionSteamBoostable extends SteamBoostable {
    // 标记接口，无额外方法。
}
