//#if MC >= 260100
package me.K1nse_.litematica.printer.go;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

public final class GhastGoal {
   public static final int VERT_TOLERANCE = 3;
   public static final double REACH = 4.5;
   private static final double REACH_SQ = 20.25;

   private GhastGoal() {
   }

   public static int radius(double halfWidth) {
      return Math.max(3, (int)Math.ceil(halfWidth + 0.5));
   }

   private static boolean reachableFrom(int x, int y, int z, int tx, int ty, int tz, GhastGoal.EyeOffset eye) {
      double ex = x + 0.5 + eye.x() - (tx + 0.5);
      double ey = y + eye.y() - (ty + 0.5);
      double ez = z + 0.5 + eye.z() - (tz + 0.5);
      return ex * ex + ey * ey + ez * ez <= 20.25;
   }

   public static GoPathfinder.Goal hoverGoal(BlockPos target, final int r, @Nullable final GhastGoal.EyeOffset eye) {
      final int tx = target.getX();
      final int ty = target.getY();
      final int tz = target.getZ();
      final boolean useEye = eye != null && anyReachableHover(tx, ty, tz, r, eye);
      return new GoPathfinder.Goal() {
         @Override
         public boolean isInGoal(int x, int y, int z) {
            if (x == tx && y == ty && z == tz) {
               return false;
            } else {
               return Math.max(Math.abs(x - tx), Math.abs(z - tz)) <= r && Math.abs(y - ty) <= 3
                  ? !useEye || GhastGoal.reachableFrom(x, y, z, tx, ty, tz, eye)
                  : false;
            }
         }

         @Override
         public float heuristic(int x, int y, int z) {
            double dx = Math.max(0, Math.abs(x - tx) - r);
            double dy = Math.max(0, Math.abs(y - ty) - 3);
            double dz = Math.max(0, Math.abs(z - tz) - r);
            return (float)Math.sqrt(dx * dx + dy * dy + dz * dz);
         }

         @Override
         public float horizDistanceTo(int x, int z) {
            return Math.max(0, Math.max(Math.abs(x - tx), Math.abs(z - tz)) - r);
         }
      };
   }

   public static GhastGoal.HoverGoalSet hoverGoalSet(List<BlockPos> targets, int limit, BlockPos from, int r, @Nullable GhastGoal.EyeOffset eye) {
      ArrayList<BlockPos> list = new ArrayList<>(targets);
      if (list.size() > limit) {
         list.sort(Comparator.comparingDouble(p -> p.distSqr(from)));

         while (list.size() > limit) {
            list.remove(list.size() - 1);
         }
      }

      Long2ObjectOpenHashMap<BlockPos> cellToTarget = new Long2ObjectOpenHashMap(list.size() * 352);
      if (eye != null && fillHoverCells(list, r, eye, cellToTarget) == 0) {
         cellToTarget.clear();
         fillHoverCells(list, r, null, cellToTarget);
      }

      Map<Long, GhastGoal.Bucket> bucketMap = new HashMap<>();

      for (BlockPos t : list) {
         long sk = BlockPos.asLong(t.getX() >> 4, t.getY() >> 4, t.getZ() >> 4);
         GhastGoal.Bucket b = bucketMap.computeIfAbsent(sk, k -> new GhastGoal.Bucket());
         b.loX = Math.min(b.loX, t.getX() - r);
         b.loY = Math.min(b.loY, t.getY() - 3);
         b.loZ = Math.min(b.loZ, t.getZ() - r);
         b.hiX = Math.max(b.hiX, t.getX() + r);
         b.hiY = Math.max(b.hiY, t.getY() + 3);
         b.hiZ = Math.max(b.hiZ, t.getZ() + r);
      }

      return new GhastGoal.HoverGoalSet(cellToTarget, bucketMap.values().toArray(new GhastGoal.Bucket[0]));
   }

   private static int fillHoverCells(List<BlockPos> targets, int r, @Nullable GhastGoal.EyeOffset eye, Long2ObjectOpenHashMap<BlockPos> out) {
      int count = 0;

      for (BlockPos t : targets) {
         int tx = t.getX();
         int ty = t.getY();
         int tz = t.getZ();

         for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
               for (int dy = -3; dy <= 3; dy++) {
                  if ((dx != 0 || dy != 0 || dz != 0)
                     && (eye == null || reachableFrom(tx + dx, ty + dy, tz + dz, tx, ty, tz, eye))
                     && out.putIfAbsent(BlockPos.asLong(tx + dx, ty + dy, tz + dz), t) == null) {
                     count++;
                  }
               }
            }
         }
      }

      return count;
   }

   private static boolean anyReachableHover(int tx, int ty, int tz, int r, GhastGoal.EyeOffset eye) {
      for (int dx = -r; dx <= r; dx++) {
         for (int dz = -r; dz <= r; dz++) {
            for (int dy = -3; dy <= 3; dy++) {
               if ((dx != 0 || dy != 0 || dz != 0) && reachableFrom(tx + dx, ty + dy, tz + dz, tx, ty, tz, eye)) {
                  return true;
               }
            }
         }
      }

      return false;
   }

   private static final class Bucket {
      int loX = Integer.MAX_VALUE;
      int loY = Integer.MAX_VALUE;
      int loZ = Integer.MAX_VALUE;
      int hiX = Integer.MIN_VALUE;
      int hiY = Integer.MIN_VALUE;
      int hiZ = Integer.MIN_VALUE;
   }

   public record EyeOffset(double x, double y, double z) {
   }

   public static final class HoverGoalSet implements GoPathfinder.Goal {
      private final Long2ObjectOpenHashMap<BlockPos> cellToTarget;
      private final LongOpenHashSet cells;
      private final GhastGoal.Bucket[] buckets;

      private HoverGoalSet(Long2ObjectOpenHashMap<BlockPos> cellToTarget, GhastGoal.Bucket[] buckets) {
         this.cellToTarget = cellToTarget;
         this.cells = new LongOpenHashSet(cellToTarget.keySet());
         this.buckets = buckets;
      }

      public Long2ObjectOpenHashMap<BlockPos> cellToTarget() {
         return this.cellToTarget;
      }

      @Override
      public boolean isInGoal(int x, int y, int z) {
         return this.cells.contains(BlockPos.asLong(x, y, z));
      }

      @Override
      public float heuristic(int x, int y, int z) {
         float best = Float.MAX_VALUE;

         for (GhastGoal.Bucket b : this.buckets) {
            double dx = Math.max(Math.max(b.loX - x, x - b.hiX), 0);
            double dy = Math.max(Math.max(b.loY - y, y - b.hiY), 0);
            double dz = Math.max(Math.max(b.loZ - z, z - b.hiZ), 0);
            float h = (float)Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (h < best) {
               best = h;
            }
         }

         return best;
      }

      @Override
      public float horizDistanceTo(int x, int z) {
         float best = Float.MAX_VALUE;

         for (GhastGoal.Bucket b : this.buckets) {
            float dx = Math.max(Math.max(b.loX - x, x - b.hiX), 0);
            float dz = Math.max(Math.max(b.loZ - z, z - b.hiZ), 0);
            float d = Math.max(dx, dz);
            if (d < best) {
               best = d;
            }
         }

         return best == Float.MAX_VALUE ? 0.0F : best;
      }
   }
}

//#endif
