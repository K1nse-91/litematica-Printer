//#if MC >= 260100
package me.K1nse_.litematica.printer.go;

import java.util.Arrays;
import me.K1nse_.litematica.printer.config.Configs;
import me.K1nse_.litematica.printer.printer.SchematicStateCache;
import me.K1nse_.litematica.printer.utils.minecraft.BlockStateUtils;
import me.K1nse_.litematica.printer.utils.ConfigUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public final class GhastFlyer {
   private static final double HORIZ_ARRIVE = 1.6;
   private static final double VERT_DEADZONE = 1.0;
   private static final float DESCEND_PITCH = 90.0F;
   private static final float ASCEND_PITCH = -90.0F;
   private static final double COAST_FACTOR = 10.1;
   private static final int ESCAPE_TICKS = 30;
   private static final double ESCAPE_PROBE = 3.0;
   private static final double ESCAPE_PROBE_STEP = 1.0;
   private static final double ESCAPE_DRIVE = 3.0;
   private static final double ESCAPE_MIN_MOVE = 1.0;
   private static final int SCHEMATIC_CHECK_INTERVAL_TICKS = 2;
   private static int escapeTicks;
   private static int escapeTier;
   private static double escapeX;
   private static double escapeY;
   private static double escapeZ;
   private static double escapeDirX;
   private static double escapeDirY;
   private static double escapeDirZ;
   private static final int FAILED_DIRECTION_SLOTS = 8;
   private static final double[][] failedDirs = new double[8][3];
   private static final long[] failedDirExpire = new long[8];
   private static int failedDirCursor;
   private static final int FAILED_DIRECTION_TTL_TICKS = 100;
   private static int tier1FailStreak;
   private static final int TIER2_AFTER_TIER1_FAILS = 3;
   private static boolean pitchOwned;
   private static int schematicCheckCountdown;

   private GhastFlyer() {
   }

   public static boolean isEscaping() {
      return escapeTicks > 0;
   }

   public static boolean isEscapingTier2() {
      return escapeTicks > 0 && escapeTier == 2;
   }

   public static boolean isSettledAt(Entity nav, BlockPos wp) {
      double dx = wp.getX() + 0.5 - nav.getX();
      double dz = wp.getZ() + 0.5 - nav.getZ();
      double dy = wp.getY() + 0.5 - nav.getY();
      return dx * dx + dz * dz <= 2.5600000000000005 && Math.abs(dy) <= 1.0;
   }

   public static void drive(LocalPlayer player) {
      Entity nav = player.getVehicle();
      if (nav == null) {
         escapeTicks = 0;
         escapeTier = 0;
         GoExecutor.writeInput(player, 0.0F, 0.0F, false, false, false);
      } else {
         if (--schematicCheckCountdown <= 0) {
            schematicCheckCountdown = 2;
            if (escapeTicks <= 0 && ConfigUtils.isPrintModeActive() && boxHitsSchematic(player, nav)) {
               tryEscape(player);
            }
         }

         if (escapeTicks > 0) {
            escapeTicks--;
            double moved = Math.abs(nav.getX() - escapeX) + Math.abs(nav.getY() - escapeY) + Math.abs(nav.getZ() - escapeZ);
            boolean tier2 = escapeTier == 2;
            if ((tier2 || !boxHitsSchematic(player, nav)) && moved >= 1.0) {
               escapeTicks = 0;
               escapeTier = 0;
               tier1FailStreak = 0;
               clearFailedDirection();
               GoManager.INSTANCE.onEscapeSucceeded();
            } else if (escapeTicks == 0) {
               escapeTicks = 0;
               escapeTier = 0;
               if (tier2) {
                  tier1FailStreak = 0;
               } else {
                  tier1FailStreak++;
               }

               ClientLevel lvl = Minecraft.getInstance().level;
               if (lvl != null) {
                  markFailedDirection(lvl.getGameTime());
               }
            }
         }

         double targetZ;
         double targetX;
         double targetY;
         if (escapeTicks > 0) {
            targetX = escapeX + escapeDirX * 3.0;
            targetY = escapeY + escapeDirY * 3.0;
            targetZ = escapeZ + escapeDirZ * 3.0;
         } else {
            BlockPos wp = GoManager.INSTANCE.getWaypoint();
            if (wp == null) {
               GoExecutor.writeInput(player, 0.0F, 0.0F, false, false, false);
               return;
            }

            targetX = wp.getX() + 0.5;
            targetY = wp.getY() + 0.5;
            targetZ = wp.getZ() + 0.5;
         }

         double dx = targetX - nav.getX();
         double dz = targetZ - nav.getZ();
         double dy = targetY - nav.getY();
         double horiz = Math.sqrt(dx * dx + dz * dz);
         Vec3 vel = nav.getDeltaMovement();
         float yaw = player.getYRot();
         float pitch = 0.0F;
         float forward = 0.0F;
         float strafe = 0.0F;
         boolean jump = false;
         if (horiz > 1.6) {
            float bearing = (float)Math.toDegrees(Math.atan2(-dx, dz));
            yaw = bearing;
            pitch = 0.0F;
            float rad = Mth.wrapDegrees(bearing - nav.getYRot()) * (float) (Math.PI / 180.0);
            strafe = -Mth.sin(rad);
            forward = Mth.cos(rad);
            if (dy > 1.0) {
               jump = true;
            }

            double vAlong = (vel.x * dx + vel.z * dz) / horiz;
            if (vAlong > (horiz + 0.5) / 10.1) {
               strafe = Mth.sin(rad);
               forward = -Mth.cos(rad);
               jump = false;
            }
         } else if (dy > 1.0 || dy < -1.0) {
            double horizSpeed = Math.sqrt(vel.x * vel.x + vel.z * vel.z);
            if (horizSpeed > (horiz + 0.5) / 10.1) {
               float vBearing = (float)Math.toDegrees(Math.atan2(-vel.x, vel.z));
               yaw = vBearing;
               pitch = 0.0F;
               float rad = Mth.wrapDegrees(vBearing - nav.getYRot()) * (float) (Math.PI / 180.0);
               strafe = -Mth.sin(rad);
               forward = -Mth.cos(rad);
            } else if (dy > 1.0) {
               pitch = -90.0F;
               if (vel.y > 0.0 && dy < vel.y * 10.1 + 1.0) {
                  forward = -1.0F;
               } else if (vel.y < 0.0) {
                  forward = 0.0F;
               } else {
                  forward = 1.0F;
               }
            } else {
               pitch = 90.0F;
               if (vel.y < 0.0 && -dy < -vel.y * 10.1 + 1.0) {
                  forward = -1.0F;
               } else if (vel.y > 0.0) {
                  forward = 0.0F;
               } else {
                  forward = 1.0F;
               }
            }
         }

         player.setYRot(yaw);
         player.setXRot(pitch);
         pitchOwned = true;
         boolean sprint = Configs.Go.GO_FORCE_SPRINT.getBooleanValue();
         GoExecutor.writeInput(player, strafe, forward, jump, sprint, false);
      }
   }

   public static void driveStandby(LocalPlayer player) {
      Entity nav = player.getVehicle();
      if (nav == null) {
         escapeTicks = 0;
         escapeTier = 0;
      } else {
         if (escapeTicks <= 0 && --schematicCheckCountdown <= 0) {
            schematicCheckCountdown = 2;
            if (ConfigUtils.isPrintModeActive() && boxHitsSchematic(player, nav)) {
               tryEscape(player);
            }
         }

         if (escapeTicks > 0) {
            drive(player);
         }
      }
   }

   private static boolean boxHitsSchematic(LocalPlayer player, Entity nav) {
      GhastPathfinder.BoxSpec spec = GhastPathfinder.BoxSpec.of(nav, player);
      return GhastPathfinder.boxHitsSchematic(spec.at(nav.getX(), nav.getY(), nav.getZ()));
   }

   public static boolean tryEscape(LocalPlayer player) {
      Entity nav = player.getVehicle();
      Minecraft mc = Minecraft.getInstance();
      ClientLevel level = mc.level;
      if (nav != null && level != null) {
         GhastPathfinder.BoxSpec spec = GhastPathfinder.BoxSpec.of(nav, player);
         double x = nav.getX();
         double y = nav.getY();
         double z = nav.getZ();
         double ax = 1.0;
         double az = 0.0;
         BlockPos wp = GoManager.INSTANCE.getWaypoint();
         if (wp != null) {
            double wx = wp.getX() + 0.5 - x;
            double wz = wp.getZ() + 0.5 - z;
            double len = Math.sqrt(wx * wx + wz * wz);
            if (len > 0.001) {
               ax = wx / len;
               az = wz / len;
            }
         }

         double sx = -az;
         double sz = ax;
         double[][] table = new double[][]{
            {0.0, 1.0, 0.0},
            {0.0, 1.0, 1.0},
            {0.0, 1.0, -1.0},
            {1.0, 1.0, 1.0},
            {1.0, 1.0, -1.0},
            {-1.0, 1.0, 1.0},
            {-1.0, 1.0, -1.0},
            {0.0, 0.0, 1.0},
            {0.0, 0.0, -1.0},
            {-1.0, 0.0, 0.0},
            {1.0, 0.0, 0.0},
            {0.0, -1.0, 1.0},
            {0.0, -1.0, -1.0},
            {-1.0, -1.0, 1.0},
            {-1.0, -1.0, -1.0},
            {1.0, -1.0, 1.0},
            {1.0, -1.0, -1.0},
            {0.0, -1.0, 0.0}
         };
         if (tier1FailStreak >= 3 && tryTier2Escape(level, spec, table, x, y, z, ax, az, sx, sz, true)) {
            return true;
         }

         for (int dirIdx = 0; dirIdx < table.length; dirIdx++) {
            double[] dir = probeDirection(level, spec, table[dirIdx], x, y, z, ax, az, sx, sz, true);
            if (dir != null) {
               enterEscape(x, y, z, dir[0], dir[1], dir[2], 1);
               return true;
            }
         }

         return tryTier2Escape(level, spec, table, x, y, z, ax, az, sx, sz, false);
      } else {
         return false;
      }
   }

   private static boolean tryTier2Escape(
      ClientLevel level,
      GhastPathfinder.BoxSpec spec,
      double[][] table,
      double x,
      double y,
      double z,
      double ax,
      double az,
      double sx,
      double sz,
      boolean escalating
   ) {
      int bestIdx = -1;
      double bestOpen = -1.0;
      double[] bestDir = null;

      for (int dirIdx = 0; dirIdx < table.length; dirIdx++) {
         double[] dir = probeDirection(level, spec, table[dirIdx], x, y, z, ax, az, sx, sz, false);
         if (dir != null) {
            double open = endpointOpenness(level, x + dir[0] * 3.0, y + dir[1] * 3.0, z + dir[2] * 3.0);
            if (open > bestOpen) {
               bestOpen = open;
               bestDir = dir;
            }
         }
      }

      if (bestDir == null) {
         return false;
      }

      enterEscape(x, y, z, bestDir[0], bestDir[1], bestDir[2], 2);
      return true;
   }

   @Nullable
   private static double[] probeDirection(
      ClientLevel level,
      GhastPathfinder.BoxSpec spec,
      double[] c,
      double x,
      double y,
      double z,
      double ax,
      double az,
      double sx,
      double sz,
      boolean endpointMustClearSchematic
   ) {
      double dirX = ax * c[0] + sx * c[2];
      double dirZ = az * c[0] + sz * c[2];
      double dirY = c[1];
      double len = Math.sqrt(dirX * dirX + dirY * dirY + dirZ * dirZ);
      dirX /= len;
      dirY /= len;
      dirZ /= len;
      if (isFailedDirection(level.getGameTime(), dirX, dirY, dirZ)) {
         return null;
      } else {
         return !escapeRouteClear(level, spec, x, y, z, dirX, dirY, dirZ, endpointMustClearSchematic) ? null : new double[]{dirX, dirY, dirZ};
      }
   }

   private static void enterEscape(double x, double y, double z, double dirX, double dirY, double dirZ, int tier) {
      escapeX = x;
      escapeY = y;
      escapeZ = z;
      escapeDirX = dirX;
      escapeDirY = dirY;
      escapeDirZ = dirZ;
      escapeTicks = 30;
      escapeTier = tier;
   }

   private static double endpointOpenness(ClientLevel level, double ex, double ey, double ez) {
      int cx = Mth.floor(ex);
      int cy = Mth.floor(ey);
      int cz = Mth.floor(ez);
      int free = 0;
      int total = 0;
      MutableBlockPos m = new MutableBlockPos();

      for (int dx = -2; dx <= 2; dx++) {
         for (int dy = -2; dy <= 2; dy++) {
            for (int dz = -2; dz <= 2; dz++) {
               total++;
               int bx = cx + dx;
               int by = cy + dy;
               int bz = cz + dz;
               if (BlockStateUtils.isColumnLoaded(level, bx >> 4, bz >> 4)) {
                  m.set(bx, by, bz);
                  if (level.getBlockState(m).isAir()) {
                     BlockState sch = SchematicStateCache.INSTANCE.getSchematicState(m);
                     if (sch == null || sch.isAir()) {
                        free++;
                     }
                  }
               }
            }
         }
      }

      return (double)free / total;
   }

   private static boolean escapeRouteClear(
      ClientLevel level, GhastPathfinder.BoxSpec spec, double x, double y, double z, double dirX, double dirY, double dirZ, boolean endpointMustClearSchematic
   ) {
      double hd = Math.sqrt(dirX * dirX + dirZ * dirZ);
      double endX;
      double endY;
      double endZ;
      if (dirY < 0.0 && hd > 1.0E-6) {
         double horizFlown = Math.max(0.0, 3.0 * hd - 1.6);
         if (horizFlown > 0.0 && !escapeSegmentClear(level, spec, x, y, z, dirX / hd, 0.0, dirZ / hd, horizFlown)) {
            return false;
         }

         endX = x + dirX / hd * horizFlown;
         endY = y;
         endZ = z + dirZ / hd * horizFlown;
         double dropLen = 3.0 * -dirY;
         if (!escapeSegmentClear(level, spec, endX, endY, endZ, 0.0, -1.0, 0.0, dropLen)) {
            return false;
         }

         endY -= dropLen;
      } else {
         if (!escapeSegmentClear(level, spec, x, y, z, dirX, dirY, dirZ, 3.0)) {
            return false;
         }

         endX = x + dirX * 3.0;
         endY = y + dirY * 3.0;
         endZ = z + dirZ * 3.0;
      }

      return endpointMustClearSchematic ? !GhastPathfinder.boxHitsSchematic(spec.at(endX, endY, endZ)) : true;
   }

   private static boolean escapeSegmentClear(
      ClientLevel level, GhastPathfinder.BoxSpec spec, double x, double y, double z, double ux, double uy, double uz, double length
   ) {
      for (double d = 1.0; d < length - 1.0E-6; d++) {
         if (!escapePointLoadedAndClear(level, spec, x, y, z, ux, uy, uz, d)) {
            return false;
         }
      }

      return escapePointLoadedAndClear(level, spec, x, y, z, ux, uy, uz, length);
   }

   private static boolean escapePointLoadedAndClear(
      ClientLevel level, GhastPathfinder.BoxSpec spec, double x, double y, double z, double ux, double uy, double uz, double d
   ) {
      double px = x + ux * d;
      double py = y + uy * d;
      double pz = z + uz * d;
      return !BlockStateUtils.isColumnLoaded(level, Mth.floor(px) >> 4, Mth.floor(pz) >> 4) ? false : level.noCollision(spec.at(px, py, pz));
   }

   private static void markFailedDirection(long gameTick) {
      failedDirs[failedDirCursor][0] = escapeDirX;
      failedDirs[failedDirCursor][1] = escapeDirY;
      failedDirs[failedDirCursor][2] = escapeDirZ;
      failedDirExpire[failedDirCursor] = gameTick + 100L;
      failedDirCursor = (failedDirCursor + 1) % 8;
   }

   private static void clearFailedDirection() {
      Arrays.fill(failedDirExpire, Long.MIN_VALUE);
   }

   private static boolean isFailedDirection(long gameTick, double dx, double dy, double dz) {
      for (int i = 0; i < 8; i++) {
         if (gameTick < failedDirExpire[i] && dx * failedDirs[i][0] + dy * failedDirs[i][1] + dz * failedDirs[i][2] > 0.999) {
            return true;
         }
      }

      return false;
   }

   public static void releasePitch(LocalPlayer player) {
      escapeTicks = 0;
      escapeTier = 0;
      if (pitchOwned) {
         player.setXRot(0.0F);
         pitchOwned = false;
      }
   }

   public static void resetRuntime() {
      escapeTicks = 0;
      escapeTier = 0;
      escapeX = 0.0;
      escapeY = 0.0;
      escapeZ = 0.0;
      escapeDirX = 0.0;
      escapeDirY = 0.0;
      escapeDirZ = 0.0;
      Arrays.fill(failedDirExpire, 0L);
      failedDirCursor = 0;
      tier1FailStreak = 0;
      pitchOwned = false;
      schematicCheckCountdown = 0;
   }
}

//#endif
