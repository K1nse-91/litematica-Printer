package me.K1nse_.litematica.printer.mixin.printer.litematica;

import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import fi.dy.masa.litematica.schematic.verifier.SchematicVerifier;
import me.K1nse_.litematica.printer.config.Configs;
import me.K1nse_.litematica.printer.printer.verifier.OptimizedSchematicVerifier;
import me.K1nse_.litematica.printer.printer.verifier.VerifierRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = SchematicPlacement.class, remap = false)
public abstract class MixinSchematicPlacement {
   @Inject(method = "getSchematicVerifier", at = @At("HEAD"), cancellable = true)
   private void printer$useOptimizedVerifier(CallbackInfoReturnable<SchematicVerifier> cir) {
      if (Configs.Core.VERIFIER_OPTIMIZED.getBooleanValue()) {
         SchematicPlacement self = (SchematicPlacement)(Object)this;
         SchematicPlacementAccessor accessor = (SchematicPlacementAccessor)self;
         SchematicVerifier current = accessor.printer$getVerifier();
         if (current == null) {
            OptimizedSchematicVerifier reused = VerifierRegistry.get(self.getHashId());
            if (reused != null) {
               reused.rebindPlacement(self);
               accessor.printer$setVerifier(reused);
               cir.setReturnValue(reused);
               return;
            }

            SchematicVerifier optimized = new OptimizedSchematicVerifier();
            accessor.printer$setVerifier(optimized);
            cir.setReturnValue(optimized);
         }
      }
   }
}
