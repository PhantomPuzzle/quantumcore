package com.matrix.quantumcore.defense.phoenix;

import com.matrix.quantumcore.defense.context.QuantumContext;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 凤凰涅槃系统（Phoenix Shell System）。
 * 
 * 终极兜底机制：当所有 Mixin 拦截都失效（如反射、异步攻击、底层字段修改）时，
 * 在玩家实例被物理移除的同一 tick 内完成"灵魂抽离 → 涅槃重组"。
 * 
 * 核心流程：
 * 1. 检测到 setRemoved 或底层移除时，立即剥离 connection（网络句柄）和 NBT 快照
 * 2. 在同一 tick 内实例化新的 ServerPlayer，注入原 connection
 * 3. 将新玩家塞回 PlayerList 和 ServerLevel
 * 4. 客户端无感知，网络套接字从未断开
 * 
 * 内存安全：
 * - SOUL_ORPHANAGE 中只保留待重组的数据，重组完成后立即 remove
 * - 如果 extractSoul 后重组失败（如异常），超过 30 秒的旧数据会被自动清理
 * - PHOENIXED 标志在重组完成后保留 5 分钟，防止旧躯壳的残留事件触发循环
 */
public class PhoenixShellSystem {
    private static final Logger LOGGER = LogManager.getLogger();
    
    // 灵魂收容所：等待重组的玩家数据
    private static final Map<UUID, SoulContainer> SOUL_ORPHANAGE = new ConcurrentHashMap<>();
    
    // 标记：已被凤凰系统处理过的旧玩家（防止循环触发）
    private static final Map<UUID, Long> PHOENIXED = new ConcurrentHashMap<>();
    
    // PlayerList.players 的反射字段（懒加载缓存）
    private static Field playerListField = null;
    
    // 是否启用（全局开关）
    private static boolean globalEnabled = true;
    
    // 超时常量（毫秒）
    private static final long SOUL_ORPHAN_TIMEOUT = 30_000L;      // 30 秒
    private static final long PHOENIXED_FLAG_TIMEOUT = 300_000L;  // 5 分钟
    
    public static void setGlobalEnabled(boolean enabled) {
        globalEnabled = enabled;
    }
    
    public static boolean isGlobalEnabled() {
        return globalEnabled;
    }
    
    public static class SoulContainer {
        public final ServerGamePacketListenerImpl connection;
        public final CompoundTag playerDataSnapshot;
        public final String dimension;
        public final long timestamp; // 记录抽离时间，用于超时清理
        
        public SoulContainer(ServerGamePacketListenerImpl connection, CompoundTag snapshot, String dimension) {
            this.connection = connection;
            this.playerDataSnapshot = snapshot;
            this.dimension = dimension;
            this.timestamp = System.currentTimeMillis();
        }
    }
    
    /**
     * 获取 PlayerList.players 的反射字段（懒加载）。
     */
    private static Field getPlayerListField() {
        if (playerListField != null) return playerListField;
        try {
            playerListField = net.minecraft.server.players.PlayerList.class.getDeclaredField("players");
            playerListField.setAccessible(true);
            return playerListField;
        } catch (NoSuchFieldException e) {
            LOGGER.error("[Phoenix] 无法获取 PlayerList.players 字段！", e);
            return null;
        }
    }
    
    /**
     * 第一阶段：灵魂抽离。
     * 当检测到玩家实例即将被不可逆移除时调用。
     * 如果该玩家已有旧的 SoulContainer，先清理旧数据。
     */
    public static void extractSoul(ServerPlayer dyingPlayer) {
        if (!globalEnabled) return;
        if (dyingPlayer.connection == null) {
            LOGGER.warn("[Phoenix] 玩家 {} 无连接，无法抽离灵魂。", dyingPlayer.getScoreboardName());
            return;
        }
        
        UUID uuid = dyingPlayer.getUUID();
        if (PHOENIXED.containsKey(uuid)) return; // 已处理过
        
        // 清理旧数据（防止高频攻击导致重复堆积）
        SoulContainer oldSoul = SOUL_ORPHANAGE.remove(uuid);
        if (oldSoul != null) {
            LOGGER.warn("[Phoenix] 清理玩家 {} 的旧 SoulContainer，防止内存泄漏。", dyingPlayer.getScoreboardName());
            // 尝试释放旧连接（如果还有效的话）
            if (oldSoul.connection != null && oldSoul.connection.player == null) {
                oldSoul.connection.disconnect(net.minecraft.network.chat.Component.literal("[Phoenix] 旧连接被清理"));
            }
        }
        
        QuantumContext.runInternal(() -> {
            // 1. 序列化核心数据
            CompoundTag nbt = new CompoundTag();
            dyingPlayer.saveWithoutId(nbt);
            
            // 2. 物理剥离网络连接（挂起）
            ServerGamePacketListenerImpl connection = dyingPlayer.connection;
            String dimension = dyingPlayer.level().dimension().location().toString();
            
            SOUL_ORPHANAGE.put(uuid, new SoulContainer(connection, nbt, dimension));
            
            // 3. 将旧对象的连接置空，防止旧对象销毁时连带断开 TCP
            dyingPlayer.connection = null;
            
            LOGGER.warn("[Phoenix] 玩家 {} 的灵魂已抽离并托管。", dyingPlayer.getScoreboardName());
        });
    }
    
    /**
     * 第二阶段：涅槃重组。
     * 在 extractSoul 后立即调用，在同一 tick 内完成重生。
     */
    public static ServerPlayer reincarnate(ServerPlayer oldPlayer) {
        if (!globalEnabled) return null;
        
        UUID uuid = oldPlayer.getUUID();
        SoulContainer soul = SOUL_ORPHANAGE.remove(uuid);
        if (soul == null) return null;
        
        PHOENIXED.put(uuid, System.currentTimeMillis());
        
        // 手动进入内部上下文（runInternal 返回 void，不能用于需要返回值的方法）
        QuantumContext.enter();
        try {
            // 1. 实例化新玩家对象（相同 GameProfile 和维度）
            ServerPlayer newPlayer = new ServerPlayer(
                    oldPlayer.server,
                    oldPlayer.serverLevel(),
                    oldPlayer.getGameProfile()
            );
            
            // 2. 恢复全量数据（背包、属性、血量、经验等）
            newPlayer.load(soul.playerDataSnapshot);
            
            // 3. 灵魂归位：将托管的网络连接重新插入新对象
            newPlayer.connection = soul.connection;
            // 极其重要：让网络层认识新躯壳
            newPlayer.connection.player = newPlayer;
            
            // 4. 使用反射替换 PlayerList 中的玩家引用
            Field field = getPlayerListField();
            if (field != null) {
                @SuppressWarnings("unchecked")
                List<ServerPlayer> players = (List<ServerPlayer>) field.get(oldPlayer.server.getPlayerList());
                players.remove(oldPlayer);
                players.add(newPlayer);
            } else {
                LOGGER.error("[Phoenix] 无法替换 PlayerList 中的玩家引用，涅槃重组失败！");
                return null;
            }
            
            // 5. 将新玩家加入世界实体树
            oldPlayer.serverLevel().addFreshEntity(newPlayer);
            
            // 6. 同步客户端（发送权限等级更新，触发客户端重载）
            oldPlayer.server.getPlayerList().sendPlayerPermissionLevel(newPlayer);
            
            LOGGER.warn("[Phoenix] 玩家 {} 已完成涅槃重组！新实体 ID: {}",
                    newPlayer.getScoreboardName(), newPlayer.getId());
            
            return newPlayer;
            
        } catch (Exception e) {
            LOGGER.error("[Phoenix] 玩家 {} 涅槃重组失败！", oldPlayer.getScoreboardName(), e);
            return null;
        } finally {
            QuantumContext.exit();
        }
    }
    
    /**
     * 检查玩家是否已被凤凰系统处理过（防止对旧躯壳重复触发）。
     */
    public static boolean isPhoenixed(UUID uuid) {
        return PHOENIXED.containsKey(uuid);
    }
    
    public static void clearPhoenixFlag(UUID uuid) {
        PHOENIXED.remove(uuid);
    }
    
    /**
     * 定期清理：移除超时的 SoulContainer 和 PHOENIXED 标志。
     * 供 DefenseManager.onServerTick 调用，每 tick 执行一次。
     */
    public static void cleanupExpired() {
        long now = System.currentTimeMillis();
        
        // 清理超时的 SoulContainer（防止 extractSoul 后重组失败导致的内存泄漏）
        SOUL_ORPHANAGE.entrySet().removeIf(entry -> {
            boolean expired = (now - entry.getValue().timestamp) > SOUL_ORPHAN_TIMEOUT;
            if (expired) {
                LOGGER.warn("[Phoenix] 清理超时的 SoulContainer: {}", entry.getKey());
            }
            return expired;
        });
        
        // 清理超时的 PHOENIXED 标志
        PHOENIXED.entrySet().removeIf(entry -> {
            boolean expired = (now - entry.getValue()) > PHOENIXED_FLAG_TIMEOUT;
            if (expired) {
                LOGGER.info("[Phoenix] 清理过期的 PHOENIXED 标志: {}", entry.getKey());
            }
            return expired;
        });
    }
    
    public static void clearAll() {
        SOUL_ORPHANAGE.clear();
        PHOENIXED.clear();
    }
    
    // 调试用：获取当前待重组的灵魂数量
    public static int getPendingSoulCount() {
        return SOUL_ORPHANAGE.size();
    }
    
    public static int getPhoenixedCount() {
        return PHOENIXED.size();
    }
}