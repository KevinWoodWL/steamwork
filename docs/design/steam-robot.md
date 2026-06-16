# 蒸汽机器人（Steam Robot）设计方案

> 目标版本：v0.5.0 主线功能。
> 形态结论：**会移动的实体机器人**，集成进 Steamwork（不单独拆插件），代码独立在 `content/robot/` 包内、留好日后抽取的缝。

## 一、目标与范围

一台烧加压蒸汽驱动、能在世界里自主行动的机器人实体，能力分四类：

1. **基础作业**：采矿、砍树（破坏指定区域内的方块并回收掉落）
2. **物流补料**：把物品从存储搬运到产线入口 / 机器输入
3. **巡逻**：在设定的工作区 / 路径点之间自主移动
4. **联动**：与 Steamwork 产线、蒸汽逻辑，以及 Pylon 本体机器交互

核心主题约束：**一切行动消耗加压蒸汽**（与 v0.4.7 产线耗汽驱动同一套理念）。没汽就回坞充能 / 停摆。

## 二、技术地基（已核实 Rebar 0.42 / Paper 1.21.11 能力）

| 能力 | Rebar 提供 | 落地点 |
|---|---|---|
| 实体抽象 + 持久化 | `RebarEntity<E: Entity>` + `EntityStorage` | 包装任意 Bukkit 实体，PDC 序列化，区块加载自动恢复 |
| 周期逻辑 | `TickingRebarEntity`（`tick()` + `setTickInterval()`，协程调度，可 async） | 机器人主循环（作业状态机） |
| 寻路 | `PathfindRebarEntityHandler` + Paper `Mob#getPathfinder().moveTo(loc)` | 包装真实 Mob 即获得原版 A* 导航 |
| 移动事件 | `MoveRebarEntityHandler`（onMove/onJump/...） | 行进中触发逻辑（如踩点、耗汽） |
| 交互 | `InteractRebarEntityHandler`、`getPickItem`、`getWaila` | 右键开 GUI / 中键拾取 / 注视显示状态 |
| 配置 | `getSettings()` 读 `settings/<key>.yml` | 平衡数值（移动耗汽、破坏耗汽、速度、工作半径上限） |

⚠️ **关键差异提醒**：`RebarEntity` **没有 place 构造器**——实体由你自己 `spawn` 再 `EntityStorage.add(...)`，但必须保留一个 `(E entity)` 的 load 构造器供恢复。这点和 `RebarBlock` 不同。

> PylonDroid 对本功能**几乎无参考价值**（它是可编程方块、无实体/移动）。唯一可借鉴的是"用指令集给机器人编程"这一创意，且属可选的后期增强层（见 Phase 4）。

## 三、架构总览

### 3.1 实体形态（已定：铜傀儡 CopperGolem）

- **底座 = 铜傀儡 `CopperGolem`**（Paper 1.21.11 原生实体，`org.bukkit.entity.CopperGolem`）。
  直接用它的模型与动作，省掉自定义模型/ItemDisplay 叠加层。
  - 动作状态 `setGolemState(State)`：`IDLE` / `GETTING_ITEM` / `GETTING_NO_ITEM` / `DROPPING_ITEM` / `DROPPING_NO_ITEM`
    ——搬运作业时正好用「拿取/放下」动画，主题契合。
  - **固定为已涂蜡 + 未氧化亮铜**：`setWeatheringState(UNAFFECTED)` + `setOxidizing(Oxidizing.waxed())`，
    外观恒定、不随时间氧化（新生成与重载恢复都经 `configure()` 重新锁定）。
- 设 `setPersistent(true)` + `setRemoveWhenFarAway(false)` 防止 despawn。
- 移动用原版寻路：`CopperGolem`（→ Mob）的 `getPathfinder().moveTo(loc, speed)`。
- `RebarEntity<CopperGolem>` 包装；`EntityStorage` 托管持久化。

> 铜傀儡自带的原版 AI（捡物入箱、氧化）后期需按需抑制，避免与机器人作业目标冲突。

### 3.2 核心循环（作业状态机）

`SteamRobot.tick()` 跑一个状态机：

```
IDLE → (有任务?) → 检查燃料 → 缺汽? → RETURNING(回坞) → CHARGING
                              ↓ 够汽
                          MOVING(寻路到目标) → WORKING(采矿/砍树/存取) → 下一个目标
```

- 每个状态按 `tickInterval`（如 5 tick）推进，避免每 game-tick 重算寻路。
- 移动耗汽按"行进距离 / 每段"扣；作业耗汽按"每次破坏 / 每次搬运"扣（复用 v0.4.7 的扣费思路）。

### 3.3 燃料系统（复用 SteamCanister）

机器人内置一个 `SteamCanister` 槽位（与装备同套能量来源）。
- 行动扣汽；汽尽 → 返回**绑定的充汽舱**补充 → 满则继续。
- WAILA / GUI 显示剩余蒸汽与当前状态。

### 3.4 充能：复用现有蒸汽充汽舱（不新建坞）

**已定方案**：不另做充能坞，直接复用现有的 **蒸汽充汽舱 `SteamChargingChamber`**（即给装备充汽那台）。

充汽舱现状（`content/machines/SteamChargingChamber.java`）：
- 多方块结构，`tick()` 用 `getNearbyEntities(BoundingBox.of(core).expand(1.0))` 扫描，
  目前**只对 `Player`** 调 `chargePlayer`，给背包里有蒸汽储能的物品（`SteamCharge.hasSteamStorage`）充汽，
  并放白色 `CLOUD` 粒子 + `BLOCK_FIRE_EXTINGUISH` 音效 + ActionBar 进度条。
- 结构四重对称，四侧（东/南/西/北）各有一段倒置铜楼梯，天然有四个「入口方向」。

落地改动（P1）：
1. **充汽舱识别机器人**：`tick()` 扫描时除 `Player` 外，也识别站内的 `SteamRobot`（经 `EntityStorage.get(entity)`），
   给其内置 `SteamCanister` 充汽，复用同一套粒子/音效（机器人踏入即「像玩家一样」弹粒子与声音）。
2. **绑定 + 四方向**：机器人与一台充汽舱建立绑定（记充汽舱主方块坐标 + 选定的一个朝向 N/E/S/W）。
   缺汽时机器人寻路回到该充汽舱在所绑定方向的**踏入点**站定充汽，充满再返回作业。
3. 绑定关系在机器人 PDC 持久化；充汽舱被破坏则解除绑定、机器人转为缺汽停摆。

### 3.5 包结构

```
content/robot/
  SteamRobot.java            // RebarEntity<Mob>, TickingRebarEntity, 状态机
  SteamRobotDock.java        // 充能坞/控制站方块
  job/
    RobotJob.java            // 任务接口（execute/选目标/耗汽量）
    MiningJob.java           // 采矿/砍树
    HaulJob.java             // 补料/搬运
    PatrolJob.java           // 巡逻
  render/RobotDisplay.java   // ItemDisplay 跟随层
SteamworkEntities.java       // 新增注册中枢，仿 SteamworkBlocks 模式，插入 onEnable 初始化顺序
```

## 四、关键设计决策（含推荐）

1. **移动方案** → **推荐混合（Mob 底座 + Display 外观）**。纯 Display 自写寻路成本极高；纯 Mob 外形受限。混合两全。
2. **控制方式** → **v1 用坞 GUI 配置任务 + 工作区**（点击/框选两角或半径）。可编程（PISA 式）层留作 Phase 4 可选增强，不在 v1 范围。
3. **破坏 Pylon 方块的正确姿势** → **必须走 Pylon/Rebar 的破坏逻辑**，不能 `setType(AIR)`，否则 Pylon 自定义方块掉落丢失。采矿作业要识别目标是否 Rebar 方块并调用其破坏 API。
4. **防破坏越界 / 防破坏保护区** → 工作区强制锚定在坞附近、半径有 `settings` 硬上限；破坏前校验区域 + 触发 `BlockBreakEvent` 让保护类插件可拦截。
5. **跨区块 / 卸载** → 底座 Mob 随区块卸载，`RebarEntity` + `EntityStorage` 负责恢复；任务进度写进 PDC（`write()`），重载续作。附属 ItemDisplay 在 `onUnload` 清理、加载时重建。
6. **多机器人性能** → 错开 tick、寻路结果缓存、async 仅用于纯计算（不碰世界）；坞限制管辖机器人数量。

## 五、与产线 / Pylon 联动

- **补料进产线**：机器人把物品投入 `ProductionLineInlet` 的原料缓存（入口已有"从相邻漏斗拉取"逻辑，机器人可作为另一种投递源）。
- **取产线产出**：从 `ProductionLineOutlet` 缓存取走成品送往存储。
- **蒸汽逻辑联动**：坞可读/写蒸汽信号（缺汽、作业中、空闲），接入你的蒸汽逻辑系统做条件控制。
- **Pylon 本体**：采矿掉落经 Pylon 破坏逻辑获得正确产物；补料目标可以是 Pylon 原版机器的输入。

## 六、分阶段路线图（诚实预估：跨多个小版本）

| 阶段 | 内容 | 产出 | 状态 |
|---|---|---|---|
| **P0 验证 spike** | spawn 铜傀儡 → 包成 `RebarEntity` → `tick()` → `pathfinder.moveTo()` → 重载恢复 + 潜行右键拆除 | 地基跑通 | ✅ 完成 |
| **P1a 蒸汽燃料** | 内置蒸汽储量、行动耗汽、缺汽停摆、WAILA 蒸汽条 | 会耗汽的机器人 | ✅ 完成 |
| **P1b 充汽舱充能** | 充汽舱识别机器人并充其内置蒸汽（粒子/声音同玩家）、低汽寻路回充、首充自动绑定 | 能自维持的机器人 | ✅ 完成 |
| **P1c 任务模式** | JobMode（巡逻/采矿/砍树）+ 右键循环切换（轻量控制；完整 InvUI 窗口 GUI 后置） | 可配置的机器人 | ✅ 完成 |
| **P2 采矿/砍树** | 工作区扫描、寻路破坏原版矿石/原木、回收到 home 旁容器、破坏耗汽、保护事件门控 | 第一种真实作业 | ✅ 完成 |
| **P3 补料/搬运** | HAUL 模式：点选绑定取货容器 + 送货点（容器/产线入口），往返搬运、携带缓存持久化、产线入口直接补料 | 物流作业 | ✅ 完成 |
| **P4 深度联动+打磨** | Pylon 本体交互、多机协作、铜傀儡放下动画、完整窗口 GUI、（可选）可编程指令层 | 完善与扩展 | 待办 |

> 燃料表示：用机器人内置的蒸汽 double（容量来自 settings），非可插拔 `SteamCanister` 物品——
> 「像玩家一样充汽」指的是<b>充能体验</b>（粒子/声音）。可插拔罐槽留作后期可选增强。
>
> P2 安全约束：只破坏<b>原版</b>矿石/原木，<b>绝不破坏任何 Rebar 方块</b>（机器安全）；破坏前触发
> `EntityChangeBlockEvent` 让保护类插件可拦截。四方向充汽绑定 / Pylon 自定义方块破坏留作后续。
>
> P2 行为细节：① 铜傀儡是 brain 制 AI，用 `setAware(false)` 关掉自主游荡（保留显式 `moveTo` 导航）；
> ② 接近目标后 flood-fill 锁定<b>整棵树 / 整条矿脉</b>（连通同类方块）逐个采伐，再扫描下一处；
> ③ 破坏需累积 `break-ticks` 次进度（挥臂 + 裂纹），<b>非秒砍</b>；④ 右键开 InvUI 控制 GUI。

## 七、待定 / 风险

- **Mob AI 干扰**：需关闭原版目标/攻击/恐慌 AI，只保留导航；确认 Paper 在本版本暴露 `getPathfinder()` 行为符合预期（P0 验证）。
- **掉落归属与丢失**：破坏掉落须立即收进机器人缓存，避免散落/被他人捡走；缓存满时的处理（停工 or 回坞卸货）。
- **刷怪/实体上限**：机器人 Mob 不应计入刷怪逻辑、不被清理插件误删。
- **平衡**：耗汽速率需让"机器人自动化"显著贵于手动，避免破坏游戏节奏。
