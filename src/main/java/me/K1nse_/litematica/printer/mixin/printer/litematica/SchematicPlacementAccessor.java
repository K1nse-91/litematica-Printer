package me.K1nse_.litematica.printer.mixin.printer.litematica;

import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import fi.dy.masa.litematica.schematic.verifier.SchematicVerifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = SchematicPlacement.class, remap = false)
public interface SchematicPlacementAccessor {
   @Accessor("verifier")
   SchematicVerifier printer$getVerifier();

   @Accessor("verifier")
   void printer$setVerifier(SchematicVerifier var1);
}
