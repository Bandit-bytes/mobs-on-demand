package net.bandit.mobs_on_demand.network;

import net.bandit.mobs_on_demand.MobsOnDemandMod;
import net.bandit.mobs_on_demand.delivery.ShopCatalog;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Sends the server's shop to clients on join and after {@code /reload}. */
public record ShopSyncPayload(CompoundTag shop) implements CustomPacketPayload {
    public static final Type<ShopSyncPayload> TYPE = new Type<>(MobsOnDemandMod.id("shop_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ShopSyncPayload> STREAM_CODEC =
            ByteBufCodecs.COMPOUND_TAG.<RegistryFriendlyByteBuf>cast().map(ShopSyncPayload::new, ShopSyncPayload::shop);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void init() {
        PayloadTypeRegistry.clientboundPlay().register(TYPE, STREAM_CODEC);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> send(handler.getPlayer(), server));
    }

    public static void send(ServerPlayer player, MinecraftServer server) {
        if (ServerPlayNetworking.canSend(player, TYPE)) {
            ServerPlayNetworking.send(player, new ShopSyncPayload(ShopCatalog.encode(server.registryAccess())));
        }
    }

    public static void sendToAll(MinecraftServer server) {
        server.getPlayerList().getPlayers().forEach(player -> send(player, server));
    }
}
