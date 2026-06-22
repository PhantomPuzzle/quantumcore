package com.matrix.quantumcore.defense;

/**
 * 防御策略枚举。
 * 决定针对单个玩家的防御方式。
 */
public enum DefenseStrategy {
    /**
     * 无防御。
     */
    NONE,

    /**
     * 仅锚点兜底：记录安全坐标，每 tick 检测异常并修复。
     */
    ANCHOR,

    /**
     * 双生虚相：启用假人替身，伤害与操作全部重定向到假人。
     * 锚点信息仍被保留，作为虚相失效后的 fallback。
     */
    MIRAGE,

    /**
     * 混合模式：当假人存在时优先 MIRAGE，否则回退 ANCHOR。
     * 这是默认推荐策略。
     */
    HYBRID
}
