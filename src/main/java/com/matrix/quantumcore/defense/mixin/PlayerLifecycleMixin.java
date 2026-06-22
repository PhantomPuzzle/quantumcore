package com.matrix.quantumcore.defense.mixin;

import com.matrix.quantumcore.defense.DefenseManager;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 玩家生命周期 Mixin：hurt / die。
 * 所有拦截逻辑委托给 DefenseManager，由它根据 DefenseState 的各功能开关决定处理方式。
 *
 * L2 行为层：在恶意模组调用 player.hurt() / player.die() 时，
 * 将执行重定向到假人（MIRAGE）或直接取消（ANCHOR）。
 * L7 主动反击：LobotomyProtocol 在 hurt/die 时追溯攻击者。
 *
 * 注意：discard 和 remove 已迁移到 EntityDiscardMixin（注入到 Entity.class），
 * 因为这两个方法在 Entity 中定义，在 LivingEntity 中注入会导致 APT 描述符推断失败。
 */
@Mixin(LivingEntity.class)
public class PlayerLifecycleMixin {

    @Inject(method = "hurt", at = @At("HEAD"), cancellable = true)
    private void quantum$onHurt(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        if (!((Object) this instanceof Player player)) return;
        if (!(player instanceof net.minecraft.server.level.ServerPlayer serverPlayer)) return;

        DefenseManager.onPlayerHurt(serverPlayer, source, amount);
        if (DefenseManager.shouldCancelHurt(serverPlayer)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "die", at = @At("HEAD"), cancellable = true)
    private void quantum$onDie(DamageSource source, CallbackInfo ci) {
        if (!((Object) this instanceof Player player)) return;
        if (!(player instanceof net.minecraft.server.level.ServerPlayer serverPlayer)) return;

        DefenseManager.onPlayerDie(serverPlayer, source);
        if (DefenseManager.shouldCancelDie(serverPlayer)) {
            player.setHealth(player.getMaxHealth());
            ci.cancel();
        }
    }
}
