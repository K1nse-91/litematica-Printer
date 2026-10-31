package me.K1nse_.litematica.printer.handler;

import me.K1nse_.litematica.printer.config.Configs;
import me.K1nse_.litematica.printer.enums.IterationOrderType;
import me.K1nse_.litematica.printer.enums.PrintModeType;
import me.K1nse_.litematica.printer.printer.PrinterBox;
import me.K1nse_.litematica.printer.utils.ConfigUtils;
import me.K1nse_.litematica.printer.utils.minecraft.PlayerUtils;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.atomic.AtomicReference;

final class InteractionBoxTracker {
    @Nullable
    private final AtomicReference<PrinterBox> boxReference;
    @Nullable
    private final PrintModeType printMode;
    @Nullable
    private PrinterBox lastBox;
    @Nullable
    private BlockPos lastPlayerPos;
    /** 上一 tick 的玩家位置（移动自适应迭代用，每次 update 都刷新） */
    @Nullable
    private BlockPos prevPlayerPos;
    private double lastRange = Double.NaN;

    InteractionBoxTracker(boolean enabled, @Nullable PrintModeType printMode) {
        this.boxReference = enabled ? new AtomicReference<>() : null;
        this.printMode = printMode;
    }

    @Nullable
    AtomicReference<PrinterBox> getBoxReference() {
        return this.boxReference;
    }

    void resetPlayerTracking() {
        this.lastPlayerPos = null;
        this.prevPlayerPos = null;
    }

    /** 移动自适应迭代（移植自 EMT）：仅挖掘模式生效，按玩家移动主方向把工作盒向前延伸 */
    private boolean isMoveAdaptiveEnabled() {
        if (!Configs.Core.MOVE_ADAPTIVE_ITERATION.getBooleanValue()) {
            return false;
        }
        if (this.printMode == PrintModeType.MINE) {
            return true;
        }
        // 打印快速定向放置（移植自 EMT）：移动够快时按移动方向预判延伸工作盒
        if (this.printMode == PrintModeType.PRINTER && Configs.Print.FAST_DIRECTIONAL_PLACEMENT.getBooleanValue()) {
            net.minecraft.client.player.LocalPlayer player = net.minecraft.client.Minecraft.getInstance().player;
            return player != null && player.getDeltaMovement().length() * 20.0D >= 14.0D;
        }
        return false;
    }

    void update(LocalPlayer player) {
        if (this.boxReference == null) {
            return;
        }
        BlockPos playerPos = trackingPos(player);
        double range = getBoxRange();
        @Nullable PrinterBox box = this.boxReference.get();

        int dominantAxis = -1;
        int dominantSign = 1;
        if (isMoveAdaptiveEnabled() && this.prevPlayerPos != null) {
            int dx = playerPos.getX() - this.prevPlayerPos.getX();
            int dy = playerPos.getY() - this.prevPlayerPos.getY();
            int dz = playerPos.getZ() - this.prevPlayerPos.getZ();
            int adx = Math.abs(dx);
            int ady = Math.abs(dy);
            int adz = Math.abs(dz);
            if (adx == 0 && ady == 0 && adz == 0) {
                dominantAxis = -1;
            } else if (adx >= ady && adx >= adz) {
                dominantAxis = 0;
                dominantSign = dx >= 0 ? 1 : -1;
            } else if (ady >= adz) {
                dominantAxis = 1;
                dominantSign = dy >= 0 ? 1 : -1;
            } else {
                dominantAxis = 2;
                dominantSign = dz >= 0 ? 1 : -1;
            }
        }
        this.prevPlayerPos = playerPos;

        if (box == null
                || !box.equals(this.lastBox)
                || this.lastPlayerPos == null
                || !this.lastPlayerPos.equals(playerPos)
                || Double.compare(this.lastRange, range) != 0) {
            this.lastPlayerPos = playerPos;
            this.lastRange = range;
            box = this.createBox(player, range, dominantAxis, dominantSign);
            this.applyIterationConfig(box);
            if (dominantAxis >= 0) {
                IterationOrderType.Axis primary = dominantAxis == 0
                        ? IterationOrderType.Axis.X
                        : (dominantAxis == 1 ? IterationOrderType.Axis.Y : IterationOrderType.Axis.Z);
                box.iterationMode = IterationOrderType.primaryFirst(box.iterationMode, primary);
                boolean fromFront = dominantSign <= 0;
                if (dominantAxis == 0) {
                    box.xIncrement = fromFront;
                } else if (dominantAxis == 1) {
                    box.yIncrement = fromFront;
                } else {
                    box.zIncrement = fromFront;
                }
            }
            this.lastBox = box;
            this.boxReference.set(box);
        }
    }

    private void applyIterationConfig(PrinterBox box) {
        box.iterationMode = (IterationOrderType) Configs.Core.ITERATION_ORDER.getOptionListValue();
        box.xIncrement = !Configs.Core.X_REVERSE.getBooleanValue();
        box.yIncrement = !Configs.Core.Y_REVERSE.getBooleanValue();
        box.zIncrement = !Configs.Core.Z_REVERSE.getBooleanValue();
    }

    private PrinterBox createBox(LocalPlayer player, double range, int dominantAxis, int dominantSign) {
        int minX = (int) Math.floor(player.getX() - range);
        int maxX = (int) Math.ceil(player.getX() + range);
        int minY = (int) Math.floor(player.getEyeY() - range);
        int maxY = (int) Math.ceil(player.getEyeY() + range);
        int minZ = (int) Math.floor(player.getZ() - range);
        int maxZ = (int) Math.ceil(player.getZ() + range);
        // 移动自适应：沿主移动方向多延伸 motionAheadBlocks 格
        int ahead = Configs.Core.MOTION_AHEAD.getIntegerValue();
        if (dominantAxis >= 0 && ahead > 0) {
            if (dominantAxis == 0) {
                if (dominantSign > 0) {
                    maxX += ahead;
                } else {
                    minX -= ahead;
                }
            } else if (dominantAxis == 1) {
                if (dominantSign > 0) {
                    maxY += ahead;
                } else {
                    minY -= ahead;
                }
            } else if (dominantSign > 0) {
                maxZ += ahead;
            } else {
                minZ -= ahead;
            }
        }
        return new PrinterBox(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private static double getBoxRange() {
        double workRange = ConfigUtils.getWorkRange();
        if (Configs.Core.CHECK_PLAYER_INTERACTION_RANGE.getBooleanValue()) {
            return Math.min(workRange, PlayerUtils.getPlayerBlockInteractionRange(5) + 3.0D);
        }
        return workRange;
    }

    private static BlockPos trackingPos(LocalPlayer player) {
        return new BlockPos(
                (int) Math.floor(player.getX()),
                (int) Math.floor(player.getEyeY()),
                (int) Math.floor(player.getZ())
        );
    }

}
