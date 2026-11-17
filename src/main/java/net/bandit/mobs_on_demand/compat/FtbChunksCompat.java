package net.bandit.mobs_on_demand.compat;

import dev.ftb.mods.ftbchunks.api.ClaimedChunk;
import dev.ftb.mods.ftbchunks.api.FTBChunksAPI;
import dev.ftb.mods.ftblibrary.math.ChunkDimPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/** Only loaded when FTB Chunks is installed. */
final class FtbChunksCompat {
    private FtbChunksCompat() {
    }

    /** Unclaimed chunks are open; claimed chunks only allow members of the owning team. */
    static boolean canAttract(ServerPlayer player, Entity target) {
        ClaimedChunk claimedChunk = FTBChunksAPI.api().getManager().getChunk(new ChunkDimPos(target));
        return claimedChunk == null || claimedChunk.getTeamData().isTeamMember(player.getUUID());
    }
}
