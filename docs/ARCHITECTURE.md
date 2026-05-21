# Steamwork 架构

Steamwork 分成四个主要关注点：

- Pylon/Rebar 附属：物品身份、方块行为、GUI、配方、配置和语言。
- Pylon 联动：金属、板材、流体、加工链、指南/研究风格。
- MonolithLib 联动：后续大型多方块蓝图、预览和结构校验。
- 资源包：物品图标、方块显示模型和机器状态视觉。

## 里程碑

1. 可编译的附属骨架，包含少量注册物品和基础方块。
2. 明确 Pylon 强依赖和联动规则。
3. 资源包工作区与第一批自定义图标/模型。
4. 实装 `CopperBoiler`，支持水、蒸汽、热源和压力状态。
5. 添加蒸汽管道与压力阀。
6. 添加第一个 MonolithLib 支持的大型结构，优先考虑工业锅炉。

## 模型键规则

- 基础物品使用 `steamwork:<item_id>`。
- 方块纹理实体使用 `steamwork:<block_id>`，并追加 Rebar 提供的方块状态字符串。
- 机器组件使用 `steamwork:<machine_id>:<part>`。
- 动态变体追加状态后缀，例如 `:active=true` 或 `:pressure=high`。
