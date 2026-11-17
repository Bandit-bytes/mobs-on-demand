package net.bandit.mobs_on_demand.crate;

import net.bandit.mobs_on_demand.MobsOnDemandMod;
import net.bandit.mobs_on_demand.item.ModItems;
import net.bandit.mobs_on_demand.registry.ModComponents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.TypedEntityData;
import net.minecraft.world.level.storage.TagValueOutput;

public final class MobCrates {
    private MobCrates() {
    }

    public static boolean isEmptyCrate(ItemStack stack) {
        return stack.is(ModItems.MOB_CRATE) && !MobCrateItem.isFilled(stack);
    }

    /** Builds a filled crate holding a snapshot of {@code mob}. Does not remove the mob from the world. */
    public static ItemStack createFilled(Mob mob) {
        CompoundTag data;
        try (ProblemReporter.ScopedCollector reporter = new ProblemReporter.ScopedCollector(mob.problemPath(), MobsOnDemandMod.LOGGER)) {
            TagValueOutput output = TagValueOutput.createWithContext(reporter, mob.registryAccess());
            mob.saveWithoutId(output);
            data = output.buildResult();
        }
        ItemStack crate = new ItemStack(ModItems.MOB_CRATE);
        crate.set(DataComponents.ENTITY_DATA, TypedEntityData.of(mob.getType(), data));
        crate.set(ModComponents.CRATE_LABEL, mob.getName());
        crate.set(DataComponents.MAX_STACK_SIZE, 1);
        return crate;
    }
}
