package net.bandit.mobs_on_demand.item;

import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;

/** Calls villagers by setting their brain's walk target, the same way a bell does. */
public class VillagerAttractorItem extends AttractorItem<Villager> {
    public VillagerAttractorItem(Properties properties) {
        super(Villager.class, properties);
    }

    @Override
    protected void attract(Villager villager, Player player) {
        villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(player.blockPosition(), 1.0F, 1));
    }
}
