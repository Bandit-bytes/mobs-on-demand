package net.bandit.mobs_on_demand.compat;

import dev.ftb.mods.ftbchunks.api.ClaimedChunk;
import dev.ftb.mods.ftbchunks.api.FTBChunksAPI;
import dev.ftb.mods.ftbchunks.api.ChunkTeamData;
import dev.ftb.mods.ftblibrary.math.ChunkDimPos;
import net.bandit.mobs_on_demand.config.ModConfig;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/** Only loaded when FTB Chunks is installed. */
final class FtbChunksCompat {
    private FtbChunksCompat() {
    }

    /** Unclaimed chunks are open; claimed chunks allow the owning team and, if configured, its allies. */
    static boolean canInteract(ServerPlayer player, Entity target) {
        ClaimedChunk claimedChunk = FTBChunksAPI.api().getManager().getChunk(new ChunkDimPos(target));
        if (claimedChunk == null) {
            return true;
        }
        ChunkTeamData owner = claimedChunk.getTeamData();
        return owner.isTeamMember(player.getUUID())
                || (ModConfig.get().ftbAllowAllies && owner.isAlly(player.getUUID()));
    }
}
