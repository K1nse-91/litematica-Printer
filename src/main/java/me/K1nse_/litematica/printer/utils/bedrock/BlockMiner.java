package me.K1nse_.litematica.printer.utils.bedrock;

import java.lang.reflect.Method;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;

public class BlockMiner implements Miner {
   public final Object instance;
   private final Method addBlockTaskMethod;
   private final Method clearTaskMethod;
   private final Method isRunningMethod;
   private final Method setEnableMethod;
   private final Method setDisableMethod;
   private final Method isInTasksMethod;

   public BlockMiner() throws Exception {
      Class<?> taskManagerClass = Class.forName("me.z7087.blockminer.task.TaskManager");
      Method getInstanceMethod = Class.forName("me.z7087.blockminer.BlockMinerMod").getDeclaredMethod("getInstance");
      Object modContainer = getInstanceMethod.invoke(null);
      Method getTaskManagerMethod = modContainer.getClass().getDeclaredMethod("getTaskManager");
      this.instance = getTaskManagerMethod.invoke(modContainer);
      this.addBlockTaskMethod = taskManagerClass.getDeclaredMethod("handleAttackBlock", BlockPos.class);
      this.clearTaskMethod = taskManagerClass.getDeclaredMethod("clearTasks");
      this.clearTaskMethod.setAccessible(true);
      this.isRunningMethod = taskManagerClass.getDeclaredMethod("isEnabled");
      this.setEnableMethod = taskManagerClass.getDeclaredMethod("onEnable");
      this.setEnableMethod.setAccessible(true);
      this.setDisableMethod = taskManagerClass.getDeclaredMethod("onDisable");
      this.setDisableMethod.setAccessible(true);
      this.isInTasksMethod = taskManagerClass.getDeclaredMethod("isTaskExists", BlockPos.class);
   }

   @Override
   public void addToBreakList(BlockPos pos, ClientLevel world) throws Exception {
      if (this.instance != null) {
         this.addBlockTaskMethod.invoke(this.instance, pos);
      }
   }

   @Override
   public void clearTask() throws Exception {
      if (this.instance != null) {
         this.clearTaskMethod.invoke(this.instance);
      }
   }

   @Override
   public boolean isWorking() throws Exception {
      return this.instance == null ? false : (Boolean)this.isRunningMethod.invoke(this.instance);
   }

   @Override
   public void setWorking(boolean running, boolean showMessage) throws Exception {
      if (this.instance != null) {
         if (running) {
            this.setEnableMethod.invoke(this.instance);
         } else {
            this.setDisableMethod.invoke(this.instance);
         }
      }
   }

   @Override
   public boolean isBedrockMinerFeatureEnable() {
      return true;
   }

   @Override
   public void setBedrockMinerFeatureEnable(boolean bedrockMinerFeatureEnable) {
   }
}
