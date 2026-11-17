package net.bandit.mobs_on_demand;

import net.bandit.mobs_on_demand.item.ModItems;
import net.fabricmc.api.ModInitializer;
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
        ModItems.init();
        LOGGER.info("Mob's On Demand initialized");
    }
}
