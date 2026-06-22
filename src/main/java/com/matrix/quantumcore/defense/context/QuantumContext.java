package com.matrix.quantumcore.defense.context;

/**
 * 量子上下文：双层隔离系统。
 * 
 * 第一层：ThreadLocal（显式标记）
 * 第二层：调用栈自动分析（Thread.getStackTrace，兼容 Forge 编译环境）
 * 
 * 通过两层隔离，确保 QuantumCore 内部调用能够获取真实状态，
 * 而外部模组（尤其是恶意模组）获取被欺骗/隔离的状态。
 */
public class QuantumContext {
    
    // ===== 第一层：显式 ThreadLocal 标记 =====
    private static final ThreadLocal<Boolean> INTERNAL = ThreadLocal.withInitial(() -> false);
    
    public static void enter() { INTERNAL.set(true); }
    public static void exit() { INTERNAL.set(false); }
    public static boolean isInternal() { return INTERNAL.get(); }
    
    public static void runInternal(Runnable action) {
        enter();
        try {
            action.run();
        } finally {
            exit();
        }
    }
    
    // ===== 第二层：调用栈自动分析 =====
    // 使用 Thread.getStackTrace() 替代 StackWalker（StackWalker 在 Forge 编译环境中不可用）
    // 性能略逊于 StackWalker，但在 Minecraft 服务器的调用频率下完全可接受
    
    /**
     * 检查当前调用链是否来自外部模组。
     * 遍历调用栈，如果发现调用者属于原版、Forge内核或本前置，则返回 false（放行）。
     * 否则说明有外部模组在操作，返回 true（应触发欺骗/隔离）。
     */
    public static boolean isExternalCaller() {
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        for (StackTraceElement element : stack) {
            String name = element.getClassName();
            // 跳过 Thread 和 QuantumContext 自身
            if (name.equals("java.lang.Thread") || name.equals("com.matrix.quantumcore.defense.context.QuantumContext")) {
                continue;
            }
            // 放行：原版、Forge、本前置
            if (name.startsWith("net.minecraft.") || 
                name.startsWith("net.minecraftforge.") || 
                name.startsWith("com.matrix.quantumcore.")) {
                continue;
            }
            // 发现外部调用者
            return true;
        }
        return false;
    }
    
    /**
     * 检查当前调用链是否来自原版/Forge/本前置。
     * 如果栈中任意一帧是外部模组，则返回 false。
     */
    public static boolean isTrustedCaller() {
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        for (StackTraceElement element : stack) {
            String name = element.getClassName();
            // 跳过 Thread 和 QuantumContext 自身
            if (name.equals("java.lang.Thread") || name.equals("com.matrix.quantumcore.defense.context.QuantumContext")) {
                continue;
            }
            // 发现外部调用者
            if (!name.startsWith("net.minecraft.") && 
                !name.startsWith("net.minecraftforge.") && 
                !name.startsWith("com.matrix.quantumcore.")) {
                return false;
            }
        }
        return true;
    }
}
