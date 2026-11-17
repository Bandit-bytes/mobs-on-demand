package net.bandit.mobs_on_demand.item;

import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;

/** Calls farm animals of one type by walking them to the player. */
public class MobAttractorItem<T extends Animal> extends AttractorItem<T> {
    private static final double SPEED = 2.0;

    public MobAttractorItem(Class<T> animalType, Properties properties) {
        super(animalType, properties);
    }

    @Override
    protected void attract(T animal, Player player) {
        animal.getNavigation().moveTo(player, SPEED);
    }
}
