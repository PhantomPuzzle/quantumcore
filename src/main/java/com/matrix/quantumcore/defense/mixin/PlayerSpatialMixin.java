package com.matrix.quantumcore.defense.mixin;

import com.matrix.quantumcore.defense.DefenseManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 玩家空间位移 Mixin：setPos。
 * L2 行为层：在双生虚相模式下，所有位移被重定向到假人，真玩家位置锁定。
 */
@Mixin(Entity.class)
public class PlayerSpatialMixin {

    @Inject(method = "setPos(DDD)V", at = @At("HEAD"), cancellable = true)
    private void quantum$onSetPos(double x, double y, double z, CallbackInfo ci) {
        if (!((Object) this instanceof Player player)) return;
        if (!(player instanceof net.minecraft.server.level.ServerPlayer serverPlayer)) return;

        DefenseManager.onPlayerSetPos(serverPlayer, x, y, z);
        if (DefenseManager.shouldCancelSetPos(serverPlayer)) {
            ci.cancel();
        }
    }
}
