//#if MC >= 260100
package me.K1nse_.litematica.printer.go;

import com.google.common.collect.ArrayListMultimap;
import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import fi.dy.masa.litematica.schematic.verifier.SchematicVerifier;
import fi.dy.masa.litematica.schematic.verifier.SchematicVerifier.MismatchType;
//#if MC >= 260200
//$$ import fi.dy.masa.malilib.util.position.LayerRange;
//#else
import fi.dy.masa.malilib.util.LayerRange;
//#endif
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import me.K1nse_.litematica.printer.Reference;
import me.K1nse_.litematica.printer.config.Configs;
import me.K1nse_.litematica.printer.enums.BlockMatchResult;
import me.K1nse_.litematica.printer.enums.SectionScanOrderType;
import me.K1nse_.litematica.printer.enums.SelectionType;
import me.K1nse_.litematica.printer.handler.ClientPlayerTickManager;
import me.K1nse_.litematica.printer.mixin.printer.litematica.SchematicVerifierAccessor;
import me.K1nse_.litematica.printer.printer.ScanWhitelistCache;
import me.K1nse_.litematica.printer.printer.SchematicStateCache;
import me.K1nse_.litematica.printer.printer.verifier.VerifierDataView;
import me.K1nse_.litematica.printer.utils.minecraft.BlockStateUtils;
import me.K1nse_.litematica.printer.utils.ConfigUtils;
import me.K1nse_.litematica.printer.utils.minecraft.PlayerUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.Nullable;

public final class AutoWalkScanner {
   public static final AutoWalkScanner INSTANCE = new AutoWalkScanner();
   private static final long UNREACHABLE_COOLDOWN_TICKS = 200L;
   private static final double PRINTER_REACH_SQ = 20.25;
   private static final long DONE_RECHECK_INTERVAL_TICKS = 100L;
   private static final long MULTI_FALLBACK_TICKS = 200L;
   private static final int SCAN_GATE_MAX_LAYERS = 4;
   private static final long FIND_CACHE_TICKS = 100L;
   private final Minecraft mc = Minecraft.getInstance();
   private volatile AutoWalkScanner.State state = AutoWalkScanner.State.IDLE;
   @Nullable
   private BlockPos cursorSection;
   private final ArrayDeque<BlockPos> sectionQueue = new ArrayDeque<>();
   private final LongOpenHashSet visitedSections = new LongOpenHashSet();
   private final Long2LongOpenHashMap unreachableCooldown = new Long2LongOpenHashMap();
   private long selectionSig;
   private static final int SCAN_WORKER_COUNT = Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors() - 2));
   private static final int SCAN_MAX_INFLIGHT = SCAN_WORKER_COUNT * 2;
   @Nullable
   private ExecutorService scanPool;
   private final ConcurrentLinkedQueue<AutoWalkScanner.SectionScanResult> scanResults = new ConcurrentLinkedQueue<>();
   private int pendingScans;
   private int scanSerial;
   private final LongOpenHashSet areaCandidates = new LongOpenHashSet();
   private int reachablePoolCount;
   private final LongOpenHashSet reachableKeys = new LongOpenHashSet();
   private final LongOpenHashSet areaExtraCandidates = new LongOpenHashSet();
   private int extraReachableCount;
   private final LongOpenHashSet extraReachableKeys = new LongOpenHashSet();
   @Nullable
   private BlockPos areaCenterSection;
   private int layerScanning = -1;
   @Nullable
   private Long2ObjectOpenHashMap<BlockPos> legGoalCells;
   @Nullable
   private GoPathfinder.Goal legGoalSet;
   private long multiFallbackTick;
   private volatile List<BlockPos> dispatchedCandidates = List.of();
   @Nullable
   private volatile BlockPos selectedTarget;
   @Nullable
   private ArrayList<BlockPos> findSections;
   private int findIndex;
   @Nullable
   private ArrayList<BlockPos> findSectionsCache;
   private long findSectionsCachePlayer = Long.MIN_VALUE;
   private long findSectionsCacheTick = Long.MIN_VALUE;
   @Nullable
   private volatile BlockPos target;
   private long revisionAtDone = -1L;
   private long nextDoneRecheckTick = -1L;
   private boolean suspendedByManual;
   private boolean verifierPollNeeded;

   public List<BlockPos> getDispatchedCandidates() {
      return this.dispatchedCandidates;
   }

   @Nullable
   public BlockPos getSelectedTarget() {
      return this.selectedTarget;
   }

   private AutoWalkScanner() {
   }

   @Nullable
   public BlockPos getWaitingTarget() {
      return this.state == AutoWalkScanner.State.ARRIVED_WAITING ? this.target : null;
   }

   public void tick() {
      if (!this.conditionsMet()) {
         this.deactivate();
      } else {
         LocalPlayer player = this.mc.player;
         if (player != null) {
            if (!GhastFlyer.isEscapingTier2()) {
               if (GoManager.INSTANCE.isManualActive()) {
                  if (!this.suspendedByManual) {
                     this.suspendedByManual = true;
                     if (GoManager.INSTANCE.isAutoActive()) {
                        GoManager.INSTANCE.stop(null);
                     }
                  }
               } else {
                  if (this.suspendedByManual) {
                     this.suspendedByManual = false;
                     if (this.state == AutoWalkScanner.State.DRIVING && this.target != null) {
                        GoManager.INSTANCE.autoDispatch(this.target);
                     } else if (this.state == AutoWalkScanner.State.DRIVING && this.legGoalSet != null) {
                        GoManager.INSTANCE.autoDispatchMulti(this.legGoalSet, this.legGoalCells);
                     }
                  }

                  switch (this.state) {
                     case IDLE:
                        this.activate();
                        break;
                     case SCANNING:
                        this.tickScan();
                        break;
                     case DRIVING:
                        this.tickDriving();
                        break;
                     case ARRIVED_WAITING:
                        this.tickWaiting();
                        break;
                     case DONE:
                        this.tickDone();
                  }
               }
            }
         }
      }
   }

   private void activate() {
      this.visitedSections.clear();
      this.sectionQueue.clear();
      this.unreachableCooldown.clear();
      this.areaCandidates.clear();
      this.reachablePoolCount = 0;
      this.reachableKeys.clear();
      this.areaCenterSection = null;
      this.layerScanning = -1;
      this.findSections = null;
      this.findIndex = 0;
      this.suspendedByManual = false;
      this.scanSerial++;
      this.pendingScans = 0;
      this.scanResults.clear();
      this.cursorSection = this.playerSectionBase();
      this.findCenterStep();
   }

   private void tickScan() {
      ClientLevel level = this.mc.level;
      if (level == null) {
         this.state = AutoWalkScanner.State.IDLE;
      } else {
         long sig = this.selectionSig();
         if (sig != this.selectionSig) {
            this.selectionSig = sig;
            this.visitedSections.clear();
         }

         if (!Configs.Go.GHAST_PATHFIND.getBooleanValue() || GhastRideState.canFly(this.mc.player)) {
            if (!GoManager.INSTANCE.isAutoActive()) {
               if (this.verifierPollNeeded) {
                  this.verifierPollNeeded = false;
                  if (this.tryDispatchVerifierTarget(level)) {
                     return;
                  }
               }

               this.drainScanResults(level);
               this.pumpScan(level);
            }
         }
      }
   }

   private void pumpScan(ClientLevel level) {
      if (this.state == AutoWalkScanner.State.SCANNING) {
         if (!multiTargetMode()) {
            if (this.pendingScans <= 0) {
               if (!this.submitNextSerialSection(level)) {
                  this.findCenterStep();
               }
            }
         } else {
            boolean layerDone = this.pendingScans == 0
               && this.layerScanning >= 0
               && (this.sectionQueue.isEmpty() || this.sectionLayer(this.sectionQueue.peek()) > this.layerScanning);
            if (layerDone) {
               if (this.poolDispatch(level, false)) {
                  return;
               }

               if (this.layerScanning >= 4 && this.poolDispatch(level, true)) {
                  return;
               }
            }

            while (!this.sectionQueue.isEmpty() && this.pendingScans < SCAN_MAX_INFLIGHT) {
               BlockPos next = this.sectionQueue.peek();
               int layer = this.sectionLayer(next);
               if (layer > this.layerScanning && this.layerScanning >= 0 && this.pendingScans > 0) {
                  return;
               }

               this.sectionQueue.poll();
               if (this.isChunkVisible(next.getX() >> 4, next.getZ() >> 4)
                  && this.visitedSections.add(sectionKey(next))
                  && SchematicStateCache.INSTANCE.intersectsSchematic(next, next.offset(15, 15, 15))) {
                  this.cursorSection = next;
                  this.layerScanning = layer;
                  this.submitSection(level, next, layer);
               }
            }

            if (this.sectionQueue.isEmpty() && this.pendingScans == 0) {
               this.findCenterStep();
            }
         }
      }
   }

   private static boolean multiTargetMode() {
      return Configs.Go.PATH_NEAREST_TARGET.getBooleanValue();
   }

   private boolean submitNextSerialSection(ClientLevel level) {
      while (!this.sectionQueue.isEmpty()) {
         BlockPos next = this.sectionQueue.poll();
         if (this.isChunkVisible(next.getX() >> 4, next.getZ() >> 4)
            && this.visitedSections.add(sectionKey(next))
            && SchematicStateCache.INSTANCE.intersectsSchematic(next, next.offset(15, 15, 15))) {
            this.cursorSection = next;
            this.layerScanning = this.sectionLayer(next);
            this.submitSection(level, next, this.layerScanning);
            return true;
         }
      }

      return false;
   }

   private void submitSection(ClientLevel level, BlockPos section, int layer) {
      List<SchematicStateCache.RegionPlanEntry> plan = SchematicStateCache.INSTANCE.planSection(section);
      this.enqueueNeighbors(section);
      if (!plan.isEmpty()) {
         if (this.scanPool == null) {
            this.scanPool = Executors.newFixedThreadPool(SCAN_WORKER_COUNT, r -> {
               Thread t = new Thread(r, "litematica-printer-scan");
               t.setDaemon(true);
               t.setPriority(4);
               return t;
            });
         }

         AutoWalkScanner.SectionScanResult result = new AutoWalkScanner.SectionScanResult(this.scanSerial, section, layer);
         boolean scanExtras = extraScanEnabled();
         this.pendingScans++;

         try {
            this.scanPool.execute(() -> {
               try {
                  MutableBlockPos pos = new MutableBlockPos();

                  for (SchematicStateCache.RegionPlanEntry entry : plan) {
                     for (int y = entry.minY; y <= entry.maxY; y++) {
                        for (int z = entry.minZ; z <= entry.maxZ; z++) {
                           for (int x = entry.minX; x <= entry.maxX; x++) {
                              BlockState expected = entry.stateAt(x, y, z);
                              if (expected != null && !expected.isAir()) {
                                 if (BlockStateUtils.isColumnLoaded(level, x >> 4, z >> 4)) {
                                    BlockState current = level.getBlockState(pos.set(x, y, z));
                                    if (!BlockMatchResult.isCorrect(expected, current)) {
                                       result.positions.add(BlockPos.asLong(x, y, z));
                                       result.expected.add(expected);
                                    }
                                 }
                              } else if (scanExtras && expected != null && BlockStateUtils.isColumnLoaded(level, x >> 4, z >> 4)) {
                                 BlockState current = level.getBlockState(pos.set(x, y, z));
                                 if (!current.isAir() && !(current.getBlock() instanceof LiquidBlock)) {
                                    result.extraPositions.add(BlockPos.asLong(x, y, z));
                                 }
                              }
                           }
                        }
                     }
                  }
               } catch (Throwable t) {
                  Reference.LOGGER.warn("[扫描寻路] 子区块后台扫描异常（按无候选处理）", t);
               } finally {
                  this.scanResults.add(result);
               }
            });
         } catch (Throwable t) {
            this.pendingScans = Math.max(0, this.pendingScans - 1);
            Reference.LOGGER.warn("[扫描寻路] 子区块扫描任务投递失败（已回滚在飞计数）", t);
         }
      }
   }

   private void drainScanResults(ClientLevel level) {
      AutoWalkScanner.SectionScanResult result;
      while ((result = this.scanResults.poll()) != null) {
         this.pendingScans = Math.max(0, this.pendingScans - 1);
         if (result.serial == this.scanSerial) {
            this.mergeScanResult(level, result);
         }
      }
   }

   private void mergeScanResult(ClientLevel level, AutoWalkScanner.SectionScanResult result) {
      ScanWhitelistCache.WALK.beginScanBatch();
      boolean flying = this.ghastFlying(this.mc.player);
      long now = ClientPlayerTickManager.getCurrentHandlerTime();
      boolean multi = multiTargetMode();
      ArrayList<BlockPos> sectionCandidates = multi ? null : new ArrayList<>();
      ArrayList<BlockPos> sectionExtras = !multi && !result.extraPositions.isEmpty() && extraScanEnabled() ? new ArrayList<>() : null;
      if (!extraScanEnabled() && !this.areaExtraCandidates.isEmpty()) {
         this.areaExtraCandidates.clear();
         this.extraReachableKeys.clear();
         this.extraReachableCount = 0;
      }

      boolean wrongBlocks = wrongBlockScanEnabled();

      for (int i = 0; i < result.positions.size(); i++) {
         BlockState expected = result.expected.get(i);
         long key = result.positions.getLong(i);
         BlockPos pos = BlockPos.of(key);
         BlockState live = SchematicStateCache.INSTANCE.getSchematicState(pos);
         if (live != null && !live.isAir() && live.equals(expected)) {
            BlockState current = level.getBlockState(pos);
            if (!expected.getBlock().equals(current.getBlock())) {
               BlockMatchResult match = BlockMatchResult.compare(expected, current);
               if (match == BlockMatchResult.WRONG_BLOCK
                  ? wrongBlocks
                  : match == BlockMatchResult.MISSING && ScanWhitelistCache.WALK.isWhitelistedFast(expected)) {
                  SchematicStateCache.INSTANCE.reconcileVerdict(pos, expected, current);
                  if (this.selectionAllows(pos)) {
                     if (!multi) {
                        if (this.unreachableCooldown.get(key) <= now && !this.targetCompleted(pos)) {
                           sectionCandidates.add(pos);
                        }
                     } else if (this.areaCandidates.add(key)
                        && this.unreachableCooldown.get(key) <= now
                        && reachableAt(level, pos, flying)
                        && this.reachableKeys.add(key)) {
                        this.reachablePoolCount++;
                     }
                  }
               }
            }
         }
      }

      if (!result.extraPositions.isEmpty() && extraScanEnabled()) {
         for (int i = 0; i < result.extraPositions.size(); i++) {
            long key = result.extraPositions.getLong(i);
            if (this.areaExtraCandidates.add(key)) {
               if (level.getBlockState(BlockPos.of(key)).isAir()) {
                  this.areaExtraCandidates.remove(key);
               } else if (this.unreachableCooldown.get(key) <= now && reachableAt(level, BlockPos.of(key), flying) && this.extraReachableKeys.add(key)) {
                  this.extraReachableCount++;
               }
            }
         }
      }

      if (sectionExtras != null) {
         for (int i = 0; i < result.extraPositions.size(); i++) {
            long key = result.extraPositions.getLong(i);
            BlockPos pos = BlockPos.of(key);
            if (!level.getBlockState(pos).isAir() && this.unreachableCooldown.get(key) <= now) {
               sectionExtras.add(pos);
            }
         }
      }

      if (sectionCandidates != null) {
         this.dispatchSerialNearest(sectionExtras != null && !sectionExtras.isEmpty() ? sectionExtras : sectionCandidates);
      }
   }

   private boolean poolDispatch(ClientLevel level, boolean force) {
      if (level != null && !this.areaCandidates.isEmpty()) {
         int required = Configs.Go.PATH_TARGET_CANDIDATE_LIMIT.getIntegerValue();
         if (!force && this.reachablePoolCount < required && this.extraReachableCount == 0) {
            return false;
         }

         long now = ClientPlayerTickManager.getCurrentHandlerTime();
         boolean flying = this.ghastFlying(this.mc.player);
         ArrayList<BlockPos> candidates = new ArrayList<>(this.areaCandidates.size());
         LongArrayList completedKeys = new LongArrayList();
         LongIterator extraList = this.areaCandidates.iterator();

         while (extraList.hasNext()) {
            long key = (Long)extraList.next();
            if (this.unreachableCooldown.get(key) <= now) {
               BlockPos pos = BlockPos.of(key);
               if (this.targetCompleted(pos)) {
                  completedKeys.add(key);
               } else {
                  if (reachableAt(level, pos, flying) && this.reachableKeys.add(key)) {
                     this.reachablePoolCount++;
                  }

                  if (this.selectionAllows(pos)) {
                     candidates.add(pos);
                  }
               }
            }
         }

         this.areaCandidates.removeAll(completedKeys);

         for (int i = 0; i < completedKeys.size(); i++) {
            if (this.reachableKeys.remove(completedKeys.getLong(i))) {
               this.reachablePoolCount = Math.max(0, this.reachablePoolCount - 1);
            }
         }

         this.filterStandSpot(level, candidates);
         ArrayList<BlockPos> extraListx = this.collectExtraCandidates(level, now, flying);
         if (extraListx != null && !extraListx.isEmpty()) {
            this.filterStandSpot(level, extraListx);
            if (!extraListx.isEmpty()) {
               LocalPlayer playerExtra = this.mc.player;
               if (playerExtra != null) {
                  extraListx.sort(Comparator.comparingDouble(p -> straightDist(p, playerExtra)));
               }

               this.dispatchCandidates(extraListx);
               return true;
            }
         }

         if (candidates.isEmpty()) {
            return false;
         }

         LocalPlayer player = this.mc.player;
         if (player != null) {
            candidates.sort(Comparator.comparingDouble(p -> straightDist(p, player)));
         }

         this.dispatchCandidates(candidates);
         return true;
      } else {
         return false;
      }
   }

   @Nullable
   private ArrayList<BlockPos> collectExtraCandidates(ClientLevel level, long now, boolean flying) {
      if (this.areaExtraCandidates.isEmpty()) {
         return null;
      }

      if (!extraScanEnabled()) {
         this.areaExtraCandidates.clear();
         this.extraReachableKeys.clear();
         this.extraReachableCount = 0;
         return null;
      }

      ArrayList<BlockPos> out = new ArrayList<>();
      LongArrayList goneKeys = new LongArrayList();
      LongIterator i = this.areaExtraCandidates.iterator();

      while (i.hasNext()) {
         long key = (Long)i.next();
         BlockPos pos = BlockPos.of(key);
         if (level.getBlockState(pos).isAir()) {
            goneKeys.add(key);
         } else if (this.unreachableCooldown.get(key) <= now) {
            if (reachableAt(level, pos, flying) && this.extraReachableKeys.add(key)) {
               this.extraReachableCount++;
            }

            if (this.selectionAllows(pos)) {
               out.add(pos);
            }
         }
      }

      if (!goneKeys.isEmpty()) {
         this.areaExtraCandidates.removeAll(goneKeys);

         for (int ix = 0; ix < goneKeys.size(); ix++) {
            if (this.extraReachableKeys.remove(goneKeys.getLong(ix))) {
               this.extraReachableCount = Math.max(0, this.extraReachableCount - 1);
            }
         }
      }

      return out;
   }

   private static boolean extraScanEnabled() {
      return Configs.Go.GO_SCAN_EXTRA_BLOCKS.getBooleanValue() && Configs.Print.BREAK_EXTRA_BLOCK.getBooleanValue();
   }

   private static boolean wrongBlockScanEnabled() {
      return Configs.Go.GO_SCAN_WRONG_BLOCKS.getBooleanValue() && Configs.Print.BREAK_WRONG_BLOCK.getBooleanValue();
   }

   private void dispatchCandidates(ArrayList<BlockPos> candidates) {
      LocalPlayer player = this.mc.player;
      long now = ClientPlayerTickManager.getCurrentHandlerTime();
      if (!multiTargetMode()) {
         this.dispatchedCandidates = List.of();
         this.selectedTarget = null;
         this.dispatchSingleNearest(candidates, "「最短路径优先」关闭");
      } else {
         this.dispatchedCandidates = List.copyOf(candidates);
         this.selectedTarget = null;
         if (candidates.size() >= 2 && now >= this.multiFallbackTick) {
            int limit = Configs.Go.PATH_TARGET_CANDIDATE_LIMIT.getIntegerValue();
            boolean flying = Configs.Go.GHAST_PATHFIND.getBooleanValue() && GhastRideState.canFly(player);
            GoPathfinder.Goal goalSet;
            Long2ObjectOpenHashMap<BlockPos> cells;
            if (flying) {
               GhastGoal.HoverGoalSet hover = GhastGoal.hoverGoalSet(
                  candidates, limit, player.blockPosition(), GoManager.INSTANCE.ghastHoverRadius(), GoManager.INSTANCE.eyeOffset()
               );
               goalSet = hover;
               cells = hover.cellToTarget();
            } else {
               GoPathfinder.GoalSet walkSet = GoPathfinder.goalSet(candidates, limit, player.blockPosition());
               goalSet = walkSet;
               cells = walkSet.cellToTarget();
            }

            this.legGoalSet = goalSet;
            this.legGoalCells = cells;
            this.target = null;
            GoManager.INSTANCE.autoDispatchMulti(goalSet, cells);
            this.state = AutoWalkScanner.State.DRIVING;
            this.sectionQueue.clear();
         } else {
            this.dispatchSingleNearest(candidates, candidates.size() < 2 ? "候选不足 2 个" : "多目标失败后的回退期（剩余 " + (this.multiFallbackTick - now) + " tick）");
         }
      }
   }

   private void dispatchSingleNearest(ArrayList<BlockPos> candidates, String reason) {
      LocalPlayer player = this.mc.player;
      BlockPos best = null;
      double bestDistSq = Double.MAX_VALUE;

      for (BlockPos pos : candidates) {
         double dx = pos.getX() + 0.5 - player.getX();
         double dy = pos.getY() + 0.5 - player.getY();
         double dz = pos.getZ() + 0.5 - player.getZ();
         double distSq = dx * dx + dy * dy + dz * dz;
         if (distSq < bestDistSq) {
            bestDistSq = distSq;
            best = pos;
         }
      }

      this.legGoalSet = null;
      this.legGoalCells = null;
      this.target = best;
      this.selectedTarget = best;
      GoManager.INSTANCE.autoDispatch(best);
      this.state = AutoWalkScanner.State.DRIVING;
      this.sectionQueue.clear();
   }

   private void dispatchSerialNearest(ArrayList<BlockPos> candidates) {
      if (!candidates.isEmpty()) {
         this.dispatchSingleNearest(candidates, "子区块内直线最近（关闭「最短路径优先」）");
      }
   }

   private static double straightDist(BlockPos a, LocalPlayer player) {
      double dx = a.getX() + 0.5 - player.getX();
      double dy = a.getY() + 0.5 - player.getY();
      double dz = a.getZ() + 0.5 - player.getZ();
      return Math.sqrt(dx * dx + dy * dy + dz * dz);
   }

   private void tickDriving() {
      BlockPos t = this.target;
      if (t == null && this.legGoalCells == null) {
         this.enterScanning();
      } else {
         if (this.legGoalCells != null) {
            BlockPos current = GoManager.INSTANCE.getMultiGoalCurrentTarget();
            if (current != null) {
               this.selectedTarget = current;
            }
         }

         if (t != null && this.targetCompleted(t)) {
            this.onTargetDone();
         } else if (!GoManager.INSTANCE.isActive()) {
            if (this.legGoalCells != null) {
               BlockPos cell = GoManager.INSTANCE.getReachedGoalCell();
               BlockPos resolved = cell != null ? (BlockPos)this.legGoalCells.get(cell.asLong()) : null;
               this.legGoalCells = null;
               this.legGoalSet = null;
               if (resolved == null) {
                  this.multiFallbackTick = ClientPlayerTickManager.getCurrentHandlerTime() + 200L;
                  this.enterScanning();
                  return;
               }

               this.target = resolved;
               this.selectedTarget = resolved;
               t = resolved;
            }

            LocalPlayer player = this.mc.player;
            if (player != null) {
               double dx = t.getX() + 0.5 - player.getX();
               double dy = t.getY() + 0.5 - player.getEyeY();
               double dz = t.getZ() + 0.5 - player.getZ();
               double distSq = dx * dx + dy * dy + dz * dz;
               if (distSq <= 20.25) {
                  this.state = AutoWalkScanner.State.ARRIVED_WAITING;
                  return;
               }
            }

            this.unreachableCooldown.put(t.asLong(), ClientPlayerTickManager.getCurrentHandlerTime() + 200L);
            this.target = null;
            this.enterScanning();
         }
      }
   }

   private void tickWaiting() {
      BlockPos t = this.target;
      if (t == null) {
         this.enterScanning();
      } else if (GhastShiftBlacklist.contains(this.mc.player, t)) {
         this.unreachableCooldown.put(t.asLong(), ClientPlayerTickManager.getCurrentHandlerTime() + 200L);
         this.target = null;
         this.enterScanning();
      } else if (!this.selectionAllows(t)) {
         this.unreachableCooldown.put(t.asLong(), ClientPlayerTickManager.getCurrentHandlerTime() + 200L);
         this.target = null;
         this.enterScanning();
      } else {
         if (this.targetCompleted(t)) {
            this.onTargetDone();
         }
      }
   }

   private void tickDone() {
      LocalPlayer player = this.mc.player;
      if (player == null || this.cursorSection == null) {
         this.state = AutoWalkScanner.State.IDLE;
      } else if (!this.inSameSectionAsPlayer(this.cursorSection)) {
         this.activate();
      } else {
         long now = ClientPlayerTickManager.getCurrentHandlerTime();
         if (now >= this.nextDoneRecheckTick) {
            if (SchematicStateCache.INSTANCE.getRevision() != this.revisionAtDone) {
               this.activate();
               return;
            }

            ClientLevel level = this.mc.level;
            if (level != null) {
               ArrayList<BlockPos> candidates = new ArrayList<>();
               this.collectVerifierCandidates(level, candidates);
               if (!candidates.isEmpty()) {
                  this.activate();
               }
            }
         }
      }
   }

   private void appendConfiguredDirections(ArrayList<int[]> out) {
      SectionScanOrderType order = (SectionScanOrderType)Configs.Go.PRINT_SCAN_SECTION_ORDER.getOptionListValue();
      boolean xReverse = Configs.Go.PRINT_SCAN_X_REVERSE.getBooleanValue();
      boolean yReverse = Configs.Go.PRINT_SCAN_Y_REVERSE.getBooleanValue();
      boolean zReverse = Configs.Go.PRINT_SCAN_Z_REVERSE.getBooleanValue();

      for (SectionScanOrderType.Axis axis : order.axis) {
         boolean reverse = axis == SectionScanOrderType.Axis.X ? xReverse : (axis == SectionScanOrderType.Axis.Y ? yReverse : zReverse);
         int[] signs = reverse ? new int[]{-1, 1} : new int[]{1, -1};

         for (int sign : signs) {
            out.add(new int[]{axis.dx * sign, axis.dy * sign, axis.dz * sign});
         }
      }
   }

   private void enqueueNeighbors(@Nullable BlockPos base) {
      if (base != null) {
         int sx = base.getX() >> 4;
         int sy = base.getY() >> 4;
         int sz = base.getZ() >> 4;
         ArrayList<int[]> dirs = new ArrayList<>(6);
         this.appendConfiguredDirections(dirs);

         for (int[] d : dirs) {
            int nx = sx + d[0];
            int ny = sy + d[1];
            int nz = sz + d[2];
            if (this.isChunkVisible(nx, nz)) {
               BlockPos neighbor = new BlockPos(nx << 4, ny << 4, nz << 4);
               if (SchematicStateCache.INSTANCE.intersectsSchematic(neighbor, neighbor.offset(15, 15, 15))) {
                  this.sectionQueue.addLast(neighbor);
               } else {
                  this.visitedSections.add(sectionKey(nx, ny, nz));
               }
            }
         }
      }
   }

   private int sectionLayer(@Nullable BlockPos section) {
      BlockPos center = this.areaCenterSection;
      return center != null && section != null
         ? Math.abs((section.getX() >> 4) - (center.getX() >> 4))
            + Math.abs((section.getY() >> 4) - (center.getY() >> 4))
            + Math.abs((section.getZ() >> 4) - (center.getZ() >> 4))
         : 0;
   }

   private void findCenterStep() {
      ClientLevel level = this.mc.level;
      LocalPlayer player = this.mc.player;
      if (level != null && player != null) {
         if (this.findSections == null) {
            long playerSec = this.playerSectionBase().asLong();
            long nowTick = ClientPlayerTickManager.getCurrentHandlerTime();
            if (this.findSectionsCache != null && playerSec == this.findSectionsCachePlayer && nowTick - this.findSectionsCacheTick < 100L) {
               this.findSections = this.findSectionsCache;
               this.findIndex = 0;
            } else {
               this.findSections = new ArrayList<>();
               SchematicStateCache.INSTANCE.collectIntersectingSections(this.findSections::add);
               double px = player.getX();
               double py = player.getY();
               double pz = player.getZ();
               this.findSections.sort((a, b) -> {
                  double ax = Math.max(a.getX(), Math.min(px, a.getX() + 15));
                  double ay = Math.max(a.getY(), Math.min(py, a.getY() + 15));
                  double az = Math.max(a.getZ(), Math.min(pz, a.getZ() + 15));
                  double bx = Math.max(b.getX(), Math.min(px, b.getX() + 15));
                  double by = Math.max(b.getY(), Math.min(py, b.getY() + 15));
                  double bz = Math.max(b.getZ(), Math.min(pz, b.getZ() + 15));
                  double da = (px - ax) * (px - ax) + (py - ay) * (py - ay) + (pz - az) * (pz - az);
                  double db = (px - bx) * (px - bx) + (py - by) * (py - by) + (pz - bz) * (pz - bz);
                  return Double.compare(da, db);
               });
               this.findSectionsCache = this.findSections;
               this.findSectionsCachePlayer = playerSec;
               this.findSectionsCacheTick = nowTick;
               this.findIndex = 0;
            }
         }

         while (this.findIndex < this.findSections.size()) {
            BlockPos section = this.findSections.get(this.findIndex);
            this.findIndex++;
            int sx = section.getX() >> 4;
            int sz = section.getZ() >> 4;
            if (BlockStateUtils.isColumnLoaded(level, sx, sz)
               && this.isChunkVisible(sx, sz)
               && !this.visitedSections.contains(sectionKey(sx, section.getY() >> 4, sz))) {
               this.visitedSections.add(sectionKey(sx, section.getY() >> 4, sz));
               this.cursorSection = section;
               this.areaCenterSection = section;
               this.layerScanning = 0;
               this.submitSection(level, section, 0);
               this.verifierPollNeeded = true;
               this.state = AutoWalkScanner.State.SCANNING;
               return;
            }
         }

         if (this.pendingScans <= 0) {
            if (!multiTargetMode() || !this.poolDispatch(level, true)) {
               this.findSections = null;
               this.revisionAtDone = SchematicStateCache.INSTANCE.getRevision();
               this.nextDoneRecheckTick = ClientPlayerTickManager.getCurrentHandlerTime() + 100L;
               this.state = AutoWalkScanner.State.DONE;
            }
         }
      } else {
         this.state = AutoWalkScanner.State.IDLE;
      }
   }

   private void enterScanning() {
      this.areaCandidates.clear();
      this.reachableKeys.clear();
      this.reachablePoolCount = 0;
      this.areaExtraCandidates.clear();
      this.extraReachableKeys.clear();
      this.extraReachableCount = 0;
      this.visitedSections.clear();
      this.sectionQueue.clear();
      this.areaCenterSection = null;
      this.layerScanning = -1;
      this.findSections = null;
      this.findIndex = 0;
      this.scanSerial++;
      this.pendingScans = 0;
      this.scanResults.clear();
      this.dispatchedCandidates = List.of();
      this.verifierPollNeeded = true;
      this.state = AutoWalkScanner.State.SCANNING;
   }

   private boolean tryDispatchVerifierTarget(ClientLevel level) {
      ArrayList<BlockPos> candidates = new ArrayList<>();
      this.collectVerifierCandidates(level, candidates);
      if (multiTargetMode()) {
         this.filterStandSpot(level, candidates);
      }

      if (candidates.isEmpty()) {
         return false;
      }

      this.dispatchCandidates(candidates);
      return true;
   }

   private boolean ghastFlying(@Nullable LocalPlayer player) {
      return Configs.Go.GHAST_PATHFIND.getBooleanValue() && GhastRideState.canFly(player);
   }

   private void filterStandSpot(ClientLevel level, ArrayList<BlockPos> candidates) {
      if (level != null && !candidates.isEmpty()) {
         boolean flying = this.ghastFlying(this.mc.player);
         GhastPathfinder.BoxSpec spec = flying ? GoManager.INSTANCE.currentBoxSpec() : null;
         candidates.removeIf(pos -> flying ? !GhastPathfinder.hasHoverSpot(level, pos, spec) : !GoPathfinder.hasStandableNeighbor(level, pos));
      }
   }

   private static boolean reachableAt(ClientLevel level, BlockPos pos, boolean flying) {
      return flying ? GhastPathfinder.hasHoverSpot(level, pos, GoManager.INSTANCE.currentBoxSpec()) : GoPathfinder.hasStandableNeighbor(level, pos);
   }

   private void collectVerifierCandidates(ClientLevel level, ArrayList<BlockPos> out) {
      long now = ClientPlayerTickManager.getCurrentHandlerTime();
      MutableBlockPos mpos = new MutableBlockPos();

      for (SchematicPlacement placement : DataManager.getSchematicPlacementManager().getAllSchematicsPlacements()) {
         SchematicVerifier verifier = placement.getSchematicVerifier();
         if (verifier != null && verifier.isFinished()) {
            if (verifier instanceof VerifierDataView view) {
               view.forEachMismatch(MismatchType.MISSING, (pair, packed) -> {
                  if (!ScanWhitelistCache.WALK.isWhitelisted((BlockState)pair.getLeft())) {
                     return true;
                  }

                  mpos.set(BlockPos.getX(packed), BlockPos.getY(packed), BlockPos.getZ(packed));
                  if (this.candidateUsable(level, now, mpos)) {
                     out.add(mpos.immutable());
                  }

                  return true;
               });
            } else {
               ArrayListMultimap<Pair<BlockState, BlockState>, BlockPos> missing = ((SchematicVerifierAccessor)verifier).printer$getMissingBlocksPositions();
               if (missing != null && !missing.isEmpty()) {
                  for (Pair<BlockState, BlockState> key : missing.keySet()) {
                     if (ScanWhitelistCache.WALK.isWhitelisted((BlockState)key.getLeft())) {
                        for (BlockPos pos : missing.get(key)) {
                           if (this.candidateUsable(level, now, pos)) {
                              out.add(pos.immutable());
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private boolean candidateUsable(ClientLevel level, long now, BlockPos pos) {
      if (!BlockStateUtils.isColumnLoaded(level, pos.getX() >> 4, pos.getZ() >> 4) || !this.isChunkVisible(pos.getX() >> 4, pos.getZ() >> 4)) {
         return false;
      } else if (this.unreachableCooldown.get(pos.asLong()) > now) {
         return false;
      } else if (GhastShiftBlacklist.contains(this.mc.player, pos)) {
         return false;
      } else {
         return !this.selectionAllows(pos) ? false : !this.targetCompleted(pos);
      }
   }

   private boolean selectionAllows(BlockPos pos) {
      return ConfigUtils.isPositionInSelectionRange(this.mc.player, pos, Configs.Print.PRINT_SELECTION_TYPE);
   }

   private long selectionSig() {
      long type = Configs.Print.PRINT_SELECTION_TYPE.getOptionListValue() instanceof SelectionType st ? st.ordinal() : -1L;
      LayerRange range = DataManager.getRenderLayerRange();
      return (type & 15L) << 56
         | (long)(range.getAxis().ordinal() & 15) << 52
         | (range.getLayerRangeMin() & 1048575L) << 20
         | range.getLayerRangeMax() & 1048575L;
   }

   private boolean targetCompleted(BlockPos t) {
      ClientLevel level = this.mc.level;
      if (level == null) {
         return true;
      }

      BlockState required = SchematicStateCache.INSTANCE.getSchematicState(t);
      return required == null ? true : BlockMatchResult.compare(required, level.getBlockState(t)) == BlockMatchResult.CORRECT;
   }

   private void onTargetDone() {
      this.target = null;
      this.enterScanning();
   }

   private boolean conditionsMet() {
      LocalPlayer player = this.mc.player;
      if (player == null || this.mc.level == null || player.isDeadOrDying()) {
         return false;
      } else {
         return !Configs.Go.PRINT_SCAN_AUTOWALK.getBooleanValue() ? false : this.printModeActive();
      }
   }

   private boolean printModeActive() {
      return ConfigUtils.isPrintModeActive();
   }

   private void deactivate() {
      if (GoManager.INSTANCE.isAutoActive()) {
         GoManager.INSTANCE.stop(null);
      }

      this.state = AutoWalkScanner.State.IDLE;
      this.target = null;
      this.legGoalCells = null;
      this.legGoalSet = null;
      this.dispatchedCandidates = List.of();
      this.selectedTarget = null;
      this.areaCandidates.clear();
      this.reachablePoolCount = 0;
      this.reachableKeys.clear();
      this.areaExtraCandidates.clear();
      this.extraReachableKeys.clear();
      this.extraReachableCount = 0;
      this.areaCenterSection = null;
      this.layerScanning = -1;
      this.scanSerial++;
      this.pendingScans = 0;
      this.scanResults.clear();
      this.sectionQueue.clear();
      this.cursorSection = null;
      this.visitedSections.clear();
      this.unreachableCooldown.clear();
      this.suspendedByManual = false;
   }

   private BlockPos playerSectionBase() {
      BlockPos feet = this.mc.player.blockPosition();
      return new BlockPos(feet.getX() & -16, feet.getY() & -16, feet.getZ() & -16);
   }

   private boolean inSameSectionAsPlayer(BlockPos base) {
      BlockPos feet = this.mc.player.blockPosition();
      return feet.getX() >> 4 == base.getX() >> 4 && feet.getY() >> 4 == base.getY() >> 4 && feet.getZ() >> 4 == base.getZ() >> 4;
   }

   private boolean isChunkVisible(int chunkX, int chunkZ) {
      LocalPlayer player = this.mc.player;
      if (player == null) {
         return false;
      }

      int rd = (Integer)this.mc.options.renderDistance().get();
      int dx = chunkX - (player.getBlockX() >> 4);
      int dz = chunkZ - (player.getBlockZ() >> 4);
      return dx * dx + dz * dz <= rd * rd;
   }

   private static long sectionKey(BlockPos base) {
      return sectionKey(base.getX() >> 4, base.getY() >> 4, base.getZ() >> 4);
   }

   private static long sectionKey(int sx, int sy, int sz) {
      return BlockPos.asLong(sx, sy, sz);
   }

   private static final class SectionScanResult {
      final int serial;
      final BlockPos section;
      final int layer;
      final LongArrayList positions = new LongArrayList();
      final ArrayList<BlockState> expected = new ArrayList<>();
      final LongArrayList extraPositions = new LongArrayList();

      SectionScanResult(int serial, BlockPos section, int layer) {
         this.serial = serial;
         this.section = section;
         this.layer = layer;
      }
   }

   private enum State {
      IDLE,
      SCANNING,
      DRIVING,
      ARRIVED_WAITING,
      DONE;

      // $VF: synthetic method
      private static AutoWalkScanner.State[] $values() {
         return new AutoWalkScanner.State[]{IDLE, SCANNING, DRIVING, ARRIVED_WAITING, DONE};
      }
   }
}

//#endif
