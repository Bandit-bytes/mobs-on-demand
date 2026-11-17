package net.bandit.mobs_on_demand.board;

import net.minecraft.util.StringRepresentable;

/**
 * The six cells of the 2-wide, 3-tall order sign. "Left"/"right" are as seen from the front;
 * {@link #LOWER_LEFT} is the master cell that owns the block entity and drops the item.
 */
public enum BoardPart implements StringRepresentable {
    LOWER_LEFT(0, 0), LOWER_RIGHT(1, 0),
    MIDDLE_LEFT(0, 1), MIDDLE_RIGHT(1, 1),
    UPPER_LEFT(0, 2), UPPER_RIGHT(1, 2);

    /** 0 = left (master column), 1 = right. */
    public final int column;
    /** 0 = bottom (posts) .. 2 = top. */
    public final int row;

    BoardPart(int column, int row) {
        this.column = column;
        this.row = row;
    }

    public boolean isMaster() {
        return this == LOWER_LEFT;
    }

    public static BoardPart at(int column, int row) {
        for (BoardPart part : values()) {
            if (part.column == column && part.row == row) {
                return part;
            }
        }
        return null;
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }
}
