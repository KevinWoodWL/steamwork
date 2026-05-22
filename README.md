# Steamwork（蒸汽工坊）

Steamwork 是一个基于 Pylon/Rebar 的蒸汽与压力科技附属。它的目标不是重做
Pylon 已经完成的金属、流体、研究、配方和指南体系，而是在这些基础上扩展锅炉、
压力控制、蒸汽输送和蒸汽驱动机器。

## 当前内容

- `brass_ingot`
- `rubber_gasket`
- `pressure_gauge`
- `copper_boiler`
- `steam_arm`

目前已经有第一条可玩的基础闭环：`copper_boiler` 接收 Pylon 的水并产出
Steamwork 的蒸汽，`steam_arm` 消耗蒸汽，在附近原版库存或 Pylon/Rebar 物流组之间搬运物品。

## 依赖

- Rebar
- Pylon
- MonolithLib 暂时为可选依赖，后续预留给大型蓝图/多方块结构。

## 资源包约定

资源包应依赖稳定的字符串 Custom Model Data。Rebar 的 `ItemStackBuilder.rebar(...)`
会自动写入物品键，例如：

```text
steamwork:brass_ingot
steamwork:copper_boiler
steamwork:steam_arm
```

机器零件和动态方块显示使用稳定后缀：

```text
steamwork:copper_boiler:main
steamwork:copper_boiler:gauge:low
steamwork:copper_boiler:gauge:high
steamwork:steam_arm:active=true
```

这些字符串应当视为资源包公开接口；模型不要依赖 Java 类名。

蒸汽传输直接复用 Pylon 的流体管道，Steamwork 不再提供独立的 `steam_pipe`
或 `pressure_valve` 方块。

## 贡献者

- [@KevinWoodWL](https://github.com/KevinWoodWL) — 项目作者与主要开发者
