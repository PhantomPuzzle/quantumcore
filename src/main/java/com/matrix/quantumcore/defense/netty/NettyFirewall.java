package com.matrix.quantumcore.defense.netty;

import com.matrix.quantumcore.defense.context.QuantumContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Netty 防火墙（Netty Firewall）。
 * 
 * 保护机制：拦截对受保护玩家的非法断连（kick）请求。
 * 无论恶意模组包装了多少层代码，最终都必须调用 
 * ServerGamePacketListenerImpl.disconnect(Component reason)。
 * 我们在该方法的 Mixin 注入中，调用本类的判定逻辑。
 * 
 * 如果发现断连请求来自外部模组，则拒绝执行，强行维持 TCP 管道。
 */
public class NettyFirewall {
    private static final Logger LOGGER = LogManager.getLogger();
    
    // 受保护的玩家 UUID 集合
    private static final Set<UUID> PROTECTED = ConcurrentHashMap.newKeySet();
    
    // 是否启用（全局开关）
    private static boolean globalEnabled = true;
    
    public static void setGlobalEnabled(boolean enabled) {
        globalEnabled = enabled;
    }
    
    public static boolean isGlobalEnabled() {
        return globalEnabled;
    }
    
    // ===== 保护名单管理 =====
    
    public static void protect(UUID uuid) {
        if (uuid == null) return;
        PROTECTED.add(uuid);
        LOGGER.info("[Netty] 玩家 {} 已纳入断连防护。", uuid);
    }
    
    public static void unprotect(UUID uuid) {
        PROTECTED.remove(uuid);
        LOGGER.info("[Netty] 玩家 {} 已解除断连防护。", uuid);
    }
    
    public static boolean isProtected(UUID uuid) {
        return globalEnabled && PROTECTED.contains(uuid);
    }
    
    // ===== 核心判定：是否应拦截断连 =====
    
    /**
     * 检查是否应该拒绝当前的 disconnect 请求。
     * 条件：1. 全局启用；2. 该玩家受保护；3. 调用者来自外部模组。
     */
    public static boolean shouldBlockDisconnect(UUID uuid) {
        if (!globalEnabled) return false;
        if (!PROTECTED.contains(uuid)) return false;
        // 如果 ThreadLocal 标记为内部，放行
        if (QuantumContext.isInternal()) return false;
        // StackWalker 检测
        return QuantumContext.isExternalCaller();
    }
    
    public static void clearAll() {
        PROTECTED.clear();
    }
}
