package com.matrix.quantumcore.api;

import com.matrix.quantumcore.config.QuantumCoreConfig;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.ForgeRegistries;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 注册表数据索引（Registry Data Index）。
 *
 * 高性能、线程安全的加权随机数据库。
 * 核心特性：
 * 1. 写时复制（Copy-on-Write）：rebuild 时局部构建新池，原子级替换引用，
 *    保证多线程下 getRandomItem/getRandomEntityType 永远不会拿到空池或并发修改异常。
 * 2. O(1) Alias 采样：构建完成后自动冻结为 AliasRandomPool，供高频抽样使用。
 * 3. 高级配置解析：支持 Tag（#forge:ingots）和 ModID 通配符（avaritia:*）。
 * 4. 健全性过滤：自动排除 air、残缺物品等非法占位符。
 * 5. 直接抽样接口：无需获取池引用，直接传入 RandomSource 即可抽样。
 * 6. 热重载：支持通过 API 或命令触发重新构建，不丢失旧池引用。
 */
public class RegistryDataIndex {
    private static final Logger LOGGER = LogManager.getLogger();

    // 使用 AtomicReference 实现无锁写时复制
    private static final AtomicReference<AliasRandomPool<Item>> ITEM_POOL = new AtomicReference<>(new AliasRandomPool<>(Collections.emptyMap()));
    private static final AtomicReference<AliasRandomPool<EntityType<?>>> ENTITY_POOL = new AtomicReference<>(new AliasRandomPool<>(Collections.emptyMap()));

    // 保留动态池用于增量构建，构建完成后转换为 AliasPool
    private static WeightedRandomPool<Item> itemBuildBuffer = new WeightedRandomPool<>();
    private static WeightedRandomPool<EntityType<?>> entityBuildBuffer = new WeightedRandomPool<>();

    /**
     * 重新构建全局加权随机数据库。
     * 采用写时复制：在局部缓冲池中构建，完成后原子替换引用。
     */
    public static void rebuildDatabase() {
        LOGGER.info("[QuantumCore] 开始动态构建全量加权随机数据库...");

        // 新建局部缓冲池（外界仍访问旧池，绝对安全）
        itemBuildBuffer = new WeightedRandomPool<>();
        entityBuildBuffer = new WeightedRandomPool<>();

        // 解析配置
        Set<ResourceLocation> itemBlacklist = parseConfigSet(QuantumCoreConfig.ITEM_BLACKLIST.get());
        Set<ResourceLocation> itemWhitelist = parseConfigSet(QuantumCoreConfig.ITEM_WHITELIST.get());
        Set<ResourceLocation> entityBlacklist = parseConfigSet(QuantumCoreConfig.ENTITY_BLACKLIST.get());
        Set<ResourceLocation> entityWhitelist = parseConfigSet(QuantumCoreConfig.ENTITY_WHITELIST.get());
        Map<ResourceLocation, Double> customWeights = parseCustomWeights(QuantumCoreConfig.CUSTOM_WEIGHTS.get());

        // 扫描并填充物品池
        int validItemCount = 0;
        int filteredItemCount = 0;
        for (var entry : ForgeRegistries.ITEMS.getEntries()) {
            ResourceLocation id = entry.getKey().location();
            Item item = entry.getValue();

            // 健全性过滤：排除 air 和残缺物品
            if (!isValidItem(item, id)) {
                filteredItemCount++;
                continue;
            }

            // 黑名单检查
            if (itemBlacklist.contains(id)) continue;
            // 白名单检查（若白名单非空则只包含白名单内容）
            if (!itemWhitelist.isEmpty() && !itemWhitelist.contains(id)) continue;

            double weight = customWeights.getOrDefault(id, 1.0);
            itemBuildBuffer.add(weight, item);
            validItemCount++;
        }

        // 扫描并填充实体池
        int validEntityCount = 0;
        for (var entry : ForgeRegistries.ENTITY_TYPES.getEntries()) {
            ResourceLocation id = entry.getKey().location();
            EntityType<?> type = entry.getValue();

            if (entityBlacklist.contains(id)) continue;
            if (!entityWhitelist.isEmpty() && !entityWhitelist.contains(id)) continue;

            double weight = customWeights.getOrDefault(id, 1.0);
            entityBuildBuffer.add(weight, type);
            validEntityCount++;
        }

        // 冻结为不可变的 O(1) AliasRandomPool
        AliasRandomPool<Item> frozenItemPool = itemBuildBuffer.toAliasPool();
        AliasRandomPool<EntityType<?>> frozenEntityPool = entityBuildBuffer.toAliasPool();

        // 原子级替换引用（无锁、无时差、不卡 Tick）
        ITEM_POOL.set(frozenItemPool);
        ENTITY_POOL.set(frozenEntityPool);

        LOGGER.info("[QuantumCore] 数据库构建完毕！物品: {}/{} (过滤{}), 实体: {}/{}",
                validItemCount, ForgeRegistries.ITEMS.getEntries().size(), filteredItemCount,
                validEntityCount, ForgeRegistries.ENTITY_TYPES.getEntries().size());
    }

    // ==================== 直接抽样接口（推荐） ====================

    /**
     * O(1) 无锁直接抽样：从物品池中随机抽取一个物品。
     * 高频调用安全，无需获取池引用。
     */
    public static Item getRandomItem(RandomSource random) {
        return ITEM_POOL.get().getRandom(random);
    }

    /**
     * O(1) 无锁直接抽样：从实体池中随机抽取一个实体类型。
     * 高频调用安全，无需获取池引用。
     */
    public static EntityType<?> getRandomEntityType(RandomSource random) {
        return ENTITY_POOL.get().getRandom(random);
    }

    // ==================== 池引用接口（向后兼容） ====================

    public static AliasRandomPool<Item> getItemPool() {
        return ITEM_POOL.get();
    }

    public static AliasRandomPool<EntityType<?>> getEntityPool() {
        return ENTITY_POOL.get();
    }

    // ==================== 热重载 ====================

    /**
     * 热重载：重新读取当前配置文件并重建数据库。
     * 适用于管理员修改 .toml 后通过命令触发，或主模组在运行时动态调整权重。
     * 无锁、零卡顿，旧池在替换瞬间被 GC 回收。
     */
    public static void reload() {
        LOGGER.info("[QuantumCore] 触发数据库热重载...");
        rebuildDatabase();
    }

    // ==================== 配置解析（增强） ====================

    /**
     * 解析配置列表，支持：
     * - 具体 ID: "minecraft:diamond"
     * - Tag: "#forge:ingots"
     * - ModID 通配符: "avaritia:*"
     */
    private static Set<ResourceLocation> parseConfigSet(java.util.List<? extends String> list) {
        Set<ResourceLocation> res = new HashSet<>();
        for (String s : list) {
            if (s == null || s.isBlank()) continue;

            s = s.trim();

            // 1. Tag 解析: #forge:ingots
            if (s.startsWith("#")) {
                String tagStr = s.substring(1);
                if (ResourceLocation.isValidResourceLocation(tagStr)) {
                    ResourceLocation tagId = new ResourceLocation(tagStr);
                    TagKey<Item> tagKey = TagKey.create(Registries.ITEM, tagId);
                    var tag = ForgeRegistries.ITEMS.tags().getTag(tagKey);
                    if (tag != null) {
                        tag.forEach(item -> res.add(ForgeRegistries.ITEMS.getKey(item)));
                    }
                    // 实体 Tag 同理（若需要）
                }
                continue;
            }

            // 2. ModID 通配符: avaritia:*
            if (s.endsWith(":*")) {
                String modId = s.substring(0, s.length() - 2);
                // 收集该 ModID 下的所有物品 ID
                for (var entry : ForgeRegistries.ITEMS.getEntries()) {
                    if (entry.getKey().location().getNamespace().equals(modId)) {
                        res.add(entry.getKey().location());
                    }
                }
                // 实体同理
                for (var entry : ForgeRegistries.ENTITY_TYPES.getEntries()) {
                    if (entry.getKey().location().getNamespace().equals(modId)) {
                        res.add(entry.getKey().location());
                    }
                }
                continue;
            }

            // 3. 普通 ID
            if (ResourceLocation.isValidResourceLocation(s)) {
                res.add(new ResourceLocation(s));
            }
        }
        return res;
    }

    private static Map<ResourceLocation, Double> parseCustomWeights(java.util.List<? extends String> list) {
        Map<ResourceLocation, Double> map = new HashMap<>();
        for (String s : list) {
            if (s == null || s.isBlank()) continue;
            String[] split = s.split(";");
            if (split.length == 2 && ResourceLocation.isValidResourceLocation(split[0])) {
                try {
                    map.put(new ResourceLocation(split[0]), Double.parseDouble(split[1]));
                } catch (NumberFormatException e) {
                    LOGGER.error("[QuantumCore] 配置文件中的权重数值解析失败: {}", s);
                }
            }
        }
        return map;
    }

    // ==================== 健全性过滤 ====================

    /**
     * 检查物品是否为合法/有效的注册项。
     * 排除：air、无翻译键的残缺占位符、技术性占位符等。
     */
    private static boolean isValidItem(Item item, ResourceLocation id) {
        // 绝对排除 air
        if (item == net.minecraft.world.item.Items.AIR || "minecraft:air".equals(id.toString())) {
            return false;
        }

        // 检测残缺物品：翻译键为空或默认格式异常
        String descId = item.getDescriptionId();
        if (descId == null || descId.isBlank() || descId.equals("item.null") || descId.equals("block.null")) {
            LOGGER.warn("[QuantumCore] 检测到残缺物品占位符: {} (descId={})，已排除。", id, descId);
            return false;
        }

        return true;
    }
}
