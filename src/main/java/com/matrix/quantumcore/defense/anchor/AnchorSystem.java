package com.matrix.quantumcore.defense.anchor;

import com.matrix.quantumcore.defense.DefenseState;
import net.minecraft.network.protocol.game.ClientboundSetHealthPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * 绝对锚点系统（精简版）。
 * 职责：在 MIRAGE 不可用时，作为兜底修复机制。
 * 每 tick 检测：血量异常、位置偏移、虚空坠落 → 强制修复/传送。
 */
public class AnchorSystem {
    private static final Logger LOGGER = LogManager.getLogger();

    /**
     * 每 tick 对处于 ANCHOR 策略的玩家执行检测与修复。
     */
    public static void tick(MinecraftServer server, DefenseState state) {
        // 找到玩家实例
        if (server == null) return;
        ServerPlayer player = server.getPlayerList().getPlayer(state.uuid);
        if (player == null) return; // 离线不处理

        // 1. 状态异常修正（血量、死亡标记）
        if (player.isDeadOrDying() || player.getHealth() <= 0.0f) {
            LOGGER.warn("[QuantumCore/Anchor] 检测到玩家 {} 异常数据态，执行元数据覆写...", player.getScoreboardName());
            player.setHealth(player.getMaxHealth());
            player.deathTime = 0;
            player.hurtTime = 0;
            player.removeAllEffects();

            if (player.connection != null) {
                player.connection.send(new ClientboundSetHealthPacket(
                        player.getHealth(),
                        player.getFoodData().getFoodLevel(),
                        player.getFoodData().getSaturationLevel()
                ));
            }
        }

        // 2. 锚点位置校验（防虚空坠落、防恶意传送）
        ResourceKey<Level> targetDim = ResourceKey.create(
                net.minecraft.core.registries.Registries.DIMENSION,
                new net.minecraft.resources.ResourceLocation(state.anchorDimension)
        );
        ServerLevel targetLevel = server.getLevel(targetDim);
        if (targetLevel != null) {
            boolean wrongDimension = player.level().dimension() != targetDim;
            boolean tooFar = player.distanceToSqr(state.anchorX, state.anchorY, state.anchorZ) > 10000;
            boolean outOfWorld = player.getY() < targetLevel.getMinBuildHeight() - 64;

            if (wrongDimension || tooFar || outOfWorld) {
                LOGGER.warn("[QuantumCore/Anchor] 玩家 {} 偏离锚点或跌入虚空，执行量子重定位...", player.getScoreboardName());
                player.teleportTo(targetLevel, state.anchorX, state.anchorY, state.anchorZ, player.getYRot(), player.getXRot());
            }
        }
    }

    // ==================== Mixin 委托回调 ====================

    public static void onHurt(ServerPlayer player, DefenseState state, DamageSource source, float amount) {
        // 锚点模式下直接取消伤害（保留原逻辑）
    }

    public static void onDie(ServerPlayer player, DefenseState state, DamageSource source) {
        // 锚点模式下直接取消死亡（由 Mixin 的 shouldCancelDie 控制）
        // 这里做额外修复：强制满血
        player.setHealth(player.getMaxHealth());
    }

    public static void onDiscard(ServerPlayer player, DefenseState state) {
        LOGGER.warn("[QuantumCore/Anchor] 拦截玩家 {} 的底层 discard 尝试", player.getScoreboardName());
    }

    public static void onRemove(ServerPlayer player, DefenseState state, Entity.RemovalReason reason) {
        LOGGER.warn("[QuantumCore/Anchor] 拦截玩家 {} 的非法底层移除 (Reason: {})", player.getScoreboardName(), reason);
    }

    public static void onSetPos(ServerPlayer player, DefenseState state, double x, double y, double z) {
        // 锚点模式下不拦截位置设置（由 tick 每帧拉回）
    }
}
