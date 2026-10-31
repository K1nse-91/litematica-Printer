package me.K1nse_.litematica.printer.mixin.printer.litematica;

import fi.dy.masa.litematica.render.schematic.BlockModelRendererSchematic;
import me.K1nse_.litematica.printer.printer.RenderOnlyBlockCache;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 只渲染指定方块（移植自 EMT）：邻居被剔除但不在名单内时，强制绘制该面。
 */
@Mixin(value = BlockModelRendererSchematic.class, remap = false)
public class MixinBlockModelRendererSchematic {
    @Inject(method = "shouldRenderModelSide", at = @At("RETURN"), cancellable = true)
    private void litematica_printer$renderFaceIfNeighborHidden(BlockAndTintGetter world, BlockState state, BlockPos pos,
                                                               Direction side, BlockPos neighbor,
                                                               CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ() && !RenderOnlyBlockCache.shouldRender(world.getBlockState(neighbor))) {
            cir.setReturnValue(true);
        }
    }
}
