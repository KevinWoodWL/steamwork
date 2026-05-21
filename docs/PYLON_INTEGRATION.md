# Pylon 联动

Steamwork 应当像 Pylon 的扩展内容，而不是一条彼此割裂的平行科技线。

## 依赖方向

```text
Rebar
  -> Pylon
      -> Steamwork
```

Steamwork 的 Bukkit 插件元数据强依赖 `Rebar` 和 `Pylon`。只要本地 Pylon jar 已经构建，
编译期代码就可以直接引用 Pylon 类。

## 复用的 Pylon 系统

- 金属与板材：按需求复用铜、青铜、钢、锡、钯等材料。
- 流体处理：水输入、流体缓冲、流体舱口、管道约定和流体条。
- 燃料阶段：前期煤/木炭，后期植物油和生物柴油。
- 机器文本风格：物品名称、lore、WAILA、GUI 标签。
- 研究/指南约定：Steamwork 的页面应接在 Pylon 现有机器进度旁边。

## 优先复用目标

- `PylonKeys.COPPER_SHEET`
- `PylonKeys.BRONZE_SHEET`
- `PylonKeys.STEEL_SHEET`
- `PylonKeys.FLUID_INPUT_HATCH`
- `PylonKeys.FLUID_OUTPUT_HATCH`
- `PylonFluids.WATER`
- `PylonFluids.PLANT_OIL`
- `PylonFluids.BIODIESEL`

在新增 Steamwork 重复物之前，优先把这些作为配方材料或机器输入。

## 设计规则

- 如果 Pylon 已经覆盖通用铜/钢加工，不再重复添加一条相同加工线。
- 新的 Steamwork 物品应解锁新的压力或蒸汽行为。
- 高级蒸汽机器应需要 Pylon 的钢、耐火材料或柴油时代组件。
- MonolithLib 留给工业锅炉、蒸汽涡轮、压力塔等大型结构。
