package net.bandit.mobs_on_demand.client;

import net.bandit.mobs_on_demand.delivery.DeliveryOffer;
import net.bandit.mobs_on_demand.delivery.ShopCatalog;
import net.bandit.mobs_on_demand.network.ShopSyncPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import java.util.List;

/** The shop as last sent by the server. Indices match the server's list, which the order button relies on. */
public final class ClientShop {
    private static List<ShopCatalog.Entry> entries = List.of();

    private ClientShop() {
    }

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(ShopSyncPayload.TYPE, (payload, context) ->
                entries = ShopCatalog.decode(payload.shop(), context.client().level.registryAccess()));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> entries = List.of());
    }

    public static List<ShopCatalog.Entry> entries() {
        return entries;
    }

    public static DeliveryOffer get(int index) {
        return index >= 0 && index < entries.size() ? entries.get(index).offer() : null;
    }
}
