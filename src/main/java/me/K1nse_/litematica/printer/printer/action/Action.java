package me.K1nse_.litematica.printer.printer.action;

import lombok.Getter;
import me.K1nse_.litematica.printer.config.Configs;
import me.K1nse_.litematica.printer.interfaces.Implementation;
import me.K1nse_.litematica.printer.printer.ActionManager;
import me.K1nse_.litematica.printer.printer.PlayerLook;
import me.K1nse_.litematica.printer.printer.PrinterUtils;
import me.K1nse_.litematica.printer.utils.minecraft.BlockUtils;
import me.K1nse_.litematica.printer.utils.minecraft.DirectionUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

@SuppressWarnings("UnusedReturnValue")
public class Action {
    private static final Direction[] DEFAULT_SIDE_ORDER = {
            Direction.UP,
            Direction.NORTH,
            Direction.SOUTH,
            Direction.EAST,
            Direction.WEST,
            Direction.DOWN
    };

    protected Map<Direction, Vec3> sides;
    protected boolean customSides;
    @Nullable
    @Getter
    protected PlayerLook playerLook = null;
    @Nullable
    protected Item[] clickItems; // null == 空手
    protected boolean requiresSupport = false;
    @Getter
    @Nullable
    protected Boolean shift = null;
    @Getter
    protected boolean consumeEffectiveExecution = true;
    @Getter
    protected int cooldownTicksOverride = -1;
    @Getter
    protected int clickRepeatCount = 1;
    @Getter
    protected boolean needWaitModifyLook = false;

    public Action() {
        this.sides = createDefaultSides();
        this.customSides = false;
    }

    public Action setLookRotation(int lookRotation) {
        this.playerLook = new PlayerLook(lookRotation);
        return this;
    }

    public Action setLookDirection(Direction lookDirection) {
        this.playerLook = new PlayerLook(lookDirection);
        return this;
    }

    public Action setLookDirection(Direction lookDirectionYaw, Direction lookDirectionPitch) {
        this.playerLook = new PlayerLook(lookDirectionYaw, lookDirectionPitch);
        return this;
    }

    public Action setNeedWaitModifyLook(boolean needWaitModifyLook) {
        this.needWaitModifyLook = needWaitModifyLook;
        return this;
    }

    public Action setNeedWaitModifyLook() {
        return this.setNeedWaitModifyLook(true);
    }

    public @Nullable Item[] getRequiredItems(Block backup) {
        return clickItems == null ? new Item[]{backup.asItem()} : clickItems;
    }

    public @NotNull Map<Direction, Vec3> getSides() {
        if (this.sides == null) {
            this.sides = createDefaultSides();
        }
        return this.sides;
    }

    protected @NotNull List<Direction> getOrderedSides() {
        return new ArrayList<>(getSides().keySet());
    }

    public Action setSides(Direction.Axis... axis) {
        Map<Direction, Vec3> sides = new LinkedHashMap<>();
        for (Direction.Axis a : axis) {
            for (Direction d : DEFAULT_SIDE_ORDER) {
                if (d.getAxis() == a) {
                    sides.put(d, new Vec3(0, 0, 0));
                }
            }
        }
        this.sides = sides;
        this.customSides = true;
        return this;
    }

    public Action setSides(Map<Direction, Vec3> sides) {
        this.sides = copySidesInDefaultOrder(sides);
        this.customSides = true;
        return this;
    }

    public Action setSides(Direction side, Vec3 offset) {
        this.sides = new LinkedHashMap<>();
        this.sides.put(side, offset);
        this.customSides = true;
        return this;
    }

    public Action setSides(Direction... directions) {
        Map<Direction, Vec3> sides = new LinkedHashMap<>();
        for (Direction d : directions) {
            sides.put(d, new Vec3(0, 0, 0));
        }
        this.sides = sides;
        this.customSides = true;
        return this;
    }

    @SuppressWarnings("SequencedCollectionMethodCanBeUsed")
    public @Nullable Direction getValidSide(ClientLevel world, BlockPos pos) {
        List<Direction> orderedSides = getOrderedSides();
        if (Configs.Print.PLACE_IN_AIR.getBooleanValue() && !this.requiresSupport) {
            return orderedSides.isEmpty() ? null : orderedSides.get(0);
        }
        if (!this.customSides) {
            sortSidesByPlayerView(orderedSides, pos);
        }
        Direction firstValidSide = null;
        BlockState currentState = world.getBlockState(pos);
        for (Direction side : orderedSides) {
            BlockPos neighborPos = pos.relative(side);
            BlockState neighborState = world.getBlockState(neighborPos);
            if (PrinterUtils.canBeClicked(world, neighborPos) && !BlockUtils.isReplaceable(neighborState)) {
                if (firstValidSide == null) {
                    firstValidSide = side;
                }
                // 选择一个不需要潜行放置的面
                if (!Implementation.isInteractive(neighborState.getBlock()) && currentState.canSurvive(world, pos)) {
                    return side;
                }
            }
        }
        return firstValidSide;
    }

    private static void sortSidesByPlayerView(List<Direction> sides, BlockPos pos) {
        if (!Configs.Print.PRINT_SORT_SIDES.getBooleanValue() || sides.size() < 2) {
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        Vec3 eye = player.getEyePosition();
        Vec3 center = Vec3.atCenterOf(pos);
        sides.sort(Comparator.comparingDouble(side -> -getClickedFaceScore(eye, center, side)));
    }

    private static double getClickedFaceScore(Vec3 eye, Vec3 center, Direction side) {
        Vec3 toEye = eye.subtract(center);
        Vec3 clickedFaceNormal = Vec3.atLowerCornerOf(DirectionUtils.getVector(side.getOpposite()));
        return clickedFaceNormal.dot(toEye);
    }

    public Action setItem(Item item) {
        return this.setItems(item);
    }

    public Action setItems(Item... items) {
        this.clickItems = items;
        return this;
    }

    public Action setRequiresSupport(boolean requiresSupport) {
        this.requiresSupport = requiresSupport;
        return this;
    }

    public Action setRequiresSupport() {
        return this.setRequiresSupport(true);
    }

    public Action setShift(boolean useShift) {
        this.shift = useShift;
        return this;
    }

    public Action setShift() {
        return this.setShift(true);
    }

    public Action setConsumeEffectiveExecution(boolean consumeEffectiveExecution) {
        this.consumeEffectiveExecution = consumeEffectiveExecution;
        return this;
    }

    public Action setCooldownTicksOverride(int cooldownTicksOverride) {
        this.cooldownTicksOverride = cooldownTicksOverride;
        return this;
    }

    public Action setClickRepeatCount(int clickRepeatCount) {
        this.clickRepeatCount = Math.max(1, clickRepeatCount);
        return this;
    }

    public Action queueAction(@NotNull BlockPos blockPos, @NotNull Direction side, boolean useShift, @NotNull LocalPlayer player) {
        return this.queueAction(blockPos, side, useShift, player, null);
    }

    public Action queueAction(@NotNull BlockPos blockPos, @NotNull Direction side, boolean useShift, @NotNull LocalPlayer player, @Nullable Item[] expectedItems) {
        if (Configs.Print.PLACE_IN_AIR.getBooleanValue() && !this.requiresSupport) {
            ActionManager.INSTANCE.queueClick(
                    blockPos,
                    side.getOpposite(),
                    getSides().get(side),
                    useShift,
                    1,
                    expectedItems
            );
        } else {
            ActionManager.INSTANCE.queueClick(
                    blockPos.relative(side),
                    side.getOpposite(),
                    getSides().get(side),
                    useShift,
                    1,
                    expectedItems
            );
        }
        return this;
    }

    private static @NotNull Map<Direction, Vec3> createDefaultSides() {
        Map<Direction, Vec3> sides = new LinkedHashMap<>();
        for (Direction direction : DEFAULT_SIDE_ORDER) {
            sides.put(direction, Vec3.ZERO);
        }
        return sides;
    }

    private static @NotNull Map<Direction, Vec3> copySidesInDefaultOrder(@NotNull Map<Direction, Vec3> source) {
        Map<Direction, Vec3> ordered = new LinkedHashMap<>();
        for (Direction direction : DEFAULT_SIDE_ORDER) {
            if (source.containsKey(direction)) {
                ordered.put(direction, source.get(direction));
            }
        }
        for (Map.Entry<Direction, Vec3> entry : source.entrySet()) {
            ordered.putIfAbsent(entry.getKey(), entry.getValue());
        }
        return ordered;
    }
}
