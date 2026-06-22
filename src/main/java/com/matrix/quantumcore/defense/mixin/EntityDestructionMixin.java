package com.matrix.quantumcore.defense.mixin;

import com.matrix.quantumcore.api.event.ObjectDestructionEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class EntityDestructionMixin {

    // 拦截实体被 discard (主动销毁) 或 remove (系统移除) 的时刻
    @Inject(method = "remove", at = @At("HEAD"), cancellable = true)
    private void quantum$onEntityRemove(Entity.RemovalReason reason, CallbackInfo ci) {
        Entity entity = (Entity) (Object) this;
        
        // 排除玩家，只针对非玩家实体或掉落物
        if (entity instanceof net.minecraft.world.entity.player.Player) return;

        // 如果是掉落物，提取里面的 ItemStack
        ItemStack stack = entity instanceof ItemEntity itemEntity ? itemEntity.getItem() : ItemStack.EMPTY;

        // 触发自定义事件
        ObjectDestructionEvent event = new ObjectDestructionEvent(entity, stack);
        boolean canceled = MinecraftForge.EVENT_BUS.post(event);

        // 如果主模组取消了事件，则阻止原生销毁逻辑
        if (canceled) {
            ci.cancel();
        }
    }
}