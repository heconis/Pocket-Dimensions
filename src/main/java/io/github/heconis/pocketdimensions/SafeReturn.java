package io.github.heconis.pocketdimensions;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

final class SafeReturn {
    @Nullable
    static Destination find(ServerPlayer player, @Nullable PocketData.ReturnPoint point) {
        ServerLevel source = point == null ? null : player.getServer().getLevel(point.dimension());
        if (source != null && !source.dimension().equals(PocketRoom.DIMENSION)) {
            if (isSafe(player, source, point.position())) {
                return new Destination(source, point.position(), point.yaw(), point.pitch(), null);
            }
            Vec3 nearby = search(player, source, BlockPos.containing(point.position()), 8, 8);
            if (nearby != null) {
                return new Destination(source, nearby, point.yaw(), point.pitch(), "return_nearby");
            }
        }

        ServerLevel overworld = player.getServer().overworld();
        BlockPos spawn = overworld.getSharedSpawnPos();
        Vec3 safe = search(player, overworld, spawn, 16, 8);
        if (safe == null) {
            BlockPos surface = overworld.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spawn);
            safe = search(player, overworld, surface, 16, 8);
        }
        return safe == null ? null : new Destination(overworld, safe, overworld.getSharedSpawnAngle(), 0, "return_spawn");
    }

    @Nullable
    private static Vec3 search(ServerPlayer player, ServerLevel level, BlockPos center, int horizontal, int vertical) {
        for (BlockPos pos : BlockPos.withinManhattan(center, horizontal, vertical, horizontal)) {
            int dx = pos.getX() - center.getX();
            int dz = pos.getZ() - center.getZ();
            if (dx * dx + dz * dz <= horizontal * horizontal) {
                Vec3 candidate = Vec3.atBottomCenterOf(pos);
                if (isSafe(player, level, candidate)) {
                    return candidate;
                }
            }
        }
        return null;
    }

    static boolean isSafe(ServerPlayer player, ServerLevel level, Vec3 position) {
        AABB bounds = player.getDimensions(Pose.STANDING).makeBoundingBox(position);
        if (bounds.minY <= level.getMinBuildHeight() || bounds.maxY > level.getMaxBuildHeight()
                || !level.getWorldBorder().isWithinBounds(bounds)) {
            return false;
        }
        // Collision queries skip unloaded chunks, including the original chunk after a long visit.
        for (int x = SectionPos.blockToSectionCoord(bounds.minX); x <= SectionPos.blockToSectionCoord(bounds.maxX); x++) {
            for (int z = SectionPos.blockToSectionCoord(bounds.minZ); z <= SectionPos.blockToSectionCoord(bounds.maxZ); z++) {
                level.getChunk(x, z);
            }
        }
        if (!level.noCollision(player, bounds)) {
            return false;
        }

        double supportDepth = 0.0625;
        if (!level.getBlockCollisions(player, bounds.move(0, -supportDepth, 0)).iterator().hasNext()) {
            return false;
        }
        AABB checked = bounds.deflate(0.0001).expandTowards(0, -supportDepth, 0);
        for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(checked.minX, checked.minY, checked.minZ),
                BlockPos.containing(checked.maxX, checked.maxY, checked.maxZ))) {
            BlockState state = level.getBlockState(pos);
            if (isHazard(state)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isHazard(BlockState state) {
        return !state.getFluidState().isEmpty() || state.is(BlockTags.FIRE)
                || state.is(Blocks.MAGMA_BLOCK) || state.is(Blocks.CACTUS) || state.is(Blocks.SWEET_BERRY_BUSH)
                || state.is(Blocks.WITHER_ROSE) || state.is(Blocks.POWDER_SNOW) || state.is(Blocks.POINTED_DRIPSTONE)
                || state.is(Blocks.NETHER_PORTAL) || state.is(Blocks.END_PORTAL) || state.is(Blocks.END_GATEWAY)
                || state.is(BlockTags.CAMPFIRES) && state.getValue(CampfireBlock.LIT);
    }

    record Destination(ServerLevel level, Vec3 position, float yaw, float pitch, @Nullable String message) {}
}
