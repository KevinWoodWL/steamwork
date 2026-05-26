package io.github.steamwork.content.machines.upgrade;

public enum UpgradeType {
    ENERGY_SAVE,
    AUTO_INPUT,
    AUTO_OUTPUT,
    BULK,
    /** 涡轮专用：每个模组 +1 扫描半径。 */
    RANGE,
    /** 涡轮专用：每个模组 +1 boost tick / 操作。 */
    BOOST,
    /** 加工机专用：插入后机器可执行对应的 Pylon 配方。 */
    PYLON_COMPAT
}
