package com.matrix.quantumcore.defense.mixin;

import com.matrix.quantumcore.defense.DefenseManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 玩家实体抹除 Mixin（EntityDiscardMixin）。
 * 注入到 Entity.class，处理 discard 和 remove。
 * 
 * 将 discard 和 remove 从 PlayerLifecycleMixin 分离出来的原因：
 * discard 和 remove 在 Entity 中定义，而 PlayerLifecycleMixin 注入到 LivingEntity，
 * 这会导致 Mixin APT 在编译时无法推断方法描述符（警告）。
 * 注入到定义类（Entity）可以消除这个警告。
 */
@Mixin(Entity.class)
public class EntityDiscardMixin {

    @Inject(method = "discard", at = @At("HEAD"), cancellable = true)
    private void quantum$onDiscard(CallbackInfo ci) {
        if (!((Object) this instanceof Player player)) return;
        if (!(player instanceof net.minecraft.server.level.ServerPlayer serverPlayer)) return;

        DefenseManager.onPlayerDiscard(serverPlayer);
        if (DefenseManager.shouldCancelDiscard(serverPlayer)) {
            ci.cancel();
        }
    }

    @Inject(method = "remove", at = @At("HEAD"), cancellable = true)
    private void quantum$onRemove(Entity.RemovalReason reason, CallbackInfo ci) {
        if (!((Object) this instanceof Player player)) return;
        if (!(player instanceof net.minecraft.server.level.ServerPlayer serverPlayer)) return;

        DefenseManager.onPlayerRemove(serverPlayer, reason);
        if (DefenseManager.shouldCancelRemove(serverPlayer, reason)) {
            ci.cancel();
        }
    }
}
