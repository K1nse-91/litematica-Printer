//#if MC >= 260100
package me.K1nse_.litematica.printer.printer;

import com.google.common.collect.UnmodifiableIterator;
import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.schematic.LitematicaSchematic;
import fi.dy.masa.litematica.schematic.container.LitematicaBlockStateContainer;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacementManager;
import fi.dy.masa.litematica.schematic.placement.SubRegionPlacement;
import fi.dy.masa.litematica.schematic.placement.SubRegionPlacement.RequiredEnabled;
import fi.dy.masa.litematica.selection.Box;
import fi.dy.masa.litematica.util.SchematicUtils;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import me.K1nse_.litematica.printer.enums.BlockMatchResult;
import me.K1nse_.litematica.printer.printer.verifier.VerifierActiveUpdate;
import me.K1nse_.litematica.printer.utils.minecraft.BlockStateUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public final class SchematicStateCache {
   public static final SchematicStateCache INSTANCE = new SchematicStateCache();
   private static final int TTL_TICKS = 40;
   private final Long2ObjectOpenHashMap<SchematicStateCache.Entry> entries = new Long2ObjectOpenHashMap();
   @Nullable
   private ClientLevel boundLevel;
   private long stampTick = Long.MIN_VALUE;
   private int stamp = Integer.MIN_VALUE;
   private volatile boolean contentDirty;
   private long revision = 0L;
   private final ArrayList<SchematicStateCache.RegionEntry> regionIndex = new ArrayList<>();
   private boolean regionIndexDirty = true;
   private static final int SWEEP_INTERVAL_TICKS = 100;
   private static final long ENTRY_RETAIN_TICKS = 200L;
   private static final long PENDING_RETAIN_TICKS = 2400L;
   private long lastSweepTick = Long.MIN_VALUE;
   private static final int PENDING_LIST_TTL_TICKS = 10;
   private final HashMap<Item, SchematicStateCache.PendingList> pendingLists = new HashMap<>();

   private SchematicStateCache() {
   }

   public long getRevision() {
      return this.revision;
   }

   public void onSchematicContentChanged() {
      this.contentDirty = true;
   }

   public void onWorldBlockChanged(BlockPos pos) {
      SchematicStateCache.Entry e = (SchematicStateCache.Entry)this.entries.get(pos.asLong());
      if (e != null && e.verdict != null) {
         e.verdict = null;
         e.verdictTick = Long.MIN_VALUE;
         this.revision++;
      }
   }

   @Nullable
   public BlockState getSchematicState(BlockPos pos) {
      long now = this.ensureFresh();
      long key = pos.asLong();
      SchematicStateCache.Entry e = (SchematicStateCache.Entry)this.entries.get(key);
      if (e != null && now - e.stateTick < 40L) {
         return e.schematicState;
      }

      BlockState state = this.querySchematicState(pos);
      if (e == null) {
         e = new SchematicStateCache.Entry();
         this.entries.put(key, e);
      }

      e.schematicState = state;
      e.stateTick = now;
      return state;
   }

   public boolean isVerifiedNoWork(BlockPos pos, ClientLevel level) {
      long now = this.ensureFresh();
      if (level != this.boundLevel) {
         return false;
      }

      long key = pos.asLong();
      SchematicStateCache.Entry e = (SchematicStateCache.Entry)this.entries.get(key);
      if (e != null && e.verdict == BlockMatchResult.CORRECT && now - e.verdictTick < 40L) {
         return true;
      }

      if (!BlockStateUtils.isColumnLoaded(level, pos.getX() >> 4, pos.getZ() >> 4)) {
         return false;
      }

      BlockState required = this.getSchematicState(pos);
      if (required == null) {
         return false;
      }

      BlockState current = level.getBlockState(pos);
      BlockMatchResult verdict = BlockMatchResult.compare(required, current);
      if (e == null) {
         e = new SchematicStateCache.Entry();
         this.entries.put(key, e);
      }

      e.verdict = verdict;
      e.verdictTick = now;
      SchematicStateCache.RegionEntry owner = this.findRegionEntry(pos);
      if (owner != null) {
         VerifierActiveUpdate.onScannerVerdict(owner.placement, pos, required, current);
      }

      return verdict == BlockMatchResult.CORRECT;
   }

   public List<BlockPos> getPendingPositions(Item item) {
      long now = this.ensureFresh();
      SchematicStateCache.PendingList pl = this.pendingLists.get(item);
      if (pl != null && pl.builtRevision == this.revision && now - pl.builtTick < 10L) {
         return pl.list;
      }

      ArrayList<BlockPos> out = new ArrayList<>();
      ObjectIterator var6 = this.entries.long2ObjectEntrySet().iterator();

      while (var6.hasNext()) {
         it.unimi.dsi.fastutil.longs.Long2ObjectMap.Entry<SchematicStateCache.Entry> le = (it.unimi.dsi.fastutil.longs.Long2ObjectMap.Entry<SchematicStateCache.Entry>)var6.next();
         SchematicStateCache.Entry e = (SchematicStateCache.Entry)le.getValue();
         if (e.verdict != null && e.verdict != BlockMatchResult.CORRECT && e.schematicState != null && e.schematicState.getBlock().asItem() == item) {
            out.add(BlockPos.of(le.getLongKey()));
         }
      }

      this.pendingLists.put(item, new SchematicStateCache.PendingList(out, now, this.revision));
      return out;
   }

   private long ensureFresh() {
      Minecraft mc = Minecraft.getInstance();
      ClientLevel lvl = mc == null ? null : mc.level;
      long now = lvl == null ? 0L : lvl.getGameTime();
      if (lvl != this.boundLevel) {
         this.boundLevel = lvl;
         if (!this.entries.isEmpty()) {
            this.revision++;
         }

         this.entries.clear();
         this.pendingLists.clear();
      }

      if (now != this.stampTick) {
         int s = computeStamp();
         if (s != this.stamp) {
            this.stamp = s;
            if (!this.entries.isEmpty()) {
               this.revision++;
            }

            this.entries.clear();
            this.pendingLists.clear();
            this.regionIndexDirty = true;
         }

         this.stampTick = now;
      }

      if (this.contentDirty) {
         this.contentDirty = false;
         if (!this.entries.isEmpty()) {
            this.revision++;
         }

         this.entries.clear();
         this.pendingLists.clear();
      }

      this.sweepStale(now);
      return now;
   }

   private void sweepStale(long now) {
      if (!this.entries.isEmpty()) {
         if (this.lastSweepTick == Long.MIN_VALUE || now - this.lastSweepTick >= 100L) {
            this.lastSweepTick = now;
            long normalMinTick = now - 200L;
            long pendingMinTick = now - 2400L;
            this.entries.values().removeIf(e -> {
               long minTick = isPendingVerdict(e) ? pendingMinTick : normalMinTick;
               return e.stateTick < minTick && e.verdictTick < minTick;
            });
         }
      }
   }

   private static boolean isPendingVerdict(SchematicStateCache.Entry e) {
      return e.verdict != null && e.verdict != BlockMatchResult.CORRECT;
   }

   private static int computeStamp() {
      SchematicPlacementManager manager = DataManager.getSchematicPlacementManager();
      Collection<SchematicPlacement> placements = manager.getAllSchematicsPlacements();
      int s = placements.size();

      for (SchematicPlacement p : placements) {
         s = 31 * s + System.identityHashCode(p);
         s = 31 * s + p.getRotation().ordinal();
         s = 31 * s + p.getMirror().ordinal();
         s = 31 * s + (p.isEnabled() ? 1 : 0);
         s = 31 * s + Long.hashCode(p.getOrigin().asLong());
         LitematicaSchematic schematic = p.getSchematic();
         UnmodifiableIterator var6 = p.getEnabledRelativeSubRegionPlacements().entrySet().iterator();

         while (var6.hasNext()) {
            Map.Entry<String, SubRegionPlacement> entry = (Map.Entry<String, SubRegionPlacement>)var6.next();
            SubRegionPlacement sub = entry.getValue();
            s = 31 * s + Long.hashCode(sub.getPos().asLong());
            s = 31 * s + sub.getRotation().ordinal();
            s = 31 * s + sub.getMirror().ordinal();
            s = 31 * s + (sub.isEnabled() ? 1 : 0);
            LitematicaBlockStateContainer container = schematic == null ? null : schematic.getSubRegionContainer(entry.getKey());
            s = 31 * s + (container == null ? 0 : System.identityHashCode(container));
         }
      }

      return s;
   }

   public boolean intersectsSchematic(BlockPos min, BlockPos max) {
      this.ensureRegionIndex();

      for (int i = 0; i < this.regionIndex.size(); i++) {
         PrinterBox box = this.regionIndex.get(i).box;
         if (box.minX <= max.getX()
            && box.maxX >= min.getX()
            && box.minY <= max.getY()
            && box.maxY >= min.getY()
            && box.minZ <= max.getZ()
            && box.maxZ >= min.getZ()) {
            return true;
         }
      }

      return false;
   }

   public void collectIntersectingSections(Consumer<BlockPos> out) {
      this.ensureRegionIndex();
      LongOpenHashSet seen = new LongOpenHashSet();

      for (int i = 0; i < this.regionIndex.size(); i++) {
         PrinterBox box = this.regionIndex.get(i).box;
         int sx0 = box.minX >> 4;
         int sx1 = box.maxX >> 4;
         int sy0 = box.minY >> 4;
         int sy1 = box.maxY >> 4;
         int sz0 = box.minZ >> 4;
         int sz1 = box.maxZ >> 4;

         for (int sx = sx0; sx <= sx1; sx++) {
            for (int sy = sy0; sy <= sy1; sy++) {
               for (int sz = sz0; sz <= sz1; sz++) {
                  if (seen.add(BlockPos.asLong(sx, sy, sz))) {
                     out.accept(new BlockPos(sx << 4, sy << 4, sz << 4));
                  }
               }
            }
         }
      }
   }

   @Nullable
   private SchematicStateCache.RegionEntry findRegionEntry(BlockPos pos) {
      this.ensureRegionIndex();

      for (int i = 0; i < this.regionIndex.size(); i++) {
         SchematicStateCache.RegionEntry re = this.regionIndex.get(i);
         if (re.box.contains(pos)) {
            return re;
         }
      }

      return null;
   }

   @Nullable
   private BlockState querySchematicState(BlockPos pos) {
      this.ensureRegionIndex();

      for (int i = 0; i < this.regionIndex.size(); i++) {
         SchematicStateCache.RegionEntry re = this.regionIndex.get(i);
         if (re.box.contains(pos)) {
            BlockPos local = SchematicUtils.getSchematicContainerPositionFromWorldPosition(
               pos, re.placement.getSchematic(), re.regionName, re.placement, re.region, re.container
            );
            if (local != null) {
               BlockState state = re.container.get(local.getX(), local.getY(), local.getZ());
               return re.transform(state);
            }
         }
      }

      return null;
   }

   private void ensureRegionIndex() {
      if (this.regionIndexDirty) {
         this.regionIndex.clear();
         SchematicPlacementManager manager = DataManager.getSchematicPlacementManager();

         for (SchematicPlacement placement : manager.getAllSchematicsPlacements()) {
            UnmodifiableIterator var4 = placement.getSubRegionBoxes(RequiredEnabled.PLACEMENT_ENABLED).entrySet().iterator();

            while (var4.hasNext()) {
               Map.Entry<String, Box> entry = (Map.Entry<String, Box>)var4.next();
               String regionName = entry.getKey();
               SubRegionPlacement region = placement.getRelativeSubRegionPlacement(regionName);
               if (region != null && region.matchesRequirement(RequiredEnabled.PLACEMENT_ENABLED)) {
                  LitematicaSchematic schematic = placement.getSchematic();
                  if (schematic != null) {
                     LitematicaBlockStateContainer container = schematic.getSubRegionContainer(regionName);
                     if (container != null) {
                        Box box = entry.getValue();
                        Rotation mainRotation = placement.getRotation();
                        Mirror mirrorMain = placement.getMirror();
                        this.regionIndex
                           .add(
                              new SchematicStateCache.RegionEntry(
                                 placement,
                                 region,
                                 regionName,
                                 container,
                                 new PrinterBox(box.getPos1(), box.getPos2()),
                                 mirrorMain == Mirror.NONE ? null : mirrorMain,
                                 effectiveSubMirror(mainRotation, region.getMirror()),
                                 combinedRotation(mainRotation, region.getRotation())
                              )
                           );
                     }
                  }
               }
            }
         }

         this.regionIndexDirty = false;
      }
   }

   @Nullable
   private static Mirror effectiveSubMirror(Rotation mainRotation, Mirror subMirror) {
      if (subMirror == Mirror.NONE) {
         return null;
      } else if (mainRotation != Rotation.CLOCKWISE_90 && mainRotation != Rotation.COUNTERCLOCKWISE_90) {
         return subMirror;
      } else {
         return subMirror == Mirror.LEFT_RIGHT ? Mirror.FRONT_BACK : Mirror.LEFT_RIGHT;
      }
   }

   @Nullable
   private static Rotation combinedRotation(Rotation mainRotation, Rotation subRotation) {
      Rotation combined = mainRotation.getRotated(subRotation);
      return combined == Rotation.NONE ? null : combined;
   }

   public List<SchematicStateCache.RegionPlanEntry> planSection(BlockPos base) {
      this.ensureRegionIndex();
      int minX = base.getX();
      int minY = base.getY();
      int minZ = base.getZ();
      int maxX = minX + 15;
      int maxY = minY + 15;
      int maxZ = minZ + 15;
      ArrayList<SchematicStateCache.RegionPlanEntry> out = new ArrayList<>(2);

      for (int i = 0; i < this.regionIndex.size(); i++) {
         SchematicStateCache.RegionEntry re = this.regionIndex.get(i);
         int loX = Math.max(re.box.minX, minX);
         int hiX = Math.min(re.box.maxX, maxX);
         int loY = Math.max(re.box.minY, minY);
         int hiY = Math.min(re.box.maxY, maxY);
         int loZ = Math.max(re.box.minZ, minZ);
         int hiZ = Math.min(re.box.maxZ, maxZ);
         if (loX <= hiX && loY <= hiY && loZ <= hiZ) {
            out.add(new SchematicStateCache.RegionPlanEntry(re, loX, loY, loZ, hiX, hiY, hiZ));
         }
      }

      return out;
   }

   public void reconcileVerdict(BlockPos pos, BlockState required, BlockState current) {
      SchematicStateCache.RegionEntry owner = this.findRegionEntry(pos);
      if (owner != null) {
         VerifierActiveUpdate.onScannerVerdict(owner.placement, pos, required, current);
      }
   }

   private static final class Entry {
      @Nullable
      BlockState schematicState;
      long stateTick = Long.MIN_VALUE;
      @Nullable
      BlockMatchResult verdict;
      long verdictTick = Long.MIN_VALUE;
   }

   private static final class PendingList {
      final ArrayList<BlockPos> list;
      final long builtTick;
      final long builtRevision;

      PendingList(ArrayList<BlockPos> list, long builtTick, long builtRevision) {
         this.list = list;
         this.builtTick = builtTick;
         this.builtRevision = builtRevision;
      }
   }

   private static final class RegionEntry {
      final SchematicPlacement placement;
      final SubRegionPlacement region;
      final String regionName;
      final LitematicaBlockStateContainer container;
      final PrinterBox box;
      @Nullable
      final Mirror mirrorMain;
      @Nullable
      final Mirror mirrorSub;
      @Nullable
      final Rotation rotation;

      private RegionEntry(
         SchematicPlacement placement,
         SubRegionPlacement region,
         String regionName,
         LitematicaBlockStateContainer container,
         PrinterBox box,
         @Nullable Mirror mirrorMain,
         @Nullable Mirror mirrorSub,
         @Nullable Rotation rotation
      ) {
         this.placement = placement;
         this.region = region;
         this.regionName = regionName;
         this.container = container;
         this.box = box;
         this.mirrorMain = mirrorMain;
         this.mirrorSub = mirrorSub;
         this.rotation = rotation;
      }

      @Nullable
      BlockState transform(@Nullable BlockState state) {
         if (state == null) {
            return null;
         }

         BlockState s = state;
         if (this.mirrorMain != null) {
            s = s.mirror(this.mirrorMain);
         }

         if (this.mirrorSub != null) {
            s = s.mirror(this.mirrorSub);
         }

         if (this.rotation != null) {
            s = s.rotate(this.rotation);
         }

         return s;
      }
   }

   public static final class RegionPlanEntry {
      public final int minX;
      public final int minY;
      public final int minZ;
      public final int maxX;
      public final int maxY;
      public final int maxZ;
      private final SchematicPlacement placement;
      private final SubRegionPlacement region;
      private final String regionName;
      private final LitematicaBlockStateContainer container;
      @Nullable
      private final Mirror mirrorMain;
      @Nullable
      private final Mirror mirrorSub;
      @Nullable
      private final Rotation rotation;

      private RegionPlanEntry(SchematicStateCache.RegionEntry re, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
         this.minX = minX;
         this.minY = minY;
         this.minZ = minZ;
         this.maxX = maxX;
         this.maxY = maxY;
         this.maxZ = maxZ;
         this.placement = re.placement;
         this.region = re.region;
         this.regionName = re.regionName;
         this.container = re.container;
         this.mirrorMain = re.mirrorMain;
         this.mirrorSub = re.mirrorSub;
         this.rotation = re.rotation;
      }

      @Nullable
      public BlockState stateAt(int x, int y, int z) {
         BlockPos local = SchematicUtils.getSchematicContainerPositionFromWorldPosition(
            new BlockPos(x, y, z), this.placement.getSchematic(), this.regionName, this.placement, this.region, this.container
         );
         if (local == null) {
            return null;
         }

         BlockState state = this.container.get(local.getX(), local.getY(), local.getZ());
         if (state == null) {
            return null;
         }

         BlockState s = state;
         if (this.mirrorMain != null) {
            s = s.mirror(this.mirrorMain);
         }

         if (this.mirrorSub != null) {
            s = s.mirror(this.mirrorSub);
         }

         if (this.rotation != null) {
            s = s.rotate(this.rotation);
         }

         return s;
      }
   }
}

//#endif
