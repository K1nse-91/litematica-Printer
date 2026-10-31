//#if MC >= 260100
package me.K1nse_.litematica.printer.go;

import java.util.ArrayList;
import java.util.List;
import me.K1nse_.litematica.printer.config.Configs;
import me.K1nse_.litematica.printer.mixin.printer.mc.ClientInputAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import org.jetbrains.annotations.Nullable;

public final class GoExecutor {
   private static final double ARRIVE_DIST_SQ = 0.2025;
   @Nullable
   private static Input lastWritten;
   private static final ArrayList<ClientInput> writtenInputs = new ArrayList<>(2);
   private static float preJumpYaw = Float.NaN;
   private static int parkourPauseTicks;
   private static long parkourStateTick = -1L;
   private static int parkourDecidedGap = -1;
   @Nullable
   private static BlockPos parkourAirTarget;
   private static boolean parkourAirSprinted;
   @Nullable
   private static BlockPos sprintHopAirTarget;
   private static boolean selfJumpAirborne;

   public static void resetViewRestore() {
      preJumpYaw = Float.NaN;
      selfJumpAirborne = false;
      parkourPauseTicks = 0;
      parkourStateTick = -1L;
      parkourDecidedGap = -1;
      parkourAirTarget = null;
      parkourAirSprinted = false;
      sprintHopAirTarget = null;
   }

   public static boolean isSelfJumpAirborne() {
      return selfJumpAirborne;
   }

   private GoExecutor() {
   }

   public static void onApplyInput(LocalPlayer player) {
      if (!GoManager.INSTANCE.isActive()) {
         restorePreJumpYaw(player, false);
         clearStaleInput(player);
         if (GoManager.canGhastFly(player, false) && !isContainerUiOpen(player)) {
            GhastFlyer.driveStandby(player);
         } else {
            GhastFlyer.releasePitch(player);
         }
      } else {
         Minecraft mc = Minecraft.getInstance();
         if (mc.player == player) {
            if (player.isPassenger()) {
               if (GoManager.INSTANCE.isGhastFlying() && !isContainerUiOpen(player)) {
                  GhastFlyer.drive(player);
               }
            } else if (isContainerUiOpen(player)) {
               clearStaleInput(player);
            } else if (!player.getAbilities().flying && !player.isSpectator() && !player.isSleeping()) {
               boolean shift = player.input.keyPresses.shift();
               boolean takeover = Configs.Go.GO_TAKEOVER_VIEW.getBooleanValue();
               if (parkourAirTarget != null) {
                  if (!player.onGround() && !player.isInWater() && !hangingOnClimbable(player)) {
                     parkourAirControl(player, takeover, shift);
                     return;
                  }

                  parkourAirTarget = null;
                  if (parkourAirSprinted) {
                     parkourAirSprinted = false;
                     restorePreJumpYaw(player, takeover);
                     writeInput(player, 0.0F, 0.0F, false, false, shift);
                     return;
                  }
               }

               if (sprintHopAirTarget != null) {
                  if (!player.onGround() && !player.isInWater() && !hangingOnClimbable(player)) {
                     sprintHopAirControl(player, takeover, shift);
                     return;
                  }

                  sprintHopAirTarget = null;
               }

               BlockPos wp = GoManager.INSTANCE.getWaypoint();
               if (wp == null) {
                  restorePreJumpYaw(player, takeover);
                  writeInput(player, 0.0F, 0.0F, false, false, shift);
               } else {
                  boolean hanging = hangingOnClimbable(player);
                  int feetY = player.getBlockY();
                  double dx = wp.getX() + 0.5 - player.getX();
                  double dz = wp.getZ() + 0.5 - player.getZ();
                  double hDist = Math.sqrt(dx * dx + dz * dz);
                  if (hDist < 0.001) {
                     if (!hanging) {
                        restorePreJumpYaw(player, takeover);
                        writeInput(player, 0.0F, 0.0F, false, false, shift);
                        return;
                     }

                     restorePreJumpYaw(player, takeover);
                     double[] wall = attachmentWall(player);
                     if (wall == null) {
                        if (wp.getY() > feetY) {
                           writeInput(player, 0.0F, 0.0F, true, false, shift);
                           return;
                        }

                        writeInput(player, 0.0F, 0.0F, false, false, shift);
                        return;
                     }

                     dx = wall[0];
                     dz = wall[1];
                     hDist = 1.0;
                  }

                  dx /= hDist;
                  dz /= hDist;
                  boolean jump = false;
                  boolean parkour = false;
                  int parkourGap = -1;
                  if (hanging) {
                     if (wp.getY() > feetY) {
                        jump = true;
                     }
                  } else {
                     if (player.onGround() && wp.getY() > feetY && hDist * hDist < 3.24) {
                        jump = true;
                     }

                     if (player.isInWater() && wp.getY() >= feetY) {
                        jump = true;
                     }

                     if (player.onGround() && wp.getY() == feetY && hDist > 1.5 && hDist < 5.0 && floorAheadMissing(player, dx, dz)) {
                        if (!Configs.Go.GO_FORCE_SPRINT.getBooleanValue()) {
                           parkourPauseTicks = 0;
                           restorePreJumpYaw(player, takeover);
                           writeInput(player, 0.0F, 0.0F, false, false, shift);
                           return;
                        }

                        parkour = true;
                        long now = player.tickCount;
                        if (parkourStateTick != now) {
                           parkourStateTick = now;
                           int gap = (int)Math.round(hDist) - 1;
                           if (parkourPauseTicks > 0) {
                              parkourPauseTicks--;
                           } else if (gap <= 2) {
                              parkourPauseTicks = 1;
                           }

                           parkourDecidedGap = parkourPauseTicks == 0 ? gap : -1;
                        }

                        if (parkourPauseTicks == 0) {
                           jump = true;
                           parkourGap = parkourDecidedGap;
                           parkourAirTarget = wp;
                           parkourAirSprinted = parkourGap >= 2;
                        }
                     } else if (parkourPauseTicks != 0) {
                        parkourPauseTicks = 0;
                     }
                  }

                  boolean jumpUp = jump && !hanging && player.onGround() && !player.isInWater() && wp.getY() > feetY;
                  boolean sprintHop = false;
                  if (!hanging && !parkour && !jumpUp && player.onGround() && !player.isInWater() && wp.getY() == feetY) {
                     BlockPos hopEnd = sprintHopTarget(player);
                     if (hopEnd != null) {
                        double ex = hopEnd.getX() + 0.5 - player.getX();
                        double ez = hopEnd.getZ() + 0.5 - player.getZ();
                        double el = Math.sqrt(ex * ex + ez * ez);
                        if (el > 0.001) {
                           sprintHop = true;
                           jump = true;
                           dx = ex / el;
                           dz = ez / el;
                           sprintHopAirTarget = hopEnd;
                        }
                     }
                  }

                  if (takeover) {
                     float offset = !hanging && !parkour && !jumpUp && !sprintHop ? Configs.Go.GO_VIEW_OFFSET.getIntegerValue() : 0.0F;
                     float targetYaw = (float)Math.toDegrees(Math.atan2(-dx, dz)) + offset;
                     if (Math.abs(Mth.wrapDegrees(targetYaw - player.getYRot())) >= 1.0F) {
                        player.setYRot(targetYaw);
                     }
                  } else if (!parkour && !jumpUp && !sprintHop) {
                     if (!Float.isNaN(preJumpYaw)) {
                        restorePreJumpYaw(player, takeover);
                     }
                  } else {
                     if (Float.isNaN(preJumpYaw)) {
                        preJumpYaw = player.getYRot();
                     }

                     player.setYRot((float)Math.toDegrees(Math.atan2(-dx, dz)));
                  }

                  float yawRad = player.getYRot() * (float) (Math.PI / 180.0);
                  float sin = (float)Math.sin(yawRad);
                  float cos = (float)Math.cos(yawRad);
                  float strafe = (float)(dx * cos + dz * sin);
                  float forward = (float)(dz * cos - dx * sin);
                  float maxSpeed = (float)Configs.Go.GO_MAX_SPEED.getDoubleValue();
                  float speedFactor = Math.min(1.0F, maxSpeed * (GoPathfinder.sprintCost() / 20.0F));
                  strafe *= speedFactor;
                  forward *= speedFactor;
                  if (hanging && (wp.getX() != player.getBlockX() || wp.getZ() != player.getBlockZ())) {
                     strafe *= 0.4F;
                     forward *= 0.4F;
                  }

                  if (parkour && parkourGap < 0) {
                     strafe = 0.0F;
                     forward = 0.0F;
                  }

                  boolean sprint;
                  if (parkour && parkourGap >= 1) {
                     sprint = parkourGap >= 2;
                  } else if (parkour) {
                     sprint = false;
                  } else {
                     sprint = Configs.Go.GO_FORCE_SPRINT.getBooleanValue();
                  }

                  writeInput(player, strafe, forward, jump, sprint, shift);
               }
            }
         }
      }
   }

   private static void parkourAirControl(LocalPlayer player, boolean takeover, boolean shift) {
      restorePreJumpYaw(player, takeover);
      double tx = parkourAirTarget.getX() + 0.5 - player.getX();
      double tz = parkourAirTarget.getZ() + 0.5 - player.getZ();
      if (takeover) {
         float offset = Configs.Go.GO_VIEW_OFFSET.getIntegerValue();
         float targetYaw = (float)Math.toDegrees(Math.atan2(-tx, tz)) + offset;
         if (Math.abs(Mth.wrapDegrees(targetYaw - player.getYRot())) >= 1.0F) {
            player.setYRot(targetYaw);
         }
      }

      double y = player.getY();
      double vy = player.getDeltaMovement().y;
      int ticksLeft = 1;

      for (int targetY = parkourAirTarget.getY(); y > targetY && ticksLeft < 40; ticksLeft++) {
         vy = (vy - 0.08) * 0.98;
         y += vy;
      }

      double accel = player.isSprinting() ? 0.026 : 0.02;
      double inX = (tx / ticksLeft - player.getDeltaMovement().x * 0.91) / accel;
      double inZ = (tz / ticksLeft - player.getDeltaMovement().z * 0.91) / accel;
      double mag = Math.sqrt(inX * inX + inZ * inZ);
      if (mag > 1.0) {
         inX /= mag;
         inZ /= mag;
      }

      float yawRad = player.getYRot() * (float) (Math.PI / 180.0);
      float sin = (float)Math.sin(yawRad);
      float cos = (float)Math.cos(yawRad);
      float strafe = (float)(inX * cos + inZ * sin);
      float forward = (float)(inZ * cos - inX * sin);
      writeInput(player, strafe, forward, false, false, shift);
   }

   private static boolean hangingOnClimbable(LocalPlayer player) {
      ClientLevel level = Minecraft.getInstance().level;
      return level != null && level.getBlockState(player.blockPosition()).is(BlockTags.CLIMBABLE);
   }

   @Nullable
   private static double[] attachmentWall(LocalPlayer player) {
      ClientLevel level = Minecraft.getInstance().level;
      if (level == null) {
         return null;
      }

      int x = player.getBlockX();
      int y = player.getBlockY();
      int z = player.getBlockZ();
      int[][] dirs = new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

      for (int[] d : dirs) {
         BlockPos p = new BlockPos(x + d[0], y, z + d[1]);
         if (!level.getBlockState(p).getCollisionShape(level, p).isEmpty()) {
            return new double[]{d[0], d[1]};
         }
      }

      return null;
   }

   private static boolean floorAheadMissing(LocalPlayer player, double dx, double dz) {
      ClientLevel level = Minecraft.getInstance().level;
      if (level == null) {
         return false;
      }

      BlockPos probe = BlockPos.containing(player.getX() + dx * 0.4, player.getBlockY() - 1, player.getZ() + dz * 0.4);
      return level.getBlockState(probe).getCollisionShape(level, probe).isEmpty();
   }

   private static void restorePreJumpYaw(LocalPlayer player, boolean takeover) {
      if (!takeover && !Float.isNaN(preJumpYaw)) {
         player.setYRot(preJumpYaw);
         preJumpYaw = Float.NaN;
      }
   }

   @Nullable
   private static BlockPos sprintHopTarget(LocalPlayer player) {
      if (!Configs.Go.GO_FORCE_SPRINT.getBooleanValue()) {
         return null;
      }

      List<BlockPos> path = GoManager.INSTANCE.getPath();
      int i = GoManager.INSTANCE.getWaypointIndex();
      int n = path.size();
      if (i + 1 >= n) {
         return null;
      }

      BlockPos a = path.get(i);
      BlockPos b = path.get(i + 1);
      if (b.getY() != a.getY()) {
         return null;
      }

      int dx = Integer.compare(b.getX(), a.getX());
      int dz = Integer.compare(b.getZ(), a.getZ());
      if (dx == 0 && dz == 0) {
         return null;
      }

      BlockPos far = b;

      for (int k = i + 2; k < n; k++) {
         BlockPos c = path.get(k);
         if (c.getY() != a.getY() || Integer.compare(c.getX(), far.getX()) != dx || Integer.compare(c.getZ(), far.getZ()) != dz) {
            break;
         }

         far = c;
      }

      double dirLen = Math.sqrt((double)dx * dx + (double)dz * dz);
      double proj = (far.getX() + 0.5 - player.getX()) * dx + (far.getZ() + 0.5 - player.getZ()) * dz;
      return proj <= 5.0 * dirLen ? null : far;
   }

   private static void sprintHopAirControl(LocalPlayer player, boolean takeover, boolean shift) {
      restorePreJumpYaw(player, takeover);
      BlockPos target = sprintHopAirTarget;
      double dx = target.getX() + 0.5 - player.getX();
      double dz = target.getZ() + 0.5 - player.getZ();
      double dist = Math.sqrt(dx * dx + dz * dz);
      if (dist > 0.001) {
         dx /= dist;
         dz /= dist;
         if (takeover) {
            float offset = Configs.Go.GO_VIEW_OFFSET.getIntegerValue();
            float targetYaw = (float)Math.toDegrees(Math.atan2(-dx, dz)) + offset;
            if (Math.abs(Mth.wrapDegrees(targetYaw - player.getYRot())) >= 1.0F) {
               player.setYRot(targetYaw);
            }
         }

         float yawRad = player.getYRot() * (float) (Math.PI / 180.0);
         float sin = (float)Math.sin(yawRad);
         float cos = (float)Math.cos(yawRad);
         float strafe = (float)(dx * cos + dz * sin);
         float forward = (float)(dz * cos - dx * sin);
         float maxSpeed = (float)Configs.Go.GO_MAX_SPEED.getDoubleValue();
         float speedFactor = Math.min(1.0F, maxSpeed * (GoPathfinder.sprintCost() / 20.0F));
         strafe *= speedFactor;
         forward *= speedFactor;
         writeInput(player, strafe, forward, false, true, shift);
      } else {
         writeInput(player, 0.0F, 0.0F, false, true, shift);
      }
   }

   public static boolean isContainerUiOpen(LocalPlayer player) {
      Minecraft mc = Minecraft.getInstance();
      //#if MC > 260100
      //$$ return mc.gui.screen() instanceof AbstractContainerScreen || player.containerMenu != player.inventoryMenu;
      //#else
      return mc.screen instanceof AbstractContainerScreen || player.containerMenu != player.inventoryMenu;
      //#endif
   }

   static void writeInput(LocalPlayer player, float strafe, float forward, boolean jump, boolean sprint, boolean shift) {
      player.input.keyPresses = new Input(forward > 0.05F, forward < -0.05F, strafe > 0.05F, strafe < -0.05F, jump, shift, sprint);
      ((ClientInputAccessor)player.input).printer$setMoveVector(new Vec2(strafe, forward));
      lastWritten = player.input.keyPresses;
      if (!writtenInputs.contains(player.input)) {
         writtenInputs.add(player.input);
      }

      if (player.onGround() || player.isInWater()) {
         selfJumpAirborne = jump;
      }
   }

   private static void clearStaleInput(LocalPlayer player) {
      for (ClientInput written : writtenInputs) {
         if (written != player.input) {
            written.keyPresses = new Input(false, false, false, false, false, false, false);
            ((ClientInputAccessor)written).printer$setMoveVector(Vec2.ZERO);
         }
      }

      writtenInputs.clear();
      if (lastWritten != null && lastWritten.equals(player.input.keyPresses)) {
         player.input.keyPresses = new Input(false, false, false, false, false, false, false);
         ((ClientInputAccessor)player.input).printer$setMoveVector(Vec2.ZERO);
         lastWritten = null;
      }
   }
}

//#endif
