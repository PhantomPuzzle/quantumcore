package com.matrix.quantumcore;

import com.matrix.quantumcore.api.RegistryDataIndex;
import com.matrix.quantumcore.config.QuantumCoreConfig;
import com.matrix.quantumcore.defense.DefenseManager;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;

@Mod(QuantumCoreMod.MODID)
public class QuantumCoreMod {
    public static final String MODID = "quantumcore";

    public QuantumCoreMod() {
        // 注册配置文件
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, QuantumCoreConfig.GENERAL_SPEC);

        // 注册 Forge 事件总线
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onServerAboutToStart(ServerAboutToStartEvent event) {
        // 当服务器准备启动，且所有的标签(Tags)和注册表完全冻结完毕时，动态构建高效率复合随机抽样池
        RegistryDataIndex.rebuildDatabase();
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        // 驱动防御系统的每 tick 检测（锚点兜底 + 虚相状态检查）
        DefenseManager.onServerTick(event.getServer());
    }

    // ==================== 配置热重载 ====================

    /**
     * 当 Forge 检测到配置文件被外部修改时，自动触发数据库重建。
     * 无需重启服务器，修改 .toml 后立即生效。
     */
    @SubscribeEvent
    public void onConfigReload(ModConfigEvent.Reloading event) {
        if (event.getConfig().getType() == ModConfig.Type.COMMON) {
            RegistryDataIndex.reload();
        }
    }

    // ==================== 管理员命令 ====================

    /**
     * 注册 /quantumcore reload 命令，供管理员手动触发数据库热重载。
     * 权限等级：4（OP/控制台）。
     */
    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
            Commands.literal("quantumcore")
                .requires(source -> source.hasPermission(4))
                .then(Commands.literal("reload")
                    .executes(ctx -> {
                        RegistryDataIndex.reload();
                        ctx.getSource().sendSuccess(
                            () -> Component.literal("[QuantumCore] 数据库已热重载。"),
                            true
                        );
                        return 1;
                    })
                )
        );
    }
}
