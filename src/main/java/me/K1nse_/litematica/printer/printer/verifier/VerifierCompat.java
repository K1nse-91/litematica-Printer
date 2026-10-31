package me.K1nse_.litematica.printer.printer.verifier;

import fi.dy.masa.litematica.schematic.verifier.SchematicVerifier.BlockMismatch;
import fi.dy.masa.litematica.schematic.verifier.SchematicVerifier.MismatchRenderPos;
import fi.dy.masa.litematica.schematic.verifier.SchematicVerifier.MismatchType;
import fi.dy.masa.malilib.util.position.IntBoundingBox;
import net.minecraft.world.level.block.state.BlockState;

/**
 * BlockMismatch 字段/访问器兼容层：
 * litematica 0.27.x（MC 26.1）用公开字段，0.28+（MC 26.2/26.3）改为 record 访问器。
 */
public final class VerifierCompat {
    private VerifierCompat() {
    }

    public static MismatchType type(BlockMismatch mismatch) {
        //#if MC >= 260200
        //$$ return mismatch.mismatchType();
        //#else
        return mismatch.mismatchType;
        //#endif
    }

    public static BlockState expected(BlockMismatch mismatch) {
        //#if MC >= 260200
        //$$ return mismatch.stateExpected();
        //#else
        return mismatch.stateExpected;
        //#endif
    }

    public static BlockState found(BlockMismatch mismatch) {
        //#if MC >= 260200
        //$$ return mismatch.stateFound();
        //#else
        return mismatch.stateFound;
        //#endif
    }
    // ---- IntBoundingBox：malilib 0.28 用 getMinX()，0.29+ 改为 record 的 minX() ----

    public static int minX(IntBoundingBox box) {
        //#if MC >= 260200
        //$$ return box.minX();
        //#else
        return box.getMinX();
        //#endif
    }

    public static int minY(IntBoundingBox box) {
        //#if MC >= 260200
        //$$ return box.minY();
        //#else
        return box.getMinY();
        //#endif
    }

    public static int minZ(IntBoundingBox box) {
        //#if MC >= 260200
        //$$ return box.minZ();
        //#else
        return box.getMinZ();
        //#endif
    }

    public static int maxX(IntBoundingBox box) {
        //#if MC >= 260200
        //$$ return box.maxX();
        //#else
        return box.getMaxX();
        //#endif
    }

    public static int maxY(IntBoundingBox box) {
        //#if MC >= 260200
        //$$ return box.maxY();
        //#else
        return box.getMaxY();
        //#endif
    }

    public static int maxZ(IntBoundingBox box) {
        //#if MC >= 260200
        //$$ return box.maxZ();
        //#else
        return box.getMaxZ();
        //#endif
    }
    // ---- MismatchRenderPos：0.27 字段 / 0.28+ record 访问器 ----

    public static MismatchType renderType(MismatchRenderPos pos) {
        //#if MC >= 260200
        //$$ return pos.type();
        //#else
        return pos.type;
        //#endif
    }

    public static net.minecraft.core.BlockPos renderPos(MismatchRenderPos pos) {
        //#if MC >= 260200
        //$$ return pos.pos();
        //#else
        return pos.pos;
        //#endif
    }
}