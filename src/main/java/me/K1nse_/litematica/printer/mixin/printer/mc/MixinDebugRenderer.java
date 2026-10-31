//#if MC >= 260100
package me.K1nse_.litematica.printer.mixin.printer.mc;

import me.K1nse_.litematica.printer.go.GoRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.debug.DebugRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DebugRenderer.class)
public abstract class MixinDebugRenderer {
   @Inject(method = "emitGizmos", at = @At("TAIL"))
   private void printer$goDrawPath(Frustum frustum, double camX, double camY, double camZ, float partialTick, CallbackInfo ci) {
      GoRenderer.emitGizmos();
   }
}

//#endif
