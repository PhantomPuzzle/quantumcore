package com.matrix.quantumcore.defense;

import com.matrix.quantumcore.defense.mirage.MirageContext;

import java.util.UUID;

/**
 * 单个玩家的防御状态快照。
 * 从互斥策略模式改为功能开关位图模式：
 * 每个子系统（锚点、虚相、影子、凤凰、断连、切除）独立开关，
 * 主模组可自由组合，DefenseManager 负责优先级分发。
 */
public class DefenseState {
    public final UUID uuid;

    // ===== 功能开关（独立） =====
    public boolean anchorEnabled = false;   // 锚点兜底
    public boolean mirageEnabled = false;   // 双生虚相（含假人 + Shadow 注册）
    public boolean shadowEnabled = false;    // 纯检索层调包（不生成假人）
    public boolean phoenixEnabled = false;   // 灵魂热插拔
    public boolean nettyEnabled = false;     // 断连防护
    public boolean lobotomyEnabled = false;  // 逻辑切除反击

    // ===== 锚点数据 =====
    public double anchorX, anchorY, anchorZ;
    public String anchorDimension = "";

    // ===== 虚相数据 =====
    public MirageContext mirage = null;

    // ===== 状态欺骗（局部覆盖） =====
    public boolean gaslighting = false;

    // ===== 动态策略（只读，由 recalculate 计算） =====
    private DefenseStrategy activeStrategy = DefenseStrategy.NONE;

    public DefenseState(UUID uuid) {
        this.uuid = uuid;
    }

    public DefenseStrategy getActiveStrategy() {
        return this.activeStrategy;
    }

    /**
     * 重新计算活跃策略（用于 Mixin 优先级分发）。
     * 优先级：MIRAGE > ANCHOR > NONE
     * 其他功能（Phoenix、Netty、Lobotomy、Shadow）为独立开关，不通过此策略控制。
     */
    public void recalculate() {
        if (mirageEnabled && mirage != null && mirage.isValid()) {
            this.activeStrategy = DefenseStrategy.MIRAGE;
        } else if (anchorEnabled) {
            this.activeStrategy = DefenseStrategy.ANCHOR;
        } else {
            this.activeStrategy = DefenseStrategy.NONE;
        }
    }

    public boolean isProtected() {
        return this.activeStrategy != DefenseStrategy.NONE
            || this.phoenixEnabled
            || this.nettyEnabled
            || this.lobotomyEnabled
            || this.shadowEnabled;
    }
}
