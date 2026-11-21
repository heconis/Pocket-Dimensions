package io.github.heconis.pocketdimensions;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundStopSoundPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityTravelToDimensionEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = PocketDimensions.ID)
public final class PocketTravel {
    private static final int CAST_TICKS = 40;
    private static final Map<UUID, Cast> CASTS = new HashMap<>();
    private static ServerPlayer returningToEnd;

    static void startCast(ServerPlayer player) {
        if (!CASTS.containsKey(player.getUUID()) && canEnter(player)) {
            CASTS.put(player.getUUID(), new Cast(player.level().dimension(), player.serverLevel().getGameTime() + CAST_TICKS));
            player.playNotifySound(PocketDimensions.CAST_SOUND.get(), SoundSource.BLOCKS, 1, 1);
            message(player, "casting");
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player) || !player.isAlive()) {
            return;
        }
        if (player.level().dimension().equals(PocketRoom.DIMENSION)) {
            tickRoom(player);
            return;
        }

        Cast cast = CASTS.get(player.getUUID());
        if (cast != null && player.serverLevel().getGameTime() >= cast.completesAt()) {
            stopCast(player);
            if (cast.dimension().equals(player.level().dimension()) && canEnter(player)) {
                enter(player);
            }
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            CASTS.remove(player.getUUID());
        }
    }

    @SubscribeEvent
    public static void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            stopCast(player);
        }
    }

    @SubscribeEvent
    public static void onDimensionTravel(EntityTravelToDimensionEvent event) {
        // Old rooms can still contain a vanilla end portal until their first room tick.
        if (event.getDimension().equals(Level.END) && event.getEntity().level().dimension().equals(PocketRoom.DIMENSION)
                && event.getEntity() != returningToEnd) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            stopCast(player);
        }
    }

    private static void stopCast(ServerPlayer player) {
        if (CASTS.remove(player.getUUID()) != null) {
            player.connection.send(new ClientboundStopSoundPacket(PocketDimensions.CAST_SOUND.get().getLocation(),
                    SoundSource.BLOCKS));
        }
    }

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (event.getLevel() instanceof ServerLevel level && level.dimension().equals(PocketRoom.DIMENSION)
                && (event.getState().is(Blocks.BEDROCK) || event.getState().is(Blocks.BARRIER)
                || event.getState().is(Blocks.END_GATEWAY) || event.getState().is(Blocks.END_PORTAL)
                || event.getState().is(PocketDimensions.EXIT.get()))) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !level.dimension().equals(PocketRoom.DIMENSION)) {
            return;
        }
        if (restricted(event.getPlacedBlock()) || event instanceof BlockEvent.EntityMultiPlaceEvent multi
                && multi.getReplacedBlockSnapshots().stream().anyMatch(snapshot -> restricted(snapshot.getCurrentBlock()))) {
            event.setCanceled(true);
            if (event.getEntity() instanceof ServerPlayer player) {
                message(player, "placement_denied");
            }
        }
    }

    @SubscribeEvent
    public static void onPortalSpawn(BlockEvent.PortalSpawnEvent event) {
        if (event.getLevel() instanceof ServerLevel level && level.dimension().equals(PocketRoom.DIMENSION)) {
            event.setCanceled(true);
        }
    }

    private static boolean restricted(BlockState state) {
        return state.is(BlockTags.BEDS) || state.is(Blocks.RESPAWN_ANCHOR);
    }

    private static boolean canEnter(ServerPlayer player) {
        if (!player.isAlive()) {
            return false;
        }
        if (player.level().dimension().equals(PocketRoom.DIMENSION)) {
            message(player, "inside");
            return false;
        }
        if (player.getCombatTracker().inCombat) {
            message(player, "combat");
            return false;
        }
        return true;
    }

    private static void enter(ServerPlayer player) {
        ServerLevel pocket = player.getServer().getLevel(PocketRoom.DIMENSION);
        if (pocket == null) {
            message(player, "entry_failed");
            return;
        }

        PocketData data = PocketData.get(player.getServer());
        PocketData.Room room = data.allocate(player.getUUID());
        Vec3 arrival = PocketRoom.prepare(pocket, data, room) ? PocketRoom.safeEntrance(player, pocket, room) : null;
        if (arrival == null) {
            message(player, "entry_failed");
            return;
        }

        PocketData.ReturnPoint origin = PocketData.ReturnPoint.capture(player);
        if (travel(player, pocket, arrival, 0, 0)) {
            room.returnPoint = origin;
            room.exitArmed = true;
            data.setDirty();
            message(player, "entered");
        } else {
            message(player, "entry_failed");
        }
    }

    private static void tickRoom(ServerPlayer player) {
        PocketData data = PocketData.get(player.getServer());
        PocketData.Room room = data.find(player.getUUID());
        if (room == null || !room.generated) {
            return;
        }
        if (!room.gatewayReady && !PocketRoom.ensureGateway(player.serverLevel(), room)) {
            return;
        }

        if (PocketRoom.insideExit(player, room)) {
            if (room.exitArmed) {
                room.exitArmed = false;
                data.setDirty();
                returnToOrigin(player, data, room);
            }
            return;
        }

        if (!PocketRoom.interior(room).contains(player.position())) {
            Vec3 arrival = PocketRoom.safeEntrance(player, player.serverLevel(), room);
            if (arrival == null || !move(player, player.serverLevel(), arrival, 0, 0)) {
                return;
            }
        }

        if (!room.exitArmed) {
            room.exitArmed = true;
            data.setDirty();
        }
    }

    private static void returnToOrigin(ServerPlayer player, PocketData data, PocketData.Room room) {
        SafeReturn.Destination target = SafeReturn.find(player, room.returnPoint);
        if (target == null || !travel(player, target.level(), target.position(), target.yaw(), target.pitch())) {
            message(player, "return_failed");
            return;
        }

        room.returnPoint = null;
        data.setDirty();
        if (target.message() != null) {
            message(player, target.message());
        }
    }

    private static boolean travel(ServerPlayer player, ServerLevel destination, Vec3 position, float yaw, float pitch) {
        ServerLevel source = player.serverLevel();
        Vec3 origin = player.position();
        if (!move(player, destination, position, yaw, pitch)) {
            return false;
        }
        source.playSound(player, origin.x, origin.y, origin.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1, 1);
        destination.playSound(player, position.x, position.y, position.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1, 1);
        player.playNotifySound(SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1, 1);
        return true;
    }

    private static boolean move(ServerPlayer player, ServerLevel level, Vec3 pos, float yaw, float pitch) {
        player.ejectPassengers();
        ServerPlayer previous = returningToEnd;
        if (level.dimension().equals(Level.END) && player.level().dimension().equals(PocketRoom.DIMENSION)) {
            returningToEnd = player;
        }
        try {
            boolean moved = player.teleportTo(level, pos.x, pos.y, pos.z, Set.of(), yaw, pitch);
            if (moved && player.level() == level) {
                player.setDeltaMovement(Vec3.ZERO);
                player.resetFallDistance();
                return true;
            }
            return false;
        } finally {
            returningToEnd = previous;
        }
    }

    private static void message(ServerPlayer player, String key) {
        player.displayClientMessage(Component.translatable("message.pocket_dimensions." + key), true);
    }

    private record Cast(ResourceKey<Level> dimension, long completesAt) {}
}
