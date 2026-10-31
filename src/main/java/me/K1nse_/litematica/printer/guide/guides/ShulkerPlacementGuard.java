package me.K1nse_.litematica.printer.guide.guides;

import me.K1nse_.litematica.printer.printer.SchematicBlockContext;
import me.K1nse_.litematica.printer.utils.InventoryUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ShulkerBoxBlock;

import java.util.HashMap;
import java.util.Map;

/**
 * 潜影盒放置守护（移植自 EMT，简化为状态判定版）：
 * 只在“手里确实有对应颜色的空潜影盒”且“附近没有刚放下的潜影盒等待服务端确认”时允许放置，
 * 避免把装东西的盒子放上去、或连续放盒导致服务端混盒。
 */
public final class ShulkerPlacementGuard {
    public static final ShulkerPlacementGuard INSTANCE = new ShulkerPlacementGuard();
    /** 刚放下的潜影盒需要等待确认的 tick 数 */
    private static final int CONFIRM_TICKS = 5;
    /** 近期放置记录：pos -> tick */
    private final Map<Long, Long> recentlyPlaced = new HashMap<>();

    public enum GuardResult {
        READY,
        WAIT_OTHER_BLOCKS,
        WAIT_CLOSE,
        WAIT_CONFIRM,
        NO_EMPTY
    }

    private ShulkerPlacementGuard() {
    }

    public void reset() {
        this.recentlyPlaced.clear();
    }

    public void onPlaced(BlockPos pos, long tick) {
        if (pos != null) {
            this.recentlyPlaced.put(pos.asLong(), tick);
        }
    }

    public GuardResult evaluate(SchematicBlockContext ctx) {
        if (ctx == null || !(ctx.requiredState.getBlock() instanceof ShulkerBoxBlock)) {
            return GuardResult.READY;
        }
        long tick = ctx.level.getGameTime();
        this.prune(tick);

        // 1) 材料：必须持有对应颜色的空潜影盒
        Item requiredItem = ctx.requiredState.getBlock().asItem();
        int emptyCount = InventoryUtils.countMatchingMainInventory(ctx.client.player,
                stack -> stack.is(requiredItem) && isEmptyShulker(stack));
        if (emptyCount <= 0) {
            return GuardResult.NO_EMPTY;
        }

        // 2) 附近 3 格内刚放下的潜影盒还在确认窗口内 → 等
        for (Map.Entry<Long, Long> entry : this.recentlyPlaced.entrySet()) {
            if (tick - entry.getValue() < CONFIRM_TICKS) {
                BlockPos placed = BlockPos.of(entry.getKey());
                if (placed.closerThan(ctx.blockPos, 3.0D)) {
                    return GuardResult.WAIT_CONFIRM;
                }
            }
        }
        return GuardResult.READY;
    }

    private void prune(long tick) {
        this.recentlyPlaced.entrySet().removeIf(entry -> tick - entry.getValue() > CONFIRM_TICKS * 4L);
    }

    /** 盒子是否为空：没有容器组件即视为空盒 */
    private static boolean isEmptyShulker(ItemStack stack) {
        //#if MC >= 12005
        return !stack.has(net.minecraft.core.component.DataComponents.CONTAINER);
        //#else
        //$$ return !stack.hasTag() || !stack.getTag().contains("BlockEntityTag");
        //#endif
    }
}
