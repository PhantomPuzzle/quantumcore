package com.matrix.quantumcore.defense.mixin;

import com.matrix.quantumcore.defense.context.QuantumContext;
import com.matrix.quantumcore.defense.shadow.ShadowDisguiseManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

/**
 * 玩家列表检索劫持 Mixin（PlayerListMixin）。
 * L1 检索层：拦截 getPlayer(UUID)，当外部模组查询受保护玩家时，返回假人指针。
 *
 * 这是 ShadowDisguiseManager 的核心注入点之一。
 * 原版内核、Forge、本前置的调用不受影响（StackWalker 放行）。
 */
@Mixin(PlayerList.class)
public class PlayerListMixin {

    @Inject(method = "getPlayer", at = @At("RETURN"), cancellable = true)
    private void quantum$getFakePlayerInstance(UUID uuid, CallbackInfoReturnable<ServerPlayer> cir) {
        ServerPlayer realPlayer = cir.getReturnValue();
        if (realPlayer == null) return;
        if (!ShadowDisguiseManager.hasShadow(uuid)) return;

        // 使用 ThreadLocal 快速检查 + StackWalker 精确分析
        if (QuantumContext.isInternal()) return;
        if (QuantumContext.isTrustedCaller()) return;

        // 调包：将返回值替换为假人
        cir.setReturnValue(ShadowDisguiseManager.getShadow(uuid));
    }
}
