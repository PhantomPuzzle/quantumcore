package com.matrix.quantumcore.defense.mixin;

import com.matrix.quantumcore.defense.DefenseManager;
import com.matrix.quantumcore.defense.phoenix.PhoenixShellSystem;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 凤凰涅槃 Mixin（PhoenixShellMixin）。
 * L6 终极兜底：在 Entity.setRemoved 被调用时触发。
 *
 * 当所有其他拦截机制（hurt/die/discard/remove 的 HEAD 拦截）都失效时，
 * 如果玩家仍被物理移除（例如反射、异步线程、底层字段修改），
 * 此 Mixin 触发灵魂热插拔：
 * 1. 剥离旧对象的 connection 和 NBT 数据
 * 2. 实例化新 ServerPlayer，注入原 connection
 * 3. 将新玩家塞回 PlayerList 和 ServerLevel
 * 4. 客户端无感知，网络套接字从未断开
 *
 * 注意：我们不 cancel setRemoved，而是让它"杀死"旧躯壳，
 * 然后立即在原位重塑一个全新因果实体。
 *
 * 注入到 Entity.class（而非 ServerPlayer.class），因为 setRemoved 在 Entity 中定义，
 * 这样可以避免 Mixin APT 无法推断跨继承链方法描述符的问题。
 */
@Mixin(Entity.class)
public class PhoenixShellMixin {

    @Inject(method = "setRemoved", at = @At("HEAD"))
    private void quantum$onSetRemoved(net.minecraft.world.entity.Entity.RemovalReason reason, CallbackInfo ci) {
        Entity entity = (Entity) (Object) this;
        
        // 只处理玩家实体
        if (!(entity instanceof Player)) return;
        if (!(entity instanceof ServerPlayer player)) return;

        // 检查是否处于凤凰保护状态
        if (!DefenseManager.isProtected(player.getUUID())) return;

        // 获取 DefenseState 确认 Phoenix 开关
        var state = DefenseManager.getState(player.getUUID());
        if (state == null || !state.phoenixEnabled) return;

        // 跨维度传送不触发
        if (reason == net.minecraft.world.entity.Entity.RemovalReason.CHANGED_DIMENSION) return;

        // 如果旧对象已经被凤凰处理过，不再重复
        if (PhoenixShellSystem.isPhoenixed(player.getUUID())) return;

        // 阶段一：灵魂抽离
        PhoenixShellSystem.extractSoul(player);

        // 阶段二：涅槃重组（立即执行，在同一 tick 内）
        ServerPlayer newPlayer = PhoenixShellSystem.reincarnate(player);

        if (newPlayer != null) {
            // 同步客户端权限
            newPlayer.server.getPlayerList().sendPlayerPermissionLevel(newPlayer);
        }
    }
}
