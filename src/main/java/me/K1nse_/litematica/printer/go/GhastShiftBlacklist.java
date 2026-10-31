//#if MC >= 260100
package me.K1nse_.litematica.printer.go;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

public final class GhastShiftBlacklist {
   private static final LongOpenHashSet BLOCKED = new LongOpenHashSet();

   private GhastShiftBlacklist() {
   }

   public static boolean contains(@Nullable LocalPlayer player, BlockPos pos) {
      if (GhastRideState.riddenGhast(player) == null) {
         if (!BLOCKED.isEmpty()) {
            BLOCKED.clear();
         }

         return false;
      } else {
         return BLOCKED.contains(pos.asLong());
      }
   }

   public static void add(@Nullable LocalPlayer player, BlockPos pos) {
      if (GhastRideState.riddenGhast(player) != null) {
         BLOCKED.add(pos.asLong());
      }
   }
}

//#endif
