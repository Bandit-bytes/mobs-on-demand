package net.bandit.mobs_on_demand.board;

import net.bandit.mobs_on_demand.registry.ModBlocks;
import net.bandit.mobs_on_demand.registry.ModMenus;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Board menu: 5 take-only output slots, the player inventory, and synced order data.
 * Ordering uses the vanilla menu-button packet with the offer's registry id as the button id.
 */
public class OrderBoardMenu extends AbstractContainerMenu {
    public static final int DATA_QUEUE_SIZE = 0;
    public static final int DATA_REQUIRE_CRATE = 1;
    /** Then (offer registry id, seconds left) per queued order. */
    public static final int DATA_ORDERS_START = 2;
    public static final int DATA_SIZE = DATA_ORDERS_START + 2 * OrderBoardBlockEntity.MAX_QUEUE;

    public static final int OUTPUT_X = 149;
    public static final int OUTPUT_Y = 103;
    public static final int INVENTORY_X = 44;
    public static final int INVENTORY_Y = 141;

    private final Container output;
    private final ContainerData data;
    private final ContainerLevelAccess access;
    private final OrderBoardBlockEntity board; // null on the client

    /** Client-side constructor. */
    public OrderBoardMenu(int containerId, Inventory inventory) {
        this(containerId, inventory, new SimpleContainer(OrderBoardBlockEntity.OUTPUT_SLOTS),
                new SimpleContainerData(DATA_SIZE), ContainerLevelAccess.NULL, null);
    }

    public OrderBoardMenu(int containerId, Inventory inventory, Container output, ContainerData data,
                          ContainerLevelAccess access, OrderBoardBlockEntity board) {
        super(ModMenus.ORDER_BOARD, containerId);
        this.output = output;
        this.data = data;
        this.access = access;
        this.board = board;

        for (int i = 0; i < OrderBoardBlockEntity.OUTPUT_SLOTS; i++) {
            addSlot(new Slot(output, i, OUTPUT_X + i * 18, OUTPUT_Y) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return false;
                }
            });
        }
        addStandardInventorySlots(inventory, INVENTORY_X, INVENTORY_Y);
        addDataSlots(data);
    }

    public int queueSize() {
        return data.get(DATA_QUEUE_SIZE);
    }

    public boolean requiresCrate() {
        return data.get(DATA_REQUIRE_CRATE) != 0;
    }

    public int queuedOfferId(int index) {
        return data.get(DATA_ORDERS_START + index * 2);
    }

    public int queuedSecondsLeft(int index) {
        return data.get(DATA_ORDERS_START + index * 2 + 1);
    }

    public boolean outputFull() {
        for (int i = 0; i < OrderBoardBlockEntity.OUTPUT_SLOTS; i++) {
            if (output.getItem(i).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean clickMenuButton(Player player, int offerId) {
        if (board != null && player instanceof ServerPlayer serverPlayer) {
            Component message = board.placeOrder(serverPlayer, offerId);
            serverPlayer.sendOverlayMessage(message);
            return true;
        }
        return false;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (index >= OrderBoardBlockEntity.OUTPUT_SLOTS || !slot.hasItem()) {
            return ItemStack.EMPTY; // nothing can be shift-clicked into the board
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (!moveItemStackTo(stack, OrderBoardBlockEntity.OUTPUT_SLOTS, slots.size(), true)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return original;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, ModBlocks.ORDER_BOARD);
    }
}
