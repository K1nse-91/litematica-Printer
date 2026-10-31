package me.K1nse_.litematica.printer.printer.verifier;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.Disconnect;

public final class VerifierRegistry {
   private static final Map<UUID, OptimizedSchematicVerifier> BY_HASH = new ConcurrentHashMap<>();

   private VerifierRegistry() {
   }

   public static void init() {
      ClientPlayConnectionEvents.DISCONNECT.register((Disconnect)(handler, client) -> {
         for (OptimizedSchematicVerifier verifier : BY_HASH.values()) {
            verifier.stopVerification();
         }

         BY_HASH.clear();
      });
   }

   public static OptimizedSchematicVerifier get(UUID hashId) {
      return hashId == null ? null : BY_HASH.get(hashId);
   }

   public static void put(UUID hashId, OptimizedSchematicVerifier verifier) {
      if (hashId != null && verifier != null) {
         BY_HASH.put(hashId, verifier);
      }
   }

   public static void remove(UUID hashId) {
      if (hashId != null) {
         BY_HASH.remove(hashId);
      }
   }
}
