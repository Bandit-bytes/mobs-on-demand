package net.bandit.mobs_on_demand.registry;

import net.bandit.mobs_on_demand.MobsOnDemandMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;

public final class ModTags {
    /** Mobs that fit in a Mob Crate. Data packs can extend or replace this. */
    public static final TagKey<EntityType<?>> CAPTURABLE = TagKey.create(Registries.ENTITY_TYPE, MobsOnDemandMod.id("capturable"));

    private ModTags() {
    }
}
