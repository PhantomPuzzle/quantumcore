package com.matrix.quantumcore.api;

import net.minecraft.util.RandomSource;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

public class WeightedRandomPool<T> {
    private final NavigableMap<Double, T> pool = new TreeMap<>();
    private double totalWeight = 0.0;

    /**
     * 向随机池添加一个对象及其权重
     */
    public void add(double weight, T object) {
        if (weight <= 0.0 || object == null) return;
        totalWeight += weight;
        pool.put(totalWeight, object);
    }

    /**
     * $O(\log n)$ 级别的高效随机抽取
     */
    public T getRandom(RandomSource random) {
        if (pool.isEmpty() || totalWeight <= 0.0) {
            return null;
        }
        double target = random.nextDouble() * totalWeight;
        Map.Entry<Double, T> entry = pool.ceilingEntry(target);
        return entry != null ? entry.getValue() : pool.lastEntry().getValue();
    }

    /**
     * 重置/清空随机池（在重新加载配置或世界时使用）
     */
    public void clear() {
        pool.clear();
        totalWeight = 0.0;
    }

    /**
     * 将当前权重导出为 Map，供 AliasRandomPool 构建。
     */
    public java.util.Map<T, Double> exportWeights() {
        java.util.Map<T, Double> weights = new java.util.HashMap<>();
        double previous = 0.0;
        for (Map.Entry<Double, T> entry : pool.entrySet()) {
            double weight = entry.getKey() - previous;
            weights.put(entry.getValue(), weight);
            previous = entry.getKey();
        }
        return weights;
    }

    /**
     * 将当前动态池冻结为不可变的 O(1) AliasRandomPool。
     * 适用于构建完成后不再修改的场景（如服务器启动后的数据库）。
     */
    public AliasRandomPool<T> toAliasPool() {
        return new AliasRandomPool<>(exportWeights());
    }
}