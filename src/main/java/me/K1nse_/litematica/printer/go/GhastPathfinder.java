//#if MC >= 260100
package me.K1nse_.litematica.printer.go;

import it.unimi.dsi.fastutil.longs.Long2BooleanOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2FloatOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.PriorityQueue;
import java.util.function.BooleanSupplier;
import me.K1nse_.litematica.printer.config.Configs;
import me.K1nse_.litematica.printer.printer.SchematicStateCache;
import me.K1nse_.litematica.printer.utils.minecraft.BlockStateUtils;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

public final class GhastPathfinder {
   private static final int MAX_NODES = 120000;
   private static final double SWEEP_STEP = 0.5;
   private static final float VERT_LATE_CAP = 16.0F;
   private static final int MIN_CLEARANCE = 2;
   private static final int CLEARANCE_HUG = 1;
   private static final int CLEARANCE_CLEAR = 2;
   private static final float EPS = 1.0E-4F;
   private static final long CHECK_INTERVAL_NANOS = 1000000L;
   private static final int[][] DIRS26 = buildDirs26();
   private final Long2ByteOpenHashMap clearanceMemo = new Long2ByteOpenHashMap();
   private final ClientLevel level;
   private final GoPathfinder.Goal goal;
   private final GhastPathfinder.BoxSpec box;
   @Nullable
   private final LongOpenHashSet schematicSolid;
   private final Long2FloatOpenHashMap bestG = new Long2FloatOpenHashMap();
   private final PriorityQueue<GhastPathfinder.Node> open = new PriorityQueue<>(Comparator.comparingDouble(GhastPathfinder.Node::f));
   private final int costLimitFactor;
   private final float heuristicWeight;
   private final int minClearance;
   private final float costOrtho;
   private final float costDiag2;
   private final float costDiag3;
   private final float ascendMult;
   private final float descendMult;
   private final float wallPenalty;
   private final float vertLateWeight;
   private final float turnPenalty;
   private static final int HOVER_SPOT_VERTICAL = 1;
   private static final int HOVER_SPOT_CACHE_MAX = 4096;
   private static final Long2BooleanOpenHashMap HOVER_SPOT_CACHE = new Long2BooleanOpenHashMap();
   private static final int HOVER_CELL_CACHE_MAX = 65536;
   private static final Long2ByteOpenHashMap HOVER_CELL_CACHE = new Long2ByteOpenHashMap();
   private static long hoverSpotCacheRevision = Long.MIN_VALUE;
   private static long hoverCellCacheRevision = Long.MIN_VALUE;
   private static long hoverCellCacheSpec = Long.MIN_VALUE;

   private static int[][] buildDirs26() {
      ArrayList<int[]> list = new ArrayList<>(26);

      for (int dx = -1; dx <= 1; dx++) {
         for (int dy = -1; dy <= 1; dy++) {
            for (int dz = -1; dz <= 1; dz++) {
               if (dx != 0 || dy != 0 || dz != 0) {
                  list.add(new int[]{dx, dy, dz});
               }
            }
         }
      }

      return list.toArray(new int[0][]);
   }

   private GhastPathfinder(
      ClientLevel level, GoPathfinder.Goal goal, GhastPathfinder.BoxSpec box, @Nullable LongOpenHashSet schematicSolid, int costLimitFactor, int minClearance
   ) {
      this.level = level;
      this.goal = goal;
      this.box = box;
      this.schematicSolid = schematicSolid;
      this.costLimitFactor = costLimitFactor;
      this.heuristicWeight = (float)Configs.Go.GO_HEURISTIC_WEIGHT.getDoubleValue();
      this.minClearance = minClearance;
      this.costOrtho = (float)Configs.Go.GO_GHAST_COST_ORTHO.getDoubleValue();
      this.costDiag2 = (float)Configs.Go.GO_GHAST_COST_DIAG2.getDoubleValue();
      this.costDiag3 = (float)Configs.Go.GO_GHAST_COST_DIAG3.getDoubleValue();
      this.ascendMult = Configs.Go.GO_GHAST_ASCEND_MULT.getIntegerValue();
      this.descendMult = Configs.Go.GO_GHAST_DESCEND_MULT.getIntegerValue();
      this.wallPenalty = (float)Configs.Go.GO_GHAST_WALL_PENALTY.getDoubleValue();
      this.vertLateWeight = (float)Configs.Go.GO_GHAST_VERT_LATE_WEIGHT.getDoubleValue();
      this.turnPenalty = (float)Configs.Go.GO_GHAST_TURN_PENALTY.getDoubleValue();
      this.bestG.defaultReturnValue(Float.POSITIVE_INFINITY);
   }

   @Nullable
   public static GoPathfinder.Result findPath(
      ClientLevel level,
      BlockPos start,
      GoPathfinder.Goal goal,
      GhastPathfinder.BoxSpec box,
      @Nullable LongOpenHashSet schematicSolid,
      long budgetMs,
      int costLimitFactor,
      BooleanSupplier cancelled
   ) {
      long budgetNanos = Math.max(1L, budgetMs) * 1000000L;
      GoPathfinder.Result strict = new GhastPathfinder(level, goal, box, schematicSolid, costLimitFactor, 2).search(start, budgetNanos, cancelled);
      return strict == null && !cancelled.getAsBoolean()
         ? new GhastPathfinder(level, goal, box, schematicSolid, costLimitFactor, 1).search(start, budgetNanos, cancelled)
         : strict;
   }

   @Nullable
   private GoPathfinder.Result search(BlockPos start, long budgetNanos, BooleanSupplier cancelled) {
      long deadline = System.nanoTime() + budgetNanos;
      long nextCheck = System.nanoTime() + 1000000L;
      float startH = this.goal.heuristic(start.getX(), start.getY(), start.getZ());
      GhastPathfinder.Node startNode = new GhastPathfinder.Node(null, start, 0.0F, 0.0F, startH, startH * this.heuristicWeight);
      this.bestG.put(start.asLong(), 0.0F);
      this.open.add(startNode);
      int expanded = 0;
      GhastPathfinder.Node best = startNode;
      float limit = GoPathfinder.costLimit(startNode.h, this.costLimitFactor);
      GhastPathfinder.Node bestGoal = null;
      boolean admissibleOrder = this.heuristicWeight <= 1.0F;

      while (!this.open.isEmpty()) {
         GhastPathfinder.Node cur = this.open.poll();
         float known = this.bestG.get(cur.pos.asLong());
         if (!(cur.total() > known + 1.0E-4F)) {
            if (this.goal.isInGoal(cur.pos.getX(), cur.pos.getY(), cur.pos.getZ())) {
               if (bestGoal == null || cur.total() < bestGoal.total()) {
                  bestGoal = cur;
               }
            } else if (bestGoal != null && cur.g + cur.h >= bestGoal.total()) {
               if (admissibleOrder) {
                  break;
               }
            } else if (!(cur.g + cur.h > limit)) {
               if (cur.h < best.h) {
                  best = cur;
               }

               if (System.nanoTime() >= nextCheck) {
                  nextCheck = System.nanoTime() + 1000000L;
                  if (cancelled.getAsBoolean() || System.nanoTime() >= deadline || expanded > 120000) {
                     break;
                  }
               }

               expanded++;
               this.expand(cur);
            }
         }
      }

      if (bestGoal != null) {
         return this.buildPath(bestGoal, true);
      } else {
         return best == startNode ? null : this.buildPath(best, false);
      }
   }

   private void expand(GhastPathfinder.Node cur) {
      for (int[] d : DIRS26) {
         BlockPos next = cur.pos.offset(d[0], d[1], d[2]);
         if (this.sweepFree(cur.pos, next)) {
            float geo = cur.g + this.geoCost(d);
            float soft = cur.soft + this.softCost(d, next) + this.turnCost(cur, d);
            float tentative = geo + soft;
            long key = next.asLong();
            if (!(tentative >= this.bestG.get(key) - 1.0E-4F)) {
               this.bestG.put(key, tentative);
               float h = this.goal.heuristic(next.getX(), next.getY(), next.getZ());
               this.open.add(new GhastPathfinder.Node(cur, next, geo, soft, h, h * this.heuristicWeight));
            }
         }
      }
   }

   private float turnCost(GhastPathfinder.Node cur, int[] d) {
      if (!(this.turnPenalty <= 0.0F) && cur.parent != null) {
         BlockPos pp = cur.parent.pos;
         return d[0] == cur.pos.getX() - pp.getX() && d[1] == cur.pos.getY() - pp.getY() && d[2] == cur.pos.getZ() - pp.getZ() ? 0.0F : this.turnPenalty;
      } else {
         return 0.0F;
      }
   }

   private float geoCost(int[] d) {
      int manhattan = Math.abs(d[0]) + Math.abs(d[1]) + Math.abs(d[2]);
      float base = manhattan == 1 ? this.costOrtho : (manhattan == 2 ? this.costDiag2 : this.costDiag3);
      if (d[1] > 0) {
         return base * this.ascendMult;
      } else {
         return d[1] < 0 ? base * this.descendMult : base;
      }
   }

   private float softCost(int[] d, BlockPos next) {
      float cost = this.cellClearance(next) == 1 ? this.wallPenalty : 0.0F;
      if (d[1] != 0) {
         float horizToGoal = Math.min(this.goal.horizDistanceTo(next.getX(), next.getZ()), 16.0F);
         cost += this.vertLateWeight * Math.abs(d[1]) * horizToGoal;
      }

      return cost;
   }

   private boolean sweepFree(BlockPos from, BlockPos to) {
      double x0 = from.getX() + 0.5;
      double y0 = from.getY();
      double z0 = from.getZ() + 0.5;
      double dx = to.getX() + 0.5 - x0;
      double dy = to.getY() - y0;
      double dz = to.getZ() + 0.5 - z0;
      double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
      int steps = Math.max(1, (int)Math.ceil(dist / 0.5));

      for (int i = 1; i <= steps; i++) {
         double t = (double)i / steps;
         if (i == steps) {
            if (!this.cellFree(to)) {
               return false;
            }
         } else if (!this.boxFree(this.box.at(x0 + dx * t, y0 + dy * t, z0 + dz * t))) {
            return false;
         }
      }

      return true;
   }

   private boolean cellFree(BlockPos p) {
      return this.cellClearance(p) >= this.minClearance;
   }

   private int cellClearance(BlockPos p) {
      long key = p.asLong();
      byte cached = this.clearanceMemo.get(key);
      if (cached != 0) {
         return cached;
      }

      int level = this.clearance(this.box.at(p.getX() + 0.5, p.getY(), p.getZ() + 0.5));
      this.clearanceMemo.put(key, (byte)level);
      return level;
   }

   private int clearance(AABB b) {
      if (!this.boxFree(b)) {
         return 0;
      } else if (!this.level.noCollision(b.inflate(0.5))) {
         return 1;
      } else {
         return this.schematicSolid != null
               && !this.schematicSolid.isEmpty()
               && this.schematicShellHasSolid(
                  1,
                  Mth.floor(b.minX),
                  Mth.floor(b.maxX - 1.0E-7),
                  Mth.floor(b.minY),
                  Mth.floor(b.maxY - 1.0E-7),
                  Mth.floor(b.minZ),
                  Mth.floor(b.maxZ - 1.0E-7)
               )
            ? 1
            : 2;
      }
   }

   private boolean boxFree(AABB b) {
      if (!this.chunksLoaded(b)) {
         return false;
      } else {
         return !this.level.noCollision(b) ? false : !this.schematicBoxHasSolid(b);
      }
   }

   private boolean schematicBoxHasSolid(AABB b) {
      if (this.schematicSolid != null && !this.schematicSolid.isEmpty()) {
         int minX = Mth.floor(b.minX);
         int maxX = Mth.floor(b.maxX - 1.0E-7);
         int minY = Mth.floor(b.minY);
         int maxY = Mth.floor(b.maxY - 1.0E-7);
         int minZ = Mth.floor(b.minZ);
         int maxZ = Mth.floor(b.maxZ - 1.0E-7);

         for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
               for (int z = minZ; z <= maxZ; z++) {
                  if (this.schematicSolid.contains(BlockPos.asLong(x, y, z))) {
                     return true;
                  }
               }
            }
         }

         return false;
      } else {
         return false;
      }
   }

   private boolean schematicShellHasSolid(int k, int baseMinX, int baseMaxX, int baseMinY, int baseMaxY, int baseMinZ, int baseMaxZ) {
      int loX = baseMinX - k;
      int hiX = baseMaxX + k;
      int loY = baseMinY - k;
      int hiY = baseMaxY + k;
      int loZ = baseMinZ - k;
      int hiZ = baseMaxZ + k;

      for (int y = loY; y <= hiY; y++) {
         boolean yEdge = y == loY || y == hiY;

         for (int z = loZ; z <= hiZ; z++) {
            if (!yEdge && z != loZ && z != hiZ) {
               if (this.schematicSolid.contains(BlockPos.asLong(loX, y, z)) || hiX != loX && this.schematicSolid.contains(BlockPos.asLong(hiX, y, z))) {
                  return true;
               }
            } else {
               for (int x = loX; x <= hiX; x++) {
                  if (this.schematicSolid.contains(BlockPos.asLong(x, y, z))) {
                     return true;
                  }
               }
            }
         }
      }

      return false;
   }

   private boolean chunksLoaded(AABB b) {
      double e = 1.0E-7;
      return this.loaded(b.minX, b.minZ) && this.loaded(b.maxX - e, b.minZ) && this.loaded(b.minX, b.maxZ - e) && this.loaded(b.maxX - e, b.maxZ - e);
   }

   private boolean loaded(double x, double z) {
      return BlockStateUtils.isColumnLoaded(this.level, Mth.floor(x) >> 4, Mth.floor(z) >> 4);
   }

   static boolean hasHoverSpot(ClientLevel level, BlockPos target, @Nullable GhastPathfinder.BoxSpec spec) {
      if (spec == null) {
         return true;
      }

      long rev = SchematicStateCache.INSTANCE.getRevision();
      if (rev != hoverSpotCacheRevision) {
         hoverSpotCacheRevision = rev;
         HOVER_SPOT_CACHE.clear();
      }

      long key = target.asLong();
      if (HOVER_SPOT_CACHE.containsKey(key)) {
         return HOVER_SPOT_CACHE.get(key);
      }

      boolean ok = computeHoverSpot(level, target, spec);
      if (HOVER_SPOT_CACHE.size() >= 4096) {
         HOVER_SPOT_CACHE.clear();
      }

      HOVER_SPOT_CACHE.put(key, ok);
      return ok;
   }

   private static boolean computeHoverSpot(ClientLevel level, BlockPos target, GhastPathfinder.BoxSpec spec) {
      int tx = target.getX();
      int ty = target.getY();
      int tz = target.getZ();
      int rMax = GhastGoal.radius(spec.halfWidth());

      for (int r = 1; r <= rMax; r++) {
         for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -r; dx <= r; dx++) {
               boolean xEdge = dx == -r || dx == r;

               for (int dz = -r; dz <= r; dz++) {
                  if ((xEdge || dz == -r || dz == r) && hoverSpotAt(level, spec, tx + dx, ty + dy, tz + dz)) {
                     return true;
                  }
               }
            }
         }
      }

      return false;
   }

   private static boolean hoverSpotAt(ClientLevel level, GhastPathfinder.BoxSpec spec, int x, int y, int z) {
      long rev = SchematicStateCache.INSTANCE.getRevision();
      long specPrint = spec.fingerprint();
      if (rev != hoverCellCacheRevision || specPrint != hoverCellCacheSpec) {
         hoverCellCacheRevision = rev;
         hoverCellCacheSpec = specPrint;
         HOVER_CELL_CACHE.clear();
      }

      long key = BlockPos.asLong(x, y, z);
      byte cached = HOVER_CELL_CACHE.get(key);
      if (cached != 0) {
         return cached > 0;
      }

      boolean ok = computeHoverSpotAt(level, spec, x, y, z);
      if (HOVER_CELL_CACHE.size() >= 65536) {
         HOVER_CELL_CACHE.clear();
      }

      HOVER_CELL_CACHE.put(key, (byte)(ok ? 1 : 2));
      return ok;
   }

   private static boolean computeHoverSpotAt(ClientLevel level, GhastPathfinder.BoxSpec spec, int x, int y, int z) {
      if (!BlockStateUtils.isColumnLoaded(level, x >> 4, z >> 4)) {
         return false;
      }

      AABB box = spec.at(x + 0.5, y, z + 0.5);
      return !level.noCollision(box) ? false : !boxHitsSchematic(box);
   }

   public static boolean boxHitsSchematic(AABB box) {
      int minX = Mth.floor(box.minX);
      int maxX = Mth.floor(box.maxX - 1.0E-7);
      int minY = Mth.floor(box.minY);
      int maxY = Mth.floor(box.maxY - 1.0E-7);
      int minZ = Mth.floor(box.minZ);
      int maxZ = Mth.floor(box.maxZ - 1.0E-7);
      MutableBlockPos m = new MutableBlockPos();

      for (int x = minX; x <= maxX; x++) {
         for (int y = minY; y <= maxY; y++) {
            for (int z = minZ; z <= maxZ; z++) {
               BlockState st = SchematicStateCache.INSTANCE.getSchematicState(m.set(x, y, z));
               if (st != null && !st.isAir()) {
                  return true;
               }
            }
         }
      }

      return false;
   }

   private GoPathfinder.Result buildPath(GhastPathfinder.Node end, boolean reachedGoal) {
      ArrayList<BlockPos> out = new ArrayList<>();

      for (GhastPathfinder.Node n = end; n != null; n = n.parent) {
         out.add(n.pos);
      }

      Collections.reverse(out);
      simplifyToTurns(out);
      return new GoPathfinder.Result(out, reachedGoal, end.h, reachedGoal ? end.pos : null, end.total());
   }

   private static void simplifyToTurns(ArrayList<BlockPos> path) {
      if (path.size() > 2) {
         ArrayList<BlockPos> kept = new ArrayList<>(path.size());
         kept.add(path.get(0));

         for (int i = 1; i < path.size() - 1; i++) {
            BlockPos a = path.get(i - 1);
            BlockPos b = path.get(i);
            BlockPos c = path.get(i + 1);
            boolean turn = Integer.compare(b.getX(), a.getX()) != Integer.compare(c.getX(), b.getX())
               || Integer.compare(b.getY(), a.getY()) != Integer.compare(c.getY(), b.getY())
               || Integer.compare(b.getZ(), a.getZ()) != Integer.compare(c.getZ(), b.getZ());
            if (turn) {
               kept.add(b);
            }
         }

         kept.add(path.get(path.size() - 1));
         path.clear();
         path.addAll(kept);
      }
   }

   public static final class BoxSpec {
      private final double offX;
      private final double offY;
      private final double offZ;
      private final double sizeX;
      private final double sizeY;
      private final double sizeZ;

      private BoxSpec(double offX, double offY, double offZ, double sizeX, double sizeY, double sizeZ) {
         this.offX = offX;
         this.offY = offY;
         this.offZ = offZ;
         this.sizeX = sizeX;
         this.sizeY = sizeY;
         this.sizeZ = sizeZ;
      }

      public static GhastPathfinder.BoxSpec of(Entity ghast, Entity rider) {
         AABB g = ghast.getBoundingBox();
         AABB r = rider.getBoundingBox();
         double minX = Math.min(g.minX, r.minX);
         double minY = Math.min(g.minY, r.minY);
         double minZ = Math.min(g.minZ, r.minZ);
         double maxX = Math.max(g.maxX, r.maxX);
         double maxY = Math.max(g.maxY, r.maxY);
         double maxZ = Math.max(g.maxZ, r.maxZ);
         double px = ghast.getX();
         double py = ghast.getY();
         double pz = ghast.getZ();
         return new GhastPathfinder.BoxSpec(minX - px, minY - py, minZ - pz, maxX - minX, maxY - minY, maxZ - minZ);
      }

      public double halfWidth() {
         return Math.max(this.sizeX, this.sizeZ) / 2.0;
      }

      long fingerprint() {
         return Double.doubleToLongBits(this.offX) * 31L
            ^ Double.doubleToLongBits(this.offY) * 131L
            ^ Double.doubleToLongBits(this.offZ) * 1009L
            ^ Double.doubleToLongBits(this.sizeX) * 65537L
            ^ Double.doubleToLongBits(this.sizeY) * 131071L
            ^ Double.doubleToLongBits(this.sizeZ) * 524287L;
      }

      public AABB at(double centerX, double feetY, double centerZ) {
         return new AABB(
            centerX + this.offX,
            feetY + this.offY,
            centerZ + this.offZ,
            centerX + this.offX + this.sizeX,
            feetY + this.offY + this.sizeY,
            centerZ + this.offZ + this.sizeZ
         );
      }
   }

   private static final class Node {
      @Nullable
      final GhastPathfinder.Node parent;
      final BlockPos pos;
      final float g;
      final float soft;
      final float h;
      final float hw;

      Node(@Nullable GhastPathfinder.Node parent, BlockPos pos, float g, float soft, float h, float hw) {
         this.parent = parent;
         this.pos = pos;
         this.g = g;
         this.soft = soft;
         this.h = h;
         this.hw = hw;
      }

      float f() {
         return this.g + this.soft + this.hw;
      }

      float total() {
         return this.g + this.soft;
      }
   }
}

//#endif
