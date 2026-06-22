package com.matrix.quantumcore.defense.mirage;

import com.mojang.authlib.GameProfile;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.util.FakePlayer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * 量子假人：继承 Forge 的 FakePlayer，用于承接对真玩家的所有恶意操作。
 * 外观与装备尽可能复制真玩家，使服务端 AI 和模组逻辑将其视为真实目标。
 */
public class QuantumFakePlayer extends FakePlayer {
    private static final Logger LOGGER = LogManager.getLogger();

    public QuantumFakePlayer(ServerLevel level, GameProfile profile) {
        super(level, profile);
    }

    /**
     * 从真玩家复制所有可见状态（装备、药水、姿态等）。
     */
    public static QuantumFakePlayer fromPlayer(ServerPlayer realPlayer) {
        ServerLevel level = realPlayer.serverLevel();
        GameProfile profile = realPlayer.getGameProfile();

        QuantumFakePlayer decoy = new QuantumFakePlayer(level, profile);

        // 复制位置与旋转
        decoy.moveTo(
                realPlayer.getX(), realPlayer.getY(), realPlayer.getZ(),
                realPlayer.getYRot(), realPlayer.getXRot()
        );

        // 复制装备
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack stack = realPlayer.getItemBySlot(slot);
            if (!stack.isEmpty()) {
                decoy.setItemSlot(slot, stack.copy());
            }
        }

        // 复制当前生命值（满血）
        decoy.setHealth(decoy.getMaxHealth());

        // 关闭假人本身的 AI 更新（FakePlayer 默认无 AI，但保险起见）
        decoy.setNoGravity(false);
        decoy.setOnGround(true);

        LOGGER.info("[QuantumCore/Mirage] 假人已实例化并复制玩家 {} 的状态", realPlayer.getScoreboardName());
        return decoy;
    }

    @Override
    public boolean hurt(net.minecraft.world.damagesource.DamageSource source, float amount) {
        // 让假人正常承受伤害，触发原版受伤逻辑（击退、着火等）
        boolean result = super.hurt(source, amount);
        if (result) {
            LOGGER.warn("[QuantumCore/Mirage] 假人 {} 代受 {} 点伤害，来源: {}",
                    this.getScoreboardName(), amount, source.getMsgId());
        }
        return result;
    }

    @Override
    public void remove(net.minecraft.world.entity.Entity.RemovalReason reason) {
        LOGGER.warn("[QuantumCore/Mirage] 假人 {} 被移除 (Reason: {})", this.getScoreboardName(), reason);
        super.remove(reason);
    }

    // 注意：discard() 在 Entity 中是 final 的，不能覆盖。
    // 当外部模组调用 decoy.discard() 时，会触发 remove(Entity.RemovalReason.DISCARDED)，
    // 已被上面的 remove() 覆盖拦截。
}
