package me.K1nse_.litematica.printer.utils.bedrock;

import me.K1nse_.litematica.printer.I18n;
import me.K1nse_.litematica.printer.utils.minecraft.MessageUtils;
import me.K1nse_.litematica.printer.utils.mods.ModLoadUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;

public class BedrockUtils {
   private static Miner bedrockMiner;

   public static void addToBreakList(BlockPos pos, ClientLevel world) {
      if (bedrockMiner != null) {
         try {
            bedrockMiner.addToBreakList(pos, world);
         } catch (Exception e) {
            e.printStackTrace();
         }
      }
   }

   public static void clearTask() {
      if (bedrockMiner != null) {
         try {
            bedrockMiner.clearTask();
         } catch (Exception e) {
            e.printStackTrace();
         }
      }
   }

   public static boolean isWorking() {
      if (bedrockMiner == null) {
         return false;
      }

      try {
         return bedrockMiner.isWorking();
      } catch (Exception e) {
         e.printStackTrace();
         return false;
      }
   }

   public static void setWorking(boolean running) {
      setWorking(running, false);
   }

   public static void setWorking(boolean running, boolean showMessage) {
      if (Minecraft.getInstance().player != null && Minecraft.getInstance().player.isCreative() && running) {
         MessageUtils.setOverlayMessage(I18n.BEDROCK_CREATIVE_MODE.getName());
      } else if (bedrockMiner != null) {
         try {
            bedrockMiner.setWorking(running, showMessage);
            if (!running) {
               clearTask();
            }
         } catch (Exception e) {
            e.printStackTrace();
         }
      }
   }

   public static boolean isBedrockMinerFeatureEnable() {
      if (bedrockMiner == null) {
         return false;
      }

      try {
         return bedrockMiner.isBedrockMinerFeatureEnable();
      } catch (Exception e) {
         e.printStackTrace();
         return false;
      }
   }

   public static void setBedrockMinerFeatureEnable(boolean bedrockMinerFeatureEnable) {
      if (bedrockMiner != null) {
         try {
            bedrockMiner.setBedrockMinerFeatureEnable(bedrockMinerFeatureEnable);
         } catch (Exception e) {
            e.printStackTrace();
         }
      }
   }

   static {
      try {
         if (ModLoadUtils.isBlockMinerLoaded()) {
            bedrockMiner = new BlockMiner();
         } else if (ModLoadUtils.isBedrockMinerLoaded()) {
            bedrockMiner = new BedrockMiner();
         } else {
            bedrockMiner = null;
         }
      } catch (Exception e) {
         e.printStackTrace();
         bedrockMiner = null;
      }
   }
}
