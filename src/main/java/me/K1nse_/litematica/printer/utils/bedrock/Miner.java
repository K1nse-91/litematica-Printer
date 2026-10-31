package me.K1nse_.litematica.printer.utils.bedrock;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;

public interface Miner {
   void addToBreakList(BlockPos var1, ClientLevel var2) throws Exception;

   void clearTask() throws Exception;

   boolean isWorking() throws Exception;

   void setWorking(boolean var1, boolean var2) throws Exception;

   boolean isBedrockMinerFeatureEnable() throws Exception;

   void setBedrockMinerFeatureEnable(boolean var1) throws Exception;
}
