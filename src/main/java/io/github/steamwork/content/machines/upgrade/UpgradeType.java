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
    PYLON_COMPAT,
    /** 产线入口专用：安装后自动触发产线内需要手动操作的机器。 */
    AUTO_PRODUCTION,
    /** 机器人控制终端专用：安装后提升可部署机器人上限。 */
    TERMINAL_CAPACITY
}
