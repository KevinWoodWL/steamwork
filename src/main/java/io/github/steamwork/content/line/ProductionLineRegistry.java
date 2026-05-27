package io.github.steamwork.content.line;

import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * 产线注册表：将所有已激活产线的元数据以 YAML 文件形式持久化，
 * 便于服务器管理员查阅和排查问题。
 *
 * <p>文件路径：{@code plugins/Steamwork/data/lines.yml}</p>
 *
 * <p><b>与 PDC 的关系：</b><br>
 * PDC 依然是每个成员方块的权威持久化来源（服务器重载时恢复方块状态）。
 * 本文件提供人类可读的全局索引，内容与 PDC 保持同步：
 * 激活产线时写入，解散产线时删除。</p>
 *
 * <p>YAML 格式示例：</p>
 * <pre>
 * lines:
 *   550e8400-e29b-41d4-a716-446655440000:
 *     creator: Steve
 *     direction: EAST
 *     activated: '2026-05-26'
 *     members:
 *     - {position: 0, world: world, x: 100, y: 64, z: 200}
 *     - {position: 1, world: world, x: 101, y: 64, z: 200}
 *     - {position: 2, world: world, x: 102, y: 64, z: 200}
 * </pre>
 */
public final class ProductionLineRegistry {

    public record LineRecord(@NotNull UUID lineId, @NotNull UUID creatorUuid,
                             @NotNull String creatorName, int number) {}

    private static ProductionLineRegistry instance;

    private final Plugin plugin;
    private final File file;
    private YamlConfiguration yaml;

    // ===== 初始化 =====

    public ProductionLineRegistry(@NotNull Plugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "data/lines.yml");
        load();
        instance = this;
    }

    /** 获取全局单例，插件 enable 后始终非 null。 */
    public static @Nullable ProductionLineRegistry get() {
        return instance;
    }

    // ===== 公共 API =====

    /**
     * 注册一条刚激活的产线。
     *
     * @param lineId    产线 UUID
     * @param creator   激活产线的玩家名
     * @param direction 产线走向（入口 → 出口）
     * @param members   成员方块列表（顺序：0 = 入口，1..n = 机器，n+1 = 出口）
     */
    public int register(@NotNull UUID lineId,
                        @NotNull UUID creatorUuid,
                        @NotNull String creator,
                        @NotNull BlockFace direction,
                        @NotNull List<Block> members) {
        int number = allocateNumber(creatorUuid);
        ConfigurationSection lineSection = yaml.createSection("lines." + lineId);
        lineSection.set("creator_uuid", creatorUuid.toString());
        lineSection.set("creator", creator);
        lineSection.set("number", number);
        lineSection.set("direction", direction.name());
        lineSection.set("activated", LocalDate.now().toString());

        List<Map<String, Object>> memberList = new ArrayList<>(members.size());
        for (int i = 0; i < members.size(); i++) {
            Block b = members.get(i);
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("position", i);
            entry.put("world", b.getWorld().getName());
            entry.put("x", b.getX());
            entry.put("y", b.getY());
            entry.put("z", b.getZ());
            memberList.add(entry);
        }
        lineSection.set("members", memberList);
        save();
        return number;
    }

    /**
     * 注销一条产线（方块被破坏或多方块结构损坏时调用）。
     * 如果该 UUID 不在文件中，本方法静默跳过。
     *
     * @param lineId 要注销的产线 UUID
     */
    public @Nullable LineRecord unregister(@NotNull UUID lineId) {
        LineRecord record = getRecord(lineId);
        if (yaml.contains("lines." + lineId)) {
            yaml.set("lines." + lineId, null);
            save();
        }
        return record;
    }

    public @Nullable LineRecord getRecord(@NotNull UUID lineId) {
        ConfigurationSection lineSection = yaml.getConfigurationSection("lines." + lineId);
        if (lineSection == null) return null;
        String creatorUuidString = lineSection.getString("creator_uuid");
        String creatorName = lineSection.getString("creator", "Unknown");
        int number = lineSection.getInt("number", 0);
        if (creatorUuidString == null || number <= 0) return null;
        try {
            return new LineRecord(lineId, UUID.fromString(creatorUuidString), creatorName, number);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    // ===== 内部工具 =====

    private void load() {
        if (file.exists()) {
            yaml = YamlConfiguration.loadConfiguration(file);
        } else {
            yaml = new YamlConfiguration();
        }
    }

    private int allocateNumber(@NotNull UUID creatorUuid) {
        String path = "player_next_numbers." + creatorUuid;
        int next = yaml.getInt(path, findNextNumberFromExistingLines(creatorUuid));
        yaml.set(path, next + 1);
        return next;
    }

    private int findNextNumberFromExistingLines(@NotNull UUID creatorUuid) {
        int highest = 0;
        ConfigurationSection lines = yaml.getConfigurationSection("lines");
        if (lines != null) {
            for (String key : lines.getKeys(false)) {
                ConfigurationSection line = lines.getConfigurationSection(key);
                if (line == null) continue;
                if (!creatorUuid.toString().equals(line.getString("creator_uuid"))) continue;
                highest = Math.max(highest, line.getInt("number", 0));
            }
        }
        return highest + 1;
    }

    private void save() {
        try {
            file.getParentFile().mkdirs();
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING,
                    "[ProductionLineRegistry] Failed to save lines.yml", e);
        }
    }
}
