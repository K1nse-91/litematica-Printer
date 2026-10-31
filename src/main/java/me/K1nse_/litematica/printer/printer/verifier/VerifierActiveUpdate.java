package me.K1nse_.litematica.printer.printer.verifier;

import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import me.K1nse_.litematica.printer.mixin.printer.litematica.SchematicPlacementAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

public final class VerifierActiveUpdate {
   private VerifierActiveUpdate() {
   }

   public static void onScannerVerdict(SchematicPlacement placement, BlockPos pos, BlockState required, BlockState found) {
      if (((SchematicPlacementAccessor)placement).printer$getVerifier() instanceof OptimizedSchematicVerifier optimized) {
         if (optimized.isFinished()) {
            if (optimized.probeMismatch(pos, required, found)) {
               optimized.markBlockChanged(pos);
            }
         }
      }
   }
}
