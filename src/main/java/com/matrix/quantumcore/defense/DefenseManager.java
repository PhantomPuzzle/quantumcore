package com.matrix.quantumcore.defense;

import com.matrix.quantumcore.config.QuantumCoreConfig;
import com.matrix.quantumcore.defense.anchor.AnchorSystem;
import com.matrix.quantumcore.defense.context.QuantumContext;
import com.matrix.quantumcore.defense.lobotomy.LobotomyProtocol;
import com.matrix.quantumcore.defense.mirage.MirageSystem;
import com.matrix.quantumcore.defense.netty.NettyFirewall;
import com.matrix.quantumcore.defense.phoenix.PhoenixShellSystem;
import com.matrix.quantumcore.defense.shadow.ShadowDisguiseManager;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.level.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 防御系统统一分发入口。
 *
 * 主要模组（后续开发）只需调用此类的公开 API，不需要关心内部实现。
 * 所有 Mixin 拦截也委托到此类，由它根据 DefenseState 的各功能开关决定如何处理。
 *
 * 功能层次：
 * L1 检索层：ShadowDisguise（getPlayer / players / getEntities 调包）
 * L2 行为层：Mixin 拦截（hurt / die / discard / remove / setPos）→ 重定向到假人
 * L3 状态层：isAlive / isRemoved 欺骗（外部调用）
 * L4 网络层：NettyFirewall（拦截 disconnect / kick）
 * L5 实体层：AnchorSystem（每 tick 修复血量/位置）
 * L6 终极兜底：PhoenixShell（setRemoved 时灵魂热插拔）
 * L7 主动反击：LobotomyProtocol（追溯攻击者，剥夺 tick）
 */
public class DefenseManager {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final Map<UUID, DefenseState> STATES = new ConcurrentHashMap<>();

    // ==================== 对外 API：主模组调用 ====================

    /**
     * 预设组合：启用锚点保护。
     */
    public static void protectWithAnchor(ServerPlayer player, ResourceKey<Level> dimension, double x, double y, double z) {
        DefenseState state = getOrCreate(player.getUUID());
        state.anchorEnabled = true;
        state.anchorDimension = dimension.location().toString();
        state.anchorX = x;
        state.anchorY = y;
        state.anchorZ = z;
        state.recalculate();
        LOGGER.info("[QuantumCore] 玩家 {} 已绑定绝对锚点 [{}]", player.getScoreboardName(), state.anchorDimension);
    }

    /**
     * 预设组合：启动双生虚相（假人 + 检索层调包 + 重定向）。
     * 如果锚点未设置，自动记录当前位置。
     */
    public static void engageMirage(ServerPlayer player) {
        DefenseState state = getOrCreate(player.getUUID());

        if (!state.anchorEnabled) {
            state.anchorEnabled = true;
            state.anchorDimension = player.level().dimension().location().toString();
            state.anchorX = player.getX();
            state.anchorY = player.getY();
            state.anchorZ = player.getZ();
        }

        MirageSystem.engage(player, state);
        state.mirageEnabled = true;
        state.recalculate();
    }

    /**
     * 启用凤凰涅槃（灵魂热插拔）。
     */
    public static void enablePhoenix(ServerPlayer player) {
        DefenseState state = getOrCreate(player.getUUID());
        state.phoenixEnabled = true;
        LOGGER.info("[QuantumCore] 玩家 {} 已启用凤凰涅槃。", player.getScoreboardName());
    }

    /**
     * 启用断连防护。
     */
    public static void enableNettyFirewall(ServerPlayer player) {
        DefenseState state = getOrCreate(player.getUUID());
        state.nettyEnabled = true;
        NettyFirewall.protect(player.getUUID());
        LOGGER.info("[QuantumCore] 玩家 {} 已启用断连防护。", player.getScoreboardName());
    }

    /**
     * 启用逻辑切除反击。
     */
    public static void enableLobotomy(ServerPlayer player) {
        DefenseState state = getOrCreate(player.getUUID());
        state.lobotomyEnabled = true;
        LOGGER.info("[QuantumCore] 玩家 {} 已启用逻辑切除反击。", player.getScoreboardName());
    }

    /**
     * 启用纯检索层调包（不生成假人，需外部提供代理）。
     */
    public static void enableShadowDisguise(ServerPlayer player) {
        DefenseState state = getOrCreate(player.getUUID());
        state.shadowEnabled = true;
        LOGGER.info("[QuantumCore] 玩家 {} 已启用检索层调包。", player.getScoreboardName());
    }

    /**
     * 解除玩家的所有防御状态。
     */
    public static void release(ServerPlayer player) {
        UUID uuid = player.getUUID();
        DefenseState state = STATES.get(uuid);
        if (state == null) return;

        // 先强制彻底清理虚相（防止残留引用导致内存泄漏）
        MirageSystem.forceCleanup(state);
        // 再执行标准解除流程（发送同步包等）
        MirageSystem.disengage(player, state);
        NettyFirewall.unprotect(uuid);
        PhoenixShellSystem.clearPhoenixFlag(uuid);
        STATES.remove(uuid);
        LOGGER.info("[QuantumCore] 玩家 {} 的防御状态已解除。", player.getScoreboardName());
    }

    public static boolean isProtected(UUID uuid) {
        DefenseState state = STATES.get(uuid);
        return state != null && state.isProtected();
    }

    public static DefenseState getState(UUID uuid) {
        return STATES.get(uuid);
    }

    // ==================== 内部：Mixin 委托分发 ====================

    public static void onPlayerHurt(ServerPlayer player, DamageSource source, float amount) {
        DefenseState state = STATES.get(player.getUUID());
        if (state == null) return;

        // L7 主动反击：追溯攻击者
        if (state.lobotomyEnabled && source.getEntity() != null) {
            LobotomyProtocol.onAttackDetected(source.getEntity());
        }

        // L2 行为层：按活跃策略分发
        switch (state.getActiveStrategy()) {
            case MIRAGE -> MirageSystem.onHurt(player, state, source, amount);
            case ANCHOR -> AnchorSystem.onHurt(player, state, source, amount);
            default -> { /* NONE */ }
        }
    }

    public static void onPlayerDie(ServerPlayer player, DamageSource source) {
        DefenseState state = STATES.get(player.getUUID());
        if (state == null) return;

        // L7 主动反击
        if (state.lobotomyEnabled && source.getEntity() != null) {
            LobotomyProtocol.onAttackDetected(source.getEntity());
        }

        switch (state.getActiveStrategy()) {
            case MIRAGE -> MirageSystem.onDie(player, state, source);
            case ANCHOR -> AnchorSystem.onDie(player, state, source);
            default -> { /* NONE */ }
        }
    }

    public static void onPlayerDiscard(ServerPlayer player) {
        DefenseState state = STATES.get(player.getUUID());
        if (state == null) return;

        switch (state.getActiveStrategy()) {
            case MIRAGE -> MirageSystem.onDiscard(player, state);
            case ANCHOR -> AnchorSystem.onDiscard(player, state);
            default -> { /* NONE */ }
        }
    }

    public static void onPlayerRemove(ServerPlayer player, net.minecraft.world.entity.Entity.RemovalReason reason) {
        DefenseState state = STATES.get(player.getUUID());
        if (state == null) return;

        // L6 终极兜底：Phoenix 灵魂热插拔
        if (state.phoenixEnabled && reason != net.minecraft.world.entity.Entity.RemovalReason.CHANGED_DIMENSION) {
            PhoenixShellSystem.extractSoul(player);
            // 注意：不 cancel，让恶意模组"杀死"旧躯壳
            // PhoenixShellMixin 会在 setRemoved 中处理涅槃
            return;
        }

        switch (state.getActiveStrategy()) {
            case MIRAGE -> MirageSystem.onRemove(player, state, reason);
            case ANCHOR -> AnchorSystem.onRemove(player, state, reason);
            default -> { /* NONE */ }
        }
    }

    public static void onPlayerSetPos(ServerPlayer player, double x, double y, double z) {
        DefenseState state = STATES.get(player.getUUID());
        if (state == null) return;

        switch (state.getActiveStrategy()) {
            case MIRAGE -> MirageSystem.onSetPos(player, state, x, y, z);
            case ANCHOR -> AnchorSystem.onSetPos(player, state, x, y, z);
            default -> { /* NONE */ }
        }
    }

    // ==================== Mixin 取消判断 ====================

    public static boolean shouldCancelHurt(ServerPlayer player) {
        DefenseState state = STATES.get(player.getUUID());
        if (state == null) return false;
        return state.getActiveStrategy() == DefenseStrategy.MIRAGE
            || state.getActiveStrategy() == DefenseStrategy.ANCHOR;
    }

    public static boolean shouldCancelDiscard(ServerPlayer player) {
        DefenseState state = STATES.get(player.getUUID());
        if (state == null) return false;
        return state.getActiveStrategy() == DefenseStrategy.MIRAGE
            || state.getActiveStrategy() == DefenseStrategy.ANCHOR;
    }

    public static boolean shouldCancelDie(ServerPlayer player) {
        DefenseState state = STATES.get(player.getUUID());
        if (state == null) return false;
        return state.getActiveStrategy() != DefenseStrategy.NONE;
    }

    public static boolean shouldCancelRemove(ServerPlayer player, net.minecraft.world.entity.Entity.RemovalReason reason) {
        DefenseState state = STATES.get(player.getUUID());
        if (state == null) return false;
        if (reason == net.minecraft.world.entity.Entity.RemovalReason.CHANGED_DIMENSION) return false;
        // Phoenix 模式下不 cancel，让旧躯壳被移除，由 PhoenixShell 处理涅槃
        if (state.phoenixEnabled) return false;
        return state.getActiveStrategy() != DefenseStrategy.NONE;
    }

    public static boolean shouldCancelSetPos(ServerPlayer player) {
        DefenseState state = STATES.get(player.getUUID());
        if (state == null) return false;
        return state.getActiveStrategy() == DefenseStrategy.MIRAGE;
    }

    // ==================== L3 状态欺骗 ====================

    public static boolean shouldGaslightAlive(ServerPlayer player) {
        if (!QuantumCoreConfig.ENABLE_STATE_GASLIGHTING.get()) return false;
        DefenseState state = STATES.get(player.getUUID());
        if (state == null) return false;
        return state.getActiveStrategy() == DefenseStrategy.MIRAGE || state.gaslighting;
    }

    public static boolean shouldGaslightRemoved(ServerPlayer player) {
        if (!QuantumCoreConfig.ENABLE_STATE_GASLIGHTING.get()) return false;
        DefenseState state = STATES.get(player.getUUID());
        if (state == null) return false;
        return state.getActiveStrategy() == DefenseStrategy.MIRAGE || state.gaslighting;
    }

    // ==================== 内部 tick ====================

    public static void onServerTick(MinecraftServer server) {
        for (DefenseState state : STATES.values()) {
            state.recalculate();
            if (state.getActiveStrategy() == DefenseStrategy.ANCHOR) {
                AnchorSystem.tick(server, state);
            } else if (state.getActiveStrategy() == DefenseStrategy.MIRAGE) {
                MirageSystem.tick(state);
            }
        }
        // 定期清理凤凰系统的过期数据（防止 extractSoul 后重组失败导致内存泄漏）
        PhoenixShellSystem.cleanupExpired();
    }

    // ==================== 辅助 ====================

    private static DefenseState getOrCreate(UUID uuid) {
        return STATES.computeIfAbsent(uuid, DefenseState::new);
    }
}
