package io.github.heconis.pocketdimensions;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class PocketRoom {
    public static final ResourceKey<Level> DIMENSION = ResourceKey.create(Registries.DIMENSION, PocketDimensions.id("pocket"));
    private static final int HEIGHT = 32;
    private static final int WALL = 5;
    private static final int FLOOR_WIDTH = 7;
    private static final int WIDTH = FLOOR_WIDTH + 2 * WALL;
    private static final int CEILING_START = HEIGHT - 3;
    private static final int FLOOR_Y = 1;
    private static final int LEGACY_WIDTH = 16;
    private static final int SPACING = 256;
    private static final int ROOMS_PER_ROW = 1024;

    static boolean prepare(ServerLevel level, PocketData data, PocketData.Room room) {
        BlockPos origin = origin(room.id);
        int width = room.compactLayout ? WIDTH : LEGACY_WIDTH;
        BlockPos corner = origin.offset(width - 1, HEIGHT - 1, width - 1);
        if (!level.getWorldBorder().isWithinBounds(origin) || !level.getWorldBorder().isWithinBounds(corner)) {
            return false;
        }

        if (!room.generated) {
            BlockPos gate = exit(room);
            BlockPos lowerGate = gate.below();
            for (BlockPos pos : BlockPos.betweenClosed(origin, corner)) {
                if ((pos.equals(gate) || pos.equals(lowerGate)) && level.getBlockState(pos).is(Blocks.END_GATEWAY)) {
                    continue;
                }
                BlockState expected = room.compactLayout ? initialBlock(pos, origin) : legacyBlock(pos, origin);
                if (expected != null && !placeIfAir(level, pos, expected)) {
                    return false;
                }
            }
            room.generated = true;
            data.setDirty();
        }

        return ensureGateway(level, room);
    }

    static AABB interior(PocketData.Room room) {
        BlockPos origin = origin(room.id);
        if (room.compactLayout) {
            return new AABB(origin.offset(WALL, FLOOR_Y + 1, WALL),
                    origin.offset(WALL + FLOOR_WIDTH, CEILING_START, WALL + FLOOR_WIDTH));
        }
        return new AABB(origin.offset(1, 1, 1), origin.offset(LEGACY_WIDTH - 1, HEIGHT - 1, LEGACY_WIDTH - 1));
    }

    static boolean insideExit(ServerPlayer player, PocketData.Room room) {
        BlockPos gate = exit(room);
        BlockPos feet = player.blockPosition();
        return (feet.equals(gate) || feet.equals(gate.below()))
                && player.serverLevel().getBlockState(feet).is(Blocks.END_GATEWAY);
    }

    public static boolean touchesReturnGateway(ServerPlayer player) {
        if (!player.level().dimension().equals(DIMENSION)) {
            return false;
        }
        PocketData.Room room = PocketData.get(player.getServer()).find(player.getUUID());
        if (room == null || !room.generated) {
            return false;
        }
        BlockPos upper = exit(room);
        AABB bounds = player.getBoundingBox();
        return overlapsGateway(player.serverLevel(), bounds, upper)
                || overlapsGateway(player.serverLevel(), bounds, upper.below());
    }

    private static boolean overlapsGateway(ServerLevel level, AABB bounds, BlockPos pos) {
        return bounds.intersects(new AABB(pos)) && level.getBlockState(pos).is(Blocks.END_GATEWAY);
    }

    static Vec3 entrance(PocketData.Room room) {
        BlockPos gate = exit(room);
        return new Vec3(gate.getX() + 0.5, interior(room).minY, gate.getZ() + 1.32);
    }

    @Nullable
    static Vec3 safeEntrance(ServerPlayer player, ServerLevel level, PocketData.Room room) {
        Vec3 entrance = entrance(room);
        if (SafeReturn.isSafe(player, level, entrance)) {
            return entrance;
        }
        AABB interior = interior(room);
        for (BlockPos pos : BlockPos.withinManhattan(BlockPos.containing(entrance), 2, 1, 2)) {
            Vec3 alternative = Vec3.atBottomCenterOf(pos);
            if (interior.contains(alternative) && SafeReturn.isSafe(player, level, alternative)) {
                return alternative;
            }
        }
        return null;
    }

    static boolean ensureGateway(ServerLevel level, PocketData.Room room) {
        BlockPos pos = exit(room);
        Block wall = room.compactLayout ? Blocks.BARRIER : Blocks.BEDROCK;
        if (!placeGateway(level, pos, wall) || !placeGateway(level, pos.below(), wall)) {
            return false;
        }

        BlockPos previous = room.compactLayout ? origin(room.id).offset(WALL + FLOOR_WIDTH / 2, FLOOR_Y + 1, WALL)
                : origin(room.id).offset(7, 1, 3);
        BlockState previousState = level.getBlockState(previous);
        if ((previousState.is(Blocks.END_PORTAL) || previousState.is(PocketDimensions.EXIT.get()))
                && !level.removeBlock(previous, false)) {
            return false;
        }
        if (!room.compactLayout && level.getBlockState(previous.above()).is(PocketDimensions.EXIT.get())
                && !level.removeBlock(previous.above(), false)) {
            return false;
        }
        room.gatewayReady = true;
        return true;
    }

    private static boolean placeGateway(ServerLevel level, BlockPos pos, Block wall) {
        BlockState state = level.getBlockState(pos);
        return state.is(Blocks.END_GATEWAY)
                || state.is(wall) && level.setBlockAndUpdate(pos, Blocks.END_GATEWAY.defaultBlockState());
    }

    private static BlockPos origin(int id) {
        return new BlockPos(id % ROOMS_PER_ROW * SPACING, 0, id / ROOMS_PER_ROW * SPACING);
    }

    static BlockPos exit(PocketData.Room room) {
        BlockPos origin = origin(room.id);
        return room.compactLayout ? origin.offset(WALL + FLOOR_WIDTH / 2, FLOOR_Y + 2, WALL - 1)
                : origin.offset(7, 2, 0);
    }

    private static BlockState initialBlock(BlockPos pos, BlockPos origin) {
        int x = pos.getX() - origin.getX();
        int y = pos.getY();
        int z = pos.getZ() - origin.getZ();
        if (x < WALL || x >= WIDTH - WALL || z < WALL || z >= WIDTH - WALL || y == 0 || y >= CEILING_START) {
            return Blocks.BARRIER.defaultBlockState();
        }
        if (y == FLOOR_Y) {
            return Blocks.BEDROCK.defaultBlockState();
        }
        return null;
    }

    private static BlockState legacyBlock(BlockPos pos, BlockPos origin) {
        int x = pos.getX() - origin.getX();
        int y = pos.getY();
        int z = pos.getZ() - origin.getZ();
        if (x == 0 || x == LEGACY_WIDTH - 1 || z == 0 || z == LEGACY_WIDTH - 1 || y == 0 || y == HEIGHT - 1) {
            return Blocks.BEDROCK.defaultBlockState();
        }
        if (y == 1 && (x >= 6 && x <= 8) && (z >= 6 && z <= 9)) {
            return Blocks.POLISHED_DEEPSLATE.defaultBlockState();
        }
        if (y == 5 && (x == 1 || x == LEGACY_WIDTH - 2) && (z == 4 || z == 11)) {
            return Blocks.SHROOMLIGHT.defaultBlockState();
        }
        return null;
    }

    private static boolean placeIfAir(ServerLevel level, BlockPos pos, BlockState expected) {
        BlockState existing = level.getBlockState(pos);
        return existing == expected || existing.isAir() && level.setBlock(pos, expected, Block.UPDATE_CLIENTS);
    }
}
