//#if MC >= 12102
package me.K1nse_.litematica.printer.mixin.printer.mc;

import net.minecraft.client.player.ClientInput;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ClientInput.class)
public interface ClientInputAccessor {
   @Accessor("moveVector")
   void printer$setMoveVector(Vec2 var1);
}

//#endif
