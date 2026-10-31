//#if MC >= 260100
package me.K1nse_.litematica.printer.go;

import it.unimi.dsi.fastutil.longs.Long2BooleanOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import me.K1nse_.litematica.printer.config.Configs;
import me.K1nse_.litematica.printer.printer.SchematicStateCache;
import me.K1nse_.litematica.printer.utils.minecraft.BlockStateUtils;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public final class GoPathfinder {
   public static final float COST_INF = 1000000.0F;
   private static final float MIN_IMPROVEMENT = 0.01F;
   private static final int MAX_EMPTY_CHUNKS = 50;
   private static final int MAX_NODES = 300000;
   private static final long CHECK_INTERVAL_NANOS = 1000000L;
   private static final int PROBE_CACHE_MAX = 1500000;
   private static final int[][] DIRS = new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
   private static final int[][] DIAGS = new int[][]{{1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
   private static final byte BIT_SOLID = 1;
   private static final byte BIT_LAVA = 2;
   private static final byte BIT_WATER = 4;
   private static final byte BIT_CLIMB = 8;
   private final ClientLevel level;
   private final GoPathfinder.Goal goal;
   private final int maxFall;
   private final float[] fallTicks;
   private final int costLimitFactor;
   private final float heuristicWeight;
   private final float walkCost;
   private final float diagonalCost;
   private final float jumpUpCost;
   private final float waterCost;
   private final float ladderCost;
   private final float ladderExitUpCost;
   private final float parkourCost;
   private final float sprintCost;
   private final Long2ObjectOpenHashMap<GoPathfinder.Node> map = new Long2ObjectOpenHashMap(4096);
   private final GoPathfinder.Heap heap = new GoPathfinder.Heap();
   private final Long2ByteOpenHashMap probeCache = new Long2ByteOpenHashMap();
   private final MutableBlockPos cursorA = new MutableBlockPos();
   private int emptyChunks;
   private static final int STAND_SPOT_CACHE_MAX = 4096;
   private static final int STAND_RADIUS = 3;
   private static final int STAND_VERTICAL = 1;
   private static final int STAND_PROBE_CACHE_MAX = 65536;
   private static final Long2ByteOpenHashMap STAND_PROBE_CACHE = new Long2ByteOpenHashMap();
   private static long standProbeRevision = Long.MIN_VALUE;
   private static final Long2BooleanOpenHashMap STAND_SPOT_CACHE = new Long2BooleanOpenHashMap();
   private static long standSpotCacheRevision = Long.MIN_VALUE;

   static float walkCost() {
      return (float)Configs.Go.GO_WALK_COST.getDoubleValue();
   }

   static float diagonalCost() {
      return (float)Configs.Go.GO_WALK_DIAGONAL_COST.getDoubleValue();
   }

   static float sprintCost() {
      return (float)Configs.Go.GO_SPRINT_COST.getDoubleValue();
   }

   static float jumpUpCost() {
      return (float)Configs.Go.GO_JUMP_UP_COST.getDoubleValue();
   }

   static float waterCost() {
      return (float)Configs.Go.GO_WATER_COST.getDoubleValue();
   }

   static float ladderCost() {
      return (float)Configs.Go.GO_LADDER_COST.getDoubleValue();
   }

   static float ladderExitCost() {
      return (float)Configs.Go.GO_LADDER_EXIT_COST.getDoubleValue();
   }

   static float costLimit(float h0, int factor) {
      return factor > 1 && !(h0 <= 0.5F) ? h0 * factor : Float.MAX_VALUE;
   }

   static float horizUnit() {
      return Configs.Go.GO_FORCE_SPRINT.getBooleanValue() ? sprintCost() : walkCost();
   }

   static float upUnit() {
      return jumpUpCost() - horizUnit();
   }

   static float octile(float dx, float dz) {
      float ax = Math.abs(dx);
      float az = Math.abs(dz);
      return Math.max(ax, az) + 0.41421357F * Math.min(ax, az);
   }

   public static GoPathfinder.Goal blockGoal(BlockPos pos) {
      final int gx = pos.getX();
      final int gy = pos.getY();
      final int gz = pos.getZ();
      final float unit = horizUnit();
      final float upB = upUnit();
      return new GoPathfinder.Goal() {
         @Override
         public boolean isInGoal(int x, int y, int z) {
            return x == gx && z == gz && Math.abs(y - gy) <= 1;
         }

         @Override
         public float heuristic(int x, int y, int z) {
            float up = Math.max(0, y - gy);
            return GoPathfinder.octile(x - gx, z - gz) * unit + up * upB;
         }
      };
   }

   public static GoPathfinder.Goal adjacentGoal(final BlockPos target) {
      final int tx = target.getX();
      final int ty = target.getY();
      final int tz = target.getZ();
      final float unit = horizUnit();
      final float upB = upUnit();
      return new GoPathfinder.Goal() {
         @Override
         public boolean isInGoal(int x, int y, int z) {
            return GoPathfinder.isAdjacentArrived(target, x, y, z);
         }

         @Override
         public float heuristic(int x, int y, int z) {
            float flat = Math.max(0.0F, GoPathfinder.octile(x - tx, z - tz) - 1.0F);
            float up = Math.max(0, y - ty - 1);
            return flat * unit + up * upB;
         }
      };
   }

   public static GoPathfinder.GoalSet goalSet(List<BlockPos> targets, int limit, BlockPos from) {
      ArrayList<BlockPos> list = new ArrayList<>(targets);
      if (list.size() > limit) {
         list.sort(Comparator.comparingDouble(p -> p.distSqr(from)));

         while (list.size() > limit) {
            list.remove(list.size() - 1);
         }
      }

      Long2ObjectOpenHashMap<BlockPos> cellToTarget = new Long2ObjectOpenHashMap(list.size() * 16);
      Map<Long, GoPathfinder.Bucket> bucketMap = new HashMap<>();

      for (BlockPos t : list) {
         for (int dy = -1; dy <= 1; dy++) {
            for (int[] d : DIRS) {
               long key = BlockPos.asLong(t.getX() + d[0], t.getY() + dy, t.getZ() + d[1]);
               cellToTarget.putIfAbsent(key, t);
            }
         }

         long sk = BlockPos.asLong(t.getX() >> 4, t.getY() >> 4, t.getZ() >> 4);
         GoPathfinder.Bucket b = bucketMap.computeIfAbsent(sk, k -> new GoPathfinder.Bucket());
         b.loX = Math.min(b.loX, t.getX() - 1);
         b.loY = Math.min(b.loY, t.getY() - 1);
         b.loZ = Math.min(b.loZ, t.getZ() - 1);
         b.hiX = Math.max(b.hiX, t.getX() + 1);
         b.hiY = Math.max(b.hiY, t.getY() + 1);
         b.hiZ = Math.max(b.hiZ, t.getZ() + 1);
      }

      return new GoPathfinder.GoalSet(cellToTarget, bucketMap.values().toArray(new GoPathfinder.Bucket[0]));
   }

   public static boolean isAdjacentArrived(BlockPos target, int x, int y, int z) {
      int dx = Math.abs(x - target.getX());
      int dz = Math.abs(z - target.getZ());
      return dx + dz == 1 && Math.abs(y - target.getY()) <= 1;
   }

   private GoPathfinder(ClientLevel level, GoPathfinder.Goal goal, int maxFall, int costLimitFactor) {
      this.level = level;
      this.goal = goal;
      this.maxFall = maxFall;
      this.costLimitFactor = costLimitFactor;
      this.heuristicWeight = (float)Configs.Go.GO_HEURISTIC_WEIGHT.getDoubleValue();
      this.fallTicks = buildFallTicks(maxFall + 4);
      this.probeCache.defaultReturnValue((byte)-1);
      this.walkCost = walkCost();
      this.diagonalCost = diagonalCost();
      this.sprintCost = sprintCost();
      this.jumpUpCost = jumpUpCost();
      this.waterCost = waterCost();
      this.ladderCost = ladderCost();
      this.ladderExitUpCost = ladderExitCost();
      this.parkourCost = (float)Configs.Go.GO_PARKOUR_COST.getDoubleValue();
   }

   @Nullable
   public static GoPathfinder.Result findPath(
      ClientLevel level, BlockPos start, GoPathfinder.Goal goal, long budgetMs, int maxFall, int costLimitFactor, BooleanSupplier cancelled
   ) {
      return new GoPathfinder(level, goal, maxFall, costLimitFactor).find(start, budgetMs * 1000000L, cancelled);
   }

   @Nullable
   private GoPathfinder.Result find(BlockPos start, long budgetNanos, BooleanSupplier cancelled) {
      long deadline = System.nanoTime() + budgetNanos;
      long nextCheck = System.nanoTime() + 1000000L;
      float h0 = this.goal.heuristic(start.getX(), start.getY(), start.getZ());
      GoPathfinder.Node startNode = new GoPathfinder.Node(null, start.getX(), start.getY(), start.getZ(), 0.0F, h0, h0 * this.heuristicWeight);
      this.map.put(BlockPos.asLong(startNode.x, startNode.y, startNode.z), startNode);
      this.heap.push(startNode);
      GoPathfinder.Node best = startNode;
      float limit = costLimit(startNode.h, this.costLimitFactor);
      GoPathfinder.Node bestGoal = null;
      boolean admissibleOrder = this.heuristicWeight <= 1.0F;

      while (!this.heap.isEmpty()) {
         GoPathfinder.Node cur = this.heap.pop();
         if (this.goal.isInGoal(cur.x, cur.y, cur.z)) {
            if (bestGoal == null || cur.g < bestGoal.g) {
               bestGoal = cur;
            }
         } else if (bestGoal != null && cur.g + cur.h >= bestGoal.g) {
            if (admissibleOrder) {
               break;
            }
         } else if (cur.g + cur.h > limit) {
            if (admissibleOrder) {
               return bestGoal == null ? null : this.buildPath(bestGoal, true);
            }
         } else {
            if (cur.h < best.h) {
               best = cur;
            }

            if (System.nanoTime() >= nextCheck) {
               nextCheck = System.nanoTime() + 1000000L;
               if (cancelled.getAsBoolean() || System.nanoTime() >= deadline || this.map.size() > 300000) {
                  break;
               }
            }

            if (this.emptyChunks >= 50) {
               break;
            }

            this.expand(cur);
         }
      }

      if (bestGoal != null) {
         return this.buildPath(bestGoal, true);
      } else {
         return best == startNode ? null : this.buildPath(best, false);
      }
   }

   private void expand(GoPathfinder.Node cur) {
      for (int[] d : DIRS) {
         this.traverse(cur, d[0], d[1]);
      }

      for (int[] d : DIAGS) {
         this.diagonal(cur, d[0], d[1]);
      }

      for (int[] d : DIRS) {
         this.ascend(cur, d[0], d[1]);
      }

      for (int[] d : DIRS) {
         this.descend(cur, d[0], d[1]);
      }

      if (Configs.Go.GO_FORCE_SPRINT.getBooleanValue() && this.gapAdjacent(cur)) {
         for (int[] d : DIRS) {
            this.parkour(cur, d[0], d[1]);
         }
      }

      this.climb(cur);
   }

   private boolean gapAdjacent(GoPathfinder.Node cur) {
      for (int[] d : DIRS) {
         if (!this.walkableFloor(cur.x + d[0], cur.y, cur.z)) {
            return true;
         }
      }

      return false;
   }

   private void traverse(GoPathfinder.Node cur, int dx, int dz) {
      int nx = cur.x + dx;
      int nz = cur.z + dz;
      if (this.loaded(nx, nz)) {
         if (this.walkableFloor(nx, cur.y, nz) && this.passable(nx, cur.y, nz) && this.passable(nx, cur.y + 1, nz)) {
            float cost = this.waterAt(nx, cur.y, nz) ? this.waterCost : this.walkCost;
            this.offer(cur, nx, cur.y, nz, cost);
         }
      }
   }

   private void diagonal(GoPathfinder.Node cur, int dx, int dz) {
      int nx = cur.x + dx;
      int nz = cur.z + dz;
      if (this.loaded(nx, nz)) {
         if (this.walkableFloor(nx, cur.y, nz) && this.passable(nx, cur.y, nz) && this.passable(nx, cur.y + 1, nz)) {
            if (this.passable(cur.x + dx, cur.y, cur.z)
               && this.passable(cur.x + dx, cur.y + 1, cur.z)
               && this.passable(cur.x, cur.y, cur.z + dz)
               && this.passable(cur.x, cur.y + 1, cur.z + dz)) {
               float cost = this.waterAt(nx, cur.y, nz) ? this.waterCost * 1.4142135F : this.diagonalCost;
               this.offer(cur, nx, cur.y, nz, cost);
            }
         }
      }
   }

   private void ascend(GoPathfinder.Node cur, int dx, int dz) {
      int nx = cur.x + dx;
      int nz = cur.z + dz;
      int ny = cur.y + 1;
      if (this.loaded(nx, nz)) {
         if (this.walkableFloor(nx, ny, nz) && this.passable(nx, ny, nz) && this.passable(nx, ny + 1, nz)) {
            if (this.passable(cur.x, cur.y + 2, cur.z)) {
               this.offer(cur, nx, ny, nz, this.jumpUpCost);
            }
         }
      }
   }

   private void descend(GoPathfinder.Node cur, int dx, int dz) {
      int nx = cur.x + dx;
      int nz = cur.z + dz;
      if (this.loaded(nx, nz)) {
         if (this.passable(nx, cur.y, nz) && this.passable(nx, cur.y + 1, nz)) {
            int feet = cur.y - 1;

            for (int minY = this.level.getMinY(); feet >= minY; feet--) {
               if (this.climbableAt(nx, feet, nz)) {
                  return;
               }

               if (!this.passable(nx, feet, nz)) {
                  return;
               }

               if (this.walkableFloor(nx, feet, nz)) {
                  int fallDist = cur.y - feet;
                  if (fallDist > this.maxFall) {
                     return;
                  }

                  this.offer(cur, nx, feet, nz, this.walkCost + this.fallTicks[fallDist]);
                  return;
               }
            }
         }
      }
   }

   private void parkour(GoPathfinder.Node cur, int dx, int dz) {
      if (!this.waterAt(cur.x, cur.y, cur.z) && this.passable(cur.x, cur.y + 2, cur.z)) {
         int gx = cur.x;
         int gz = cur.z;

         for (int gap = 1; gap <= 3; gap++) {
            gx += dx;
            gz += dz;
            if (!this.loaded(gx, gz)) {
               return;
            }

            if (this.walkableFloor(gx, cur.y, gz)
               || this.climbableAt(gx, cur.y, gz)
               || !this.passable(gx, cur.y, gz)
               || !this.passable(gx, cur.y + 1, gz)
               || !this.passable(gx, cur.y + 2, gz)) {
               return;
            }

            int lx = gx + dx;
            int lz = gz + dz;
            if (this.loaded(lx, lz) && this.walkableFloor(lx, cur.y, lz) && this.passable(lx, cur.y, lz) && this.passable(lx, cur.y + 1, lz)) {
               this.offer(cur, lx, cur.y, lz, this.parkourCost);
            }
         }
      }
   }

   private void climb(GoPathfinder.Node cur) {
      int x = cur.x;
      int y = cur.y;
      int z = cur.z;
      if (this.climbableAt(x, y, z)) {
         if (this.climbableAt(x, y + 1, z) && this.passable(x, y + 2, z)) {
            this.offer(cur, x, y + 1, z, this.ladderCost);
         }

         if (this.climbableAt(x, y - 1, z)) {
            this.offer(cur, x, y - 1, z, this.ladderCost);
         }

         for (int[] d : DIRS) {
            int nx = x + d[0];
            int nz = z + d[1];
            if (this.loaded(nx, nz)) {
               if (this.walkableFloor(nx, y, nz) && this.passable(nx, y, nz) && this.passable(nx, y + 1, nz)) {
                  this.offer(cur, nx, y, nz, this.walkCost);
               }

               if (this.walkableFloor(nx, y + 1, nz) && this.passable(nx, y + 1, nz) && this.passable(nx, y + 2, nz)) {
                  this.offer(cur, nx, y + 1, nz, this.ladderExitUpCost);
               }
            }
         }
      } else if (this.passable(x, y, z)) {
         for (int[] d : DIRS) {
            int nx = x + d[0];
            int nz = z + d[1];
            if (this.loaded(nx, nz)) {
               if (this.climbableAt(nx, y, nz) && this.passable(nx, y + 1, nz)) {
                  this.offer(cur, nx, y, nz, this.walkCost);
               } else if (this.climbableAt(nx, y + 1, nz) && this.passable(nx, y + 2, nz)) {
                  this.offer(cur, nx, y + 1, nz, this.jumpUpCost);
               } else if (this.passable(nx, y, nz) && this.climbableAt(nx, y - 1, nz)) {
                  this.offer(cur, nx, y - 1, nz, this.walkCost + this.fallTicks[1]);
               }
            }
         }
      }
   }

   private void offer(GoPathfinder.Node parent, int x, int y, int z, float cost) {
      float tentative = parent.g + cost;
      long key = BlockPos.asLong(x, y, z);
      GoPathfinder.Node n = (GoPathfinder.Node)this.map.get(key);
      if (n == null) {
         float h = this.goal.heuristic(x, y, z);
         n = new GoPathfinder.Node(parent, x, y, z, tentative, h, h * this.heuristicWeight);
         this.map.put(key, n);
         this.heap.push(n);
      } else if (tentative < n.g - 0.01F) {
         if (n.heapIndex == -1) {
            return;
         }

         n.g = tentative;
         n.parent = parent;
         this.heap.update(n);
      }
   }

   private boolean loaded(int x, int z) {
      if (BlockStateUtils.isColumnLoaded(this.level, x >> 4, z >> 4)) {
         return true;
      }

      this.emptyChunks++;
      return false;
   }

   private byte probe(int x, int y, int z) {
      long key = BlockPos.asLong(x, y, z);
      byte b = this.probeCache.get(key);
      if (b >= 0) {
         return b;
      }

      b = probeBlock(this.level, this.cursorA, x, y, z);
      if (this.probeCache.size() >= 1500000) {
         this.probeCache.clear();
      }

      this.probeCache.put(key, b);
      return b;
   }

   static byte probeBlock(ClientLevel level, MutableBlockPos mpos, int x, int y, int z) {
      BlockState state = level.getBlockState(mpos.set(x, y, z));
      byte b = 0;
      if (!state.getCollisionShape(level, mpos).isEmpty()) {
         b = (byte)(b | 1);
      } else if (state.getFluidState().is(FluidTags.LAVA)) {
         b = (byte)(b | 2);
      }

      if (state.getFluidState().is(FluidTags.WATER)) {
         b = (byte)(b | 4);
      }

      if (state.is(BlockTags.CLIMBABLE)) {
         b = (byte)(b | 8);
      }

      return b;
   }

   static boolean hasStandableNeighbor(ClientLevel level, BlockPos target) {
      long rev = SchematicStateCache.INSTANCE.getRevision();
      if (rev != standSpotCacheRevision) {
         standSpotCacheRevision = rev;
         STAND_SPOT_CACHE.clear();
      }

      long key = target.asLong();
      if (STAND_SPOT_CACHE.containsKey(key)) {
         return STAND_SPOT_CACHE.get(key);
      }

      boolean ok = computeStandableNeighbor(level, target);
      if (STAND_SPOT_CACHE.size() >= 4096) {
         STAND_SPOT_CACHE.clear();
      }

      STAND_SPOT_CACHE.put(key, ok);
      return ok;
   }

   private static boolean computeStandableNeighbor(ClientLevel level, BlockPos target) {
      int tx = target.getX();
      int ty = target.getY();
      int tz = target.getZ();

      for (int r = 1; r <= 3; r++) {
         for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -r; dx <= r; dx++) {
               boolean xEdge = dx == -r || dx == r;

               for (int dz = -r; dz <= r; dz++) {
                  if ((xEdge || dz == -r || dz == r) && standableAt(level, tx + dx, ty + dy, tz + dz)) {
                     return true;
                  }
               }
            }
         }
      }

      return false;
   }

   private static boolean standableAt(ClientLevel level, int x, int y, int z) {
      long rev = SchematicStateCache.INSTANCE.getRevision();
      if (rev != standProbeRevision) {
         standProbeRevision = rev;
         STAND_PROBE_CACHE.clear();
      }

      long key = BlockPos.asLong(x, y, z);
      byte cached = STAND_PROBE_CACHE.get(key);
      if (cached != 0) {
         return cached > 0;
      }

      boolean ok = computeStandable(level, x, y, z);
      if (STAND_PROBE_CACHE.size() >= 65536) {
         STAND_PROBE_CACHE.clear();
      }

      STAND_PROBE_CACHE.put(key, (byte)(ok ? 1 : -1));
      return ok;
   }

   private static boolean computeStandable(ClientLevel level, int x, int y, int z) {
      if (!BlockStateUtils.isColumnLoaded(level, x >> 4, z >> 4)) {
         return false;
      }

      MutableBlockPos m = new MutableBlockPos();
      byte under = probeBlock(level, m, x, y - 1, z);
      if ((under & 1) != 0) {
         byte feet = probeBlock(level, m, x, y, z);
         byte head = probeBlock(level, m, x, y + 1, z);
         if (((feet & 8) != 0 || (feet & 3) == 0) && ((head & 8) != 0 || (head & 3) == 0)) {
            return true;
         }
      }

      return (probeBlock(level, m, x, y, z) & 8) != 0;
   }

   private boolean passable(int x, int y, int z) {
      byte b = this.probe(x, y, z);
      return (b & 8) != 0 || (b & 3) == 0;
   }

   private boolean walkableFloor(int x, int y, int z) {
      return (this.probe(x, y - 1, z) & 1) != 0;
   }

   private boolean waterAt(int x, int y, int z) {
      return (this.probe(x, y, z) & 4) != 0;
   }

   private boolean climbableAt(int x, int y, int z) {
      return (this.probe(x, y, z) & 8) != 0;
   }

   private GoPathfinder.Result buildPath(GoPathfinder.Node end, boolean reachedGoal) {
      int count = 0;

      for (GoPathfinder.Node n = end; n != null; n = n.parent) {
         count++;
      }

      ArrayList<BlockPos> out = new ArrayList<>(count);

      for (GoPathfinder.Node n = end; n != null; n = n.parent) {
         out.add(new BlockPos(n.x, n.y, n.z));
      }

      Collections.reverse(out);
      BlockPos goalCell = reachedGoal ? new BlockPos(end.x, end.y, end.z) : null;
      return new GoPathfinder.Result(out, reachedGoal, end.h / this.sprintCost, goalCell, end.g);
   }

   private static float[] buildFallTicks(int maxBlocks) {
      float[] ticks = new float[maxBlocks + 1];
      double velocity = 0.0;
      double fallen = 0.0;
      int t = 0;

      for (int n = 1; n <= maxBlocks; n++) {
         while (fallen < n) {
            velocity = (velocity - 0.08) * 0.98;
            fallen += -velocity;
            t++;
         }

         ticks[n] = t;
      }

      return ticks;
   }

   private static final class Bucket {
      int loX = Integer.MAX_VALUE;
      int loY = Integer.MAX_VALUE;
      int loZ = Integer.MAX_VALUE;
      int hiX = Integer.MIN_VALUE;
      int hiY = Integer.MIN_VALUE;
      int hiZ = Integer.MIN_VALUE;
   }

   public interface Goal {
      boolean isInGoal(int var1, int var2, int var3);

      float heuristic(int var1, int var2, int var3);

      default float horizDistanceTo(int x, int z) {
         return 0.0F;
      }
   }

   public static final class GoalSet implements GoPathfinder.Goal {
      private final Long2ObjectOpenHashMap<BlockPos> cellToTarget;
      private final LongOpenHashSet standCells;
      private final GoPathfinder.Bucket[] buckets;
      private final float unit;
      private final float upB;

      private GoalSet(Long2ObjectOpenHashMap<BlockPos> cellToTarget, GoPathfinder.Bucket[] buckets) {
         this.cellToTarget = cellToTarget;
         this.standCells = new LongOpenHashSet(cellToTarget.keySet());
         this.buckets = buckets;
         this.unit = GoPathfinder.horizUnit();
         this.upB = GoPathfinder.upUnit();
      }

      public Long2ObjectOpenHashMap<BlockPos> cellToTarget() {
         return this.cellToTarget;
      }

      @Override
      public boolean isInGoal(int x, int y, int z) {
         return this.standCells.contains(BlockPos.asLong(x, y, z));
      }

      @Override
      public float heuristic(int x, int y, int z) {
         float best = Float.MAX_VALUE;

         for (GoPathfinder.Bucket b : this.buckets) {
            float dx = Math.max(Math.max(b.loX - x, x - b.hiX), 0);
            float dz = Math.max(Math.max(b.loZ - z, z - b.hiZ), 0);
            float dv = y < b.loY ? b.loY - y : 0.0F;
            float h = (float)Math.sqrt(dx * dx + dz * dz) * this.unit + dv * this.upB;
            if (h < best) {
               best = h;
            }
         }

         return best;
      }
   }

   private static final class Heap {
      private GoPathfinder.Node[] nodes = new GoPathfinder.Node[1024];
      private int size;

      boolean isEmpty() {
         return this.size == 0;
      }

      void push(GoPathfinder.Node node) {
         if (this.size == this.nodes.length - 1) {
            this.nodes = Arrays.copyOf(this.nodes, this.nodes.length * 2);
         }

         this.nodes[++this.size] = node;
         this.siftUp(this.size);
      }

      GoPathfinder.Node pop() {
         GoPathfinder.Node top = this.nodes[1];
         GoPathfinder.Node last = this.nodes[this.size];
         this.nodes[this.size--] = null;
         if (this.size > 0) {
            this.nodes[1] = last;
            this.siftDown(1);
         }

         top.heapIndex = -1;
         return top;
      }

      void update(GoPathfinder.Node node) {
         if (node.heapIndex > 1) {
            this.siftUp(node.heapIndex);
         }
      }

      private void siftUp(int i) {
         GoPathfinder.Node n = this.nodes[i];

         while (i > 1) {
            int p = i >> 1;
            if (this.nodes[p].f() <= n.f()) {
               break;
            }

            this.nodes[i] = this.nodes[p];
            this.nodes[i].heapIndex = i;
            i = p;
         }

         this.nodes[i] = n;
         n.heapIndex = i;
      }

      private void siftDown(int i) {
         GoPathfinder.Node n = this.nodes[i];

         while (true) {
            int c = i << 1;
            if (c > this.size) {
               break;
            }

            if (c + 1 <= this.size && this.nodes[c + 1].f() < this.nodes[c].f()) {
               c++;
            }

            if (this.nodes[c].f() >= n.f()) {
               break;
            }

            this.nodes[i] = this.nodes[c];
            this.nodes[i].heapIndex = i;
            i = c;
         }

         this.nodes[i] = n;
         n.heapIndex = i;
      }
   }

   private static final class Node {
      @Nullable
      GoPathfinder.Node parent;
      final int x;
      final int y;
      final int z;
      final float h;
      final float hw;
      float g;
      int heapIndex = -1;

      Node(@Nullable GoPathfinder.Node parent, int x, int y, int z, float g, float h, float hw) {
         this.parent = parent;
         this.x = x;
         this.y = y;
         this.z = z;
         this.g = g;
         this.h = h;
         this.hw = hw;
      }

      float f() {
         return this.g + this.hw;
      }
   }

   public static final class Result {
      public final ArrayList<BlockPos> positions;
      public final boolean reachedGoal;
      public final float distanceToGoal;
      @Nullable
      public final BlockPos goalCell;
      public final float cost;

      Result(ArrayList<BlockPos> positions, boolean reachedGoal, float distanceToGoal, @Nullable BlockPos goalCell, float cost) {
         this.positions = positions;
         this.reachedGoal = reachedGoal;
         this.distanceToGoal = distanceToGoal;
         this.goalCell = goalCell;
         this.cost = cost;
      }
   }
}

//#endif
