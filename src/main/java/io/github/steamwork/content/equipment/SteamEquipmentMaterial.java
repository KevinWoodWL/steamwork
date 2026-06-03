package io.github.steamwork.content.equipment;

public enum SteamEquipmentMaterial {
    //        工具耗汽 护甲耗汽 武器伤害 套装抗性 技能耗汽 冲刺力度 技能冷却  挖掘半径(1=3×3,2=5×5)
    BRASS   (1.00,  1.00,  1.18,   0,      1.0,   1.20, 6000L, 1),
    BRONZE  (0.95,  0.95,  1.16,   0,      1.0,   1.15, 5500L, 1),
    INVAR   (0.75,  0.70,  1.12,   0,      1.0,   1.30, 8000L, 1),
    // 钨套：抗性放大降到 1（套装抗性 II），技能耗汽倍率提至 2.0，冷却 9s，挖掘半径 2（5×5）
    TUNGSTEN(1.35,  1.30,  1.28,   1,      2.0,   1.00, 9000L, 2);

    private final double toolSteamCostMultiplier;
    private final double armorSteamCostMultiplier;
    private final double poweredDamageMultiplier;
    private final int fullSetResistanceAmplifier;
    private final double skillSteamCostMultiplier;
    private final double dashStrength;
    private final long skillCooldownMillis;
    private final int miningRadius;

    SteamEquipmentMaterial(
            double toolSteamCostMultiplier,
            double armorSteamCostMultiplier,
            double poweredDamageMultiplier,
            int fullSetResistanceAmplifier,
            double skillSteamCostMultiplier,
            double dashStrength,
            long skillCooldownMillis,
            int miningRadius
    ) {
        this.toolSteamCostMultiplier = toolSteamCostMultiplier;
        this.armorSteamCostMultiplier = armorSteamCostMultiplier;
        this.poweredDamageMultiplier = poweredDamageMultiplier;
        this.fullSetResistanceAmplifier = fullSetResistanceAmplifier;
        this.skillSteamCostMultiplier = skillSteamCostMultiplier;
        this.dashStrength = dashStrength;
        this.skillCooldownMillis = skillCooldownMillis;
        this.miningRadius = miningRadius;
    }

    public double toolSteamCostMultiplier() {
        return toolSteamCostMultiplier;
    }

    public double armorSteamCostMultiplier() {
        return armorSteamCostMultiplier;
    }

    public double poweredDamageMultiplier() {
        return poweredDamageMultiplier;
    }

    public int fullSetResistanceAmplifier() {
        return fullSetResistanceAmplifier;
    }

    public double skillSteamCostMultiplier() {
        return skillSteamCostMultiplier;
    }

    public double dashStrength() {
        return dashStrength;
    }

    public long skillCooldownMillis() {
        return skillCooldownMillis;
    }

    /** 范围采掘/耕地半径：1 = 3×3，2 = 5×5。 */
    public int miningRadius() {
        return miningRadius;
    }
}
