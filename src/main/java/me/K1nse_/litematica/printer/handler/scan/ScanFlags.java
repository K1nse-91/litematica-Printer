package me.K1nse_.litematica.printer.handler.scan;

public final class ScanFlags {
    public static final byte WORLD_NON_AIR = 1;
    public static final byte WORLD_FLUID = 1 << 1;
    public static final byte SCHEMATIC_NON_AIR = 1 << 2;
    public static final byte BASE_FILL_TARGET = 1 << 3;
    public static final byte TARGET = 1 << 4;
    public static final byte SCHEMATIC_SAMPLED = 1 << 5;

    private ScanFlags() {
    }

    public static boolean has(byte flags, byte flag) {
        return (flags & flag) != 0;
    }
}
