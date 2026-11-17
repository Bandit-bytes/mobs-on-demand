package net.bandit.mobs_on_demand.board;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.bandit.mobs_on_demand.MobsOnDemandMod;
import net.bandit.mobs_on_demand.config.ModConfig;
import net.bandit.mobs_on_demand.delivery.DeliveryOffer;
import net.bandit.mobs_on_demand.delivery.ShopCatalog;
import net.bandit.mobs_on_demand.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Holds the order queue and the delivered crates. Orders are fulfilled one after another; each order's arrival time
 * is fixed in world time when it's placed, so deliveries keep progressing while the chunk is unloaded.
 * <p>
 * Acts as an extract-only container so hoppers can pull delivered crates out.
 */
public class OrderBoardBlockEntity extends BlockEntity implements Container, MenuProvider {
    public static final int OUTPUT_SLOTS = 5;
    public static final int MAX_QUEUE = 5;

    /**
     * @param offer  shop offer id
     * @param refund what the player paid (empty for free creative orders); returned if the order can't be fulfilled,
     *               so refunds stay exact even if the shop's prices change later
     */
    private record Order(String offer, long readyAt, List<ItemStack> refund) {
        static final Codec<Order> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("offer").forGetter(Order::offer),
                Codec.LONG.fieldOf("ready_at").forGetter(Order::readyAt),
                ItemStack.CODEC.listOf().optionalFieldOf("refund", List.of()).forGetter(Order::refund)
        ).apply(i, Order::new));
    }

    private final NonNullList<ItemStack> output = NonNullList.withSize(OUTPUT_SLOTS, ItemStack.EMPTY);
    private final List<Order> queue = new ArrayList<>();

    /** Synced to open menus: see {@link OrderBoardMenu} for the layout. */
    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            if (index == OrderBoardMenu.DATA_QUEUE_SIZE) {
                return queue.size();
            }
            if (index == OrderBoardMenu.DATA_REQUIRE_CRATE) {
                return ModConfig.get().ordersRequireCrate ? 1 : 0;
            }
            int orderIndex = (index - OrderBoardMenu.DATA_ORDERS_START) / 2;
            if (orderIndex >= queue.size() || level == null) {
                return -1;
            }
            Order order = queue.get(orderIndex);
            if ((index - OrderBoardMenu.DATA_ORDERS_START) % 2 == 0) {
                return ShopCatalog.indexOf(order.offer());
            }
            long ticksLeft = Math.max(0, order.readyAt() - level.getGameTime());
            return (int) Math.min(Short.MAX_VALUE, (ticksLeft + 19) / 20); // container data is sent as shorts
        }

        @Override
        public void set(int index, int value) {
        }

        @Override
        public int getCount() {
            return OrderBoardMenu.DATA_SIZE;
        }
    };

    public OrderBoardBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ORDER_BOARD, pos, state);
    }

    /** Tries to order the shop offer at {@code offerIndex}. Returns the message to show the player. */
    public Component placeOrder(ServerPlayer player, int offerIndex) {
        ShopCatalog.Entry entry = ShopCatalog.byIndex(offerIndex);
        if (entry == null) {
            return Component.translatable("message.mobs_on_demand.unknown_offer");
        }
        if (queue.size() >= MAX_QUEUE) {
            return Component.translatable("message.mobs_on_demand.queue_full");
        }
        DeliveryOffer value = entry.offer();
        boolean free = player.hasInfiniteMaterials();
        boolean requireCrate = ModConfig.get().ordersRequireCrate && !free;
        if (!free && !value.takePayment(player.getInventory(), requireCrate)) {
            return Component.translatable("message.mobs_on_demand.cant_afford");
        }

        long now = level.getGameTime();
        long start = queue.isEmpty() ? now : Math.max(now, queue.getLast().readyAt());
        List<ItemStack> refund = free ? List.of() : value.refund(requireCrate);
        queue.add(new Order(entry.id(), start + value.deliveryTicks(), refund));
        setChanged();
        level.playSound(null, worldPosition, SoundEvents.VILLAGER_WORK_CARTOGRAPHER, SoundSource.BLOCKS, 1.0F, 1.0F);
        return Component.translatable("message.mobs_on_demand.order_placed", value.displayName());
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, OrderBoardBlockEntity board) {
        if (board.queue.isEmpty() || level.getGameTime() < board.queue.getFirst().readyAt()) {
            return;
        }
        int slot = board.output.indexOf(ItemStack.EMPTY);
        if (slot < 0) {
            return; // wait until there's room
        }
        Order order = board.queue.removeFirst();
        board.setChanged();

        DeliveryOffer offer = ShopCatalog.get(order.offer());
        Optional<ItemStack> crate = offer == null ? Optional.empty() : offer.createCrate((ServerLevel) level, pos);
        if (crate.isEmpty()) {
            // The offer was removed from the shop (or its mob can't be created): give the payment back.
            MobsOnDemandMod.LOGGER.warn("Refunding order for missing or invalid shop offer '{}'", order.offer());
            board.dropRefund(order);
            return;
        }
        board.output.set(slot, crate.get());
        level.playSound(null, pos, SoundEvents.BUNDLE_DROP_CONTENTS, SoundSource.BLOCKS, 1.0F, 1.0F);
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state); // drops the output slots
        if (level == null) {
            return;
        }
        queue.forEach(this::dropRefund);
        queue.clear();
    }

    private void dropRefund(Order order) {
        for (ItemStack stack : order.refund()) {
            Containers.dropItemStack(level, worldPosition.getX() + 0.5, worldPosition.getY() + 1, worldPosition.getZ() + 0.5, stack.copy());
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, this.output);
        output.store("orders", Order.CODEC.listOf(), queue);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        output.clear();
        ContainerHelper.loadAllItems(input, output);
        queue.clear();
        input.read("orders", Order.CODEC.listOf()).ifPresent(queue::addAll);
    }

    // MenuProvider

    @Override
    public Component getDisplayName() {
        return getBlockState().getBlock().getName();
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new OrderBoardMenu(containerId, inventory, this, data, ContainerLevelAccess.create(level, worldPosition), this);
    }

    // Container: extract-only output slots

    @Override
    public int getContainerSize() {
        return OUTPUT_SLOTS;
    }

    @Override
    public boolean isEmpty() {
        return output.stream().allMatch(ItemStack::isEmpty);
    }

    @Override
    public ItemStack getItem(int slot) {
        return output.get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack removed = ContainerHelper.removeItem(output, slot, amount);
        if (!removed.isEmpty()) {
            setChanged();
        }
        return removed;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(output, slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        output.set(slot, stack);
        setChanged();
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return false;
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(this, player);
    }

    @Override
    public void clearContent() {
        output.clear();
    }
}
