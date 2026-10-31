package me.K1nse_.litematica.printer.mixin.printer.litematica;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.HashMultimap;
import fi.dy.masa.litematica.schematic.verifier.SchematicVerifier;
import fi.dy.masa.litematica.schematic.verifier.SchematicVerifier.BlockMismatch;
import fi.dy.masa.litematica.schematic.verifier.SchematicVerifier.MismatchType;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.apache.commons.lang3.tuple.Pair;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = SchematicVerifier.class, remap = false)
public interface SchematicVerifierAccessor {
   @Accessor("missingBlocksPositions")
   ArrayListMultimap<Pair<BlockState, BlockState>, BlockPos> printer$getMissingBlocksPositions();

   @Accessor("selectedCategories")
   Set<MismatchType> printer$getSelectedCategories();

   @Accessor("selectedEntries")
   HashMultimap<MismatchType, BlockMismatch> printer$getSelectedEntries();
}
