package io.github.heconis.pocketdimensions;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;

final class PocketData extends SavedData {
    private final Map<UUID, Room> rooms = new HashMap<>();
    private int nextId;

    static PocketData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(PocketData::load, PocketData::new,
                PocketDimensions.ID);
    }

    Room find(UUID player) {
        return rooms.get(player);
    }

    Room allocate(UUID player) {
        return rooms.computeIfAbsent(player, key -> {
            setDirty();
            return new Room(nextId++);
        });
    }

    static PocketData load(CompoundTag root) {
        PocketData data = new PocketData();
        data.nextId = root.getInt("NextId");
        for (Tag item : root.getList("Rooms", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) item;
            Room room = new Room(entry.getInt("Id"));
            room.generated = entry.getBoolean("Generated");
            room.exitArmed = entry.getBoolean("ExitArmed");
            if (entry.contains("Return", Tag.TAG_COMPOUND)) {
                room.returnPoint = ReturnPoint.load(entry.getCompound("Return"));
            }
            data.rooms.put(entry.getUUID("Owner"), room);
            data.nextId = Math.max(data.nextId, room.id + 1);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag root) {
        ListTag entries = new ListTag();
        rooms.forEach((owner, room) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Owner", owner);
            entry.putInt("Id", room.id);
            entry.putBoolean("Generated", room.generated);
            entry.putBoolean("ExitArmed", room.exitArmed);
            if (room.returnPoint != null) {
                entry.put("Return", room.returnPoint.save());
            }
            entries.add(entry);
        });
        root.put("Rooms", entries);
        root.putInt("NextId", nextId);
        return root;
    }

    static final class Room {
        final int id;
        boolean generated;
        boolean exitArmed;
        ReturnPoint returnPoint;

        private Room(int id) {
            this.id = id;
        }
    }

    record ReturnPoint(ResourceKey<Level> dimension, Vec3 position, float yaw, float pitch) {
        static ReturnPoint capture(ServerPlayer player) {
            return new ReturnPoint(player.level().dimension(), player.position(), player.getYRot(), player.getXRot());
        }

        private static ReturnPoint load(CompoundTag tag) {
            ResourceLocation dimension = ResourceLocation.tryParse(tag.getString("Dimension"));
            if (dimension == null) {
                return null;
            }
            Vec3 position = new Vec3(tag.getDouble("X"), tag.getDouble("Y"), tag.getDouble("Z"));
            return new ReturnPoint(ResourceKey.create(Registries.DIMENSION, dimension), position,
                    tag.getFloat("Yaw"), tag.getFloat("Pitch"));
        }

        private CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putString("Dimension", dimension.location().toString());
            tag.putDouble("X", position.x);
            tag.putDouble("Y", position.y);
            tag.putDouble("Z", position.z);
            tag.putFloat("Yaw", yaw);
            tag.putFloat("Pitch", pitch);
            return tag;
        }
    }
}
