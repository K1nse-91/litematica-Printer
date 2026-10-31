package me.K1nse_.litematica.printer.utils.bedrock;

import java.lang.reflect.Method;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;

public class BedrockMiner implements Miner {
   public final Object instance;
   private final Method addBlockTaskMethod;
   private final Method clearTaskMethod;
   private final Method isRunningMethod;
   private final Method setRunningMethod;
   private final Method isBedrockMinerFeatureEnableMethod;
   private final Method setBedrockMinerFeatureEnableMethod;

   public BedrockMiner() throws Exception {
      Class<?> taskManagerClass = Class.forName("com.github.bunnyi116.bedrockminer.task.TaskManager");
      Method getInstanceMethod = taskManagerClass.getDeclaredMethod("getInstance");
      this.instance = getInstanceMethod.invoke(null);
      this.addBlockTaskMethod = taskManagerClass.getDeclaredMethod("addBlockTask", ClientLevel.class, BlockPos.class, Block.class);
      this.clearTaskMethod = taskManagerClass.getDeclaredMethod("clearTask");
      this.isRunningMethod = taskManagerClass.getDeclaredMethod("isRunning");
      this.setRunningMethod = taskManagerClass.getDeclaredMethod("setRunning", boolean.class, boolean.class);
      this.isBedrockMinerFeatureEnableMethod = taskManagerClass.getDeclaredMethod("isBedrockMinerFeatureEnable");
      this.setBedrockMinerFeatureEnableMethod = taskManagerClass.getDeclaredMethod("setBedrockMinerFeatureEnable", boolean.class);
   }

   @Override
   public void addToBreakList(BlockPos pos, ClientLevel world) throws Exception {
      if (this.instance != null) {
         Block block = world.getBlockState(pos).getBlock();
         this.addBlockTaskMethod.invoke(this.instance, world, pos, block);
      }
   }

   @Override
   public void clearTask() throws Exception {
      if (this.instance != null) {
         this.clearTaskMethod.invoke(null);
      }
   }

   @Override
   public boolean isWorking() throws Exception {
      return this.instance == null ? false : (Boolean)this.isRunningMethod.invoke(this.instance);
   }

   @Override
   public void setWorking(boolean running, boolean showMessage) throws Exception {
      if (this.instance != null) {
         this.setRunningMethod.invoke(this.instance, running, showMessage);
      }
   }

   @Override
   public boolean isBedrockMinerFeatureEnable() throws Exception {
      return this.instance == null ? false : (Boolean)this.isBedrockMinerFeatureEnableMethod.invoke(this.instance);
   }

   @Override
   public void setBedrockMinerFeatureEnable(boolean bedrockMinerFeatureEnable) throws Exception {
      if (this.instance != null) {
         this.setBedrockMinerFeatureEnableMethod.invoke(this.instance, bedrockMinerFeatureEnable);
      }
   }
}
