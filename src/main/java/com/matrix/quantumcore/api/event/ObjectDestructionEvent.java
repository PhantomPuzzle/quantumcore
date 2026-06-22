package com.matrix.quantumcore.api.event;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.Event;

/**
 * 自定义前置事件：当游戏内任何判定对象（物品堆或非玩家实体）因非自然原因或系统销毁时触发。
 */
public class ObjectDestructionEvent extends Event {
    private final Entity sourceEntity;
    private final ItemStack destroyedItem;

    public ObjectDestructionEvent(Entity sourceEntity, ItemStack destroyedItem) {
        this.sourceEntity = sourceEntity;
        this.destroyedItem = destroyedItem;
    }

    public Entity getSourceEntity() { return sourceEntity; }
    public ItemStack getDestroyedItem() { return destroyedItem; }

    @Override
    public boolean isCancelable() { return true; } // 允许主模组强制拦截并阻止对象的销毁判定
}