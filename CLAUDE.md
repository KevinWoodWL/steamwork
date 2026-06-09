# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 沟通语言

**始终用中文回复。** 与该仓库相关的所有对话、说明、提交说明与变更日志默认使用中文（代码标识符、API 名保持英文）。

## What this is

Steamwork (蒸汽工坊) is a **Pylon addon built on the Rebar framework** — a Bukkit/Paper plugin for Minecraft adding steam & pressure tech (boilers, steam-driven machines, a pneumatic item-logistics network, production lines, upgrade modules, and steam tools/armor). Package root: `io.github.steamwork`. Plugin main: `io.github.steamwork.Steamwork`.

## Build & run

Uses Gradle (wrapper) + Shadow. **Requires JDK 25** (toolchain auto-selects it — rebar/pylon jars are compiled to class version 69).

```bash
./gradlew shadowJar      # main artifact → build/libs/steamwork-<version>[-yyyyMMdd-HHmm].jar
./gradlew runServer      # launches a test Paper/Purpur server (MC 1.21.11) with rebar+pylon
./gradlew build          # full build
```

- Add `--offline` to skip dependency resolution when iterating locally (common here).
- **SNAPSHOT versions get a `-yyyyMMdd-HHmm` timestamp suffix** on the jar name; release versions do not (see `build.gradle.kts`).
- The plain `jar` task is disabled on purpose — always use `shadowJar`.
- **There is no test suite** (`src/test` does not exist). "Verifying" a change means building and loading the jar in-game.

### Local rebar/pylon dependency

`rebar` and `pylon` are **not on public Maven**. `build.gradle.kts` references local build jars via `compileOnly(files("../rebar/rebar/build/libs/rebar-1.0.0-SNAPSHOT.jar"))` and `../pylon/...`. Those sibling projects must be built first, and `runServer` loads the local rebar jar (pylon is downloaded by tag). Versions are pinned in `gradle.properties` (`rebar.version`/`pylon.version` are an upstream-paired release; the `-26.1` suffix matches the GitHub release tags). Don't bump one without the other.

## Architecture

### Initialization order (`Steamwork.onEnable`)
`Fluids → Items → Blocks → global listeners → Researches → Recipes → Pages`. This order matters: blocks reference items/fluids; global listeners (`SteamArm`, `SteamPress`, `PneumaticCargoHub`, upgrade/line/equipment listeners) must register **after** all blocks; recipes/researches/pages reference everything.

### Registration hubs (top-level `Steamwork*` classes)
Content is declared in static `initialize()` hubs, not scattered:
- `SteamworkKeys` — every `NamespacedKey`, created via `steamworkKey("…")` (in `util/SteamworkUtils`).
- `SteamworkFluids`, `SteamworkItems`, `SteamworkBlocks` — register fluids/items/blocks against rebar (`RebarBlock.register(KEY, Material, BlockClass.class)`).
- `SteamworkResearches`, `SteamworkRecipes`, `SteamworkPages` — researches, recipe types + recipe data, and guide pages.

### Content packages (`content/`)
- `machines/` — ~45 block classes: boiler chain (Bronze→Invar→ManganeseSteel→Tungsten), turbines, processors (extend `AbstractSteamBoiler`/`AbstractSteamProcessor`/`AbstractSteamBooster`), and the **pneumatic network** (`PneumaticInput`, `PneumaticOutput`, `PneumaticDuct`, `PneumaticDistributor`, `PneumaticCargoHub`, `SteamSorter`).
- `machines/upgrade/` — pluggable machine upgrade modules (`UpgradeModule` subtypes: energy-save, auto in/out, boost, bulk, range) + `MachineCalibrator`.
- `equipment/` — steam tools/armor/weapons. Energy comes from **installable steam canisters** (`SteamCanister`/`SteamCanisterType`), not built-in capacity. `SteamSetBonusListener` + `SteamWeaponSkillListener` drive set bonuses and skills.
- `line/` — production-line system that chains machines into a linear assembly via blueprint items.

### Recipes
Each processing machine has a `RecipeType` (`recipes/*Recipe.java`) and a registration file (`recipes/registration/<Type>Recipes.java`) that declares the actual recipe data. `recipes/pylon/` handles cross-compatibility with parent Pylon machines.

### Settings (balance) live in YAML, not code
Each registered block/item key has a matching `src/main/resources/settings/<key>.yml`. Machines read values via `getSettings().getOrThrow("field", ConfigAdapter.…)`. **Tune balance numbers in the settings YAML**, not in Java.

### i18n
Fully bilingual EN + zh_CN (`getLanguages()` returns both). `src/main/resources/lang/en.yml` and `lang/zh_CN.yml` must be kept in sync — any new translatable string needs both.

### Block displays / rendering
Machines render via **`ItemDisplay` entities**, built with `ItemDisplayBuilder` + `TransformBuilder` (`.lookAlong(face).translate(x,y,z).scale(…)`) and `LineBuilder` (`.from().to().thickness().extraLength()` → matrix). Custom model-data strings like `:main`, `:duct`, `:line` resolve to resource-pack models. `PneumaticEndpointSupport` centralizes endpoint display/facing math (connection-face resolution, duct arm transforms).

## Releases & commits

- Release notes go in `docs/releases/v<X.Y.Z>.md`, mirroring the format on GitHub (KevinWoodWL/steamwork) releases: setext `--` section headings (`新增` / `变更` / `修复`) with `*   **bold**：description` bullets and `    *   ` sub-bullets.
- **Commits must be authored as `KevinWoodWL` with no Claude/AI identity** (no `Co-authored-by` Claude, no AI author). This is a hard project rule.
- `build/`, `bin/`, `.gradle/`, IDE files are gitignored. Stray top-level dirs (`co/`, `com/`, `io/`, `org/`, `main/`, `META-INF/`, `apiVersioning.json`) are build cruft, **not source** — never commit them.
