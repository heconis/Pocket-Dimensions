package io.github.heconis.pocketdimensions;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

final class PocketRoom {
    static final ResourceKey<Level> DIMENSION = ResourceKey.create(Registries.DIMENSION, PocketDimensions.id("pocket"));
    private static final int WIDTH = 16;
    private static final int HEIGHT = 32;
    private static final int SPACING = 256;
    private static final int ROOMS_PER_ROW = 1024;

    static boolean prepare(ServerLevel level, PocketData data, PocketData.Room room) {
        BlockPos origin = origin(room.id);
        BlockPos corner = origin.offset(WIDTH - 1, HEIGHT - 1, WIDTH - 1);
        if (!level.getWorldBorder().isWithinBounds(origin) || !level.getWorldBorder().isWithinBounds(corner)) {
            return false;
        }

        if (!room.generated) {
            for (BlockPos pos : BlockPos.betweenClosed(origin, corner)) {
                BlockState expected = initialBlock(pos, origin);
                if (expected != null && !placeIfAir(level, pos, expected)) {
                    return false;
                }
            }
            room.generated = true;
            data.setDirty();
        }

        BlockPos exit = exit(room.id);
        return level.getBlockState(exit).is(PocketDimensions.EXIT.get())
                && level.getBlockState(exit.above()).is(PocketDimensions.EXIT.get());
    }

    static AABB interior(int id) {
        BlockPos origin = origin(id);
        return new AABB(origin.offset(1, 1, 1), origin.offset(WIDTH - 1, HEIGHT - 1, WIDTH - 1));
    }

    static AABB exitBounds(int id) {
        BlockPos pos = exit(id);
        return new AABB(pos, pos.offset(1, 2, 1));
    }

    static Vec3 entrance(int id) {
        return Vec3.atBottomCenterOf(exit(id));
    }

    private static BlockPos origin(int id) {
        return new BlockPos(id % ROOMS_PER_ROW * SPACING, 0, id / ROOMS_PER_ROW * SPACING);
    }

    private static BlockPos exit(int id) {
        return origin(id).offset(7, 1, 3);
    }

    private static BlockState initialBlock(BlockPos pos, BlockPos origin) {
        int x = pos.getX() - origin.getX();
        int y = pos.getY();
        int z = pos.getZ() - origin.getZ();
        if (x == 0 || x == WIDTH - 1 || z == 0 || z == WIDTH - 1 || y == 0 || y == HEIGHT - 1) {
            return Blocks.BEDROCK.defaultBlockState();
        }
        if (x == 7 && z == 3 && (y == 1 || y == 2)) {
            return PocketDimensions.EXIT.get().defaultBlockState();
        }
        if (y == 1 && (x >= 6 && x <= 8) && (z >= 6 && z <= 9)) {
            return Blocks.POLISHED_DEEPSLATE.defaultBlockState();
        }
        if (y == 5 && (x == 1 || x == WIDTH - 2) && (z == 4 || z == 11)) {
            return Blocks.SHROOMLIGHT.defaultBlockState();
        }
        return null;
    }

    private static boolean placeIfAir(ServerLevel level, BlockPos pos, BlockState expected) {
        BlockState existing = level.getBlockState(pos);
        return existing == expected || existing.isAir() && level.setBlock(pos, expected, Block.UPDATE_CLIENTS);
    }
}
