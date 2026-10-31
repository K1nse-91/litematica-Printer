package me.K1nse_.litematica.printer.printer.verifier;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.UnmodifiableIterator;
import fi.dy.masa.litematica.config.Configs.Generic;
import fi.dy.masa.litematica.config.Configs.InfoOverlays;
import fi.dy.masa.litematica.config.Configs.Visuals;
import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import fi.dy.masa.litematica.schematic.placement.SubRegionPlacement.RequiredEnabled;
import fi.dy.masa.litematica.schematic.verifier.SchematicVerifier;
import fi.dy.masa.litematica.schematic.verifier.SchematicVerifier.BlockMismatch;
import fi.dy.masa.litematica.schematic.verifier.SchematicVerifier.MismatchRenderPos;
import fi.dy.masa.litematica.schematic.verifier.SchematicVerifier.MismatchType;
import fi.dy.masa.litematica.selection.Box;
import fi.dy.masa.litematica.util.BlockInfoListType;
import fi.dy.masa.litematica.util.IgnoreBlockRegistry;
import fi.dy.masa.litematica.util.ItemUtils;
import fi.dy.masa.litematica.util.PositionUtils;
import fi.dy.masa.litematica.util.WorldUtils;
import fi.dy.masa.litematica.world.SchematicWorldHandler;
import fi.dy.masa.litematica.world.WorldSchematic;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.Message.MessageType;
import fi.dy.masa.malilib.interfaces.ICompletionListener;
import fi.dy.masa.malilib.util.StringUtils;
import fi.dy.masa.malilib.util.game.BlockUtils;
import fi.dy.masa.malilib.util.position.IntBoundingBox;
//#if MC >= 260200
//$$ import fi.dy.masa.malilib.util.position.LayerRange;
//#else
import fi.dy.masa.malilib.util.LayerRange;
//#endif
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import me.K1nse_.litematica.printer.Reference;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.Nullable;

public class OptimizedSchematicVerifier extends SchematicVerifier implements VerifierDataView {
   private static final long SNAPSHOT_BUDGET_NS = 3000000L;
   private static final long RECHECK_BUDGET_NS = 1500000L;
   private static final int JOB_QUEUE_CAPACITY = 32;
   private static final int OVERFLOW_CAPACITY = 64;
   private static final int NEAREST_OVERSAMPLE = 2;
   private static final BlockState AIR = Blocks.AIR.defaultBlockState();
   private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
   private final List<OptimizedSchematicVerifier.Entry> entries = new ArrayList<>();
   private final Map<OptimizedSchematicVerifier.Entry, Integer> entryIndex = new HashMap<>();
   private final Int2IntOpenHashMap entryCounts = new Int2IntOpenHashMap();
   private final IntOpenHashSet ignoredEntries = new IntOpenHashSet();
   private final HashSet<Pair<BlockState, BlockState>> ignoredPairs = new HashSet<>();
   private final int[] typeEffective = new int[MismatchType.values().length];
   private final Long2ObjectOpenHashMap<OptimizedSchematicVerifier.ChunkData> byChunk = new Long2ObjectOpenHashMap();
   private final Long2IntOpenHashMap mismatchByPos = new Long2IntOpenHashMap();
   private final Object2IntOpenHashMap<BlockState> correctStateCounts = new Object2IntOpenHashMap();
   private int correctStatesCount;
   private int schematicBlocks;
   private int clientBlocks;
   private final Set<MismatchType> selectedCategories = new HashSet<>();
   private final HashMultimap<MismatchType, BlockMismatch> selectedEntries = HashMultimap.create();
   private volatile List<MismatchRenderPos> mismatchPositionsForRender = new ArrayList<>();
   private volatile List<BlockPos> mismatchBlockPositionsForRender = new ArrayList<>();
   private final List<BlockPos>[] closestByType = new List[MismatchType.values().length];
   private final ArrayBlockingQueue<OptimizedSchematicVerifier.ChunkJob> jobQueue = new ArrayBlockingQueue<>(32);
   private final ConcurrentLinkedQueue<OptimizedSchematicVerifier.ChunkJob> overflowJobs = new ConcurrentLinkedQueue<>();
   private final LongOpenHashSet pendingChunks = new LongOpenHashSet();
   private int totalChunks;
   private volatile boolean scanStarted;
   private volatile boolean scanActive;
   private volatile boolean scanDone;
   private volatile boolean allDispatched;
   private volatile boolean workerRunning;
   private volatile Thread scanWorker;
   private volatile long[] pendingSnapshot = new long[0];
   private volatile int renderMinY;
   private volatile int renderMaxY;
   @Nullable
   private ClientLevel worldClient;
   @Nullable
   private WorldSchematic worldSchematic;
   @Nullable
   private SchematicPlacement schematicPlacement;
   @Nullable
   private IgnoreBlockRegistry ignoreRegistry;
   @Nullable
   private UUID ownerHashId;
   @Nullable
   private ResourceKey<Level> boundDimension;
   private volatile boolean geometryDirty;
   private final LongOpenHashSet recheckQueue = new LongOpenHashSet();
   private final LongOpenHashSet deferredRechecks = new LongOpenHashSet();
   private static final long DEFERRED_RETRY_BUDGET_NS = 500000L;
   private long lastDeferredRetryGameTime = 0L;
   private boolean overlayRefreshPending;
   private long lastOverlayRefreshGameTime = 0L;
   private final Set<BlockState> iconStatesSchematic = ConcurrentHashMap.newKeySet();
   private final Set<BlockState> iconStatesClient = ConcurrentHashMap.newKeySet();
   private int areaMinX = Integer.MIN_VALUE;
   private int areaMaxX = Integer.MAX_VALUE;
   private int areaMinZ = Integer.MIN_VALUE;
   private int areaMaxZ = Integer.MAX_VALUE;
   private volatile int[][] schematicBoxes = new int[0][];

   public OptimizedSchematicVerifier() {
      for (int i = 0; i < this.closestByType.length; i++) {
         this.closestByType[i] = new ArrayList<>();
      }
   }

   public void startVerification(
      ClientLevel worldClient, WorldSchematic worldSchematic, SchematicPlacement schematicPlacement, ICompletionListener completionListener
   ) {
      UUID hashId = schematicPlacement.getHashId();
      this.ownerHashId = hashId;
      super.startVerification(worldClient, worldSchematic, schematicPlacement, completionListener);
      this.worldClient = worldClient;
      this.boundDimension = worldClient.dimension();
      this.worldSchematic = worldSchematic;
      this.schematicPlacement = schematicPlacement;
      this.ignoreRegistry = new IgnoreBlockRegistry();
      OptimizedSchematicVerifier.BlockUtilsWarmup.warmup();
      this.lock.writeLock().lock();

      try {
         this.pendingChunks.clear();

         for (ChunkPos pos : schematicPlacement.getTouchedChunks(RequiredEnabled.ANY)) {
            this.pendingChunks.add(chunkPosToLong(pos));
         }

         this.totalChunks = this.pendingChunks.size();
      } finally {
         this.lock.writeLock().unlock();
      }

      this.recomputeAreaBounds();
      this.recomputeSchematicBoxes();
      this.recomputeRenderExtent();
      this.rebuildPendingSnapshot();
      this.scanDone = false;
      this.allDispatched = false;
      this.scanStarted = true;
      this.scanActive = true;
      this.startWorker();
      this.updateRequiredChunksStringList();
      this.ownerHashId = hashId;
      VerifierRegistry.put(this.ownerHashId, this);
   }

   public void resume() {
      if (this.scanStarted && !this.finished) {
         this.scanActive = true;
         if (this.scanWorker == null) {
            this.startWorker();
         }
      }
   }

   public void stopVerification() {
      this.scanActive = false;
      this.stopWorker();
   }

   public void reset() {
      this.stopWorker();
      VerifierRegistry.remove(this.ownerHashId);
      this.ownerHashId = null;
      super.reset();
      this.lock.writeLock().lock();

      try {
         this.entries.clear();
         this.entryIndex.clear();
         this.entryCounts.clear();
         this.ignoredEntries.clear();
         Arrays.fill(this.typeEffective, 0);
         this.byChunk.clear();
         this.mismatchByPos.clear();
         this.correctStateCounts.clear();
         this.recheckQueue.clear();
         this.deferredRechecks.clear();
         this.jobQueue.clear();
         this.overflowJobs.clear();
         this.iconStatesSchematic.clear();
         this.iconStatesClient.clear();
         this.pendingChunks.clear();
         this.pendingSnapshot = new long[0];
         this.schematicBoxes = new int[0][];
         this.selectedCategories.clear();
         this.selectedEntries.clear();
         this.mismatchPositionsForRender = new ArrayList<>();
         this.mismatchBlockPositionsForRender = new ArrayList<>();

         for (List<BlockPos> list : this.closestByType) {
            list.clear();
         }

         this.infoHudLines.clear();
      } finally {
         this.lock.writeLock().unlock();
      }

      this.correctStatesCount = 0;
      this.schematicBlocks = 0;
      this.clientBlocks = 0;
      this.totalChunks = 0;
      this.worldClient = null;
      this.worldSchematic = null;
      this.schematicPlacement = null;
      this.ignoreRegistry = null;
      this.boundDimension = null;
      this.scanStarted = false;
      this.scanActive = false;
      this.scanDone = false;
      this.allDispatched = false;
   }

   private void recomputeAreaBounds() {
      int minX = Integer.MAX_VALUE;
      int maxX = Integer.MIN_VALUE;
      int minZ = Integer.MAX_VALUE;
      int maxZ = Integer.MIN_VALUE;
      LongIterator it = this.pendingChunks.iterator();

      while (it.hasNext()) {
         long key = it.nextLong();
         minX = Math.min(minX, chunkX(key));
         maxX = Math.max(maxX, chunkX(key));
         minZ = Math.min(minZ, chunkZ(key));
         maxZ = Math.max(maxZ, chunkZ(key));
      }

      if (minX > maxX) {
         this.areaMinX = Integer.MIN_VALUE;
         this.areaMaxX = Integer.MAX_VALUE;
         this.areaMinZ = Integer.MIN_VALUE;
         this.areaMaxZ = Integer.MAX_VALUE;
      } else {
         this.areaMinX = (minX << 4) - 16;
         this.areaMaxX = (maxX << 4) + 31;
         this.areaMinZ = (minZ << 4) - 16;
         this.areaMaxZ = (maxZ << 4) + 31;
      }
   }

   private void recomputeRenderExtent() {
      int minY = Integer.MAX_VALUE;
      int maxY = Integer.MIN_VALUE;
      UnmodifiableIterator var3 = this.schematicPlacement.getSubRegionBoxes(RequiredEnabled.ANY).values().iterator();

      while (var3.hasNext()) {
         Box box = (Box)var3.next();
         minY = Math.min(minY, Math.min(box.getPos1().getY(), box.getPos2().getY()));
         maxY = Math.max(maxY, Math.max(box.getPos1().getY(), box.getPos2().getY()));
      }

      if (minY > maxY) {
         this.renderMinY = this.worldClient != null ? this.worldClient.getMinY() : -64;
         this.renderMaxY = this.worldClient != null ? this.worldClient.getMaxY() : 320;
      } else {
         this.renderMinY = minY;
         this.renderMaxY = maxY + 1;
      }
   }

   private void rebuildPendingSnapshot() {
      long[] arr = new long[this.pendingChunks.size()];
      int i = 0;
      LongIterator it = this.pendingChunks.iterator();

      while (it.hasNext()) {
         arr[i++] = it.nextLong();
      }

      this.pendingSnapshot = arr;
   }

   private void recomputeSchematicBoxes() {
      ImmutableCollection<Box> values = this.schematicPlacement.getSubRegionBoxes(RequiredEnabled.ANY).values();
      int[][] boxes = new int[values.size()][];
      int i = 0;
      UnmodifiableIterator var4 = values.iterator();

      while (var4.hasNext()) {
         Box box = (Box)var4.next();
         BlockPos p1 = box.getPos1();
         BlockPos p2 = box.getPos2();
         boxes[i++] = new int[]{
            Math.min(p1.getX(), p2.getX()),
            Math.min(p1.getY(), p2.getY()),
            Math.min(p1.getZ(), p2.getZ()),
            Math.max(p1.getX(), p2.getX()),
            Math.max(p1.getY(), p2.getY()),
            Math.max(p1.getZ(), p2.getZ())
         };
      }

      this.schematicBoxes = boxes;
   }

   private boolean inSchematicBoxes(int x, int y, int z) {
      for (int[] b : this.schematicBoxes) {
         if (x >= b[0] && x <= b[3] && y >= b[1] && y <= b[4] && z >= b[2] && z <= b[5]) {
            return true;
         }
      }

      return false;
   }

   private void startWorker() {
      this.scanWorker = new Thread(this::workerLoop, "litematica-printer-schematic-verifier");
      this.scanWorker.setDaemon(true);
      this.workerRunning = true;
      this.scanWorker.start();
   }

   private void stopWorker() {
      this.workerRunning = false;
      Thread sw = this.scanWorker;
      this.scanWorker = null;
      if (sw != null) {
         sw.interrupt();
      }
   }

   public boolean isActive() {
      return this.scanActive;
   }

   public boolean isPaused() {
      return this.scanStarted && !this.scanActive && !this.finished;
   }

   public boolean isFinished() {
      return this.finished;
   }

   public int getTotalChunks() {
      return this.totalChunks;
   }

   public int getUnseenChunks() {
      return this.pendingChunks.size();
   }

   public long[] getPendingSnapshot() {
      return this.pendingSnapshot;
   }

   public boolean isScanStarted() {
      return this.scanStarted;
   }

   public int getRenderMinY() {
      return this.renderMinY;
   }

   public int getRenderMaxY() {
      return this.renderMaxY;
   }

   public int getSchematicTotalBlocks() {
      this.lock.readLock().lock();

      try {
         return this.schematicBlocks;
      } finally {
         this.lock.readLock().unlock();
      }
   }

   public int getRealWorldTotalBlocks() {
      this.lock.readLock().lock();

      try {
         return this.clientBlocks;
      } finally {
         this.lock.readLock().unlock();
      }
   }

   public int getCorrectStatesCount() {
      this.lock.readLock().lock();

      try {
         return this.correctStatesCount;
      } finally {
         this.lock.readLock().unlock();
      }
   }

   public int getMissingBlocks() {
      return this.effectiveCount(MismatchType.MISSING);
   }

   public int getExtraBlocks() {
      return this.effectiveCount(MismatchType.EXTRA);
   }

   public int getMismatchedBlocks() {
      return this.effectiveCount(MismatchType.WRONG_BLOCK);
   }

   public int getMismatchedStates() {
      return this.effectiveCount(MismatchType.WRONG_STATE);
   }

   public int getDiffBlocks() {
      return this.effectiveCount(MismatchType.DIFF_BLOCK);
   }

   private int effectiveCount(MismatchType type) {
      this.lock.readLock().lock();

      try {
         return this.typeEffective[type.ordinal()];
      } finally {
         this.lock.readLock().unlock();
      }
   }

   public Object2IntOpenHashMap<BlockState> getCorrectStates() {
      this.lock.readLock().lock();

      try {
         return new Object2IntOpenHashMap(this.correctStateCounts);
      } finally {
         this.lock.readLock().unlock();
      }
   }

   private static int packLocal(int x, int y, int z) {
      return (y + 2048 & 8191) << 8 | (z & 15) << 4 | x & 15;
   }

   private static long chunkKey(int x, int z) {
      return packChunkPos(x >> 4, z >> 4);
   }

   private static long packChunkPos(int cx, int cz) {
      return (long)cz << 32 | cx & 4294967295L;
   }

   static int chunkX(long key) {
      return (int)key;
   }

   static int chunkZ(long key) {
      return (int)(key >> 32);
   }

   private static long toWorldPacked(long chunkKey, int local) {
      return BlockPos.asLong((chunkX(chunkKey) << 4) + (local & 15), (local >> 8) - 2048, (chunkZ(chunkKey) << 4) + (local >> 4 & 15));
   }

   private void record(MismatchType type, BlockState expected, BlockState found, int x, int y, int z) {
      Pair<BlockState, BlockState> pair = Pair.of(expected, found);
      OptimizedSchematicVerifier.Entry key = new OptimizedSchematicVerifier.Entry(type, pair);
      Integer boxed = this.entryIndex.get(key);
      int idx;
      if (boxed == null) {
         this.entries.add(key);
         idx = this.entries.size();
         this.entryIndex.put(key, idx);
         this.entryCounts.put(idx, 0);
      } else {
         idx = boxed;
      }

      this.entryCounts.addTo(idx, 1);
      this.typeEffective[type.ordinal()]++;
      long ck = chunkKey(x, z);
      OptimizedSchematicVerifier.ChunkData cd = (OptimizedSchematicVerifier.ChunkData)this.byChunk
         .computeIfAbsent(ck, k -> new OptimizedSchematicVerifier.ChunkData());
      int local = packLocal(x, y, z);
      ((IntOpenHashSet)cd.byEntry.computeIfAbsent(idx, k -> new IntOpenHashSet())).add(local);
      cd.reverse.put(local, idx);
      this.mismatchByPos.put(BlockPos.asLong(x, y, z), idx);
   }

   private int unrecord(long worldPacked) {
      int idx = this.mismatchByPos.remove(worldPacked);
      if (idx == 0) {
         return 0;
      }

      OptimizedSchematicVerifier.Entry entry = this.entries.get(idx - 1);
      int x = BlockPos.getX(worldPacked);
      int y = BlockPos.getY(worldPacked);
      int z = BlockPos.getZ(worldPacked);
      OptimizedSchematicVerifier.ChunkData cd = (OptimizedSchematicVerifier.ChunkData)this.byChunk.get(chunkKey(x, z));
      if (cd != null) {
         int local = packLocal(x, y, z);
         IntOpenHashSet set = (IntOpenHashSet)cd.byEntry.get(idx);
         if (set != null) {
            set.rem(local);
            if (set.isEmpty()) {
               cd.byEntry.remove(idx);
            }
         }

         cd.reverse.remove(local);
      }

      int had = this.entryCounts.get(idx);
      if (had > 0) {
         this.entryCounts.put(idx, had - 1);
         if (!this.ignoredEntries.contains(idx)) {
            this.typeEffective[entry.type.ordinal()]--;
         }
      }

      return idx;
   }

   @Nullable
   private MismatchType classify(BlockState schematic, BlockState found) {
      if (schematic.isAir()) {
         if (Visuals.IGNORE_EXISTING_FLUIDS.getBooleanValue() && found.liquid()) {
            return null;
         } else {
            return this.ignoreRegistry != null && this.ignoreRegistry.hasBlock(found.getBlock()) ? null : MismatchType.EXTRA;
         }
      } else {
         if (found.isAir()) {
            return MismatchType.MISSING;
         }

         if (schematic.getBlock() != found.getBlock()) {
            if (Generic.ENABLE_DIFFERENT_BLOCKS.getBooleanValue() && BlockUtils.isInSameGroup(schematic, found)) {
               return BlockUtils.matchPropertiesOnly(schematic, found) ? MismatchType.DIFF_BLOCK : MismatchType.WRONG_STATE;
            } else {
               return MismatchType.WRONG_BLOCK;
            }
         } else {
            return MismatchType.WRONG_STATE;
         }
      }
   }

   private void checkStates(MutableBlockPos pos, BlockState schematic, BlockState found) {
      this.iconStatesSchematic.add(schematic);
      this.iconStatesClient.add(found);
      if (found == schematic || found.isAir() && schematic.isAir()) {
         this.correctStateCounts.addTo(found, 1);
         if (!schematic.isAir()) {
            this.correctStatesCount++;
         }
      } else {
         Pair<BlockState, BlockState> pair = Pair.of(schematic, found);
         if (!this.ignoredPairs.contains(pair)) {
            MismatchType type = this.classify(schematic, found);
            if (type != null) {
               this.record(type, schematic, found, pos.getX(), pos.getY(), pos.getZ());
            }
         }
      }
   }

   public boolean isBoundToCurrentDimension() {
      Minecraft mc = Minecraft.getInstance();
      return this.boundDimension != null && mc.level != null && mc.level.dimension() == this.boundDimension;
   }

   public void rebindPlacement(SchematicPlacement placement) {
      this.schematicPlacement = placement;
      this.geometryDirty = true;
   }

   public boolean execute(ProfilerFiller profiler) {
      if (!this.isBoundToCurrentDimension()) {
         return false;
      }

      Minecraft mc = Minecraft.getInstance();
      if (this.worldClient != mc.level) {
         this.worldClient = mc.level;
      }

      if (this.geometryDirty) {
         this.geometryDirty = false;
         this.recomputeAreaBounds();
         this.recomputeSchematicBoxes();
         this.recomputeRenderExtent();
      }

      WorldSchematic currentSchematicWorld = SchematicWorldHandler.getSchematicWorld();
      if (currentSchematicWorld != null && this.worldSchematic != currentSchematicWorld) {
         this.worldSchematic = currentSchematicWorld;
      }

      if (this.scanStarted && this.scanActive && !this.scanDone) {
         this.dispatchSnapshots(System.nanoTime() + 3000000L);
      }

      if (this.finished || this.scanDone) {
         this.processRechecks(System.nanoTime() + 1500000L);
      }

      this.drainIconWarmups();
      if (this.scanDone && !this.finished) {
         this.finished = true;
         this.scanStarted = false;
         this.scanActive = false;
         this.notifyListener();
      }

      if (this.scanStarted && !this.finished && (!this.selectedCategories.isEmpty() || !this.selectedEntries.isEmpty())) {
         long now = this.worldClient != null ? this.worldClient.getGameTime() : 0L;
         if (now >= this.lastOverlayRefreshGameTime + 20L) {
            this.updateMismatchOverlays();
            this.lastOverlayRefreshGameTime = now;
         }
      }

      return false;
   }

   private static long chunkPosToLong(ChunkPos pos) {
      return pos.pack();
   }

   private boolean neighborsLoaded(long chunkKey) {
      for (int cx = chunkX(chunkKey) - 1; cx <= chunkX(chunkKey) + 1; cx++) {
         for (int cz = chunkZ(chunkKey) - 1; cz <= chunkZ(chunkKey) + 1; cz++) {
            if (this.worldClient == null || !WorldUtils.isClientChunkLoaded(this.worldClient, cx, cz)) {
               return false;
            }
         }
      }

      return true;
   }

   private void dispatchSnapshots(long deadline) {
      while (!this.overflowJobs.isEmpty() && this.jobQueue.offer(this.overflowJobs.peek())) {
         this.overflowJobs.poll();
      }

      LongIterator it = this.pendingChunks.iterator();
      boolean dispatched = false;

      while (it.hasNext() && System.nanoTime() < deadline) {
         long chunkKey = it.nextLong();
         int ccx = chunkX(chunkKey);
         int ccz = chunkZ(chunkKey);
         if (this.neighborsLoaded(chunkKey) && this.worldSchematic != null && this.worldSchematic.getChunkSource().hasChunk(ccx, ccz)) {
            List<IntBoundingBox> boxes = new ArrayList<>();
            UnmodifiableIterator free = this.schematicPlacement.getBoxesWithinChunk(ccx, ccz).values().iterator();

            while (free.hasNext()) {
               IntBoundingBox box = (IntBoundingBox)free.next();
               boxes.add(this.clipToRenderLayers(box));
            }

            int freex = this.jobQueue.remainingCapacity() + (64 - this.overflowJobs.size());
            if (boxes.size() > freex) {
               break;
            }

            OptimizedSchematicVerifier.SectionSnapshot clientSnap = copySections(this.worldClient.getChunk(ccx, ccz));
            OptimizedSchematicVerifier.SectionSnapshot schematicSnap = copySections(this.worldSchematic.getChunk(ccx, ccz));

            for (IntBoundingBox box : boxes) {
               OptimizedSchematicVerifier.ChunkJob job = new OptimizedSchematicVerifier.ChunkJob(box, clientSnap, schematicSnap);
               if (!this.jobQueue.offer(job)) {
                  this.overflowJobs.add(job);
               }
            }

            it.remove();
            dispatched = true;
         }
      }

      if (dispatched) {
         this.updateRequiredChunksStringList();
         this.rebuildPendingSnapshot();
      }

      if (this.pendingChunks.isEmpty()) {
         this.allDispatched = true;
      }
   }

   private IntBoundingBox clipToRenderLayers(IntBoundingBox box) {
      if (this.schematicPlacement.getSchematicVerifierType() != BlockInfoListType.RENDER_LAYERS) {
         return box;
      }

      LayerRange range = DataManager.getRenderLayerRange();
      // malilib 0.28 起 getAxis() 返回 malilib 自己的 Direction.Axis，按序数比较以兼容各版本
      int axis = range.getAxis().ordinal();
      int minX = axis == 0 ? Math.max(VerifierCompat.minX(box), range.getLayerRangeMin()) : VerifierCompat.minX(box);
      int minY = axis == 1 ? Math.max(VerifierCompat.minY(box), range.getLayerRangeMin()) : VerifierCompat.minY(box);
      int minZ = axis == 2 ? Math.max(VerifierCompat.minZ(box), range.getLayerRangeMin()) : VerifierCompat.minZ(box);
      int maxX = axis == 0 ? Math.min(VerifierCompat.maxX(box), range.getLayerRangeMax()) : VerifierCompat.maxX(box);
      int maxY = axis == 1 ? Math.min(VerifierCompat.maxY(box), range.getLayerRangeMax()) : VerifierCompat.maxY(box);
      int maxZ = axis == 2 ? Math.min(VerifierCompat.maxZ(box), range.getLayerRangeMax()) : VerifierCompat.maxZ(box);
      return new IntBoundingBox(minX, minY, minZ, maxX, maxY, maxZ);
   }

   private static OptimizedSchematicVerifier.SectionSnapshot copySections(ChunkAccess chunk) {
      LevelChunkSection[] src = chunk.getSections();
      LevelChunkSection[] out = new LevelChunkSection[src.length];

      for (int i = 0; i < src.length; i++) {
         LevelChunkSection section = src[i];
         out[i] = section != null && !section.hasOnlyAir() ? section.copy() : null;
      }

      return new OptimizedSchematicVerifier.SectionSnapshot(out, chunk.getMinY());
   }

   private void workerLoop() {
      while (this.workerRunning) {
         OptimizedSchematicVerifier.ChunkJob job;
         try {
            job = this.jobQueue.poll(100L, TimeUnit.MILLISECONDS);
         } catch (InterruptedException e) {
            break;
         }

         if (job != null) {
            try {
               this.verifyJob(job);
            } catch (Throwable t) {
               Reference.LOGGER.error("Schematic verifier job failed", t);
            }
         } else {
            if (!this.allDispatched || !this.overflowJobs.isEmpty()) {
               continue;
            }
            break;
         }
      }

      if (this.workerRunning && Thread.currentThread() == this.scanWorker) {
         if (this.allDispatched && this.overflowJobs.isEmpty()) {
            this.scanDone = true;
         }

         this.workerRunning = false;
      }
   }

   private void verifyJob(OptimizedSchematicVerifier.ChunkJob job) {
      IntBoundingBox box = job.box;
      MutableBlockPos pos = new MutableBlockPos();
      int y = VerifierCompat.minY(box);

      while (y <= VerifierCompat.maxY(box)) {
         if (!this.workerRunning || Thread.currentThread() != this.scanWorker) {
            return;
         }

         int bandEnd = Math.min(VerifierCompat.maxY(box), y | 15);
         this.lock.writeLock().lock();

         try {
            if (!this.workerRunning || Thread.currentThread() != this.scanWorker) {
               return;
            }

            for (int yy = y; yy <= bandEnd; yy++) {
               for (int z = VerifierCompat.minZ(box); z <= VerifierCompat.maxZ(box); z++) {
                  for (int x = VerifierCompat.minX(box); x <= VerifierCompat.maxX(box); x++) {
                     BlockState stateSchematic = stateAt(job.schematic, x, yy, z);
                     BlockState stateClient = stateAt(job.client, x, yy, z);
                     pos.set(x, yy, z);
                     this.checkStates(pos, stateSchematic, stateClient);
                     if (!stateSchematic.isAir()) {
                        this.schematicBlocks++;
                     }

                     if (!stateClient.isAir()) {
                        this.clientBlocks++;
                     }
                  }
               }
            }
         } finally {
            this.lock.writeLock().unlock();
         }

         y = bandEnd + 1;
      }
   }

   private static BlockState stateAt(OptimizedSchematicVerifier.SectionSnapshot snapshot, int x, int y, int z) {
      int idx = y - snapshot.minY >> 4;
      if (idx >= 0 && idx < snapshot.sections.length) {
         LevelChunkSection section = snapshot.sections[idx];
         return section == null ? AIR : section.getBlockState(x & 15, y & 15, z & 15);
      } else {
         return AIR;
      }
   }

   public void markBlockChanged(BlockPos pos) {
      if (this.isBoundToCurrentDimension()) {
         int x = pos.getX();
         int z = pos.getZ();
         if (x >= this.areaMinX && x <= this.areaMaxX && z >= this.areaMinZ && z <= this.areaMaxZ) {
            this.recheckQueue.add(pos.asLong());
         }
      }
   }

   private void drainIconWarmups() {
      if (this.worldClient != null && this.worldSchematic != null) {
         Minecraft mc = Minecraft.getInstance();
         if (mc.player != null) {
            BlockPos at = mc.player.blockPosition();
            int budget = 512;

            for (Iterator<BlockState> it = this.iconStatesSchematic.iterator(); it.hasNext() && budget > 0; budget--) {
               BlockState state = it.next();
               it.remove();

               try {
                  ItemUtils.setItemForBlock(this.worldSchematic, at, state);
               } catch (Throwable t) {
                  Reference.LOGGER.error("Schematic verifier icon warmup failed", t);
               }
            }

            for (Iterator<BlockState> it = this.iconStatesClient.iterator(); it.hasNext() && budget > 0; budget--) {
               BlockState state = it.next();
               it.remove();

               try {
                  ItemUtils.setItemForBlock(this.worldClient, at, state);
               } catch (Throwable t) {
                  Reference.LOGGER.error("Schematic verifier icon warmup failed", t);
               }
            }
         }
      }
   }

   private void processRechecks(long deadline) {
      if ((!this.recheckQueue.isEmpty() || !this.deferredRechecks.isEmpty()) && this.worldClient != null && this.worldSchematic != null) {
         ClientLevel client = this.worldClient;
         WorldSchematic schematic = this.worldSchematic;
         boolean any = false;
         if (this.drainRechecks(this.recheckQueue, client, schematic, deadline, false)) {
            any = true;
         }

         if (!this.deferredRechecks.isEmpty()) {
            long now = client.getGameTime();
            if (now >= this.lastDeferredRetryGameTime + 20L) {
               this.lastDeferredRetryGameTime = now;
               if (this.drainRechecks(this.deferredRechecks, client, schematic, System.nanoTime() + 500000L, true)) {
                  any = true;
               }
            }
         }

         if (any) {
            this.overlayRefreshPending = true;
         }

         if (this.overlayRefreshPending) {
            long now = client.getGameTime();
            if (this.recheckQueue.isEmpty() && this.deferredRechecks.isEmpty() || now >= this.lastOverlayRefreshGameTime + 10L) {
               this.updateMismatchOverlays();
               this.overlayRefreshPending = false;
               this.lastOverlayRefreshGameTime = now;
            }
         }
      }
   }

   private boolean drainRechecks(LongOpenHashSet queue, ClientLevel client, WorldSchematic schematic, long deadline, boolean deferred) {
      boolean any = false;
      LongIterator it = queue.iterator();

      while (it.hasNext() && System.nanoTime() < deadline) {
         long worldPacked = it.nextLong();
         int x = BlockPos.getX(worldPacked);
         int y = BlockPos.getY(worldPacked);
         int z = BlockPos.getZ(worldPacked);
         if (!this.inSchematicBoxes(x, y, z)) {
            it.remove();
         } else if (client.hasChunk(x >> 4, z >> 4) && schematic.hasChunk(x >> 4, z >> 4)) {
            BlockState stateSchematic = schematic.getBlockState(new BlockPos(x, y, z));
            BlockState stateFound = client.getBlockState(new BlockPos(x, y, z));
            this.lock.writeLock().lock();

            try {
               int oldIdx = this.unrecord(worldPacked);
               if (oldIdx != 0 && !stateFound.isAir() && ((BlockState)this.entries.get(oldIdx - 1).pair.getRight()).isAir()) {
                  this.clientBlocks++;
               }

               MutableBlockPos pos = new MutableBlockPos();
               pos.set(x, y, z);
               this.checkStates(pos, stateSchematic, stateFound);
            } finally {
               this.lock.writeLock().unlock();
            }

            it.remove();
            any = true;
         } else if (x < this.areaMinX || x > this.areaMaxX || z < this.areaMinZ || z > this.areaMaxZ) {
            it.remove();
         } else if (!deferred) {
            it.remove();
            this.deferredRechecks.add(worldPacked);
         }
      }

      return any;
   }

   public void ignoreStateMismatch(BlockMismatch mismatch) {
      this.ignoreStateMismatch(mismatch, true);
   }

   private void ignoreStateMismatch(BlockMismatch mismatch, boolean updateOverlay) {
      Pair<BlockState, BlockState> pair = Pair.of(VerifierCompat.expected(mismatch), VerifierCompat.found(mismatch));
      this.lock.writeLock().lock();

      try {
         if (this.ignoredPairs.add(pair)) {
            Integer boxed = this.entryIndex.get(new OptimizedSchematicVerifier.Entry(VerifierCompat.type(mismatch), pair));
            if (boxed != null) {
               this.ignoredEntries.add(boxed);
               this.typeEffective[VerifierCompat.type(mismatch).ordinal()] -= this.entryCounts.get(boxed);
            }

            this.selectedEntries.remove(VerifierCompat.type(mismatch), mismatch);
         }
      } finally {
         this.lock.writeLock().unlock();
      }

      if (updateOverlay) {
         this.updateMismatchOverlays();
      }
   }

   public void addIgnoredStateMismatches(Collection<BlockMismatch> mismatches) {
      for (BlockMismatch mismatch : mismatches) {
         this.ignoreStateMismatch(mismatch, false);
      }

      this.updateMismatchOverlays();
   }

   public void resetIgnoredStateMismatches() {
      this.lock.writeLock().lock();

      try {
         IntIterator it = this.ignoredEntries.iterator();

         while (it.hasNext()) {
            int idx = it.nextInt();
            OptimizedSchematicVerifier.Entry entry = this.entries.get(idx - 1);
            this.typeEffective[entry.type.ordinal()] += this.entryCounts.get(idx);
         }

         this.ignoredPairs.clear();
         this.ignoredEntries.clear();
      } finally {
         this.lock.writeLock().unlock();
      }
   }

   public Set<Pair<BlockState, BlockState>> getIgnoredMismatches() {
      this.lock.readLock().lock();

      try {
         return new HashSet<>(this.ignoredPairs);
      } finally {
         this.lock.readLock().unlock();
      }
   }

   public List<BlockMismatch> getMismatchOverviewFor(MismatchType type) {
      ArrayList<BlockMismatch> list = new ArrayList<>();
      if (type == MismatchType.ALL) {
         return this.getMismatchOverviewCombined();
      }

      this.lock.readLock().lock();

      try {
         this.addCountFor(type, list);
      } finally {
         this.lock.readLock().unlock();
      }

      return list;
   }

   public List<BlockMismatch> getMismatchOverviewCombined() {
      ArrayList<BlockMismatch> list = new ArrayList<>();
      this.lock.readLock().lock();

      try {
         this.addCountFor(MismatchType.MISSING, list);
         this.addCountFor(MismatchType.EXTRA, list);
         this.addCountFor(MismatchType.WRONG_BLOCK, list);
         this.addCountFor(MismatchType.WRONG_STATE, list);
         this.addCountFor(MismatchType.DIFF_BLOCK, list);
      } finally {
         this.lock.readLock().unlock();
      }

      list.sort(Comparator.naturalOrder());
      return list;
   }

   private void addCountFor(MismatchType type, List<BlockMismatch> list) {
      for (int i = 0; i < this.entries.size(); i++) {
         OptimizedSchematicVerifier.Entry entry = this.entries.get(i);
         int count = this.entryCounts.get(i + 1);
         if (entry.type == type && count > 0 && !this.ignoredEntries.contains(i + 1)) {
            list.add(new BlockMismatch(entry.type, (BlockState)entry.pair.getLeft(), (BlockState)entry.pair.getRight(), count));
         }
      }
   }

   public List<Pair<BlockState, BlockState>> getIgnoredStateMismatchPairs(GuiBase gui) {
      this.lock.readLock().lock();

      ArrayList<Pair<BlockState, BlockState>> list;
      try {
         list = new ArrayList<>(this.ignoredPairs);
      } finally {
         this.lock.readLock().unlock();
      }

      try {
         list.sort((o1, o2) -> {
            String name1 = BuiltInRegistries.BLOCK.getKey(((BlockState)o1.getLeft()).getBlock()).toString();
            String name2 = BuiltInRegistries.BLOCK.getKey(((BlockState)o2.getLeft()).getBlock()).toString();
            int val = name1.compareTo(name2);
            if (val != 0) {
               return val;
            }

            name1 = BuiltInRegistries.BLOCK.getKey(((BlockState)o1.getRight()).getBlock()).toString();
            name2 = BuiltInRegistries.BLOCK.getKey(((BlockState)o2.getRight()).getBlock()).toString();
            return name1.compareTo(name2);
         });
      } catch (Exception e) {
         gui.addMessage(MessageType.ERROR, "litematica.error.generic.failed_to_sort_list_of_ignored_states", new Object[0]);
      }

      return list;
   }

   @Nullable
   public BlockMismatch getMismatchForPosition(BlockPos pos) {
      this.lock.readLock().lock();

      try {
         int idx = this.mismatchByPos.get(pos.asLong());
         if (idx != 0 && !this.ignoredEntries.contains(idx)) {
            OptimizedSchematicVerifier.Entry entry = this.entries.get(idx - 1);
            return new BlockMismatch(entry.type, (BlockState)entry.pair.getLeft(), (BlockState)entry.pair.getRight(), 1);
         } else {
            return null;
         }
      } finally {
         this.lock.readLock().unlock();
      }
   }

   public boolean probeMismatch(BlockPos pos, BlockState required, BlockState found) {
      this.lock.readLock().lock();

      try {
         int actual = this.mismatchByPos.get(pos.asLong());
         if (found != required && (!found.isAir() || !required.isAir())) {
            Pair<BlockState, BlockState> pair = Pair.of(required, found);
            if (this.ignoredPairs.contains(pair)) {
               return actual != 0;
            }

            MismatchType type = this.classify(required, found);
            if (type == null) {
               return actual != 0;
            }

            if (actual == 0) {
               return true;
            }

            OptimizedSchematicVerifier.Entry entry = this.entries.get(actual - 1);
            return entry.type != type || !entry.pair.equals(pair);
         } else {
            return actual != 0;
         }
      } finally {
         this.lock.readLock().unlock();
      }
   }

   public void toggleMismatchCategorySelected(MismatchType type) {
      if (type != MismatchType.CORRECT_STATE) {
         if (this.selectedCategories.contains(type)) {
            this.selectedCategories.remove(type);
         } else {
            this.selectedCategories.add(type);
            this.selectedEntries.removeAll(type);
         }

         this.updateMismatchOverlays();
      }
   }

   public void toggleMismatchEntrySelected(BlockMismatch mismatch) {
      MismatchType type = VerifierCompat.type(mismatch);
      if (this.selectedEntries.containsValue(mismatch)) {
         this.selectedEntries.remove(type, mismatch);
      } else {
         this.selectedCategories.remove(type);
         this.selectedEntries.put(type, mismatch);
      }

      this.updateMismatchOverlays();
   }

   public boolean isMismatchCategorySelected(MismatchType type) {
      return this.selectedCategories.contains(type);
   }

   public boolean isMismatchEntrySelected(BlockMismatch mismatch) {
      return this.selectedEntries.containsValue(mismatch);
   }

   public List<MismatchRenderPos> getSelectedMismatchPositionsForRender() {
      return this.mismatchPositionsForRender;
   }

   public List<BlockPos> getSelectedMismatchBlockPositionsForRender() {
      return this.mismatchBlockPositionsForRender;
   }

   public void updateRequiredChunksStringList() {
      List<ChunkPos> pending = new ArrayList<>(this.pendingChunks.size());
      LongIterator it = this.pendingChunks.iterator();

      while (it.hasNext()) {
         long key = it.nextLong();
         pending.add(new ChunkPos(chunkX(key), chunkZ(key)));
      }

      this.updateInfoHudLinesPendingChunks(pending);
   }

   private void updateMismatchOverlays() {
      LocalPlayer player = Minecraft.getInstance().player;
      if (player != null) {
         int maxEntries = InfoOverlays.VERIFIER_ERROR_HILIGHT_MAX_POSITIONS.getIntegerValue();
         BlockPos centerPos = player.blockPosition();
         this.updateClosestPositions(centerPos, maxEntries);
         this.combineClosestPositions(centerPos, maxEntries);
         if (this.selectedCategories.size() == 1 && this.selectedEntries.isEmpty()) {
            MismatchType type = this.mismatchPositionsForRender.isEmpty() ? null : VerifierCompat.renderType(this.mismatchPositionsForRender.get(0));
            this.updateMismatchPositionStringList(type, this.mismatchPositionsForRender);
         } else {
            this.updateMismatchPositionStringList(null, this.mismatchPositionsForRender);
         }
      }
   }

   private void updateClosestPositions(BlockPos centerPos, int maxEntries) {
      this.lock.readLock().lock();

      List<it.unimi.dsi.fastutil.longs.Long2ObjectMap.Entry<OptimizedSchematicVerifier.ChunkData>> chunks;
      try {
         chunks = new ArrayList<>(this.byChunk.long2ObjectEntrySet());
      } finally {
         this.lock.readLock().unlock();
      }

      double ccx = centerPos.getX() + 0.5;
      double ccz = centerPos.getZ() + 0.5;
      chunks.sort(Comparator.comparingDouble(e -> {
         double dx = chunkX(e.getLongKey()) * 16 + 8 - ccx;
         double dz = chunkZ(e.getLongKey()) * 16 + 8 - ccz;
         return dx * dx + dz * dz;
      }));
      this.selectNearest(MismatchType.DIFF_BLOCK, chunks, centerPos, maxEntries, this.closestByType[MismatchType.DIFF_BLOCK.ordinal()]);
      this.selectNearest(MismatchType.WRONG_BLOCK, chunks, centerPos, maxEntries, this.closestByType[MismatchType.WRONG_BLOCK.ordinal()]);
      this.selectNearest(MismatchType.WRONG_STATE, chunks, centerPos, maxEntries, this.closestByType[MismatchType.WRONG_STATE.ordinal()]);
      this.selectNearest(MismatchType.EXTRA, chunks, centerPos, maxEntries, this.closestByType[MismatchType.EXTRA.ordinal()]);
      this.selectNearest(MismatchType.MISSING, chunks, centerPos, maxEntries, this.closestByType[MismatchType.MISSING.ordinal()]);
   }

   private void selectNearest(
      MismatchType type,
      List<it.unimi.dsi.fastutil.longs.Long2ObjectMap.Entry<OptimizedSchematicVerifier.ChunkData>> chunks,
      BlockPos centerPos,
      int maxEntries,
      List<BlockPos> out
   ) {
      out.clear();
      HashSet<Pair<BlockState, BlockState>> selectedPairs = null;
      if (!this.selectedCategories.contains(type)) {
         selectedPairs = new HashSet<>();

         for (BlockMismatch mismatch : this.selectedEntries.get(type)) {
            selectedPairs.add(Pair.of(VerifierCompat.expected(mismatch), VerifierCompat.found(mismatch)));
         }
      }

      List<BlockPos> candidates = new ArrayList<>(Math.min(maxEntries * 2 + 1, 4096));
      this.lock.readLock().lock();

      try {
         int need = maxEntries * 2;

         label125:
         for (it.unimi.dsi.fastutil.longs.Long2ObjectMap.Entry<OptimizedSchematicVerifier.ChunkData> chunkEntry : chunks) {
            OptimizedSchematicVerifier.ChunkData cd = (OptimizedSchematicVerifier.ChunkData)this.byChunk.get(chunkEntry.getLongKey());
            if (cd != null) {
               ObjectIterator var12 = cd.byEntry.int2ObjectEntrySet().iterator();

               while (var12.hasNext()) {
                  it.unimi.dsi.fastutil.ints.Int2ObjectMap.Entry<IntOpenHashSet> setEntry = (it.unimi.dsi.fastutil.ints.Int2ObjectMap.Entry<IntOpenHashSet>)var12.next();
                  OptimizedSchematicVerifier.Entry entry = this.entries.get(setEntry.getIntKey() - 1);
                  if (entry.type == type
                     && !this.ignoredEntries.contains(setEntry.getIntKey())
                     && (selectedPairs == null || selectedPairs.contains(entry.pair))) {
                     IntIterator localIt = ((IntOpenHashSet)setEntry.getValue()).iterator();

                     while (localIt.hasNext()) {
                        candidates.add(BlockPos.of(toWorldPacked(chunkEntry.getLongKey(), localIt.nextInt())));
                        if (candidates.size() >= need) {
                           break label125;
                        }
                     }
                  }
               }
            }
         }
      } finally {
         this.lock.readLock().unlock();
      }

      PositionUtils.BLOCK_POS_COMPARATOR.setReferencePosition(centerPos);
      PositionUtils.BLOCK_POS_COMPARATOR.setClosestFirst(true);
      candidates.sort(PositionUtils.BLOCK_POS_COMPARATOR);
      int var21 = Math.min(maxEntries, candidates.size());

      for (int var22 = 0; var22 < var21; var22++) {
         out.add(candidates.get(var22));
      }
   }

   private void combineClosestPositions(BlockPos centerPos, int maxEntries) {
      ArrayList<MismatchRenderPos> tempList = new ArrayList<>();
      this.getMismatchRenderPositionFor(MismatchType.WRONG_BLOCK, tempList);
      this.getMismatchRenderPositionFor(MismatchType.DIFF_BLOCK, tempList);
      this.getMismatchRenderPositionFor(MismatchType.WRONG_STATE, tempList);
      this.getMismatchRenderPositionFor(MismatchType.EXTRA, tempList);
      this.getMismatchRenderPositionFor(MismatchType.MISSING, tempList);
      tempList.sort(new OptimizedSchematicVerifier.RenderPosComparator(centerPos, true));
      int max = Math.min(maxEntries, tempList.size());
      ArrayList<MismatchRenderPos> renderList = new ArrayList<>(max);
      ArrayList<BlockPos> renderBlockList = new ArrayList<>(max);

      for (int i = 0; i < max; i++) {
         MismatchRenderPos entry = tempList.get(i);
         renderList.add(entry);
         renderBlockList.add(VerifierCompat.renderPos(entry));
      }

      this.mismatchBlockPositionsForRender = renderBlockList;
      this.mismatchPositionsForRender = renderList;
   }

   private void getMismatchRenderPositionFor(MismatchType type, List<MismatchRenderPos> listOut) {
      for (BlockPos pos : this.closestByType[type.ordinal()]) {
         listOut.add(new MismatchRenderPos(type, pos));
      }
   }

   private void updateMismatchPositionStringList(@Nullable MismatchType mismatchType, List<MismatchRenderPos> positionList) {
      this.infoHudLines.clear();
      if (!positionList.isEmpty()) {
         String rst = GuiBase.TXT_RST;
         if (mismatchType != null) {
            this.infoHudLines.add(String.format("%s%s%s", mismatchType.getFormattingCode(), mismatchType.getDisplayname(), rst));
         } else {
            String title = StringUtils.translate("litematica.gui.title.schematic_verifier_errors", new Object[0]);
            this.infoHudLines.add(String.format("%s%s%s", GuiBase.TXT_BOLD, title, rst));
         }

         int count = Math.min(positionList.size(), InfoOverlays.INFO_HUD_MAX_LINES.getIntegerValue());

         for (int i = 0; i < count; i++) {
            MismatchRenderPos entry = positionList.get(i);
            BlockPos pos = VerifierCompat.renderPos(entry);
            String pre = VerifierCompat.renderType(entry).getColorCode();
            this.infoHudLines.add(String.format("%sx: %5d, y: %3d, z: %5d%s", pre, pos.getX(), pos.getY(), pos.getZ(), rst));
         }
      }
   }

   @Override
   public boolean forEachMismatch(MismatchType type, VerifierDataView.MismatchVisitor visitor) {
      this.lock.readLock().lock();

      try {
         ObjectIterator var3 = this.byChunk.long2ObjectEntrySet().iterator();

         while (var3.hasNext()) {
            it.unimi.dsi.fastutil.longs.Long2ObjectMap.Entry<OptimizedSchematicVerifier.ChunkData> chunkEntry = (it.unimi.dsi.fastutil.longs.Long2ObjectMap.Entry<OptimizedSchematicVerifier.ChunkData>)var3.next();
            OptimizedSchematicVerifier.ChunkData cd = (OptimizedSchematicVerifier.ChunkData)chunkEntry.getValue();
            ObjectIterator var6 = cd.byEntry.int2ObjectEntrySet().iterator();

            while (var6.hasNext()) {
               it.unimi.dsi.fastutil.ints.Int2ObjectMap.Entry<IntOpenHashSet> setEntry = (it.unimi.dsi.fastutil.ints.Int2ObjectMap.Entry<IntOpenHashSet>)var6.next();
               int idx = setEntry.getIntKey();
               OptimizedSchematicVerifier.Entry entry = this.entries.get(idx - 1);
               if (entry.type == type && !this.ignoredEntries.contains(idx)) {
                  IntIterator localIt = ((IntOpenHashSet)setEntry.getValue()).iterator();

                  while (localIt.hasNext()) {
                     if (!visitor.accept(entry.pair, toWorldPacked(chunkEntry.getLongKey(), localIt.nextInt()))) {
                        return false;
                     }
                  }
               }
            }
         }

         return true;
      } finally {
         this.lock.readLock().unlock();
      }
   }

   @Override
   public Set<MismatchType> getSelectedMismatchTypes() {
      this.lock.readLock().lock();

      try {
         return new HashSet<>(this.selectedCategories);
      } finally {
         this.lock.readLock().unlock();
      }
   }

   @Override
   public HashMultimap<MismatchType, BlockMismatch> getSelectedMismatchEntries() {
      this.lock.readLock().lock();

      try {
         HashMultimap<MismatchType, BlockMismatch> copy = HashMultimap.create();
         copy.putAll(this.selectedEntries);
         return copy;
      } finally {
         this.lock.readLock().unlock();
      }
   }

   private static final class BlockUtilsWarmup {
      private static boolean warmed;

      static void warmup() {
         if (!warmed) {
            warmed = true;
            BlockState stone = Blocks.STONE.defaultBlockState();
            BlockUtils.isInSameGroup(stone, stone);
            BlockUtils.matchPropertiesOnly(stone, stone);
         }
      }
   }

   private static final class ChunkData {
      final Int2ObjectOpenHashMap<IntOpenHashSet> byEntry = new Int2ObjectOpenHashMap();
      final Int2IntOpenHashMap reverse = new Int2IntOpenHashMap();
   }

   private record ChunkJob(IntBoundingBox box, OptimizedSchematicVerifier.SectionSnapshot client, OptimizedSchematicVerifier.SectionSnapshot schematic) {
   }

   private static final class Entry {
      final MismatchType type;
      final Pair<BlockState, BlockState> pair;

      Entry(MismatchType type, Pair<BlockState, BlockState> pair) {
         this.type = type;
         this.pair = pair;
      }

      @Override
      public boolean equals(Object o) {
         if (this == o) {
            return true;
         } else {
            return !(o instanceof OptimizedSchematicVerifier.Entry entry) ? false : this.type == entry.type && this.pair.equals(entry.pair);
         }
      }

      @Override
      public int hashCode() {
         int result = this.type.hashCode();
         return 31 * result + this.pair.hashCode();
      }
   }

   private static final class RenderPosComparator implements Comparator<MismatchRenderPos> {
      private final BlockPos posReference;
      private final boolean closestFirst;

      RenderPosComparator(BlockPos posReference, boolean closestFirst) {
         this.posReference = posReference;
         this.closestFirst = closestFirst;
      }

      public int compare(MismatchRenderPos pos1, MismatchRenderPos pos2) {
         double dist1 = VerifierCompat.renderPos(pos1).distSqr(this.posReference);
         double dist2 = VerifierCompat.renderPos(pos2).distSqr(this.posReference);
         if (dist1 == dist2) {
            return 0;
         } else {
            return dist1 < dist2 == this.closestFirst ? -1 : 1;
         }
      }
   }

   private record SectionSnapshot(@Nullable LevelChunkSection[] sections, int minY) {
   }
}
