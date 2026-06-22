package com.matrix.quantumcore.config;

import net.minecraftforge.common.ForgeConfigSpec;
import java.util.Collections;
import java.util.List;

public class QuantumCoreConfig {
    public static final ForgeConfigSpec GENERAL_SPEC;

    // 加权随机数据库配置
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> ITEM_BLACKLIST;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> ITEM_WHITELIST;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> ENTITY_BLACKLIST;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> ENTITY_WHITELIST;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> CUSTOM_WEIGHTS;

    // 防御系统配置
    public static final ForgeConfigSpec.BooleanValue ENABLE_STATE_GASLIGHTING;
    public static final ForgeConfigSpec.BooleanValue ANCHOR_AUTO_PROTECT_ON_JOIN;
    public static final ForgeConfigSpec.BooleanValue MIRAGE_AUTO_ON_JOIN;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.comment("QuantumCore 前置全局配置文件").push("general");

        ITEM_BLACKLIST = builder.comment(
                "物品黑名单：被排除在随机替换池之外的物品ID。",
                "支持三种格式：",
                "  1. 具体ID: 'minecraft:air'",
                "  2. Tag: '#forge:ingots'（Tag 内所有物品）",
                "  3. ModID通配符: 'avaritia:*'（整个模组的所有物品）"
        ).defineList("item_blacklist", Collections.singletonList("minecraft:air"), obj -> obj instanceof String);

        ITEM_WHITELIST = builder.comment(
                "物品白名单：若不为空，则随机替换池*仅*包含此列表中的物品。",
                "支持格式同黑名单：具体ID / Tag / ModID通配符"
        ).defineList("item_whitelist", Collections.emptyList(), obj -> obj instanceof String);

        ENTITY_BLACKLIST = builder.comment(
                "实体黑名单：被排除在随机实体替换池之外的实体ID。",
                "支持格式：具体ID / Tag / ModID通配符"
        ).defineList("entity_blacklist", Collections.singletonList("minecraft:player"), obj -> obj instanceof String);

        ENTITY_WHITELIST = builder.comment(
                "实体白名单：若不为空，则实体随机替换池*仅*包含此列表中的实体。",
                "支持格式：具体ID / Tag / ModID通配符"
        ).defineList("entity_whitelist", Collections.emptyList(), obj -> obj instanceof String);

        CUSTOM_WEIGHTS = builder.comment(
                "自定义特定物品/实体的权重比例。",
                "格式为 '注册名;权重' (例如 'minecraft:diamond;5.5')"
        ).defineList("custom_weights", Collections.emptyList(), obj -> obj instanceof String);

        builder.pop();
        builder.comment("防御系统配置").push("defense");

        ENABLE_STATE_GASLIGHTING = builder.comment(
                "实验性功能：启用状态欺骗。当玩家处于双生虚相时，",
                "对外部模组返回 isAlive=false / isRemoved=true。",
                "可能导致原版玩家列表或第三方模组误判，默认关闭。"
        ).define("enable_state_gaslighting", false);

        ANCHOR_AUTO_PROTECT_ON_JOIN = builder.comment(
                "玩家加入服务器时是否自动绑定锚点（记录当前位置）。",
                "主要模组应接管此逻辑，前置默认关闭。"
        ).define("anchor_auto_protect_on_join", false);

        MIRAGE_AUTO_ON_JOIN = builder.comment(
                "玩家加入服务器时是否自动启动双生虚相。",
                "主要模组应接管此逻辑，前置默认关闭。"
        ).define("mirage_auto_on_join", false);

        builder.pop();
        GENERAL_SPEC = builder.build();
    }
}
