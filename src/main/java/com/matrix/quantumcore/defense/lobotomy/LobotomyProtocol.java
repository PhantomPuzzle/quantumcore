package com.matrix.quantumcore.defense.lobotomy;

import com.matrix.quantumcore.defense.context.QuantumContext;
import net.minecraft.world.entity.Entity;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 逻辑切除协议（Lobotomy Protocol）。
 * 
 * 主动反击机制：当恶意模组/实体攻击受保护玩家时，
 * 追踪攻击者的 Class，将其加入"处刑名单"（LOBIOTOMIZED_CLASSES）。
 * 所有属于该 Class 的实体将被剥夺 tick 更新权限，变成永久静止的雕像。
 * 
 * 效果：攻击者不会死，但永远无法移动、攻击、AI 决策、发包。
 * 这种反击比抹杀更具威慑力——恶意模组的逻辑闭环完成（以为攻击成功），
 * 但攻击者本身被冻结。
 * 
 * 安全策略：
 * 1. 白名单保护：原版、Forge、JVM 核心类、服务器管理类绝对禁止切除
 * 2. 特征判断：类名包含 "server"、"console"、"command"、"minecraft" 等关键词的拒绝切除
 * 3. 实体类型排除：ServerPlayer、FakePlayer 等关键玩家实体禁止切除
 */
public class LobotomyProtocol {
    private static final Logger LOGGER = LogManager.getLogger();
    
    // 被切除逻辑的类集合（Class 级连坐）
    private static final Set<Class<?>> LOBOTOMIZED_CLASSES = ConcurrentHashMap.newKeySet();
    
    // 是否启用（全局开关）
    private static boolean globalEnabled = false;
    
    public static void setGlobalEnabled(boolean enabled) {
        globalEnabled = enabled;
    }
    
    public static boolean isGlobalEnabled() {
        return globalEnabled;
    }
    
    // ===== 白名单：绝对禁止切除的类前缀 =====
    private static final String[] CLASS_BLACKLIST = {
        "net.minecraft.",           // 原版
        "net.minecraftforge.",      // Forge
        "com.matrix.quantumcore.", // 本前置
        "java.",                    // JVM 核心
        "javax.",                   // JVM 扩展
        "sun.",                     // Sun 内部
        "jdk.",                     // JDK 内部
        "com.sun.",                 // Sun 扩展
        "org.apache.logging.",      // 日志系统
        "org.spongepowered.",       // Mixin 核心
    };
    
    // ===== 白名单：类名关键词（如果类名包含这些，拒绝切除） =====
    private static final String[] FORBIDDEN_KEYWORDS = {
        "server", "console", "command", "minecraft", "forge", "playerlist",
        "packet", "connection", "network", "thread", "executor", "scheduler"
    };
    
    // ===== 处刑名单管理 =====
    
    public static void lobotomize(Class<?> clazz) {
        if (!globalEnabled) return;
        if (clazz == null) return;
        
        String name = clazz.getName().toLowerCase();
        String simpleName = clazz.getSimpleName().toLowerCase();
        
        // 1. 检查类前缀白名单
        for (String prefix : CLASS_BLACKLIST) {
            if (clazz.getName().startsWith(prefix)) {
                LOGGER.warn("[Lobotomy] 拒绝切除受信任的类（白名单前缀）：{}", clazz.getName());
                return;
            }
        }
        
        // 2. 检查类名关键词（防止伪造/借用原版系统广播）
        for (String keyword : FORBIDDEN_KEYWORDS) {
            if (name.contains(keyword) || simpleName.contains(keyword)) {
                LOGGER.warn("[Lobotomy] 拒绝切除可疑类（关键词 '{}': {}", keyword, clazz.getName());
                return;
            }
        }
        
        // 3. 检查是否为关键玩家实体类型
        if (net.minecraft.server.level.ServerPlayer.class.isAssignableFrom(clazz) ||
            net.minecraftforge.common.util.FakePlayer.class.isAssignableFrom(clazz)) {
            LOGGER.warn("[Lobotomy] 拒绝切除玩家实体类：{}", clazz.getName());
            return;
        }
        
        if (LOBOTOMIZED_CLASSES.add(clazz)) {
            LOGGER.warn("[Lobotomy] 已将类 {} 加入逻辑切除名单！", clazz.getName());
        }
    }
    
    public static void lobotomize(Entity attacker) {
        if (attacker == null) return;
        lobotomize(attacker.getClass());
    }
    
    public static void pardon(Class<?> clazz) {
        LOBOTOMIZED_CLASSES.remove(clazz);
        LOGGER.info("[Lobotomy] 已赦免类 {}", clazz.getName());
    }
    
    public static boolean isLobotomized(Class<?> clazz) {
        return LOBOTOMIZED_CLASSES.contains(clazz);
    }
    
    public static boolean isLobotomized(Entity entity) {
        if (entity == null) return false;
        return isLobotomized(entity.getClass());
    }
    
    public static Set<Class<?>> getLobotomizedClasses() {
        return Collections.unmodifiableSet(LOBOTOMIZED_CLASSES);
    }
    
    // ===== 触发接口 =====
    
    /**
     * 当检测到攻击时调用。提取攻击者并执行连坐处决。
     */
    public static void onAttackDetected(Entity attacker) {
        if (!globalEnabled) return;
        if (attacker == null) return;
        
        // 使用调用栈分析确认攻击来源是外部模组
        if (QuantumContext.isExternalCaller()) {
            lobotomize(attacker);
        }
    }
    
    public static void clearAll() {
        LOBOTOMIZED_CLASSES.clear();
        LOGGER.info("[Lobotomy] 已清空所有逻辑切除名单。");
    }
}
