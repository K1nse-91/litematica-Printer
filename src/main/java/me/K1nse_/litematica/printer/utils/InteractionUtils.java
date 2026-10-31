package me.K1nse_.litematica.printer.utils;

import fi.dy.masa.malilib.config.IConfigOptionListEntry;
import fi.dy.masa.malilib.util.restrictions.UsageRestriction;
import me.K1nse_.litematica.printer.config.Configs;
import me.K1nse_.litematica.printer.enums.ExcavateListMode;
import me.K1nse_.litematica.printer.enums.FluidAvoidStrategyType;
import me.K1nse_.litematica.printer.mixin_extension.BlockBreakResult;
import me.K1nse_.litematica.printer.mixin_extension.MultiPlayerGameModeExtension;
import me.K1nse_.litematica.printer.printer.SchematicBlockContext;
import me.K1nse_.litematica.printer.utils.mods.ModLoadUtils;
import me.K1nse_.litematica.printer.utils.mods.TweakerooUtils;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DragonEggBlock;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static fi.dy.masa.tweakeroo.config.Configs.Lists.BLOCK_TYPE_BREAK_RESTRICTION_BLACKLIST;
import static fi.dy.masa.tweakeroo.config.Configs.Lists.BLOCK_TYPE_BREAK_RESTRICTION_WHITELIST;
import static fi.dy.masa.tweakeroo.tweaks.PlacementTweaks.BLOCK_TYPE_BREAK_RESTRICTION;

@SuppressWarnings({"DataFlowIssue", "BooleanMethodIsAlwaysInverted"})
@Environment(EnvType.CLIENT)
public class InteractionUtils {
    public static final Minecraft client = Minecraft.getInstance();
    public static final InteractionUtils INSTANCE = new InteractionUtils();
    private static final UsageRestrictionCache BREAK_RESTRICTION_CACHE = new UsageRestrictionCache();

    // ---- 避让流体（移植自 EMT）----
    private static final Set<Block> FLUID_AVOID_BLOCKS = Collections.newSetFromMap(new IdentityHashMap<>());
    private static List<String> fluidListSnapshot = List.of();
    private static FluidAvoidStrategyType fluidStrategySnapshot = FluidAvoidStrategyType.FIVE_FACES;
    private static boolean fluidMatcherInitialized = false;
    private static long lastPlayerMineGameTime = -1L;

    private final Queue<BlockPos> breakQueue = new LinkedList<>();
    private final Set<BlockPos> queuedBreaks = new HashSet<>();
    private final Map<BlockPos, Integer> recentlyBroken = new HashMap<>();
    private final Map<BlockPos, Integer> pendingBroken = new HashMap<>();
    private BlockPos breakPos;
    private boolean forceDelayedDestroy;
    private int externalDestroyLockTicks;

    private InteractionUtils() {
    }

    /** 瞬时破坏名单命中：名单内方块无视硬度/破坏进度，直接 START+STOP 两包瞬破 */
    public static boolean isInstantBreakTarget(@Nullable BlockState state) {
        if (state == null || state.isAir()) {
            return false;
        }
        if (!Configs.Break.BREAK_INSTANT_MINE.getBooleanValue()) {
            return false;
        }
        List<String> veilList = Configs.Break.BREAK_INSTANT_MINE_LIST.getStrings();
        if (veilList.isEmpty()) {
            return false;
        }
        for (String rule : veilList) {
            if (FilterUtils.matchBlockName(rule, state)) {
                return true;
            }
        }
        return false;
    }

    public static boolean canBreakBlock(BlockPos pos) {
        ClientLevel world = client.level;
        LocalPlayer player = client.player;
        if (world == null || player == null) return false;
        BlockState currentState = world.getBlockState(pos);
        if (isInstantBreakTarget(currentState)) {
            return true;
        }
        if (Configs.Break.BREAK_CHECK_HARDNESS.getBooleanValue() && currentState.getBlock().defaultDestroyTime() < 0) {
            return false;
        }
        if (Configs.Break.BREAK_AVOID_FLUID.getBooleanValue() && isFluidProtected(pos, world)) {
            return false;
        }
        if (Configs.Break.BREAK_AVOID_SUPPORT.getBooleanValue() && isSupportProtected(pos, world)) {
            return false;
        }
        return !currentState.isAir() &&
                !currentState.is(Blocks.AIR) &&
                !currentState.is(Blocks.CAVE_AIR) &&
                !currentState.is(Blocks.VOID_AIR) &&
                !(currentState.getBlock() instanceof LiquidBlock) &&
                !player.blockActionRestricted(client.level, pos, client.gameMode.getPlayerMode());
    }

    /** 玩家是否正在手动按住左键挖掘（1 tick 判定窗口），供「让位手动挖掘」使用 */
    public static boolean isPlayerMining() {
        LocalPlayer player = client.player;
        ClientLevel level = client.level;
        if (player == null || level == null || player.getAbilities().instabuild || player.isSpectator()) {
            return false;
        }
        boolean mining = client.options.keyAttack.isDown()
                && client.hitResult != null
                && client.hitResult.getType() == HitResult.Type.BLOCK;
        if (mining) {
            lastPlayerMineGameTime = level.getGameTime();
        }
        long diff = level.getGameTime() - lastPlayerMineGameTime;
        return diff >= 0 && diff <= 1;
    }

    // ---- 避让流体匹配器 ----

    private static void ensureFluidAvoidMatcher() {
        List<String> configured = List.copyOf(Configs.Break.BREAK_FLUID_LIST.getStrings());
        FluidAvoidStrategyType strategy = (FluidAvoidStrategyType) Configs.Break.BREAK_FLUID_STRATEGY.getOptionListValue();
        if (fluidMatcherInitialized && fluidListSnapshot.equals(configured)) {
            fluidStrategySnapshot = strategy;
            return;
        }
        FLUID_AVOID_BLOCKS.clear();
        for (Block block : BuiltInRegistries.BLOCK) {
            BlockState state = block.defaultBlockState();
            boolean matched = configured.stream().anyMatch(entry -> FilterUtils.matchBlockName(entry, state));
            Item item = block.asItem();
            if (!matched && item != Items.AIR) {
                matched = configured.stream().anyMatch(entry -> FilterUtils.matchItemName(entry, new ItemStack(item)));
            }
            if (matched) {
                FLUID_AVOID_BLOCKS.add(block);
            }
        }
        fluidListSnapshot = configured;
        fluidStrategySnapshot = strategy;
        fluidMatcherInitialized = true;
    }

    private static boolean isConfiguredFluid(BlockState state) {
        if (state == null) {
            return false;
        }
        return FLUID_AVOID_BLOCKS.contains(state.getBlock())
                || !state.getFluidState().isEmpty() && FLUID_AVOID_BLOCKS.contains(state.getFluidState().createLegacyBlock().getBlock());
    }

    private static boolean isGravityBlock(BlockState state) {
        if (state == null || state.isAir()) {
            return false;
        }
        Block block = state.getBlock();
        return block instanceof FallingBlock || block instanceof AnvilBlock || block instanceof DragonEggBlock;
    }

    private static boolean isFluidProtected(BlockPos pos, ClientLevel level) {
        if (client.player == null) {
            return false;
        }
        if (pos == null || level == null) {
            return false;
        }
        ensureFluidAvoidMatcher();
        boolean besideFluid = isConfiguredFluid(level.getBlockState(pos.above()))
                || isConfiguredFluid(level.getBlockState(pos.east()))
                || isConfiguredFluid(level.getBlockState(pos.west()))
                || isConfiguredFluid(level.getBlockState(pos.north()))
                || isConfiguredFluid(level.getBlockState(pos.south()));
        if (besideFluid) {
            return true;
        }
        return fluidStrategySnapshot == FluidAvoidStrategyType.SIX_FACES
                && isConfiguredFluid(level.getBlockState(pos.below()));
    }

    private static boolean isSupportProtected(BlockPos pos, ClientLevel level) {
        return pos != null && level != null && isGravityBlock(level.getBlockState(pos.above()));
    }

    public static boolean breakRestriction(BlockState blockState) {
        if (Configs.Break.BREAK_LIMITER.getOptionListValue().equals(ExcavateListMode.TWEAKEROO)) {
            if (!ModLoadUtils.isTweakerooLoaded()) return true;
            UsageRestriction.ListType listType = BLOCK_TYPE_BREAK_RESTRICTION.getListType();
            return BREAK_RESTRICTION_CACHE.allows("break_tweakeroo", listType,
                    BLOCK_TYPE_BREAK_RESTRICTION_BLACKLIST.getStrings(),
                    BLOCK_TYPE_BREAK_RESTRICTION_WHITELIST.getStrings(),
                    blockState);
        } else {
            IConfigOptionListEntry optionListValue = Configs.Break.BREAK_LIMIT.getOptionListValue();
            UsageRestriction.ListType listType = optionListValue instanceof UsageRestriction.ListType type
                    ? type
                    : UsageRestriction.ListType.NONE;
            return BREAK_RESTRICTION_CACHE.allows("break_custom", listType,
                    Configs.Break.BREAK_BLACKLIST.getStrings(),
                    Configs.Break.BREAK_WHITELIST.getStrings(),
                    blockState);
        }
    }

    public static boolean trySwitchToEffectiveTool(BlockPos pos, BlockState blockState) {
        if (pos == null || blockState == null || blockState.isAir() || blockState.getBlock() instanceof LiquidBlock) {
            return false;
        }
        LocalPlayer player = client.player;
        if (Configs.Break.BREAK_AUTO_TOOL.getBooleanValue()) {
            return player != null && InventoryUtils.switchToBestTool(player, blockState);
        }
        if (ModLoadUtils.isTweakerooLoaded() && TweakerooUtils.isToolSwitchEnabled()) {
            TweakerooUtils.trySwitchToEffectiveTool(pos);
            return protectCurrentToolBeforeBreak(blockState);
        }
        return false;
    }

    public static boolean isToolAllowedByDurabilityProtection(ItemStack stack) {
        return !TweakerooUtils.isToolTooDamagedForBreaking(stack);
    }

    public static int getCurrentToolSafeBreakBudget() {
        LocalPlayer player = client.player;
        if (player == null || player.getAbilities().instabuild) {
            return Integer.MAX_VALUE;
        }
        return TweakerooUtils.getSafeBreakBudget(player.getMainHandItem());
    }

    public static boolean protectCurrentToolBeforeBreak() {
        return protectCurrentToolBeforeBreak(null);
    }

    public static boolean protectCurrentToolBeforeBreak(@Nullable BlockState blockState) {
        LocalPlayer player = client.player;
        if (player == null || player.getAbilities().instabuild) {
            return true;
        }
        if (isToolAllowedByDurabilityProtection(player.getMainHandItem())) {
            return true;
        }
        TweakerooUtils.trySwapCurrentToolIfNearlyBroken();
        if (isToolAllowedByDurabilityProtection(player.getMainHandItem())) {
            return true;
        }
        if (blockState != null && InventoryUtils.switchToBestTool(player, blockState)) {
            return isToolAllowedByDurabilityProtection(player.getMainHandItem());
        }
        return false;
    }

    public void add(BlockPos pos) {
        if (pos == null) return;
        BlockPos queuedPos = pos.immutable();
        if (queuedPos.equals(this.breakPos)
                || this.recentlyBroken.containsKey(queuedPos)
                || this.pendingBroken.containsKey(queuedPos)
                || !this.queuedBreaks.add(queuedPos)) {
            return;
        }
        breakQueue.add(queuedPos);
    }

    public void add(SchematicBlockContext ctx) {
        if (ctx == null) return;
        this.add(ctx.blockPos);
    }

    private void tickRecentlyBroken() {
        tickBreakMarkerMap(this.recentlyBroken);
        tickBreakMarkerMap(this.pendingBroken);
    }

    private void tickBreakMarkerMap(Map<BlockPos, Integer> markerMap) {
        if (markerMap.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<BlockPos, Integer>> iterator = markerMap.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<BlockPos, Integer> entry = iterator.next();
            int remainingTicks = entry.getValue() - 1;
            if (remainingTicks <= 0) {
                iterator.remove();
            } else {
                entry.setValue(remainingTicks);
            }
        }
    }

    public void preprocess() {
        this.tickRecentlyBroken();
        if (this.externalDestroyLockTicks > 0) {
            this.externalDestroyLockTicks--;
        }
        if (!ConfigUtils.isEnable()) {
            if (!breakQueue.isEmpty()) {
                breakQueue.clear();
                queuedBreaks.clear();
            }
            if (breakPos != null) {
                breakPos = null;
            }
            if (!this.recentlyBroken.isEmpty()) {
                this.recentlyBroken.clear();
            }
            if (!this.pendingBroken.isEmpty()) {
                this.pendingBroken.clear();
            }
            this.externalDestroyLockTicks = 0;
            this.forceDelayedDestroy = false;
        }
    }

    public void resetRuntime() {
        this.breakQueue.clear();
        this.queuedBreaks.clear();
        this.recentlyBroken.clear();
        this.pendingBroken.clear();
        this.breakPos = null;
        this.forceDelayedDestroy = false;
        this.externalDestroyLockTicks = 0;
    }

    public boolean isNeedHandle() {
        return !breakQueue.isEmpty() || breakPos != null;
    }

    public void onTick() {
        LocalPlayer player = client.player;
        ClientLevel level = client.level;
        if (player == null || level == null) {
            return;
        }
        if (this.externalDestroyLockTicks > 0) {
            return;
        }
        if (breakPos == null && breakQueue.isEmpty()) {
            return;
        }
        if (breakPos == null) {
            while (!breakQueue.isEmpty()) {
                BlockPos pos = breakQueue.poll();
                queuedBreaks.remove(pos);
                if (pos == null) {
                    continue;
                }
                if (!ConfigUtils.canInteracted(pos) || !canBreakBlock(pos) || !breakRestriction(level.getBlockState(pos))) {
                    continue;
                }
                BlockBreakResult result = continueDestroyBlock(pos, Direction.DOWN);
                if (result == BlockBreakResult.IN_PROGRESS) {
                    breakPos = pos;
                    break;
                }
                if (result == BlockBreakResult.COMPLETED || result == BlockBreakResult.COMPLETED_WAIT) {
                    this.markRecentlyBroken(pos);
                    if (result == BlockBreakResult.COMPLETED_WAIT) {
                        this.markPendingBroken(pos, ConfigUtils.getBreakCooldown());
                    }
                }
            }
        } else {
            // 检查当前目标是否仍可破坏（如冰挖掘后生成水/流体，流体不可破坏）
            if (!canBreakBlock(breakPos)) {
                breakPos = null;
                this.forceDelayedDestroy = false;
                onTick();
                return;
            }
            if (continueDestroyBlock(breakPos, Direction.DOWN) != BlockBreakResult.IN_PROGRESS) {
                this.markRecentlyBroken(breakPos);
                breakPos = null;
                this.forceDelayedDestroy = false;
                onTick();
            }
        }
    }

    public boolean hasActiveDestroyTarget() {
        return this.breakPos != null;
    }

    public void suppressQueuedBreaks(int ticks) {
        this.externalDestroyLockTicks = Math.max(this.externalDestroyLockTicks, ticks);
    }

    public void markRecentlyBroken(BlockPos pos) {
        if (pos != null) {
            this.recentlyBroken.put(pos.immutable(), 2);
        }
    }

    public void markPendingBroken(BlockPos pos, int timeoutTicks) {
        if (pos != null) {
            this.pendingBroken.put(pos.immutable(), Math.max(timeoutTicks, 1));
        }
    }

    public void confirmServerBlockUpdate(BlockPos pos) {
        if (pos == null) {
            return;
        }
        this.recentlyBroken.remove(pos);
        this.pendingBroken.remove(pos);
    }

    public void clearPendingBroken(BlockPos pos) {
        if (pos != null) {
            this.pendingBroken.remove(pos);
        }
    }

    public boolean isRecentlyBroken(BlockPos pos) {
        return pos != null && (this.recentlyBroken.containsKey(pos) || this.pendingBroken.containsKey(pos));
    }

    public BlockBreakResult continueDestroyBlock(final BlockPos blockPos, Direction direction, boolean localPrediction, boolean trackBreakPos) {
        MultiPlayerGameModeExtension gameMode = (@Nullable MultiPlayerGameModeExtension) client.gameMode;
        BlockBreakResult result = gameMode.litematica_printer$continueDestroyBlock(localPrediction, blockPos, direction, this.forceDelayedDestroy);
        if (trackBreakPos && result == BlockBreakResult.IN_PROGRESS) {
            breakPos = blockPos;
        }
        if (result != BlockBreakResult.IN_PROGRESS) {
            this.forceDelayedDestroy = false;
        }
        return result;
    }

    public BlockBreakResult continueDestroyBlock(final BlockPos blockPos, Direction direction, boolean localPrediction) {
        return this.continueDestroyBlock(blockPos, direction, localPrediction, true);
    }

    public BlockBreakResult continueDestroyBlock(BlockPos blockPos, Direction direction) {
        return this.continueDestroyBlock(blockPos, direction, true);
    }

    public BlockBreakResult continueDestroyBlock(BlockPos blockPos) {
        return this.continueDestroyBlock(blockPos, Direction.DOWN);
    }

    public BlockBreakResult continueDestroyBlockForMine(BlockPos blockPos, Direction direction) {
        return this.continueDestroyBlockForMine(blockPos, direction, true);
    }

    public BlockBreakResult continueDestroyBlockForMine(BlockPos blockPos, Direction direction, boolean allowToolSwitch) {
        MultiPlayerGameModeExtension gameMode = (@Nullable MultiPlayerGameModeExtension) client.gameMode;
        if (gameMode == null) {
            return BlockBreakResult.FAILED;
        }
        return gameMode.litematica_printer$continueDestroyBlockForMine(blockPos, direction, allowToolSwitch);
    }

    public BlockBreakResult continueDestroyBlockForMine(BlockPos blockPos) {
        return this.continueDestroyBlockForMine(blockPos, Direction.DOWN);
    }

    public boolean isPendingDelayedDestroy(BlockPos blockPos) {
        MultiPlayerGameModeExtension gameMode = (@Nullable MultiPlayerGameModeExtension) client.gameMode;
        return gameMode != null && gameMode.litematica_printer$isPendingDelayedDestroy(blockPos);
    }

    public BlockBreakResult continueDestroyBlockWithoutTracking(BlockPos blockPos, Direction direction) {
        return this.continueDestroyBlock(blockPos, direction, true, false);
    }

    public BlockBreakResult continueDestroyBlockWithoutTracking(BlockPos blockPos) {
        return this.continueDestroyBlockWithoutTracking(blockPos, Direction.DOWN);
    }

    public BlockBreakResult continueDestroyBlockWithoutToolSwitch(BlockPos blockPos, Direction direction, boolean trackBreakPos) {
        MultiPlayerGameModeExtension gameMode = (@Nullable MultiPlayerGameModeExtension) client.gameMode;
        if (gameMode == null) {
            return BlockBreakResult.FAILED;
        }
        BlockBreakResult result = gameMode.litematica_printer$continueDestroyBlock(
                true,
                blockPos,
                direction,
                this.forceDelayedDestroy,
                false
        );
        if (trackBreakPos && result == BlockBreakResult.IN_PROGRESS) {
            breakPos = blockPos;
        }
        if (result != BlockBreakResult.IN_PROGRESS) {
            this.forceDelayedDestroy = false;
        }
        return result;
    }

    public BlockBreakResult continueDestroyBlockWithoutToolSwitch(BlockPos blockPos, Direction direction) {
        return this.continueDestroyBlockWithoutToolSwitch(blockPos, direction, true);
    }

    public InteractionResult useItemOn(boolean localPrediction, InteractionHand hand, BlockHitResult blockHit) {
        MultiPlayerGameModeExtension gameMode = (@Nullable MultiPlayerGameModeExtension) client.gameMode;
        return gameMode.litematica_printer$useItemOn(localPrediction, hand, blockHit);
    }

    public InteractionResult useItemOn(InteractionHand hand, BlockHitResult blockHit) {
        return this.useItemOn(true, hand, blockHit);
    }
}
