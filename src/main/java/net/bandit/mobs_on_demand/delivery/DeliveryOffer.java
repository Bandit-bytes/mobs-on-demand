package net.bandit.mobs_on_demand.delivery;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.bandit.mobs_on_demand.crate.MobCrates;
import net.bandit.mobs_on_demand.item.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.component.TypedEntityData;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * One Ranch Order Board offer, from {@code data/<namespace>/mobs_on_demand/shop/<name>.json}.
 *
 * @param enabled         false hides the offer (a data pack can override a default offer's file with this)
 * @param sort            shop position; lower first, ties by id
 * @param entity          mob to deliver
 * @param entityData      optional NBT merged into the freshly spawned mob (e.g. a villager profession)
 * @param name            optional catalog name; defaults to the entity's name
 * @param icon            optional catalog icon; defaults to the entity's spawn egg
 * @param cost            items taken from the player's inventory
 * @param deliverySeconds time from the start of this order until the crate arrives
 */
public record DeliveryOffer(boolean enabled, int sort, EntityType<?> entity, Optional<CompoundTag> entityData, Optional<Component> name,
                            Optional<Item> icon, List<Cost> cost, int deliverySeconds) {

    public record Cost(Ingredient ingredient, int count) {
        public static final Codec<Cost> CODEC = RecordCodecBuilder.create(i -> i.group(
                Ingredient.CODEC.fieldOf("ingredient").forGetter(Cost::ingredient),
                ExtraCodecs.POSITIVE_INT.optionalFieldOf("count", 1).forGetter(Cost::count)
        ).apply(i, Cost::new));

        /** A representative stack for display and refunds. */
        public ItemStack displayStack() {
            return ingredient.items().findFirst().map(item -> new ItemStack(item, count)).orElse(ItemStack.EMPTY);
        }
    }

    public static final Codec<DeliveryOffer> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.BOOL.optionalFieldOf("enabled", true).forGetter(DeliveryOffer::enabled),
            Codec.INT.optionalFieldOf("sort", 0).forGetter(DeliveryOffer::sort),
            BuiltInRegistries.ENTITY_TYPE.byNameCodec().fieldOf("entity").forGetter(DeliveryOffer::entity),
            CompoundTag.CODEC.optionalFieldOf("entity_data").forGetter(DeliveryOffer::entityData),
            ComponentSerialization.CODEC.optionalFieldOf("name").forGetter(DeliveryOffer::name),
            BuiltInRegistries.ITEM.byNameCodec().optionalFieldOf("icon").forGetter(DeliveryOffer::icon),
            Cost.CODEC.listOf().fieldOf("cost").forGetter(DeliveryOffer::cost),
            ExtraCodecs.POSITIVE_INT.fieldOf("delivery_seconds").forGetter(DeliveryOffer::deliverySeconds)
    ).apply(i, DeliveryOffer::new));

    public int deliveryTicks() {
        return deliverySeconds * 20;
    }

    public Component displayName() {
        return name.orElseGet(entity::getDescription);
    }

    public ItemStack iconStack() {
        return icon.map(ItemStack::new)
                .or(() -> SpawnEggItem.byId(entity).map(ItemStack::new))
                .orElseGet(() -> new ItemStack(ModItems.MOB_CRATE));
    }

    /** True if {@code inventory} holds the full price (and an empty crate when required). */
    public boolean canAfford(Inventory inventory, boolean requireCrate) {
        return simulatePayment(inventory, requireCrate) != null;
    }

    /** Removes the price from {@code inventory}. Returns false and changes nothing if it can't be paid. */
    public boolean takePayment(Inventory inventory, boolean requireCrate) {
        List<ItemStack> remaining = simulatePayment(inventory, requireCrate);
        if (remaining == null) {
            return false;
        }
        NonNullList<ItemStack> items = inventory.getNonEquipmentItems();
        for (int slot = 0; slot < items.size(); slot++) {
            items.set(slot, remaining.get(slot));
        }
        inventory.setChanged();
        return true;
    }

    /** Pays from copies of the inventory so overlapping costs can't double-count. Null if unaffordable. */
    private List<ItemStack> simulatePayment(Inventory inventory, boolean requireCrate) {
        List<ItemStack> copies = new ArrayList<>();
        for (ItemStack stack : inventory.getNonEquipmentItems()) {
            copies.add(stack.copy());
        }
        for (Cost entry : cost) {
            int needed = entry.count();
            for (ItemStack stack : copies) {
                if (needed == 0) {
                    break;
                }
                if (!stack.isEmpty() && entry.ingredient().test(stack)) {
                    int taken = Math.min(needed, stack.getCount());
                    stack.shrink(taken);
                    needed -= taken;
                }
            }
            if (needed > 0) {
                return null;
            }
        }
        if (requireCrate) {
            ItemStack crate = copies.stream().filter(MobCrates::isEmptyCrate).findFirst().orElse(null);
            if (crate == null) {
                return null;
            }
            crate.shrink(1);
        }
        return copies;
    }

    /** Items returned when a paid order is cancelled (e.g. the board is broken). */
    public List<ItemStack> refund(boolean includeCrate) {
        List<ItemStack> stacks = new ArrayList<>();
        for (Cost entry : cost) {
            ItemStack stack = entry.displayStack();
            if (!stack.isEmpty()) {
                stacks.add(stack);
            }
        }
        if (includeCrate) {
            stacks.add(new ItemStack(ModItems.MOB_CRATE));
        }
        return stacks;
    }

    /** Spawns (without adding to the world) a fresh mob as natural spawning would, applies {@link #entityData}, and crates it. */
    public Optional<ItemStack> createCrate(ServerLevel level, BlockPos origin) {
        Entity created = entity.create(level, EntitySpawnReason.SPAWN_ITEM_USE);
        if (!(created instanceof Mob mob)) {
            return Optional.empty();
        }
        // Position first: some variants (villager type, frog/cat variant, ...) are picked from the biome.
        mob.snapTo(origin.getX() + 0.5, origin.getY() + 1, origin.getZ() + 0.5, 0.0F, 0.0F);
        mob.finalizeSpawn(level, level.getCurrentDifficultyAt(origin), EntitySpawnReason.SPAWN_ITEM_USE, null);
        mob.ejectPassengers(); // e.g. striders can spawn with a rider
        entityData.ifPresent(tag -> TypedEntityData.of(entity, tag).loadInto(mob));
        mob.setPersistenceRequired();
        return Optional.of(MobCrates.createFilled(mob));
    }
}
