package com.matrix.quantumcore.defense.mirage;

import net.minecraft.server.level.ServerPlayer;

/**
 * 双生对数据结构：记录真玩家与假人的映射关系。
 */
public class MirageContext {
    public final ServerPlayer realPlayer;
    public final QuantumFakePlayer decoyPlayer;

    public MirageContext(ServerPlayer realPlayer, QuantumFakePlayer decoyPlayer) {
        this.realPlayer = realPlayer;
        this.decoyPlayer = decoyPlayer;
    }

    public boolean isValid() {
        return realPlayer != null
            && decoyPlayer != null
            && decoyPlayer.isAlive()
            && !decoyPlayer.isRemoved();
    }
}
