package com.matrix.quantumcore.defense.mirage;

import com.matrix.quantumcore.defense.DefenseState;
import com.matrix.quantumcore.defense.context.QuantumContext;
import com.matrix.quantumcore.defense.shadow.ShadowDisguiseManager;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * 双生虚相系统（Twin Mirage System）。
 *
 * 核心职责：
 * 1. 为受保护玩家生成 QuantumFakePlayer 假人替身
 * 2. 将假人注册到 ShadowDisguiseManager（检索层调包）
 * 3. 在 hurt/die/discard/remove/setPos 时重定向到假人
 * 4. 解除时彻底清理引用，防止内存泄漏，并强制同步客户端位置
 *
 * 与 ShadowDisguise 的关系：
 * - ShadowDisguise 负责"检索层调包"（getPlayer / players / getEntities）
 * - MirageSystem 负责"假人生命周期管理"（生成/销毁/重定向）
 * - 两者共用同一个 QuantumFakePlayer 实体
 */
public class MirageSystem {
    private static final Logger LOGGER = LogManager.getLogger();

    /**
     * 启动双生虚相：创建假人，注册到 ShadowDisguise，真玩家虚化。
     */
    public static void engage(ServerPlayer realPlayer, DefenseState state) {
        if (state.mirage != null && state.mirage.isValid()) {
            LOGGER.info("[Mirage] 玩家 {} 已存在有效假人，跳过重复启动。", realPlayer.getScoreboardName());
            return;
        }

        // 如果之前有残留数据，先彻底清理（防内存泄漏）
        forceCleanup(state);

        QuantumFakePlayer decoy = QuantumFakePlayer.fromPlayer(realPlayer);

        if (!realPlayer.serverLevel().addFreshEntity(decoy)) {
            LOGGER.error("[Mirage] 假人加入世界失败！玩家 {} 虚相启动失败。", realPlayer.getScoreboardName());
            state.mirage = null;
            state.mirageEnabled = false;
            return;
        }

        // 真玩家进入虚化状态
        realPlayer.setInvisible(true);
        realPlayer.setNoGravity(true);
        realPlayer.setSilent(true);

        // 建立双生对
        state.mirage = new MirageContext(realPlayer, decoy);

        // 将假人注册到检索层调包系统
        ShadowDisguiseManager.registerShadow(realPlayer.getUUID(), decoy);

        LOGGER.info("[Mirage] 玩家 {} 双生虚相已启动，假人 UID: {}",
                realPlayer.getScoreboardName(), decoy.getUUID());
    }

    /**
     * 解除双生虚相：移除假人，注销 ShadowDisguise，真玩家恢复，强制同步客户端。
     */
    public static void disengage(ServerPlayer realPlayer, DefenseState state) {
        if (state.mirage == null) {
            // 即使 mirage 为 null，也确保 ShadowDisguise 被清理
            ShadowDisguiseManager.unregisterShadow(realPlayer.getUUID());
            return;
        }

        QuantumFakePlayer decoy = state.mirage.decoyPlayer;
        if (decoy != null && decoy.isAlive() && !decoy.isRemoved()) {
            decoy.discard();
        }

        // 真玩家恢复
        realPlayer.setInvisible(false);
        realPlayer.setNoGravity(false);
        realPlayer.setSilent(false);

        // 注销检索层调包
        ShadowDisguiseManager.unregisterShadow(realPlayer.getUUID());

        // 记录真玩家当前位置，用于同步
        double syncX = realPlayer.getX();
        double syncY = realPlayer.getY();
        double syncZ = realPlayer.getZ();

        // 彻底清理 MirageContext 引用，防止内存泄漏
        state.mirage = null;
        state.mirageEnabled = false;

        // 强制向客户端发送位置同步包，消除 Rubberbanding
        if (realPlayer.connection != null) {
            realPlayer.connection.send(new ClientboundTeleportEntityPacket(realPlayer));
            LOGGER.info("[Mirage] 已向客户端 {} 发送强制位置同步包 [{}, {}, {}]",
                    realPlayer.getScoreboardName(), syncX, syncY, syncZ);
        }

        LOGGER.info("[Mirage] 玩家 {} 的双生虚相已解除，引用已彻底清理。", realPlayer.getScoreboardName());
    }

    /**
     * 强制清理：无论状态如何，彻底清空所有与虚相相关的引用。
     * 供 DefenseManager.release() 和 engage() 调用，防止高频触发导致的内存泄漏。
     */
    public static void forceCleanup(DefenseState state) {
        if (state.mirage != null) {
            QuantumFakePlayer decoy = state.mirage.decoyPlayer;
            if (decoy != null && decoy.isAlive() && !decoy.isRemoved()) {
                try {
                    decoy.discard();
                } catch (Exception e) {
                    LOGGER.warn("[Mirage] 强制清理时 discard 假人失败: {}", e.getMessage());
                }
            }
            state.mirage = null;
        }
        ShadowDisguiseManager.unregisterShadow(state.uuid);
        state.mirageEnabled = false;
    }

    /**
     * 每 tick 检查假人状态。如果失效，则 DefenseState.recalculate() 会自动回退到 ANCHOR。
     */
    public static void tick(DefenseState state) {
        if (state.mirage == null || !state.mirage.isValid()) {
            LOGGER.warn("[Mirage] 玩家 {} 的假人失效，虚相回退到锚点兜底。", state.uuid);
            // 假人失效时彻底清理，防止僵尸引用
            forceCleanup(state);
        }
    }

    // ==================== Mixin 委托：重定向到假人 ====================

    public static void onHurt(ServerPlayer realPlayer, DefenseState state, DamageSource source, float amount) {
        if (state.mirage == null || !state.mirage.isValid()) return;

        QuantumContext.runInternal(() -> {
            QuantumFakePlayer decoy = state.mirage.decoyPlayer;
            decoy.hurt(source, amount);
            LOGGER.warn("[Mirage] 玩家 {} 的伤害已重定向至假人 ({} 点)",
                    realPlayer.getScoreboardName(), amount);
        });
    }

    public static void onDie(ServerPlayer realPlayer, DefenseState state, DamageSource source) {
        if (state.mirage == null || !state.mirage.isValid()) return;

        QuantumContext.runInternal(() -> {
            QuantumFakePlayer decoy = state.mirage.decoyPlayer;
            // die() 为 protected，通过给予 MAX_VALUE 伤害触发自然死亡
            decoy.hurt(source, Float.MAX_VALUE);
            LOGGER.warn("[Mirage] 玩家 {} 的死亡进程已重定向至假人", realPlayer.getScoreboardName());
        });
    }

    public static void onDiscard(ServerPlayer realPlayer, DefenseState state) {
        if (state.mirage == null || !state.mirage.isValid()) return;

        QuantumContext.runInternal(() -> {
            QuantumFakePlayer decoy = state.mirage.decoyPlayer;
            decoy.discard();
            LOGGER.warn("[Mirage] 玩家 {} 的 discard 已重定向至假人，假人替死", realPlayer.getScoreboardName());
        });
    }

    public static void onRemove(ServerPlayer realPlayer, DefenseState state, net.minecraft.world.entity.Entity.RemovalReason reason) {
        if (reason == net.minecraft.world.entity.Entity.RemovalReason.CHANGED_DIMENSION) return;
        if (state.mirage == null || !state.mirage.isValid()) return;

        QuantumContext.runInternal(() -> {
            QuantumFakePlayer decoy = state.mirage.decoyPlayer;
            decoy.remove(reason);
            LOGGER.warn("[Mirage] 玩家 {} 的底层移除 (Reason: {}) 已重定向至假人",
                    realPlayer.getScoreboardName(), reason);
        });
    }

    public static void onSetPos(ServerPlayer realPlayer, DefenseState state, double x, double y, double z) {
        if (state.mirage == null || !state.mirage.isValid()) return;

        QuantumContext.runInternal(() -> {
            QuantumFakePlayer decoy = state.mirage.decoyPlayer;
            decoy.setPos(x, y, z);
            LOGGER.warn("[Mirage] 玩家 {} 的位置位移已重定向至假人", realPlayer.getScoreboardName());
        });
    }
}
