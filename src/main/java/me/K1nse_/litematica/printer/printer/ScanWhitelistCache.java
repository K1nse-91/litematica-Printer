//#if MC >= 260100
package me.K1nse_.litematica.printer.printer;

import com.google.common.collect.HashMultimap;
import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import fi.dy.masa.litematica.schematic.verifier.SchematicVerifier;
import fi.dy.masa.litematica.schematic.verifier.SchematicVerifier.BlockMismatch;
import fi.dy.masa.litematica.schematic.verifier.SchematicVerifier.MismatchType;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import me.K1nse_.litematica.printer.config.Configs;
import me.K1nse_.litematica.printer.handler.ClientPlayerTickManager;
import me.K1nse_.litematica.printer.mixin.printer.litematica.SchematicVerifierAccessor;
import me.K1nse_.litematica.printer.printer.verifier.VerifierDataView;
import me.K1nse_.litematica.printer.utils.FilterUtils;
import net.minecraft.world.level.block.state.BlockState;

public final class ScanWhitelistCache {
   public static final ScanWhitelistCache PRINT = new ScanWhitelistCache(
      () -> Configs.Print.PRINT_SCAN_WHITELIST.getBooleanValue(), () -> Configs.Print.PRINT_SCAN_WHITELIST_LIST.getStrings()
   );
   public static final ScanWhitelistCache WALK = new ScanWhitelistCache(
      () -> Configs.Go.WALK_SCAN_WHITELIST.getBooleanValue(), () -> Configs.Go.WALK_SCAN_WHITELIST_LIST.getStrings()
   );
   private final Supplier<Boolean> enabledConfig;
   private final Supplier<List<String>> listConfig;
   private List<String> source = List.of();
   private boolean enabled;
   private List<String> patterns = List.of();
   private final Map<BlockState, Boolean> matchCache = new HashMap<>();
   private static long highlightRefreshTick = Long.MIN_VALUE;
   private static boolean highlightAllMissing;
   private static final Set<BlockState> highlightStates = new HashSet<>();

   private ScanWhitelistCache(Supplier<Boolean> enabledConfig, Supplier<List<String>> listConfig) {
      this.enabledConfig = enabledConfig;
      this.listConfig = listConfig;
   }

   public boolean active() {
      return this.enabled && !this.patterns.isEmpty();
   }

   public void beginScanBatch() {
      boolean en = this.enabledConfig.get();
      List<String> cur = this.listConfig.get();
      if (en != this.enabled || cur.size() != this.source.size() || !cur.equals(this.source)) {
         this.enabled = en;
         this.source = List.copyOf(cur);
         this.patterns = List.copyOf(cur);
         this.matchCache.clear();
      }

      if (this.active()) {
         refreshHighlight(ClientPlayerTickManager.getCurrentHandlerTime());
      }
   }

   public boolean isWhitelisted(BlockState requiredState) {
      this.beginScanBatch();
      return this.isWhitelistedFast(requiredState);
   }

   public boolean isWhitelistedFast(BlockState requiredState) {
      return !this.active() ? true : this.matchCache.computeIfAbsent(requiredState, st -> {
         for (String s : this.patterns) {
            if (FilterUtils.matchName(s, st)) {
               return true;
            }
         }

         return highlightAllMissing || highlightStates.contains(st);
      });
   }

   private static void refreshHighlight(long now) {
      if (now != highlightRefreshTick) {
         highlightRefreshTick = now;
         boolean allMissing = false;
         HashSet<BlockState> states = new HashSet<>();

         for (SchematicPlacement placement : DataManager.getSchematicPlacementManager().getAllSchematicsPlacements()) {
            SchematicVerifier verifier = placement.getSchematicVerifier();
            if (verifier != null && verifier.isFinished()) {
               Set<MismatchType> selectedCats;
               HashMultimap<MismatchType, BlockMismatch> selectedMap;
               if (verifier instanceof VerifierDataView view) {
                  selectedCats = view.getSelectedMismatchTypes();
                  selectedMap = view.getSelectedMismatchEntries();
               } else {
                  SchematicVerifierAccessor accessor = (SchematicVerifierAccessor)verifier;
                  selectedCats = accessor.printer$getSelectedCategories();
                  selectedMap = accessor.printer$getSelectedEntries();
               }

               if (selectedCats != null && selectedCats.contains(MismatchType.MISSING)) {
                  allMissing = true;
                  break;
               }

               for (BlockMismatch mismatch : selectedMap.get(MismatchType.MISSING)) {
                  states.add(me.K1nse_.litematica.printer.printer.verifier.VerifierCompat.expected(mismatch));
               }
            }
         }

         if (allMissing != highlightAllMissing || !states.equals(highlightStates)) {
            highlightAllMissing = allMissing;
            highlightStates.clear();
            highlightStates.addAll(states);
            PRINT.matchCache.clear();
            WALK.matchCache.clear();
         }
      }
   }

   public static boolean hasHighlight() {
      return highlightAllMissing || !highlightStates.isEmpty();
   }
}

//#endif
