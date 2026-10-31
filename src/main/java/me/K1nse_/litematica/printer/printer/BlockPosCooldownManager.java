//#if MC >= 260100
package me.K1nse_.litematica.printer.printer;

import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import java.util.HashMap;
import java.util.Map;
import me.K1nse_.litematica.printer.utils.ConfigUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;

public class BlockPosCooldownManager {
   public static final BlockPosCooldownManager INSTANCE = new BlockPosCooldownManager();
   private static final long SWEEP_INTERVAL_TICKS = 100L;
   private final Map<String, Map<Identifier, Long2LongOpenHashMap>> cooldowns = new HashMap<>();
   private long lastSweepTick = Long.MIN_VALUE;

   public void tick() {
      if (!ConfigUtils.isEnable()) {
         if (!this.cooldowns.isEmpty()) {
            this.cooldowns.clear();
         }
      } else if (!this.cooldowns.isEmpty()) {
         ClientLevel level = Minecraft.getInstance().level;
         if (level != null) {
            long now = level.getGameTime();
            if (this.lastSweepTick == Long.MIN_VALUE || now - this.lastSweepTick >= 100L) {
               this.lastSweepTick = now;

               for (Map<Identifier, Long2LongOpenHashMap> byDim : this.cooldowns.values()) {
                  for (Long2LongOpenHashMap map : byDim.values()) {
                     if (!map.isEmpty()) {
                        map.long2LongEntrySet().removeIf(e -> e.getLongValue() <= now);
                     }
                  }
               }
            }
         }
      }
   }

   public void setCooldown(ClientLevel level, String type, BlockPos pos, int cooldownTicks) {
      if (cooldownTicks > 0) {
         this.mapForWrite(level, type).put(pos.asLong(), level.getGameTime() + cooldownTicks);
      }
   }

   public boolean isOnCooldown(ClientLevel level, String type, BlockPos pos) {
      Map<Identifier, Long2LongOpenHashMap> byDim = this.cooldowns.get(type);
      if (byDim == null) {
         return false;
      } else {
         Long2LongOpenHashMap map = byDim.get(level.dimension().identifier());
         if (map == null) {
            return false;
         } else {
            long key = pos.asLong();
            long expiry = map.get(key);
            if (expiry == 0L) {
               return false;
            } else if (expiry <= level.getGameTime()) {
               map.remove(key);
               return false;
            } else {
               return true;
            }
         }
      }
   }

   public void removeCooldown(ClientLevel level, String type, BlockPos pos) {
      Map<Identifier, Long2LongOpenHashMap> byDim = this.cooldowns.get(type);
      if (byDim != null) {
         Long2LongOpenHashMap map = byDim.get(level.dimension().identifier());
         if (map != null) {
            map.remove(pos.asLong());
         }
      }
   }

   public int getRemainingCooldown(ClientLevel level, String type, BlockPos pos) {
      Map<Identifier, Long2LongOpenHashMap> byDim = this.cooldowns.get(type);
      if (byDim == null) {
         return 0;
      }

      Long2LongOpenHashMap map = byDim.get(level.dimension().identifier());
      if (map == null) {
         return 0;
      }

      long expiry = map.get(pos.asLong());
      long remaining = expiry - level.getGameTime();
      return remaining > 0L ? (int)Math.min(remaining, 2147483647L) : 0;
   }

   public void clearDimensionCooldowns(ClientLevel level) {
      Identifier dimension = level.dimension().identifier();

      for (Map<Identifier, Long2LongOpenHashMap> byDim : this.cooldowns.values()) {
         byDim.remove(dimension);
      }
   }

   public void clearTypeCooldowns(ClientLevel level, String type) {
      Map<Identifier, Long2LongOpenHashMap> byDim = this.cooldowns.get(type);
      if (byDim != null) {
         byDim.remove(level.dimension().identifier());
      }
   }

   public void clearAllCooldowns() {
      this.cooldowns.clear();
   }

   private Long2LongOpenHashMap mapForWrite(ClientLevel level, String type) {
      Map<Identifier, Long2LongOpenHashMap> byDim = this.cooldowns.get(type);
      if (byDim == null) {
         byDim = new HashMap<>();
         this.cooldowns.put(type, byDim);
      }

      Identifier dim = level.dimension().identifier();
      Long2LongOpenHashMap map = byDim.get(dim);
      if (map == null) {
         map = new Long2LongOpenHashMap();
         byDim.put(dim, map);
      }

      return map;
   }
}

//#endif
