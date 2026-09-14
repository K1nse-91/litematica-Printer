package me.K1nse_.litematica.printer.handler.handlers;

import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.Predicate;

/**
 * 跨扫描批次、跨 tick 保留挖掘候选。
 *
 * <p>扫描引擎在挖掘动作进行中允许等待下一批；坐标键控队列把这一边界显式化：
 * 重扫会刷新目标而不是重复添加，扫描暂时为空也不会丢弃已经受理的目标。</p>
 */
final class MineCandidateQueue {
    private final LinkedHashMap<BlockPos, MineBreakExecutor.Target> entries = new LinkedHashMap<>();

    void add(MineBreakExecutor.Target target) {
        if (target != null && target.pos() != null) {
            this.entries.put(target.pos().immutable(), target);
        }
    }

    boolean isEmpty() {
        return this.entries.isEmpty();
    }

    int size() {
        return this.entries.size();
    }

    List<MineBreakExecutor.Target> ordered(Comparator<MineBreakExecutor.Target> comparator) {
        List<MineBreakExecutor.Target> result = new ArrayList<>(this.entries.values());
        result.sort(comparator);
        return result;
    }

    List<MineBreakExecutor.Target> snapshot() {
        return new ArrayList<>(this.entries.values());
    }

    void remove(BlockPos pos) {
        if (pos != null) {
            this.entries.remove(pos);
        }
    }

    void removeIf(Predicate<MineBreakExecutor.Target> predicate) {
        this.entries.entrySet().removeIf(entry -> predicate.test(entry.getValue()));
    }

    void clear() {
        this.entries.clear();
    }
}
