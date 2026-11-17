package net.bandit.mobs_on_demand;

import net.bandit.mobs_on_demand.config.ModConfig;
import net.bandit.mobs_on_demand.crate.MobCrateItem;
import net.bandit.mobs_on_demand.delivery.ShopCatalog;
import net.bandit.mobs_on_demand.item.ModItems;
import net.bandit.mobs_on_demand.registry.ModBlockEntities;
import net.bandit.mobs_on_demand.registry.ModBlocks;
import net.bandit.mobs_on_demand.registry.ModComponents;
import net.bandit.mobs_on_demand.registry.ModMenus;
import net.bandit.mobs_on_demand.network.ShopSyncPayload;
import net.bandit.mobs_on_demand.village.VillageAdditions;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MobsOnDemandMod implements ModInitializer {
    public static final String MOD_ID = "mobs_on_demand";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    @Override
    public void onInitialize() {
        ModConfig.load();
        ModComponents.init();
        ModBlocks.init();
        ModBlockEntities.init();
        ModMenus.init();
        ModItems.init();
        MobCrateItem.registerEvents();
        VillageAdditions.init();
        ShopCatalog.init();
        ShopSyncPayload.init();
        // /reload also re-reads config/mobs_on_demand.json, then pushes the reloaded shop to everyone online.
        ServerLifecycleEvents.START_DATA_PACK_RELOAD.register((server, resources) -> ModConfig.load());
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resources, success) -> ShopSyncPayload.sendToAll(server));
        LOGGER.info("Mob's On Demand initialized");
    }
}
