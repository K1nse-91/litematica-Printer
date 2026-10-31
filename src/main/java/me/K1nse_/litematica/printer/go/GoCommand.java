//#if MC >= 260100
package me.K1nse_.litematica.printer.go;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

public final class GoCommand {
   private GoCommand() {
   }

   public static void register() {
      ClientCommandRegistrationCallback.EVENT
         .register(
            (ClientCommandRegistrationCallback)(dispatcher, registryAccess) -> dispatcher.register(
               (LiteralArgumentBuilder)((LiteralArgumentBuilder)ClientCommands.literal("go").then(ClientCommands.literal("stop").executes(ctx -> {
                  GoManager.INSTANCE.stop("已手动停止寻路");
                  return 1;
               }))).then(ClientCommands.argument("target", StringArgumentType.greedyString()).suggests(GoCommand::suggestTargets).executes(ctx -> {
                  handle(StringArgumentType.getString(ctx, "target"));
                  return 1;
               }))
            )
         );
   }

   private static CompletableFuture<Suggestions> suggestTargets(CommandContext<FabricClientCommandSource> ctx, SuggestionsBuilder builder) {
      Minecraft mc = Minecraft.getInstance();
      LocalPlayer self = mc.player;
      if (mc.level != null && self != null) {
         String remaining = builder.getRemaining().trim().toLowerCase();
         if (remaining.isEmpty() || remaining.charAt(0) != '~' && (remaining.charAt(0) < '0' || remaining.charAt(0) > '9')) {
            List<AbstractClientPlayer> players = new ArrayList<>(mc.level.players());
            players.removeIf(px -> px.getUUID().equals(self.getUUID()));
            players.sort(Comparator.comparingDouble(px -> px.distanceToSqr(self)));

            for (AbstractClientPlayer p : players) {
               String name = p.getName().getString();
               if (name.toLowerCase().startsWith(remaining)) {
                  builder.suggest(name);
               }
            }

            return builder.buildFuture();
         } else {
            return builder.buildFuture();
         }
      } else {
         return builder.buildFuture();
      }
   }

   private static void handle(String arg) {
      Minecraft mc = Minecraft.getInstance();
      LocalPlayer player = mc.player;
      if (player != null && mc.level != null) {
         String[] parts = arg.trim().split("\\s+");
         if (parts.length == 3) {
            Integer x = parseCoord(parts[0], player.getX());
            Integer y = parseCoord(parts[1], player.getY());
            Integer z = parseCoord(parts[2], player.getZ());
            if (x != null && y != null && z != null) {
               GoManager.INSTANCE.go(new BlockPos(x, y, z));
            } else {
               chat("§c[寻路] 坐标格式无效: " + arg);
            }
         } else if (parts.length == 1 && !parts[0].isEmpty()) {
            AbstractClientPlayer target = GoManager.INSTANCE.findPlayerByName(parts[0]);
            if (target == null) {
               chat("§c[寻路] 找不到（或无法唯一确定）玩家: " + parts[0]);
            } else {
               GoManager.INSTANCE.go(target);
            }
         } else {
            chat("§e[寻路] 用法: /go <x y z> 或 /go <玩家名> 或 /go stop");
         }
      } else {
         chat("§c[寻路] 未进入世界");
      }
   }

   @Nullable
   private static Integer parseCoord(String token, double relativeTo) {
      token = token.trim();
      if (token.equals("~")) {
         return (int)Math.floor(relativeTo);
      }

      if (token.startsWith("~")) {
         try {
            return (int)Math.floor(relativeTo + Double.parseDouble(token.substring(1)));
         } catch (NumberFormatException e) {
            return null;
         }
      } else {
         try {
            return Integer.parseInt(token);
         } catch (NumberFormatException e) {
            return null;
         }
      }
   }

   private static void chat(String text) {
      LocalPlayer p = Minecraft.getInstance().player;
      if (p != null) {
         p.sendSystemMessage(Component.literal(text));
      }
   }
}

//#endif
