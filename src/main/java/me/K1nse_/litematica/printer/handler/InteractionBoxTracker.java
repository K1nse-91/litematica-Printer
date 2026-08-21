package me.K1nse_.litematica.printer.handler;

import me.K1nse_.litematica.printer.config.Configs;
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
    private PrinterBox lastBox;
    @Nullable
    private BlockPos lastPlayerPos;
    private double lastRange = Double.NaN;

    InteractionBoxTracker(boolean enabled) {
        this.boxReference = enabled ? new AtomicReference<>() : null;
    }

    @Nullable
    AtomicReference<PrinterBox> getBoxReference() {
        return this.boxReference;
    }

    void resetPlayerTracking() {
        this.lastPlayerPos = null;
    }

    void update(LocalPlayer player) {
        if (this.boxReference == null) {
            return;
        }
        BlockPos playerPos = trackingPos(player);
        double range = getBoxRange();
        @Nullable PrinterBox box = this.boxReference.get();
        if (box == null
                || !box.equals(this.lastBox)
                || this.lastPlayerPos == null
                || !this.lastPlayerPos.equals(playerPos)
                || Double.compare(this.lastRange, range) != 0) {
            this.lastPlayerPos = playerPos;
            this.lastRange = range;
            box = this.createBox(player, range);
            this.lastBox = box;
            this.boxReference.set(box);
        }
    }

    private PrinterBox createBox(LocalPlayer player, double range) {
        int minX = (int) Math.floor(player.getX() - range);
        int maxX = (int) Math.ceil(player.getX() + range);
        int minY = (int) Math.floor(player.getEyeY() - range);
        int maxY = (int) Math.ceil(player.getEyeY() + range);
        int minZ = (int) Math.floor(player.getZ() - range);
        int maxZ = (int) Math.ceil(player.getZ() + range);
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
