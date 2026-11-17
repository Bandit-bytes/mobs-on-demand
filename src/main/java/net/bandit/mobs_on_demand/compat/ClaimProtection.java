package net.bandit.mobs_on_demand.compat;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * Decides whether a player may pull a given mob, respecting land-claim mods when present.
 * Integrations are referenced only through their own classes so they're never loaded when the mod is absent.
 */
public final class ClaimProtection {
    private static final boolean FTB_CHUNKS_LOADED = FabricLoader.getInstance().isModLoaded("ftbchunks");

    private ClaimProtection() {
    }

    public static boolean canAttract(ServerPlayer player, Entity target) {
        if (FTB_CHUNKS_LOADED) {
            return FtbChunksCompat.canAttract(player, target);
        }
        return true;
    }
}
