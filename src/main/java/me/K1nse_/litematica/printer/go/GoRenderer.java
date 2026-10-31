//#if MC >= 260100
package me.K1nse_.litematica.printer.go;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class GoRenderer {
   private static final int PATH_COLOR = -12716739;
   private static final int GOAL_COLOR = -10179;
   private static final int CANDIDATE_COLOR = -10179;
   private static final int SELECTED_COLOR = -12716739;
   private static final int MAX_CANDIDATE_BOXES = 256;

   private GoRenderer() {
   }

   public static void emitGizmos() {
      Minecraft mc = Minecraft.getInstance();
      LocalPlayer player = mc.player;
      if (player != null && mc.level != null) {
         BlockPos selected = GoManager.INSTANCE.isActive() ? GoManager.INSTANCE.getMultiGoalCurrentTarget() : null;
         if (selected == null) {
            selected = AutoWalkScanner.INSTANCE.getSelectedTarget();
         }

         int drawn = 0;

         for (BlockPos candidate : AutoWalkScanner.INSTANCE.getDispatchedCandidates()) {
            if (!candidate.equals(selected)) {
               if (++drawn > 256) {
                  break;
               }

               Gizmos.cuboid(new AABB(candidate), GizmoStyle.stroke(-10179, 1.0F));
            }
         }

         if (selected != null) {
            Gizmos.cuboid(new AABB(selected), GizmoStyle.stroke(-12716739, 2.0F));
         }

         if (GoManager.INSTANCE.isActive()) {
            BlockPos goal = GoManager.INSTANCE.getGoal();
            if (goal != null) {
               if (!goal.equals(selected)) {
                  Gizmos.cuboid(new AABB(goal), GizmoStyle.stroke(-10179, 2.0F));
               }
            } else {
               BlockPos waiting = AutoWalkScanner.INSTANCE.getWaitingTarget();
               if (waiting != null && !waiting.equals(selected)) {
                  Gizmos.cuboid(new AABB(waiting), GizmoStyle.stroke(-10179, 2.0F));
               }
            }

            List<BlockPos> path = GoManager.INSTANCE.getPath();
            int index = GoManager.INSTANCE.getWaypointIndex();
            Vec3 prev = GoManager.INSTANCE.navPosition();
            if (prev != null) {
               for (int i = index; i < path.size(); i++) {
                  Vec3 next = center(path.get(i));
                  Gizmos.line(prev, next, -12716739);
                  prev = next;
               }
            }
         }
      }
   }

   private static Vec3 center(BlockPos pos) {
      return new Vec3(pos.getX() + 0.5, pos.getY() + 0.05, pos.getZ() + 0.5);
   }
}

//#endif
