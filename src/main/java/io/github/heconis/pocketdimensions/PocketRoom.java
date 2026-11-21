package io.github.heconis.pocketdimensions;

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

final class PocketRoom {
    static final ResourceKey<Level> DIMENSION = ResourceKey.create(Registries.DIMENSION, PocketDimensions.id("pocket"));
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
            for (BlockPos pos : BlockPos.betweenClosed(origin, corner)) {
                BlockState expected = room.compactLayout ? initialBlock(pos, origin) : legacyBlock(pos, origin);
                if (expected != null && !placeIfAir(level, pos, expected)) {
                    return false;
                }
            }
            room.generated = true;
            data.setDirty();
        }

        return room.compactLayout ? level.getBlockState(exit(room)).is(Blocks.END_PORTAL) : upgradeLegacyGate(level, room);
    }

    static AABB interior(PocketData.Room room) {
        BlockPos origin = origin(room.id);
        if (room.compactLayout) {
            return new AABB(origin.offset(WALL, FLOOR_Y + 1, WALL),
                    origin.offset(WALL + FLOOR_WIDTH, CEILING_START, WALL + FLOOR_WIDTH));
        }
        return new AABB(origin.offset(1, 1, 1), origin.offset(LEGACY_WIDTH - 1, HEIGHT - 1, LEGACY_WIDTH - 1));
    }

    static boolean touchingExit(ServerPlayer player, PocketData.Room room) {
        BlockPos pos = exit(room);
        ServerLevel level = player.serverLevel();
        BlockState state = level.getBlockState(pos);
        return state.is(Blocks.END_PORTAL) && state.getShape(level, pos).bounds()
                .move(pos).intersects(player.getBoundingBox());
    }

    static Vec3 entrance(PocketData.Room room) {
        return Vec3.atBottomCenterOf(exit(room));
    }

    static boolean upgradeLegacyGate(ServerLevel level, PocketData.Room room) {
        BlockPos pos = exit(room);
        BlockState lower = level.getBlockState(pos);
        if (!lower.is(Blocks.END_PORTAL) && (!lower.is(PocketDimensions.EXIT.get())
                || !level.setBlockAndUpdate(pos, Blocks.END_PORTAL.defaultBlockState()))) {
            return false;
        }
        BlockPos upper = pos.above();
        return !level.getBlockState(upper).is(PocketDimensions.EXIT.get())
                || level.setBlockAndUpdate(upper, Blocks.AIR.defaultBlockState());
    }

    private static BlockPos origin(int id) {
        return new BlockPos(id % ROOMS_PER_ROW * SPACING, 0, id / ROOMS_PER_ROW * SPACING);
    }

    private static BlockPos exit(PocketData.Room room) {
        BlockPos origin = origin(room.id);
        return room.compactLayout ? origin.offset(WALL + FLOOR_WIDTH / 2, FLOOR_Y + 1, WALL)
                : origin.offset(7, 1, 3);
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
        return x == WALL + FLOOR_WIDTH / 2 && y == FLOOR_Y + 1 && z == WALL
                ? Blocks.END_PORTAL.defaultBlockState() : null;
    }

    private static BlockState legacyBlock(BlockPos pos, BlockPos origin) {
        int x = pos.getX() - origin.getX();
        int y = pos.getY();
        int z = pos.getZ() - origin.getZ();
        if (x == 0 || x == LEGACY_WIDTH - 1 || z == 0 || z == LEGACY_WIDTH - 1 || y == 0 || y == HEIGHT - 1) {
            return Blocks.BEDROCK.defaultBlockState();
        }
        if (x == 7 && z == 3 && (y == 1 || y == 2)) {
            return PocketDimensions.EXIT.get().defaultBlockState();
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
