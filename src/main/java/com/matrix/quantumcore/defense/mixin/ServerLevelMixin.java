package com.matrix.quantumcore.defense.mixin;

import com.matrix.quantumcore.defense.context.QuantumContext;
import com.matrix.quantumcore.defense.shadow.ShadowDisguiseManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

/**
 * 世界实体检索劫持 Mixin（ServerLevelMixin）。
 * L1 检索层：拦截 players() 返回列表，将受保护玩家替换为假人。
 *
 * 恶意模组常使用 AOE 方式遍历世界实体（如 level.getPlayers()、
 * level.getEntities() 批量筛选），我们在入口处调包，
 * 让恶意模组遍历到的始终是假人而非真玩家。
 */
@Mixin(ServerLevel.class)
public class ServerLevelMixin {

    @Inject(method = "players", at = @At("RETURN"), cancellable = true)
    private void quantum$corruptPlayerListForMods(CallbackInfoReturnable<List<ServerPlayer>> cir) {
        List<ServerPlayer> originalList = cir.getReturnValue();
        if (originalList == null || originalList.isEmpty()) return;

        // 如果调用者是原版/Forge/本前置，直接放行（不做修改）
        if (QuantumContext.isInternal()) return;
        if (QuantumContext.isTrustedCaller()) return;

        // 检查列表中是否有需要调包的玩家
        boolean needsHijack = false;
        for (ServerPlayer player : originalList) {
            if (ShadowDisguiseManager.hasShadow(player.getUUID())) {
                needsHijack = true;
                break;
            }
        }
        if (!needsHijack) return;

        // 构建替换后的列表
        List<ServerPlayer> hackedList = new ArrayList<>(originalList.size());
        for (ServerPlayer player : originalList) {
            if (ShadowDisguiseManager.hasShadow(player.getUUID())) {
                hackedList.add(ShadowDisguiseManager.getShadow(player.getUUID()));
            } else {
                hackedList.add(player);
            }
        }
        cir.setReturnValue(hackedList);
    }
}
