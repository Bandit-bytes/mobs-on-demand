package net.bandit.mobs_on_demand.compat;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * Decides whether a player may capture or release a mob at a location, respecting land-claim mods when present.
 * Integrations are referenced only through their own classes so they're never loaded when the mod is absent.
 */
public final class ClaimProtection {
    private static final boolean FTB_CHUNKS_LOADED = FabricLoader.getInstance().isModLoaded("ftbchunks");

    private ClaimProtection() {
    }

    public static boolean canInteract(ServerPlayer player, Entity target) {
        if (FTB_CHUNKS_LOADED) {
            return FtbChunksCompat.canInteract(player, target);
        }
        return true;
    }
}
