package me.K1nse_.litematica.printer.enums;

import me.K1nse_.litematica.printer.I18n;
import me.K1nse_.litematica.printer.config.ConfigOptionListEntry;
import me.K1nse_.litematica.printer.printer.PrinterBox;

/**
 * 迭代顺序（移植自 EMT）：控制 PrinterBox 遍历方块的轴优先级。
 */
public enum IterationOrderType implements ConfigOptionListEntry<IterationOrderType> {
    XYZ(I18n.of("printerIteratorMode.xyz"), Axis.X, Axis.Y, Axis.Z),
    XZY(I18n.of("printerIteratorMode.xzy"), Axis.X, Axis.Z, Axis.Y),
    YXZ(I18n.of("printerIteratorMode.yxz"), Axis.Y, Axis.X, Axis.Z),
    YZX(I18n.of("printerIteratorMode.yzx"), Axis.Y, Axis.Z, Axis.X),
    ZXY(I18n.of("printerIteratorMode.zxy"), Axis.Z, Axis.X, Axis.Y),
    ZYX(I18n.of("printerIteratorMode.zyx"), Axis.Z, Axis.Y, Axis.X);

    private final I18n i18n;
    public final Axis[] axis;

    IterationOrderType(I18n i18n, Axis... axis) {
        this.i18n = i18n;
        this.axis = axis;
    }

    @Override
    public I18n getI18n() {
        return this.i18n;
    }

    /** 把 primary 提到最外层，其余轴保持原有相对顺序 */
    public static IterationOrderType primaryFirst(IterationOrderType base, Axis primary) {
        Axis[] target = reorder(base.axis, primary);
        for (IterationOrderType t : values()) {
            if (t.axis[0] == target[0] && t.axis[1] == target[1] && t.axis[2] == target[2]) {
                return t;
            }
        }
        return base;
    }

    private static Axis[] reorder(Axis[] base, Axis primary) {
        Axis[] result = new Axis[]{primary, null, null};
        int idx = 1;
        for (Axis a : base) {
            if (a != primary) {
                result[idx++] = a;
            }
        }
        return result;
    }

    public enum Axis {
        X {
            @Override
            public int getCoord(PrinterBox box, int x, int y, int z) {
                return x;
            }

            @Override
            public int increment(PrinterBox box, int current) {
                return current + (box.xIncrement ? 1 : -1);
            }

            @Override
            public boolean isOverflow(PrinterBox box, int value) {
                return box.xIncrement ? value > box.maxX : value < box.minX;
            }

            @Override
            public int reset(PrinterBox box) {
                return box.xIncrement ? box.minX : box.maxX;
            }
        },
        Y {
            @Override
            public int getCoord(PrinterBox box, int x, int y, int z) {
                return y;
            }

            @Override
            public int increment(PrinterBox box, int current) {
                return current + (box.yIncrement ? 1 : -1);
            }

            @Override
            public boolean isOverflow(PrinterBox box, int value) {
                return box.yIncrement ? value > box.maxY : value < box.minY;
            }

            @Override
            public int reset(PrinterBox box) {
                return box.yIncrement ? box.minY : box.maxY;
            }
        },
        Z {
            @Override
            public int getCoord(PrinterBox box, int x, int y, int z) {
                return z;
            }

            @Override
            public int increment(PrinterBox box, int current) {
                return current + (box.zIncrement ? 1 : -1);
            }

            @Override
            public boolean isOverflow(PrinterBox box, int value) {
                return box.zIncrement ? value > box.maxZ : value < box.minZ;
            }

            @Override
            public int reset(PrinterBox box) {
                return box.zIncrement ? box.minZ : box.maxZ;
            }
        };

        public abstract int getCoord(PrinterBox box, int x, int y, int z);

        public abstract int increment(PrinterBox box, int current);

        public abstract boolean isOverflow(PrinterBox box, int value);

        public abstract int reset(PrinterBox box);
    }
}
