package io.github.steamwork;

import io.github.pylonmc.rebar.entity.RebarEntity;
import io.github.steamwork.content.robot.SteamRobot;
import org.bukkit.entity.CopperGolem;

/**
 * 实体注册中枢 —— 仿 {@link SteamworkBlocks} 模式，集中登记所有 Rebar 实体 schema。
 *
 * <p>在 {@code Steamwork.onEnable} 中于 fluids/items/blocks 之后调用：实体可能引用方块/物品/流体。</p>
 */
public final class SteamworkEntities {

    private SteamworkEntities() {
        throw new AssertionError("Utility class");
    }

    public static void initialize() {
        RebarEntity.register(SteamworkKeys.STEAM_ROBOT, CopperGolem.class, SteamRobot.class, true);
    }
}
