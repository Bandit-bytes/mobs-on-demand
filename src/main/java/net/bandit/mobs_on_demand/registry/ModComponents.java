package net.bandit.mobs_on_demand.registry;

import net.bandit.mobs_on_demand.MobsOnDemandMod;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;

public final class ModComponents {
    /**
     * Display name of the crated mob, captured at pack time (e.g. "Librarian" or a name tag).
     * The mob itself lives in vanilla {@code minecraft:entity_data}, which the game's data fixers upgrade across versions.
     */
    public static final DataComponentType<Component> CRATE_LABEL = Registry.register(
            BuiltInRegistries.DATA_COMPONENT_TYPE,
            MobsOnDemandMod.id("crate_label"),
            DataComponentType.<Component>builder()
                    .persistent(ComponentSerialization.CODEC)
                    .networkSynchronized(ComponentSerialization.STREAM_CODEC)
                    .build());

    private ModComponents() {
    }

    public static void init() {
    }
}
