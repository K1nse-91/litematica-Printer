package me.K1nse_.litematica.printer.mixin.printer.litematica;

import fi.dy.masa.litematica.render.schematic.ChunkMeshDataSchematic;
import fi.dy.masa.litematica.render.schematic.ChunkRenderDataSchematic;
import fi.dy.masa.litematica.render.schematic.ChunkRenderDispatcherBuffers;
import fi.dy.masa.litematica.render.schematic.ChunkRendererSchematicVbo;
import fi.dy.masa.litematica.util.OverlayType;
import me.K1nse_.litematica.printer.printer.RenderOnlyBlockCache;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 只渲染指定方块（移植自 EMT）：缺失方块的线框只对名单内的方块绘制。
 */
@Mixin(value = ChunkRendererSchematicVbo.class, remap = false)
public class MixinChunkRendererSchematicVbo {
    @Inject(method = "renderOverlay", at = @At("HEAD"), cancellable = true)
    private void litematica_printer$filterRenderOverlay(OverlayType type, BlockPos pos, BlockState state, boolean missing,
                                                        ChunkRenderDataSchematic data, ChunkMeshDataSchematic chunkMeshData,
                                                        ChunkRenderDispatcherBuffers pack, CallbackInfo ci) {
        if (type == OverlayType.MISSING && !RenderOnlyBlockCache.shouldRender(state)) {
            ci.cancel();
        }
    }
}
