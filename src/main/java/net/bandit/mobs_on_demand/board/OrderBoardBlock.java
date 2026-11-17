package net.bandit.mobs_on_demand.board;

import net.bandit.mobs_on_demand.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.EnumMap;
import java.util.Map;

/**
 * Freestanding Ranch Order sign, 2 blocks wide and 3 tall: posts on the bottom row, a notice board above.
 * Every cell opens the master cell's menu; breaking any cell breaks the whole sign (like doors and beds).
 */
public class OrderBoardBlock extends BaseEntityBlock {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<BoardPart> PART = EnumProperty.create("part", BoardPart.class);

    /** Collision/outline boxes for the whole sign facing north, in pixels; X 0..16 is the right cell, 16..32 the left. */
    private static final double[][] BOXES = {
            {3, 0, 6.5, 6, 18, 9.5},    // right post
            {26, 0, 6.5, 29, 18, 9.5},  // left post
            {1, 18, 7, 31, 46, 9},      // board
            {0, 46, 5, 32, 48, 11},     // roof
    };
    private static final Map<Direction, Map<BoardPart, VoxelShape>> SHAPES = buildShapes();

    public OrderBoardBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(PART, BoardPart.LOWER_LEFT));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PART);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    /** Direction from the left column to the right column, for a sign whose front faces {@code facing}. */
    private static Direction rightOf(Direction facing) {
        return facing.getCounterClockWise();
    }

    private static BlockPos cellPos(BlockPos master, Direction facing, BoardPart part) {
        return master.above(part.row).relative(rightOf(facing), part.column);
    }

    public static BlockPos masterPos(BlockPos pos, BlockState state) {
        BoardPart part = state.getValue(PART);
        return pos.below(part.row).relative(rightOf(state.getValue(FACING)), -part.column);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection().getOpposite();
        BlockPos master = context.getClickedPos();
        Level level = context.getLevel();
        for (BoardPart part : BoardPart.values()) {
            BlockPos cell = cellPos(master, facing, part);
            if (level.isOutsideBuildHeight(cell) || !level.getWorldBorder().isWithinBounds(cell)
                    || !level.getBlockState(cell).canBeReplaced(context)) {
                return null;
            }
        }
        return defaultBlockState().setValue(FACING, facing).setValue(PART, BoardPart.LOWER_LEFT);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide()) {
            return;
        }
        Direction facing = state.getValue(FACING);
        for (BoardPart part : BoardPart.values()) {
            if (!part.isMaster()) {
                level.setBlock(cellPos(pos, facing, part), state.setValue(PART, part), Block.UPDATE_ALL);
            }
        }
    }

    /** A cell whose sign-neighbour is missing removes itself, so breaking one cell takes down the whole sign. */
    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
                                     Direction direction, BlockPos neighborPos, BlockState neighborState, RandomSource random) {
        BoardPart part = state.getValue(PART);
        Direction facing = state.getValue(FACING);
        Direction right = rightOf(facing);
        int column = part.column;
        int row = part.row;
        if (direction == right) column++;
        else if (direction == right.getOpposite()) column--;
        else if (direction == Direction.UP) row++;
        else if (direction == Direction.DOWN) row--;
        else return state;

        BoardPart expected = BoardPart.at(column, row);
        if (expected == null) {
            return state; // neighbour is outside the sign
        }
        boolean intact = neighborState.is(this)
                && neighborState.getValue(PART) == expected
                && neighborState.getValue(FACING) == facing;
        return intact ? state : Blocks.AIR.defaultBlockState();
    }

    /** Creative players breaking a non-master cell shouldn't get the item from the master cell's loot. */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide() && player.preventsBlockDrops() && !state.getValue(PART).isMaster()) {
            BlockPos master = masterPos(pos, state);
            if (level.getBlockState(master).is(this)) {
                level.setBlock(master, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING)).get(state.getValue(PART));
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(PART).isMaster() ? new OrderBoardBlockEntity(pos, state) : null;
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide() || !state.getValue(PART).isMaster()) {
            return null;
        }
        return createTickerHelper(type, ModBlockEntities.ORDER_BOARD, OrderBoardBlockEntity::serverTick);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide() && level.getBlockEntity(masterPos(pos, state)) instanceof OrderBoardBlockEntity board) {
            player.openMenu(board);
        }
        return InteractionResult.SUCCESS;
    }

    private static Map<Direction, Map<BoardPart, VoxelShape>> buildShapes() {
        Map<Direction, Map<BoardPart, VoxelShape>> shapes = new EnumMap<>(Direction.class);
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            Map<BoardPart, VoxelShape> byPart = new EnumMap<>(BoardPart.class);
            for (BoardPart part : BoardPart.values()) {
                double cellX = part.column == 0 ? 16 : 0;
                double cellY = part.row * 16;
                VoxelShape shape = Shapes.empty();
                for (double[] b : BOXES) {
                    double minX = Math.max(b[0], cellX) - cellX, maxX = Math.min(b[3], cellX + 16) - cellX;
                    double minY = Math.max(b[1], cellY) - cellY, maxY = Math.min(b[4], cellY + 16) - cellY;
                    if (minX < maxX && minY < maxY) {
                        shape = Shapes.or(shape, rotatedBox(facing, minX, minY, b[2], maxX, maxY, b[5]));
                    }
                }
                byPart.put(part, shape.optimize());
            }
            shapes.put(facing, byPart);
        }
        return shapes;
    }

    /** Rotates a north-facing box (pixels) to {@code facing}, matching the blockstate's y rotation. */
    private static VoxelShape rotatedBox(Direction facing, double x1, double y1, double z1, double x2, double y2, double z2) {
        return switch (facing) {
            case EAST -> Block.box(16 - z2, y1, x1, 16 - z1, y2, x2);
            case SOUTH -> Block.box(16 - x2, y1, 16 - z2, 16 - x1, y2, 16 - z1);
            case WEST -> Block.box(z1, y1, 16 - x2, z2, y2, 16 - x1);
            default -> Block.box(x1, y1, z1, x2, y2, z2);
        };
    }
}
