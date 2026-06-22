package com.matrix.quantumcore.api;

import net.minecraft.util.RandomSource;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;

/**
 * Alias Random Pool — Walker's Alias Method 实现。
 *
 * 适用于：离散分布、频繁采样、总体几乎不变 的场景。
 * - 初始化：O(n)
 * - 每次采样：O(1)
 * - 内存：O(n)
 *
 * 相比 WeightedRandomPool（TreeMap O(log n)），在高频抽样时性能更优，
 * 且避免了 TreeMap 节点的内存开销和 GC 压力。
 *
 * 构建完成后不可修改（Immutable），如果需要更新权重，重新构建一个新实例。
 */
public class AliasRandomPool<T> {
    private final List<T> items;
    private final double[] prob;
    private final int[] alias;
    private final int size;

    /**
     * 使用 Vose 变体算法构建 Alias 表。
     *
     * @param weights 权重映射（value 不能为 null，weight 必须 > 0）
     */
    public AliasRandomPool(Map<T, Double> weights) {
        // 先过滤掉无效项，收集到有效列表中
        List<T> validItems = new ArrayList<>();
        List<Double> validWeights = new ArrayList<>();
        double total = 0.0;

        for (Map.Entry<T, Double> entry : weights.entrySet()) {
            T item = entry.getKey();
            double weight = entry.getValue();
            if (item == null || weight <= 0.0) continue;
            validItems.add(item);
            validWeights.add(weight);
            total += weight;
        }

        this.size = validItems.size();
        if (size == 0) {
            this.items = new ArrayList<>();
            this.prob = new double[0];
            this.alias = new int[0];
            return;
        }

        this.items = validItems;
        this.prob = new double[size];
        this.alias = new int[size];

        double scale = size / total;
        Deque<Integer> small = new ArrayDeque<>();
        Deque<Integer> large = new ArrayDeque<>();
        double[] normalized = new double[size];

        for (int i = 0; i < size; i++) {
            normalized[i] = validWeights.get(i) * scale;
            if (normalized[i] < 1.0) {
                small.addLast(i);
            } else {
                large.addLast(i);
            }
        }

        // Vose 算法核心：配对 Small 和 Large
        while (!small.isEmpty() && !large.isEmpty()) {
            int s = small.removeLast();
            int l = large.removeLast();

            prob[s] = normalized[s];
            alias[s] = l;

            normalized[l] = normalized[l] + normalized[s] - 1.0;

            if (normalized[l] < 1.0) {
                small.addLast(l);
            } else {
                large.addLast(l);
            }
        }

        // 处理剩余的 Large（概率设为 1.0）
        while (!large.isEmpty()) {
            int l = large.removeLast();
            prob[l] = 1.0;
        }

        // 处理剩余的 Small（理论上不应有，但数值误差可能导致）
        while (!small.isEmpty()) {
            int s = small.removeLast();
            prob[s] = 1.0;
        }
    }

    /**
     * O(1) 级别的高效随机抽取。
     */
    public T getRandom(RandomSource random) {
        if (size == 0) return null;
        int column = random.nextInt(size);
        boolean coin = random.nextDouble() < prob[column];
        return items.get(coin ? column : alias[column]);
    }

    public boolean isEmpty() {
        return size == 0;
    }

    public int size() {
        return size;
    }
}
