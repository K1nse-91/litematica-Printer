//#if MC >= 260100
package me.K1nse_.litematica.printer.go;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import me.K1nse_.litematica.printer.config.Configs;
import me.K1nse_.litematica.printer.handler.ClientPlayerTickManager;
import me.K1nse_.litematica.printer.printer.SchematicStateCache;
import me.K1nse_.litematica.printer.printer.zxy.inventory.InventoryUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.happyghast.HappyGhast;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public final class GoManager {
   public static final GoManager INSTANCE = new GoManager();
   private static final ExecutorService CALC_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
      Thread t = new Thread(r, "litematica-printer-go-calc");
      t.setDaemon(true);
      return t;
   });
   private static final long STUCK_CHECK_INTERVAL_TICKS = 20L;
   private static final double STUCK_MIN_MOVE_SQ = 0.0625;
   private static final int MAX_STUCK_REPATHS = 4;
   private static final int MAX_REPATHS = 60;
   private static final long TARGET_CHECK_INTERVAL_TICKS = 20L;
   private static final double TARGET_REROUTE_DISTANCE_SQ = 16.0;
   private static final long DEVIATION_CHECK_INTERVAL_TICKS = 1L;
   private static final int GHAST_OBSTACLE_PAD = 4;
   private static final int GHAST_OBSTACLE_RANGE = 16;
   private static final int GHAST_OBSTACLE_MULTI_CANDIDATES = 32;
   private static final double GHAST_WAYPOINT_ARRIVE_SQ = 6.25;
   private static final double GHAST_CORRIDOR_RADIUS = 3.0;
   private static final long GHAST_OBSTACLE_MIN_REBUILD_TICKS = 100L;
   @Nullable
   private LongOpenHashSet ghastObstacleCache;
   private long ghastObstacleRevision = Long.MIN_VALUE;
   private long ghastObstacleFrom = Long.MIN_VALUE;
   private long ghastObstacleTo = Long.MIN_VALUE;
   private long ghastObstacleBuiltTick = Long.MIN_VALUE;
   private long waypointKey = Long.MIN_VALUE;
   private long waypointStartTick = -1L;
   private double waypointAnchorX;
   private double waypointAnchorY;
   private double waypointAnchorZ;
   private final Minecraft mc = Minecraft.getInstance();
   private volatile boolean active;
   private volatile boolean calculating;
   private volatile long calcSerial;
   private volatile GoManager.DriveMode driveMode = GoManager.DriveMode.NONE;
   private volatile GoPathfinder.Goal activeGoal;
   private volatile BlockPos goal;
   private volatile UUID liveTargetId;
   private volatile List<BlockPos> path = List.of();
   @Nullable
   private volatile Long2ObjectOpenHashMap<BlockPos> multiGoalCells;
   @Nullable
   private int[] multiGoalBounds;
   private long multiGoalBoundsKey = Long.MIN_VALUE;
   @Nullable
   private volatile BlockPos reachedGoalCell;
   private int waypointIndex;
   private boolean pathReachedGoal;
   private float bestDistToGoal = Float.MAX_VALUE;
   private int repaths;
   private int stuckRepaths;
   private long nextStuckCheckTick = -1L;
   private double stuckRefX;
   private double stuckRefY;
   private double stuckRefZ;
   private long nextTargetCheckTick = -1L;
   private long nextDeviationCheckTick = -1L;

   @Nullable
   public BlockPos getMultiGoalCurrentTarget() {
      Long2ObjectOpenHashMap<BlockPos> cells = this.multiGoalCells;
      List<BlockPos> p = this.path;
      return cells != null && !p.isEmpty() ? (BlockPos)cells.get(p.get(p.size() - 1).asLong()) : null;
   }

   private GoManager() {
   }

   public boolean isActive() {
      return this.active;
   }

   @Nullable
   public BlockPos getGoal() {
      return this.goal;
   }

   public List<BlockPos> getPath() {
      return this.path;
   }

   public int getWaypointIndex() {
      return this.waypointIndex;
   }

   @Nullable
   public BlockPos getWaypoint() {
      List<BlockPos> p = this.path;
      return this.waypointIndex < p.size() ? p.get(this.waypointIndex) : null;
   }

   public void go(BlockPos target) {
      if (this.mc.player != null && this.mc.level != null) {
         this.begin(
            target,
            null,
            GoManager.DriveMode.MANUAL,
            this.goalFor(target, true, true),
            "§a[寻路] 目标: " + target.getX() + " " + target.getY() + " " + target.getZ()
         );
      }
   }

   public void go(AbstractClientPlayer target) {
      if (this.mc.player != null && this.mc.level != null) {
         BlockPos tpos = target.blockPosition();
         this.begin(tpos, target.getUUID(), GoManager.DriveMode.MANUAL, this.goalFor(tpos, true, true), "§a[寻路] 跟随玩家: " + target.getName().getString());
      }
   }

   public void autoDispatch(BlockPos target) {
      if (this.mc.player != null && this.mc.level != null) {
         this.multiGoalCells = null;
         this.begin(target, null, GoManager.DriveMode.AUTO, this.autoGoal(target), null);
      }
   }

   private GoPathfinder.Goal goalFor(BlockPos target, boolean walkGoal, boolean manual) {
      if (canGhastFly(this.mc.player, manual)) {
         return GhastGoal.hoverGoal(target, this.ghastHoverRadius(), this.eyeOffset());
      } else {
         return walkGoal ? GoPathfinder.blockGoal(target) : GoPathfinder.adjacentGoal(target);
      }
   }

   @Nullable
   public GhastGoal.EyeOffset eyeOffset() {
      LocalPlayer p = this.mc.player;
      if (p == null) {
         return null;
      }

      HappyGhast ghast = GhastRideState.riddenGhast(p);
      if (ghast == null) {
         return null;
      }

      Vec3 eye = p.getEyePosition();
      return new GhastGoal.EyeOffset(eye.x - ghast.getX(), eye.y - ghast.getY(), eye.z - ghast.getZ());
   }

   private GoPathfinder.Goal autoGoal(BlockPos target) {
      return this.goalFor(target, false, false);
   }

   public void autoDispatchMulti(GoPathfinder.Goal goalSet, Long2ObjectOpenHashMap<BlockPos> cellToTarget) {
      if (this.mc.player != null && this.mc.level != null) {
         this.multiGoalCells = cellToTarget;
         this.computeMultiGoalBounds(cellToTarget);
         this.begin(null, null, GoManager.DriveMode.AUTO, goalSet, null);
      }
   }

   private void computeMultiGoalBounds(Long2ObjectOpenHashMap<BlockPos> cells) {
      if (cells != null && !cells.isEmpty()) {
         LongOpenHashSet seen = new LongOpenHashSet(256);
         ArrayList<BlockPos> cands = new ArrayList<>(256);
         ObjectIterator nav = cells.values().iterator();

         while (nav.hasNext()) {
            BlockPos c = (BlockPos)nav.next();
            if (seen.add(c.asLong())) {
               cands.add(c);
            }
         }

         Entity navx = this.navEntity();
         BlockPos from = navx != null ? navx.blockPosition() : (this.mc.player != null ? this.mc.player.blockPosition() : null);
         if (from != null) {
            cands.sort(Comparator.comparingDouble(cx -> cx.distSqr(from)));
         }

         int n = Math.min(cands.size(), 32);
         int minX = Integer.MAX_VALUE;
         int minY = Integer.MAX_VALUE;
         int minZ = Integer.MAX_VALUE;
         int maxX = Integer.MIN_VALUE;
         int maxY = Integer.MIN_VALUE;
         int maxZ = Integer.MIN_VALUE;
         long h = 1125899906842597L;

         for (int i = 0; i < n; i++) {
            BlockPos c = cands.get(i);
            minX = Math.min(minX, c.getX());
            minY = Math.min(minY, c.getY());
            minZ = Math.min(minZ, c.getZ());
            maxX = Math.max(maxX, c.getX());
            maxY = Math.max(maxY, c.getY());
            maxZ = Math.max(maxZ, c.getZ());
            h = h * 31L + c.asLong();
         }

         this.multiGoalBounds = new int[]{minX, minY, minZ, maxX, maxY, maxZ};
         this.multiGoalBoundsKey = h;
      } else {
         this.multiGoalBounds = null;
         this.multiGoalBoundsKey = Long.MIN_VALUE;
      }
   }

   @Nullable
   public BlockPos getReachedGoalCell() {
      return this.reachedGoalCell;
   }

   public boolean isManualActive() {
      return this.active && this.driveMode == GoManager.DriveMode.MANUAL;
   }

   public boolean isAutoActive() {
      return this.active && this.driveMode == GoManager.DriveMode.AUTO;
   }

   public void stop(@Nullable String reason) {
      boolean wasActive = this.active;
      this.active = false;
      this.calcSerial++;
      this.calculating = false;
      this.driveMode = GoManager.DriveMode.NONE;
      this.activeGoal = null;
      this.path = List.of();
      this.waypointIndex = 0;
      this.liveTargetId = null;
      this.multiGoalCells = null;
      this.multiGoalBounds = null;
      this.multiGoalBoundsKey = Long.MIN_VALUE;
      if (wasActive && reason != null) {
         this.msg("§e[寻路] " + reason);
      }
   }

   public void tick() {
      if (this.active) {
         LocalPlayer player = this.mc.player;
         ClientLevel level = this.mc.level;
         if (player != null && level != null && !player.isDeadOrDying()) {
            long now = ClientPlayerTickManager.getCurrentHandlerTime();
            if (this.liveTargetId != null && now >= this.nextTargetCheckTick) {
               this.nextTargetCheckTick = now + 20L;
               AbstractClientPlayer target = this.findPlayer(this.liveTargetId);
               if (target == null) {
                  this.stop("目标玩家已不在世界中");
                  return;
               }

               BlockPos tpos = target.blockPosition();
               BlockPos g = this.goal;
               if (!tpos.equals(g)) {
                  this.goal = tpos;
                  this.activeGoal = this.goalFor(tpos, true, true);
                  double dx = tpos.getX() + 0.5 - player.getX();
                  double dz = tpos.getZ() + 0.5 - player.getZ();
                  if (!this.calculating && dx * dx + dz * dz > 16.0) {
                     this.requestPath(this.navEntity() != null ? this.navEntity().blockPosition() : player.blockPosition());
                  }
               }
            }

            Entity nav = this.navEntity();
            if (nav != null) {
               GoPathfinder.Goal ag = this.activeGoal;
               boolean arrived = ag != null && ag.isInGoal(nav.getBlockX(), nav.getBlockY(), nav.getBlockZ());
               if (!arrived && this.isGhastFlying() && this.pathReachedGoal && !this.path.isEmpty()) {
                  arrived = GhastFlyer.isSettledAt(nav, this.path.get(this.path.size() - 1));
               }

               if (arrived) {
                  if (this.driveMode == GoManager.DriveMode.AUTO) {
                     if (this.multiGoalCells != null) {
                        this.reachedGoalCell = this.path.isEmpty() ? nav.blockPosition().immutable() : this.path.get(this.path.size() - 1);
                     }

                     this.stopInternal();
                  } else {
                     this.stop("已到达目标附近");
                  }
               } else {
                  this.advanceWaypoints(nav);
                  this.tickWaypointTimeout(nav, now);
                  if (this.nextDeviationCheckTick < 0L) {
                     this.nextDeviationCheckTick = now + 1L;
                  } else if (now >= this.nextDeviationCheckTick && !this.calculating) {
                     this.nextDeviationCheckTick = now + 1L;
                     if (!this.isGhastFlying() && Configs.Go.GO_DEVIATION_STOP.getBooleanValue()) {
                        int maxDist = Configs.Go.GO_DEVIATION_DISTANCE.getIntegerValue();
                        double maxDistSq = sq(maxDist);
                        boolean grounded = player.onGround() || player.isInWater();
                        boolean externalAir = !grounded && !GoExecutor.isSelfJumpAirborne();
                        if (grounded || externalAir) {
                           double minSq = Double.MAX_VALUE;
                           List<BlockPos> p = this.path;

                           for (int i = Math.max(this.waypointIndex - 1, 0); i < p.size(); i++) {
                              BlockPos wp = p.get(i);
                              double dx = wp.getX() + 0.5 - player.getX();
                              double dz = wp.getZ() + 0.5 - player.getZ();
                              double dsq = dx * dx + dz * dz;
                              if (!externalAir) {
                                 double dy = wp.getY() + 0.5 - player.getY();
                                 dsq += dy * dy;
                              }

                              if (dsq < minSq) {
                                 minSq = dsq;
                                 if (minSq <= maxDistSq) {
                                    break;
                                 }
                              }
                           }

                           if (minSq > maxDistSq) {
                              this.stop("已远离路径超过 " + maxDist + " 格，已停止寻路");
                              return;
                           }
                        }
                     }
                  }

                  if (this.waypointIndex >= this.path.size()) {
                     if (!this.calculating) {
                        if (this.repaths >= 60) {
                           this.stop(this.driveMode == GoManager.DriveMode.AUTO ? null : "多次重算仍未到达，已停止");
                           return;
                        }

                        this.requestPath(nav.blockPosition());
                     }
                  } else {
                     boolean detectionPaused = pausedByContainerUi(player);
                     if (detectionPaused) {
                        this.stuckRefX = nav.getX();
                        this.stuckRefY = nav.getY();
                        this.stuckRefZ = nav.getZ();
                        this.nextStuckCheckTick = now + 20L;
                     } else if (this.nextStuckCheckTick < 0L) {
                        this.stuckRefX = nav.getX();
                        this.stuckRefY = nav.getY();
                        this.stuckRefZ = nav.getZ();
                        this.nextStuckCheckTick = now + 20L;
                     } else if (now >= this.nextStuckCheckTick) {
                        double movedSq = sq(nav.getX() - this.stuckRefX) + sq(nav.getZ() - this.stuckRefZ);
                        if (this.isGhastFlying()) {
                           movedSq += sq(nav.getY() - this.stuckRefY);
                        }

                        if (movedSq > 4.0) {
                           this.stuckRepaths = 0;
                        }

                        if (movedSq < 0.0625 && (this.isGhastFlying() || player.onGround()) && !this.calculating && !GhastFlyer.isEscaping()) {
                           this.stuckRepaths++;
                           if (this.stuckRepaths >= 4) {
                              this.stop(this.driveMode == GoManager.DriveMode.AUTO ? null : "反复卡住，已停止寻路");
                              return;
                           }

                           if (!this.isGhastFlying() || !GhastFlyer.tryEscape(player)) {
                              this.requestPath(nav.blockPosition());
                           }
                        }

                        this.stuckRefX = nav.getX();
                        this.stuckRefY = nav.getY();
                        this.stuckRefZ = nav.getZ();
                        this.nextStuckCheckTick = now + 20L;
                     }
                  }
               }
            }
         } else {
            GoExecutor.resetViewRestore();
            this.active = false;
            this.calcSerial++;
            this.calculating = false;
            this.driveMode = GoManager.DriveMode.NONE;
            this.activeGoal = null;
            this.path = List.of();
            this.multiGoalCells = null;
            this.reachedGoalCell = null;
         }
      }
   }

   private void advanceWaypoints(Entity nav) {
      List<BlockPos> p = this.path;
      boolean flying = this.isGhastFlying();

      while (this.waypointIndex < p.size()) {
         BlockPos wp = p.get(this.waypointIndex);
         if (flying) {
            if (this.waypointIndex >= p.size() - 1) {
               break;
            }

            double dx = wp.getX() + 0.5 - nav.getX();
            double dy = wp.getY() + 0.5 - nav.getY();
            double dz = wp.getZ() + 0.5 - nav.getZ();
            if (dx * dx + dy * dy + dz * dz > 6.25
               && !this.ghastPassedWaypoint(nav, wp, this.waypointIndex + 1 < p.size() ? p.get(this.waypointIndex + 1) : null)) {
               break;
            }

            this.waypointIndex++;
         } else {
            double distSq = sq(wp.getX() + 0.5 - nav.getX()) + sq(wp.getZ() + 0.5 - nav.getZ());
            if (distSq > 0.2025 && !this.passedWaypoint(nav, wp, this.waypointIndex + 1 < p.size() ? p.get(this.waypointIndex + 1) : null)
               || nav.getY() < wp.getY() - 0.2
               || nav.getY() > wp.getY() + 1.2) {
               break;
            }

            this.waypointIndex++;
         }
      }
   }

   private boolean ghastPassedWaypoint(Entity nav, BlockPos wp, @Nullable BlockPos next) {
      if (next == null) {
         return false;
      }

      double dirX = next.getX() - wp.getX();
      double dirY = next.getY() - wp.getY();
      double dirZ = next.getZ() - wp.getZ();
      double dirLen = Math.sqrt(dirX * dirX + dirY * dirY + dirZ * dirZ);
      if (dirLen < 0.001) {
         return false;
      }

      double relX = nav.getX() - (wp.getX() + 0.5);
      double relY = nav.getY() - wp.getY();
      double relZ = nav.getZ() - (wp.getZ() + 0.5);
      if (relX * dirX + relY * dirY + relZ * dirZ <= 0.0) {
         return false;
      }

      double crossX = relY * dirZ - relZ * dirY;
      double crossY = relZ * dirX - relX * dirZ;
      double crossZ = relX * dirY - relY * dirX;
      double perp = Math.sqrt(crossX * crossX + crossY * crossY + crossZ * crossZ) / dirLen;
      return perp <= 3.0;
   }

   private boolean passedWaypoint(Entity nav, BlockPos wp, @Nullable BlockPos next) {
      if (next != null && next.getY() == wp.getY()) {
         int dirX = Integer.compare(next.getX(), wp.getX());
         int dirZ = Integer.compare(next.getZ(), wp.getZ());
         if (dirX == 0 && dirZ == 0) {
            return false;
         }

         double relX = nav.getX() - (wp.getX() + 0.5);
         double relZ = nav.getZ() - (wp.getZ() + 0.5);
         if (relX * dirX + relZ * dirZ <= 0.0) {
            return false;
         }

         double dirLen = Math.sqrt((double)dirX * dirX + (double)dirZ * dirZ);
         return Math.abs(relX * dirZ - relZ * dirX) / dirLen <= 0.9;
      } else {
         return false;
      }
   }

   private void tickWaypointTimeout(Entity nav, long now) {
      boolean flying = this.isGhastFlying();
      List<BlockPos> p = this.path;
      if (!p.isEmpty() && this.waypointIndex < p.size() && !this.calculating && (!flying || !GhastFlyer.isEscaping())) {
         BlockPos wp = p.get(this.waypointIndex);
         long key = wp.asLong() * 31L + this.waypointIndex;
         double mult = flying ? Configs.Go.GO_GHAST_WAYPOINT_TIMEOUT.getDoubleValue() : Configs.Go.GO_WAYPOINT_TIMEOUT.getDoubleValue();
         LocalPlayer player = this.mc.player;
         if (key == this.waypointKey && !(mult <= 0.0) && (player == null || !pausedByContainerUi(player))) {
            double dx = wp.getX() + 0.5 - this.waypointAnchorX;
            double dy = wp.getY() + 0.5 - this.waypointAnchorY;
            double dz = wp.getZ() + 0.5 - this.waypointAnchorZ;
            double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
            long limitTicks = (long)Math.ceil(Math.max(dist, 1.0) * mult);
            if (now - this.waypointStartTick > limitTicks) {
               this.abandonRoute();
            }
         } else {
            this.waypointKey = key;
            this.waypointStartTick = now;
            this.waypointAnchorX = nav.getX();
            this.waypointAnchorY = nav.getY();
            this.waypointAnchorZ = nav.getZ();
         }
      } else {
         this.waypointKey = Long.MIN_VALUE;
      }
   }

   private static boolean pausedByContainerUi(LocalPlayer player) {
      return GoExecutor.isContainerUiOpen(player) || InventoryUtils.isOpenHandler;
   }

   private void begin(
      @Nullable BlockPos target, @Nullable UUID liveId, GoManager.DriveMode mode, GoPathfinder.Goal goalEvaluator, @Nullable String startMessage
   ) {
      this.active = true;
      this.calcSerial++;
      this.calculating = false;
      this.driveMode = mode;
      this.activeGoal = goalEvaluator;
      this.goal = target;
      this.liveTargetId = liveId;
      this.path = List.of();
      this.reachedGoalCell = null;
      this.waypointIndex = 0;
      this.pathReachedGoal = false;
      this.waypointKey = Long.MIN_VALUE;
      this.bestDistToGoal = Float.MAX_VALUE;
      this.repaths = 0;
      this.stuckRepaths = 0;
      this.nextStuckCheckTick = -1L;
      this.nextTargetCheckTick = -1L;
      this.nextDeviationCheckTick = -1L;
      GoExecutor.resetViewRestore();
      if (startMessage != null) {
         this.msg(startMessage);
      }

      if (this.mc.player != null) {
         this.requestPath(this.navEntity() != null ? this.navEntity().blockPosition() : this.mc.player.blockPosition());
      }
   }

   private void stopInternal() {
      this.stop(null);
   }

   public void onEscapeSucceeded() {
      if (this.active) {
         this.abandonRoute();
      }
   }

   private void abandonRoute() {
      if (this.driveMode == GoManager.DriveMode.AUTO) {
         this.stopInternal();
      } else {
         this.calcSerial++;
         this.calculating = false;
         this.bestDistToGoal = Float.MAX_VALUE;
         this.repaths = 0;
         this.path = List.of();
         this.waypointIndex = 0;
         this.pathReachedGoal = false;
         Entity nav = this.navEntity();
         if (nav != null) {
            this.requestPath(nav.blockPosition());
         }
      }
   }

   private void requestPath(BlockPos from) {
      if (!this.calculating && this.mc.level != null) {
         this.calculating = true;
         long serial = ++this.calcSerial;
         ClientLevel level = this.mc.level;
         GoPathfinder.Goal goalEvaluator = this.activeGoal;
         if (goalEvaluator == null) {
            this.calculating = false;
         } else {
            long budgetMs = Configs.Go.GO_TIME_LIMIT.getIntegerValue();
            int maxFall = Configs.Go.GO_MAX_FALL.getIntegerValue();
            int costLimitFactor = Configs.Go.GO_COST_LIMIT_FACTOR.getIntegerValue();
            GhastPathfinder.BoxSpec boxSpec = null;
            LongOpenHashSet obstacles = null;
            if (this.isGhastFlying()) {
               boxSpec = this.currentBoxSpec();
               if (boxSpec != null) {
                  obstacles = this.ghastObstacles(from);
               }
            }

            GhastPathfinder.BoxSpec flyBox = boxSpec;
            LongOpenHashSet flyObstacles = obstacles;
            CALC_EXECUTOR.execute(
               () -> {
                  GoPathfinder.Result calcResult = null;

                  try {
                     if (flyBox != null) {
                        calcResult = GhastPathfinder.findPath(
                           level, from, goalEvaluator, flyBox, flyObstacles, budgetMs, costLimitFactor, () -> serial != this.calcSerial
                        );
                     } else {
                        calcResult = GoPathfinder.findPath(level, from, goalEvaluator, budgetMs, maxFall, costLimitFactor, () -> serial != this.calcSerial);
                     }
                  } catch (Throwable var14) {
                  }

                  GoPathfinder.Result result = calcResult;
                  Minecraft.getInstance().execute(() -> this.onPathResult(serial, result));
               }
            );
         }
      }
   }

   static boolean canGhastFly(@Nullable LocalPlayer player, boolean manual) {
      return player != null
         && Configs.Go.GHAST_PATHFIND.getBooleanValue()
         && (manual || Configs.Go.PRINT_SCAN_AUTOWALK.getBooleanValue())
         && GhastRideState.canFly(player);
   }

   boolean isGhastFlying() {
      return canGhastFly(this.mc.player, this.isManualActive());
   }

   @Nullable
   private Entity navEntity() {
      LocalPlayer p = this.mc.player;
      if (p == null) {
         return null;
      }

      if (this.isGhastFlying()) {
         HappyGhast ghast = GhastRideState.riddenGhast(p);
         if (ghast != null) {
            return ghast;
         }
      }

      return p;
   }

   @Nullable
   public Vec3 navPosition() {
      Entity nav = this.navEntity();
      return nav == null ? null : nav.position();
   }

   @Nullable
   GhastPathfinder.BoxSpec currentBoxSpec() {
      LocalPlayer p = this.mc.player;
      if (p == null) {
         return null;
      }

      HappyGhast ghast = GhastRideState.riddenGhast(p);
      return ghast == null ? null : GhastPathfinder.BoxSpec.of(ghast, p);
   }

   private LongOpenHashSet ghastObstacles(BlockPos from) {
      BlockPos g = this.goal;
      int[] bounds = this.multiGoalBounds;
      long fromKey = BlockPos.asLong(from.getX() >> 4, from.getY() >> 4, from.getZ() >> 4);
      long toKey;
      if (g != null) {
         toKey = BlockPos.asLong(g.getX() >> 4, g.getY() >> 4, g.getZ() >> 4);
      } else if (bounds != null) {
         toKey = this.multiGoalBoundsKey;
      } else {
         toKey = Long.MIN_VALUE;
      }

      long now = ClientPlayerTickManager.getCurrentHandlerTime();
      long rev = SchematicStateCache.INSTANCE.getRevision();
      boolean sameTarget = this.ghastObstacleCache != null && fromKey == this.ghastObstacleFrom && toKey == this.ghastObstacleTo;
      if (!sameTarget || rev != this.ghastObstacleRevision && now - this.ghastObstacleBuiltTick >= 100L) {
         int minX = from.getX();
         int maxX = from.getX();
         int minY = from.getY();
         int maxY = from.getY();
         int minZ = from.getZ();
         int maxZ = from.getZ();
         if (g != null) {
            minX = Math.min(minX, g.getX());
            maxX = Math.max(maxX, g.getX());
            minY = Math.min(minY, g.getY());
            maxY = Math.max(maxY, g.getY());
            minZ = Math.min(minZ, g.getZ());
            maxZ = Math.max(maxZ, g.getZ());
         } else if (bounds != null) {
            minX = Math.min(minX, bounds[0]);
            minY = Math.min(minY, bounds[1]);
            minZ = Math.min(minZ, bounds[2]);
            maxX = Math.max(maxX, bounds[3]);
            maxY = Math.max(maxY, bounds[4]);
            maxZ = Math.max(maxZ, bounds[5]);
         } else {
            minX -= 16;
            maxX += 16;
            minY -= 16;
            maxY += 16;
            minZ -= 16;
            maxZ += 16;
         }

         int minChunkX = minX - 4 >> 4;
         int maxChunkX = maxX + 4 >> 4;
         int minChunkY = minY - 4 >> 4;
         int maxChunkY = maxY + 4 >> 4;
         int minChunkZ = minZ - 4 >> 4;
         int maxChunkZ = maxZ + 4 >> 4;
         LongOpenHashSet out = new LongOpenHashSet();
         MutableBlockPos m = new MutableBlockPos();
         MutableBlockPos secMin = new MutableBlockPos();

         for (int sx = minChunkX; sx <= maxChunkX; sx++) {
            for (int sz = minChunkZ; sz <= maxChunkZ; sz++) {
               for (int sy = minChunkY; sy <= maxChunkY; sy++) {
                  secMin.set(sx << 4, sy << 4, sz << 4);
                  if (SchematicStateCache.INSTANCE.intersectsSchematic(secMin, secMin.offset(15, 15, 15))) {
                     for (int x = sx << 4; x < (sx << 4) + 16; x++) {
                        for (int y = sy << 4; y < (sy << 4) + 16; y++) {
                           for (int z = sz << 4; z < (sz << 4) + 16; z++) {
                              BlockState state = SchematicStateCache.INSTANCE.getSchematicState(m.set(x, y, z));
                              if (state != null && !state.isAir()) {
                                 out.add(BlockPos.asLong(x, y, z));
                              }
                           }
                        }
                     }
                  }
               }
            }
         }

         this.ghastObstacleCache = out;
         this.ghastObstacleRevision = rev;
         this.ghastObstacleFrom = fromKey;
         this.ghastObstacleTo = toKey;
         this.ghastObstacleBuiltTick = now;
         return out;
      } else {
         return this.ghastObstacleCache;
      }
   }

   public int ghastHoverRadius() {
      GhastPathfinder.BoxSpec spec = this.currentBoxSpec();
      return spec != null ? GhastGoal.radius(spec.halfWidth()) : 3;
   }

   private void onPathResult(long serial, @Nullable GoPathfinder.Result result) {
      this.calculating = false;
      if (this.active && serial == this.calcSerial) {
         if (result == null) {
            this.stop(this.driveMode == GoManager.DriveMode.AUTO ? null : "未找到可行路径");
         } else if (result.reachedGoal) {
            this.bestDistToGoal = 0.0F;
            this.stuckRepaths = 0;
            this.path = result.positions;
            this.waypointIndex = 0;
            this.waypointKey = Long.MIN_VALUE;
            this.pathReachedGoal = true;
         } else if (result.distanceToGoal >= this.bestDistToGoal - 0.5F) {
            this.stop(this.driveMode == GoManager.DriveMode.AUTO ? null : String.format("已走到离目标最近的位置（约 %.1f 格）", result.distanceToGoal));
         } else {
            this.bestDistToGoal = result.distanceToGoal;
            this.repaths++;
            this.path = result.positions;
            this.waypointIndex = 0;
            this.waypointKey = Long.MIN_VALUE;
            this.pathReachedGoal = result.distanceToGoal < 6.0F;
         }
      }
   }

   @Nullable
   private AbstractClientPlayer findPlayer(UUID id) {
      if (this.mc.level == null) {
         return null;
      }

      for (AbstractClientPlayer p : this.mc.level.players()) {
         if (p.getUUID().equals(id)) {
            return p;
         }
      }

      return null;
   }

   @Nullable
   public AbstractClientPlayer findPlayerByName(String name) {
      if (this.mc.level == null) {
         return null;
      }

      UUID selfId = this.mc.player != null ? this.mc.player.getUUID() : null;
      AbstractClientPlayer prefixMatch = null;
      String lower = name.toLowerCase();

      for (AbstractClientPlayer p : this.mc.level.players()) {
         if (!p.getUUID().equals(selfId)) {
            String n = p.getName().getString();
            if (n.equalsIgnoreCase(name)) {
               return p;
            }

            if (n.toLowerCase().startsWith(lower)) {
               if (prefixMatch != null) {
                  return null;
               }

               prefixMatch = p;
            }
         }
      }

      return prefixMatch;
   }

   private static double sq(double d) {
      return d * d;
   }

   private void msg(String text) {
      LocalPlayer p = this.mc.player;
      if (p != null) {
         p.sendSystemMessage(Component.literal(text));
      }
   }

   private enum DriveMode {
      NONE,
      MANUAL,
      AUTO;

      // $VF: synthetic method
      private static GoManager.DriveMode[] $values() {
         return new GoManager.DriveMode[]{NONE, MANUAL, AUTO};
      }
   }
}

//#endif
