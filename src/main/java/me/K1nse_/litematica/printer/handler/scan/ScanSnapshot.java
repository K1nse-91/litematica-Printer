package me.K1nse_.litematica.printer.handler.scan;

import net.minecraft.core.BlockPos;

public record ScanSnapshot(long key, int x, int y, int z, byte flags) {
    public BlockPos blockPos() {
        return new BlockPos(this.x, this.y, this.z);
    }

    public double distanceToSqr(double eyeX, double eyeY, double eyeZ) {
        double dx = this.x + 0.5D - eyeX;
        double dy = this.y + 0.5D - eyeY;
        double dz = this.z + 0.5D - eyeZ;
        return dx * dx + dy * dy + dz * dz;
    }
}
