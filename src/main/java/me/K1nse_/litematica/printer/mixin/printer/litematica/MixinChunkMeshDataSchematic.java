package me.K1nse_.litematica.printer.mixin.printer.litematica;

import fi.dy.masa.litematica.render.schematic.ChunkMeshDataSchematic;
import me.K1nse_.litematica.printer.printer.RenderOnlyBlockCache;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 只渲染指定方块（移植自 EMT）：方块实体不在名单内时不加入网格。
 */
@Mixin(value = ChunkMeshDataSchematic.class, remap = false)
public class MixinChunkMeshDataSchematic {
    @Inject(method = "addBlockEntity", at = @At("HEAD"), cancellable = true)
    private void litematica_printer$filterAddBlockEntity(BlockEntity blockEntity, CallbackInfo ci) {
        if (!RenderOnlyBlockCache.shouldRender(blockEntity.getBlockState())) {
            ci.cancel();
        }
    }

    @Inject(method = "addNoCullBlockEntity", at = @At("HEAD"), cancellable = true)
    private void litematica_printer$filterAddNoCullBlockEntity(BlockEntity blockEntity, CallbackInfo ci) {
        if (!RenderOnlyBlockCache.shouldRender(blockEntity.getBlockState())) {
            ci.cancel();
        }
    }
}
