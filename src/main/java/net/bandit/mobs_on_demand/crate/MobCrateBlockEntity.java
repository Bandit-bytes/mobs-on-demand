package net.bandit.mobs_on_demand.crate;

import net.bandit.mobs_on_demand.registry.ModBlockEntities;
import net.bandit.mobs_on_demand.registry.ModComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.component.TypedEntityData;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;

/**
 * Holds the placed crate's item components; vanilla saves unconsumed components under {@code components}.
 * Clients only receive the mob's type and label (not its full data), for the sneak-to-inspect HUD.
 */
public class MobCrateBlockEntity extends BlockEntity {
    private static final String SYNC_ENTITY = "crate_entity";
    private static final String SYNC_LABEL = "crate_label";

    private EntityType<?> syncedType;
    private Component syncedLabel;

    public MobCrateBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MOB_CRATE, pos, state);
    }

    /** The crated mob's type, or null if empty. Works on both sides. */
    public EntityType<?> contentType() {
        TypedEntityData<EntityType<?>> data = components().get(DataComponents.ENTITY_DATA);
        return data != null ? data.type() : syncedType;
    }

    /** The crated mob's name (profession or name tag), or null if empty. Works on both sides. */
    public Component contentLabel() {
        Component label = components().get(ModComponents.CRATE_LABEL);
        if (label != null) {
            return label;
        }
        if (syncedLabel != null) {
            return syncedLabel;
        }
        EntityType<?> type = contentType();
        return type == null ? null : type.getDescription();
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        TypedEntityData<EntityType<?>> data = components().get(DataComponents.ENTITY_DATA);
        if (data != null) {
            tag.putString(SYNC_ENTITY, BuiltInRegistries.ENTITY_TYPE.getKey(data.type()).toString());
        }
        Component label = components().get(ModComponents.CRATE_LABEL);
        if (label != null) {
            tag.store(SYNC_LABEL, ComponentSerialization.CODEC, registries.createSerializationContext(NbtOps.INSTANCE), label);
        }
        return tag;
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        syncedType = input.getString(SYNC_ENTITY)
                .map(Identifier::tryParse)
                .flatMap(BuiltInRegistries.ENTITY_TYPE::getOptional)
                .orElse(null);
        syncedLabel = input.read(SYNC_LABEL, ComponentSerialization.CODEC).orElse(null);
    }
}
