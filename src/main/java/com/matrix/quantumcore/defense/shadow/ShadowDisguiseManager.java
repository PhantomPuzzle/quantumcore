package com.matrix.quantumcore.defense.shadow;

import com.matrix.quantumcore.defense.context.QuantumContext;
import com.matrix.quantumcore.defense.mirage.QuantumFakePlayer;
import net.minecraft.server.level.ServerPlayer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 影子伪装管理器（Shadow Disguise Manager）。
 * 
 * 核心机制：在实体检索层调包。
 * 当外部模组通过 getPlayer()、players()、getEntities() 获取玩家时，
 * 使用 StackWalker 自动检测调用者身份。
 * 如果是外部模组，返回假人指针；如果是原版/Forge/本前置，返回真玩家。
 * 
 * 这样恶意模组从头到尾拿到的都是假人，而原版内核和客户端完全不受影响。
 */
public class ShadowDisguiseManager {
    private static final Logger LOGGER = LogManager.getLogger();
    
    // UUID -> 假人映射（与 MirageSystem 共用同一个假人）
    private static final Map<UUID, QuantumFakePlayer> SHADOW_MAP = new ConcurrentHashMap<>();
    
    // 是否启用检索层调包（全局开关，可由主模组控制）
    private static boolean globalEnabled = true;
    
    public static void setGlobalEnabled(boolean enabled) {
        globalEnabled = enabled;
    }
    
    public static boolean isGlobalEnabled() {
        return globalEnabled;
    }
    
    // ===== 注册/注销假人 =====
    
    public static void registerShadow(UUID uuid, QuantumFakePlayer decoy) {
        SHADOW_MAP.put(uuid, decoy);
        LOGGER.info("[Shadow] 已注册影子映射 {} -> 假人 {}", uuid, decoy.getUUID());
    }
    
    public static void unregisterShadow(UUID uuid) {
        SHADOW_MAP.remove(uuid);
        LOGGER.info("[Shadow] 已注销影子映射 {}", uuid);
    }
    
    public static boolean hasShadow(UUID uuid) {
        return SHADOW_MAP.containsKey(uuid);
    }
    
    public static QuantumFakePlayer getShadow(UUID uuid) {
        return SHADOW_MAP.get(uuid);
    }
    
    // ===== 核心决策：是否应欺骗调用者 =====
    
    /**
     * 检查当前调用是否应该被欺骗（返回假人而非真玩家）。
     * 条件：1. 全局启用；2. 该玩家有注册假人；3. 调用者来自外部模组。
     */
    public static boolean shouldDeceive(UUID uuid) {
        if (!globalEnabled) return false;
        if (!SHADOW_MAP.containsKey(uuid)) return false;
        // 如果 ThreadLocal 已标记为内部，直接放行
        if (QuantumContext.isInternal()) return false;
        // 使用 StackWalker 分析调用栈
        return QuantumContext.isExternalCaller();
    }
    
    /**
     * 无条件获取假人（用于 MirageSystem 的重定向逻辑）。
     */
    public static QuantumFakePlayer resolveDecoy(UUID uuid) {
        return SHADOW_MAP.get(uuid);
    }
    
    public static void clearAll() {
        SHADOW_MAP.clear();
        LOGGER.info("[Shadow] 已清空所有影子映射。");
    }
}
