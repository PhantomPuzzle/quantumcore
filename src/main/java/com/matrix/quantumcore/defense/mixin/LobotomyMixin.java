package com.matrix.quantumcore.defense.mixin;

import com.matrix.quantumcore.defense.lobotomy.LobotomyProtocol;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 逻辑切除 Mixin（LobotomyMixin）。
 * L7 主动反击：注入到最底层的 Entity.tick()。
 *
 * 当某个实体所属的 Class 被 LobotomyProtocol 标记为"处刑对象"时，
 * 该实体的 tick 更新被永久取消。效果：实体变成静止雕像，
 * 不执行 AI、不移动、不发包、不攻击、不触发任何事件。
 *
 * 这比杀死它更具威慑力——恶意模组的逻辑闭环已完成（以为攻击成功），
 * 但攻击者本身被冻结在时间中。
 */
@Mixin(Entity.class)
public class LobotomyMixin {

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void quantum$lobotomize(CallbackInfo ci) {
        Entity entity = (Entity) (Object) this;

        // 检查该实体是否已被切除逻辑
        if (LobotomyProtocol.isLobotomized(entity)) {
            ci.cancel(); // 剥夺 tick 更新，时间静止
        }
    }
}
