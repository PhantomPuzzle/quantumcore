package com.matrix.quantumcore.defense.mixin;

import com.matrix.quantumcore.defense.DefenseManager;
import com.matrix.quantumcore.defense.context.QuantumContext;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 玩家状态欺骗 Mixin：isAlive / isRemoved。
 * L3 状态层：仅在双生虚相模式下，对外部调用者返回虚假状态，
 * 使恶意模组误以为目标已死亡/已移除。
 *
 * 通过 QuantumContext 的 ThreadLocal（显式）和 StackWalker（自动）双重隔离：
 * - 原版/Forge/本前置内部调用 → 返回真实值
 * - 外部模组调用 → 返回欺骗值
 *
 * 警告：此功能具有侵入性，通过配置文件默认关闭。
 */
@Mixin(Entity.class)
public class PlayerStateMixin {

    @Inject(method = "isAlive", at = @At("HEAD"), cancellable = true)
    private void quantum$onIsAlive(CallbackInfoReturnable<Boolean> cir) {
        if (!((Object) this instanceof Player player)) return;
        if (!(player instanceof net.minecraft.server.level.ServerPlayer serverPlayer)) return;

        // 优先使用 ThreadLocal 显式标记
        if (QuantumContext.isInternal()) return;
        // 再使用 StackWalker 自动分析（冗余但安全）
        if (QuantumContext.isTrustedCaller()) return;
        // 最后检查是否应启用欺骗
        if (!DefenseManager.shouldGaslightAlive(serverPlayer)) return;

        cir.setReturnValue(false); // 假装已死亡
    }

    @Inject(method = "isRemoved", at = @At("HEAD"), cancellable = true)
    private void quantum$onIsRemoved(CallbackInfoReturnable<Boolean> cir) {
        if (!((Object) this instanceof Player player)) return;
        if (!(player instanceof net.minecraft.server.level.ServerPlayer serverPlayer)) return;

        if (QuantumContext.isInternal()) return;
        if (QuantumContext.isTrustedCaller()) return;
        if (!DefenseManager.shouldGaslightRemoved(serverPlayer)) return;

        cir.setReturnValue(true); // 假装已被移除
    }
}
