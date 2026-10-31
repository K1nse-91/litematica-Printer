package me.K1nse_.litematica.printer.printer.verifier;

import com.google.common.collect.HashMultimap;
import fi.dy.masa.litematica.schematic.verifier.SchematicVerifier.BlockMismatch;
import fi.dy.masa.litematica.schematic.verifier.SchematicVerifier.MismatchType;
import java.util.Set;
import net.minecraft.world.level.block.state.BlockState;
import org.apache.commons.lang3.tuple.Pair;

public interface VerifierDataView {
   boolean forEachMismatch(MismatchType var1, VerifierDataView.MismatchVisitor var2);

   Set<MismatchType> getSelectedMismatchTypes();

   HashMultimap<MismatchType, BlockMismatch> getSelectedMismatchEntries();

   interface MismatchVisitor {
      boolean accept(Pair<BlockState, BlockState> var1, long var2);
   }
}
