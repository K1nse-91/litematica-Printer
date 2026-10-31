//#if MC >= 260100
package me.K1nse_.litematica.printer.go;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.animal.happyghast.HappyGhast;
import org.jetbrains.annotations.Nullable;

public final class GhastRideState {
   private GhastRideState() {
   }

   @Nullable
   public static HappyGhast riddenGhast(@Nullable LocalPlayer player) {
      if (player == null) {
         return null;
      } else {
         return player.getVehicle() instanceof HappyGhast ghast ? ghast : null;
      }
   }

   public static GhastRideState.Status check(@Nullable LocalPlayer player) {
      HappyGhast ghast = riddenGhast(player);
      if (ghast == null) {
         return GhastRideState.Status.NOT_RIDING;
      } else if (ghast.getFirstPassenger() != player) {
         return GhastRideState.Status.NOT_CONTROLLER;
      } else if (ghast.isOnStillTimeout()) {
         return GhastRideState.Status.STILL_TIMEOUT;
      } else {
         return ghast.getControllingPassenger() != player ? GhastRideState.Status.NO_HARNESS : GhastRideState.Status.OK;
      }
   }

   public static boolean canFly(@Nullable LocalPlayer player) {
      return check(player) == GhastRideState.Status.OK;
   }

   public enum Status {
      NOT_RIDING,
      NOT_CONTROLLER,
      NO_HARNESS,
      STILL_TIMEOUT,
      OK;

      // $VF: synthetic method
      private static GhastRideState.Status[] $values() {
         return new GhastRideState.Status[]{NOT_RIDING, NOT_CONTROLLER, NO_HARNESS, STILL_TIMEOUT, OK};
      }
   }
}

//#endif
