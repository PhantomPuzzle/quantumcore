package com.matrix.quantumcore.defense.mixin;

import java.util.UUID;
import com.matrix.quantumcore.defense.netty.NettyFirewall;
import net.minecraft.network.chat.Component;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Netty 防火墙 Mixin（NettyFirewallMixin）。
 * L4 网络层：拦截 ServerGamePacketListenerImpl.disconnect() 方法。
 *
 * 无论恶意模组包装了多少层代码，最终都必须调用 disconnect() 来踢出玩家。
 * 我们在该方法 HEAD 处切入，检查：
 * 1. 该玩家是否受 NettyFirewall 保护
 * 2. 调用者是否来自外部模组
 * 如果都满足，则 cancel 断连，强行维持 TCP 管道。
 */
@Mixin(ServerGamePacketListenerImpl.class)
public class NettyFirewallMixin {

    @Inject(method = "disconnect", at = @At("HEAD"), cancellable = true)
    private void quantum$preventMaliciousKick(Component reason, CallbackInfo ci) {
        ServerGamePacketListenerImpl connection = (ServerGamePacketListenerImpl) (Object) this;
        if (connection.player == null) return;

        var player = connection.player;
        UUID uuid = player.getUUID();

        if (NettyFirewall.shouldBlockDisconnect(uuid)) {
            LogManager.getLogger().warn("[Netty] 拦截到针对受保护玩家 {} 的非法断连请求，已驳回。", player.getScoreboardName());
            ci.cancel();
        }
    }
}
