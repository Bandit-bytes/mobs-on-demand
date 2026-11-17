package net.bandit.mobs_on_demand.client;

import net.bandit.mobs_on_demand.registry.ModMenus;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.gui.screens.MenuScreens;

public class MobsOnDemandClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        MenuScreens.register(ModMenus.ORDER_BOARD, OrderBoardScreen::new);
        CrateInspectHud.register();
        ClientShop.register();
    }
}
