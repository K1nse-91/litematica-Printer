package me.K1nse_.litematica.printer.handler.handlers;

import me.K1nse_.litematica.printer.config.Configs;
import me.K1nse_.litematica.printer.utils.InteractionUtils;
import me.K1nse_.litematica.printer.utils.InventoryUtils;
import me.K1nse_.litematica.printer.utils.minecraft.PlayerUtils;
import me.K1nse_.litematica.printer.utils.minecraft.ToolSelectionUtils;
import me.K1nse_.litematica.printer.utils.mods.ModLoadUtils;
import me.K1nse_.litematica.printer.utils.mods.TweakerooUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.IdentityHashMap;
import java.util.Map;

final class MineBreakExecutor {
    private static final Minecraft CLIENT = Minecraft.getInstance();
    private static final float FAST_FINISH_PROGRESS = 0.5F;
    private static final float CURRENT_TOOL_MIN_EFFICIENCY_RATIO = 0.75F;

    private final Map<BlockState, Float> currentProgressCache = new IdentityHashMap<>();
    private final Map<BlockState, ToolChoice> bestToolCache = new IdentityHashMap<>();
    private int inventorySignature = Integer.MIN_VALUE;
    private boolean resolveBestTool;

    public void beginTick() {
        this.currentProgressCache.clear();
        this.bestToolCache.clear();
        this.resolveBestTool = Configs.Break.BREAK_AUTO_TOOL.getBooleanValue()
                || ModLoadUtils.isTweakerooLoaded() && TweakerooUtils.isToolSwitchEnabled();
    }

    public void reset() {
        this.currentProgressCache.clear();
        this.bestToolCache.clear();
        this.inventorySignature = Integer.MIN_VALUE;
        this.resolveBestTool = false;
    }

    @Nullable
    public Target analyze(BlockPos pos) {
        LocalPlayer player = CLIENT.player;
        ClientLevel level = CLIENT.level;
        if (player == null || level == null || pos == null) {
            return null;
        }
        this.refreshInventoryCaches(player);
        BlockState state = level.getBlockState(pos);
        if (!InteractionUtils.canBreakBlock(pos)) {
            return null;
        }
        ItemStack currentStack = player.getMainHandItem();
        if (InteractionUtils.isInstantBreakTarget(state)) {
            // 瞬时破坏名单：无视破坏进度，直接作为可完成目标返回
            float veilCurrentProgress = this.getCurrentProgress(player, state, currentStack, pos);
            ToolChoice veilToolChoice = this.getBestToolChoice(player, state, currentStack, veilCurrentProgress, pos);
            return new Target(pos.immutable(), state, veilCurrentProgress, 1.0F, Direction.DOWN,
                    veilToolChoice.item(), veilToolChoice.currentPreservesDrops(), veilToolChoice.preservesDrops());
        }
        if (player.getAbilities().instabuild) {
            return new Target(pos.immutable(), state, 1.0F, 1.0F, Direction.DOWN, currentStack.getItem(), false, false);
        }
        float currentProgress = this.getCurrentProgress(player, state, currentStack, pos);
        ToolChoice toolChoice = this.getBestToolChoice(player, state, currentStack, currentProgress, pos);
        float bestProgress = toolChoice.progress();
        if (bestProgress <= 0.0F) {
            return null;
        }
        return new Target(pos.immutable(), state, currentProgress, bestProgress, Direction.DOWN,
                toolChoice.item(), toolChoice.currentPreservesDrops(), toolChoice.preservesDrops());
    }

    public boolean isInstantWithCurrentTool(Target target) {
        LocalPlayer player = CLIENT.player;
        return player != null && (player.getAbilities().instabuild || target.currentProgress > FAST_FINISH_PROGRESS);
    }

    public boolean isInstantWithBestTool(Target target) {
        LocalPlayer player = CLIENT.player;
        return player != null && (player.getAbilities().instabuild || target.bestProgress > FAST_FINISH_PROGRESS);
    }

    public boolean canUseCurrentTool(Target target) {
        return target.currentProgress > 0.0F && this.isCurrentToolEfficient(target);
    }

    public boolean canUseBetterTool(Target target) {
        return (target.bestPreservesDrops && !target.currentPreservesDrops)
                || target.bestProgress > target.currentProgress;
    }

    public boolean isCurrentToolEffective(Target target) {
        LocalPlayer player = CLIENT.player;
        if (player == null || !this.shouldResolveBestTool()) {
            return true;
        }
        if (target.bestToolItem == player.getMainHandItem().getItem()) {
            return true;
        }
        if (target.bestPreservesDrops && !target.currentPreservesDrops) {
            return false;
        }
        return target.currentProgress >= target.bestProgress * CURRENT_TOOL_MIN_EFFICIENCY_RATIO;
    }

    public boolean hasSameBestTool(Target target, @Nullable Item item) {
        return target != null && target.bestToolItem == item;
    }

    private ToolChoice getBestToolChoice(LocalPlayer player, BlockState state, ItemStack currentStack, float currentProgress, BlockPos pos) {
        ToolChoice cached = this.bestToolCache.get(state);
        if (cached != null) {
            return cached;
        }
        boolean preferSilkTouch = ToolSelectionUtils.prefersSilkTouchForDrops(state);
        boolean currentPreservesDrops = preferSilkTouch && ToolSelectionUtils.hasSilkTouch(currentStack);
        float bestProgress = currentProgress;
        Item bestItem = currentStack.getItem();
        boolean bestPreservesDrops = currentPreservesDrops;
        if (!this.shouldResolveBestTool()) {
            ToolChoice choice = new ToolChoice(bestItem, bestProgress, currentPreservesDrops, bestPreservesDrops);
            this.bestToolCache.put(state, choice);
            return choice;
        }
        for (ItemStack stack : InventoryUtils.getMainStacks(player.getInventory())) {
            if (stack.isEmpty()) {
                continue;
            }
            float progress = this.getDestroyProgress(player, state, stack, pos);
            boolean stackPreservesDrops = preferSilkTouch && ToolSelectionUtils.hasSilkTouch(stack);
            if ((stackPreservesDrops && !bestPreservesDrops)
                    || (stackPreservesDrops == bestPreservesDrops && progress > bestProgress)) {
                bestProgress = progress;
                bestItem = stack.getItem();
                bestPreservesDrops = stackPreservesDrops;
            }
        }
        ToolChoice choice = new ToolChoice(bestItem, bestProgress, currentPreservesDrops, bestPreservesDrops);
        this.bestToolCache.put(state, choice);
        return choice;
    }

    private boolean shouldResolveBestTool() {
        return this.resolveBestTool;
    }

    private boolean isCurrentToolEfficient(Target target) {
        if (!this.shouldResolveBestTool()) {
            return true;
        }
        if (target.bestProgress <= 0.0F) {
            return false;
        }
        if (target.bestPreservesDrops && !target.currentPreservesDrops) {
            return false;
        }
        return target.currentProgress >= target.bestProgress * CURRENT_TOOL_MIN_EFFICIENCY_RATIO;
    }

    private float getDestroyProgress(LocalPlayer player, BlockState state, ItemStack stack, BlockPos pos) {
        if (!InteractionUtils.isToolAllowedByDurabilityProtection(stack)) {
            return 0.0F;
        }
        // 用实时状态硬度：Block.defaultDestroyTime() 只是注册默认值，对方块状态相关的实现会过时
        float hardness = state.getDestroySpeed(CLIENT.level, pos);
        if (hardness < 0.0F) {
            return 0.0F;
        }
        if (hardness == 0.0F) {
            return 1.0F;
        }
        int divisor = (!state.requiresCorrectToolForDrops() || stack.isCorrectToolForDrops(state)) ? 30 : 100;
        return PlayerUtils.getBlockBreakingSpeed(player, state, stack) / hardness / (float) divisor;
    }

    private float getCurrentProgress(LocalPlayer player, BlockState state, ItemStack stack, BlockPos pos) {
        Float cached = this.currentProgressCache.get(state);
        if (cached != null) {
            return cached;
        }
        float progress = this.getDestroyProgress(player, state, stack, pos);
        this.currentProgressCache.put(state, progress);
        return progress;
    }

    /**
     * 工具可能在同 tick 内被换手：只按 BlockState 缓存会让下一个目标沿用换前的工具速度。
     * 库存签名变化时清空缓存即可，避免工具耗尽后出现一次多余的停顿。
     */
    private void refreshInventoryCaches(LocalPlayer player) {
        int signature = InventoryUtils.getSelectedSlot(player.getInventory());
        for (ItemStack stack : InventoryUtils.getMainStacks(player.getInventory())) {
            signature = 31 * signature + stack.hashCode();
        }
        if (this.inventorySignature != Integer.MIN_VALUE && this.inventorySignature != signature) {
            this.currentProgressCache.clear();
            this.bestToolCache.clear();
        }
        this.inventorySignature = signature;
    }

    public static final class Target {
        private final BlockPos pos;
        private final BlockState state;
        private final float currentProgress;
        private final float bestProgress;
        private final Direction direction;
        private final Item bestToolItem;
        private final boolean currentPreservesDrops;
        private final boolean bestPreservesDrops;

        private Target(BlockPos pos, BlockState state, float currentProgress, float bestProgress, Direction direction,
                       Item bestToolItem, boolean currentPreservesDrops, boolean bestPreservesDrops) {
            this.pos = pos;
            this.state = state;
            this.currentProgress = currentProgress;
            this.bestProgress = bestProgress;
            this.direction = direction;
            this.bestToolItem = bestToolItem;
            this.currentPreservesDrops = currentPreservesDrops;
            this.bestPreservesDrops = bestPreservesDrops;
        }

        public BlockPos pos() {
            return this.pos;
        }

        public BlockState state() {
            return this.state;
        }

        public float progress() {
            return this.bestProgress;
        }

        public float currentProgress() {
            return this.currentProgress;
        }

        public Item bestToolItem() {
            return this.bestToolItem;
        }

        /** 最佳工具能保住掉落物、当前手持没有精准采集 → 需要强制换工具 */
        public boolean shouldSwitchToRecoveryTool(ItemStack currentStack) {
            return this.bestPreservesDrops && !ToolSelectionUtils.hasSilkTouch(currentStack);
        }
    }

    private record ToolChoice(Item item, float progress, boolean currentPreservesDrops, boolean preservesDrops) {
    }
}
