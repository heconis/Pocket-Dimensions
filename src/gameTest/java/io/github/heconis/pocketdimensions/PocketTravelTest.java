package io.github.heconis.pocketdimensions;

import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.protocol.game.ClientboundStopSoundPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.TheEndGatewayBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(PocketDimensions.ID)
@PrefixGameTestTemplate(false)
public final class PocketTravelTest {
    @GameTest(template = "platform", timeoutTicks = 160)
    public static void roundTripKeepsRoomAndFindsSafeReturn(GameTestHelper helper) {
        ServerLevel source = helper.getLevel();
        ServerLevel pocket = source.getServer().getLevel(PocketRoom.DIMENSION);
        helper.assertTrue(pocket != null, "The pocket dimension must load from the mod data pack");

        Vec3 origin = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(3, 2, 3)));
        ServerPlayer first = mockPlayer(source, "PocketTestOne");
        ServerLevel end = source.getServer().getLevel(Level.END);
        ServerPlayer second = mockPlayer(end, "PocketTestTwo");
        first.moveTo(origin.x, origin.y, origin.z, 45, 10);
        Vec3 secondOrigin = Vec3.atBottomCenterOf(new BlockPos(96, 120, 64));
        end.setBlockAndUpdate(BlockPos.containing(secondOrigin).below(), Blocks.STONE.defaultBlockState());
        second.moveTo(secondOrigin.x, secondOrigin.y, secondOrigin.z, 0, 0);
        helper.assertTrue(SafeReturn.isSafe(first, source, origin), "The test origin needs a clear stone floor at " + origin
                + ", floor=" + source.getBlockState(BlockPos.containing(origin).below()));

        first.getCombatTracker().inCombat = true;
        PocketTravel.startCast(first);
        first.getCombatTracker().inCombat = false;
        PocketTravel.startCast(second);
        RecordingConnection sounds = (RecordingConnection) second.connection.connection;
        helper.assertTrue(sounds.cues.equals(List.of(PocketDimensions.CAST_SOUND.getId().toString())),
                "The Nether portal ambience must begin when the cast starts");

        helper.runAfterDelay(20, () -> {
            tick(second);
            helper.assertTrue(second.level() == end, "The cast must wait 40 ticks");
            helper.assertTrue(sounds.cues.size() == 1, "The ambience must still play midway through the cast");
        });
        helper.runAfterDelay(40, () -> {
            tick(first);
            tick(second);
            helper.assertTrue(first.level() == source, "Combat must block the first cast");
            helper.assertTrue(second.level() == pocket, "The second player must enter after 40 ticks");
            String cast = PocketDimensions.CAST_SOUND.getId().toString();
            helper.assertTrue(sounds.cues.equals(List.of(cast, "stop " + cast,
                            SoundEvents.ENDERMAN_TELEPORT.getLocation().toString())),
                    "Successful entry must stop the ambience and play the teleport sound");
            PocketTravel.startCast(first);
        });
        helper.runAfterDelay(80, () -> {
            tick(first);
            tick(first);
            helper.assertTrue(first.level() == pocket, "Standing in the exit on entry must not return the player");

            PocketData data = PocketData.get(source.getServer());
            PocketData.Room room = data.find(first.getUUID());
            helper.assertTrue(room.id != data.find(second.getUUID()).id, "The two players must have different rooms");
            helper.assertTrue(room.returnPoint.position().distanceToSqr(origin) < 0.001,
                    "The first player must cast from " + origin + " but cast from " + room.returnPoint.position());
            PocketData.Room restored = PocketData.load(data.save(new CompoundTag())).find(first.getUUID());
            helper.assertTrue(restored.id == room.id && restored.compactLayout && restored.generated && !restored.exitArmed
                            && room.returnPoint.equals(restored.returnPoint),
                    "Saved room ownership and return data must survive serialization and reload");
            Vec3 arrival = PocketRoom.entrance(room);
            assertCompactLayout(helper, pocket, arrival);
            assertGatewayEffects(helper, pocket, source, room);
            stepThroughExit(second, PocketRoom.entrance(data.find(second.getUUID())));
            helper.assertTrue(second.level() == end && second.position().distanceToSqr(secondOrigin) < 0.001,
                    "Expected the End at " + secondOrigin + " but arrived at " + second.position()
                            + " in " + second.level().dimension().location());
            helper.assertTrue(sounds.cues.size() == 4 && sounds.cues.get(3).equals(
                            SoundEvents.ENDERMAN_TELEPORT.getLocation().toString()),
                    "Successful return must play the teleport sound again");
            BlockPos chestPos = BlockPos.containing(arrival.add(2, 0, 0));
            pocket.setBlockAndUpdate(chestPos, Blocks.CHEST.defaultBlockState());
            ChestBlockEntity chest = (ChestBlockEntity) pocket.getBlockEntity(chestPos);
            chest.setItem(0, new ItemStack(Items.DIAMOND));

            stepThroughExit(first, arrival);
            helper.assertTrue(first.level() == source && first.position().distanceToSqr(origin) < 0.001,
                    "The exit must return the player to " + origin + " but arrived at " + first.position()
                            + " in " + first.level().dimension().location());
            helper.assertTrue(first.getYRot() == 45 && first.getXRot() == 10, "Return must restore the recorded rotation");
            PocketTravel.startCast(first);

            helper.runAfterDelay(40, () -> {
                tick(first);
                helper.assertTrue(first.level() == pocket, "Re-entering immediately must not require a cooldown");
                ChestBlockEntity savedChest = (ChestBlockEntity) pocket.getBlockEntity(chestPos);
                helper.assertTrue(savedChest.getItem(0).is(Items.DIAMOND), "Re-entry must preserve room contents");
                source.setBlockAndUpdate(BlockPos.containing(origin), Blocks.STONE.defaultBlockState());
                stepThroughExit(first, arrival);
                helper.assertTrue(first.level() == source && first.position().distanceToSqr(origin) > 0.1
                                && SafeReturn.isSafe(first, source, first.position()),
                        "A blocked origin must return the player to a safe nearby position");
                helper.succeed();
            });
        });
    }

    @GameTest(template = "platform")
    public static void legacyRoomKeepsContentsWhenGateChanges(GameTestHelper helper) {
        ServerLevel pocket = helper.getLevel().getServer().getLevel(PocketRoom.DIMENSION);
        UUID owner = UUID.randomUUID();
        CompoundTag entry = new CompoundTag();
        entry.putUUID("Owner", owner);
        entry.putInt("Id", 512);
        entry.putBoolean("Generated", true);
        ListTag rooms = new ListTag();
        rooms.add(entry);
        CompoundTag root = new CompoundTag();
        root.put("Rooms", rooms);
        PocketData data = PocketData.load(root);
        PocketData.Room room = data.find(owner);
        BlockPos gate = PocketRoom.exit(room);
        BlockPos oldGate = gate.offset(0, -1, 3);
        pocket.setBlockAndUpdate(gate, Blocks.BEDROCK.defaultBlockState());
        pocket.setBlockAndUpdate(oldGate, PocketDimensions.EXIT.get().defaultBlockState());
        pocket.setBlockAndUpdate(oldGate.above(), PocketDimensions.EXIT.get().defaultBlockState());
        BlockPos chestPos = oldGate.offset(-6, 0, 0);
        pocket.setBlockAndUpdate(chestPos, Blocks.CHEST.defaultBlockState());
        ChestBlockEntity chest = (ChestBlockEntity) pocket.getBlockEntity(chestPos);
        chest.setItem(0, new ItemStack(Items.DIAMOND));
        helper.assertTrue(PocketRoom.prepare(pocket, data, room) && PocketRoom.prepare(pocket, data, room),
                "Upgrading the same legacy gate twice must succeed");
        helper.assertTrue(pocket.getBlockState(gate).is(Blocks.END_GATEWAY) && pocket.getBlockState(oldGate).isAir()
                        && pocket.getBlockState(oldGate.above()).isAir(),
                "The legacy gate must become an End gateway in the wall and free up its old floor space");
        helper.assertTrue(pocket.getBlockEntity(chestPos) instanceof ChestBlockEntity savedChest
                        && savedChest.getItem(0).is(Items.DIAMOND),
                "Upgrading the gate must preserve the legacy room's storage outside the new room footprint");

        entry.putInt("Id", 513);
        entry.putBoolean("CompactLayout", true);
        PocketData compactData = PocketData.load(root);
        PocketData.Room compact = compactData.find(owner);
        BlockPos compactGate = PocketRoom.exit(compact);
        BlockPos compactOldGate = compactGate.offset(0, -1, 1);
        pocket.setBlockAndUpdate(compactGate, Blocks.BARRIER.defaultBlockState());
        pocket.setBlockAndUpdate(compactOldGate, Blocks.END_PORTAL.defaultBlockState());
        pocket.setBlockAndUpdate(compactOldGate.above(), Blocks.POLISHED_DEEPSLATE.defaultBlockState());
        helper.assertTrue(PocketRoom.prepare(pocket, compactData, compact), "The 0.2.0 gate must upgrade too");
        helper.assertTrue(pocket.getBlockState(compactGate).is(Blocks.END_GATEWAY)
                        && pocket.getBlockState(compactOldGate).isAir()
                        && pocket.getBlockState(compactOldGate.above()).is(Blocks.POLISHED_DEEPSLATE),
                "The 0.2.0 migration must remove only the old portal and keep the adjacent building block");
        helper.succeed();
    }

    private static void assertCompactLayout(GameTestHelper helper, ServerLevel level, Vec3 arrival) {
        BlockPos origin = BlockPos.containing(arrival).offset(-8, -2, -5);
        for (BlockPos pos : BlockPos.betweenClosed(origin, origin.offset(16, 31, 16))) {
            int x = pos.getX() - origin.getX();
            int y = pos.getY();
            int z = pos.getZ() - origin.getZ();
            BlockState state = level.getBlockState(pos);
            if (x == 8 && y == 3 && z == 4) {
                helper.assertTrue(state.is(Blocks.END_GATEWAY), "The gateway must occupy a wall block outside the platform");
            } else if (x < 5 || x >= 12 || z < 5 || z >= 12 || y == 0 || y >= 29) {
                helper.assertTrue(state.is(Blocks.BARRIER), "Missing barrier at " + pos);
            } else if (y == 1) {
                helper.assertTrue(state.is(Blocks.BEDROCK), "Missing platform at " + pos);
            } else {
                helper.assertTrue(state.isAir(), "Unexpected block inside the room at " + pos);
            }
        }
    }

    private static void assertGatewayEffects(GameTestHelper helper, ServerLevel level, ServerLevel source, PocketData.Room room) {
        BlockPos pos = PocketRoom.exit(room);
        TheEndGatewayBlockEntity gateway = (TheEndGatewayBlockEntity) level.getBlockEntity(pos);
        BlockState state = level.getBlockState(pos);
        helper.assertTrue(!gateway.isSpawning(), "The gateway renderer must never see a spawn beam in a pocket room");
        gateway.triggerEvent(1, 0);
        helper.assertTrue(!gateway.isCoolingDown(), "The gateway renderer must never see a cooldown beam in a pocket room");
        long age = gateway.saveWithoutMetadata().getLong("Age");
        TheEndGatewayBlockEntity.teleportTick(level, pos, state, gateway);
        helper.assertTrue(gateway.saveWithoutMetadata().getLong("Age") == age, "Vanilla gateway ticks must be bypassed");

        TheEndGatewayBlockEntity vanilla = new TheEndGatewayBlockEntity(pos, state);
        vanilla.setLevel(source);
        vanilla.triggerEvent(1, 0);
        helper.assertTrue(vanilla.isSpawning() && vanilla.isCoolingDown(), "Vanilla gateways outside the pocket keep their beams");
    }

    private static void tick(ServerPlayer player) {
        PocketTravel.onPlayerTick(new TickEvent.PlayerTickEvent(TickEvent.Phase.END, player));
    }

    private static ServerPlayer mockPlayer(ServerLevel level, String name) {
        ServerPlayer player = new ServerPlayer(level.getServer(), level, new GameProfile(UUID.randomUUID(), name));
        // Supply a listener for vanilla teleport packets without running the Forge network login handshake.
        new ServerGamePacketListenerImpl(level.getServer(), new RecordingConnection(), player);
        level.addNewPlayer(player);
        return player;
    }

    private static void stepThroughExit(ServerPlayer player, Vec3 arrival) {
        player.setPos(arrival.x, arrival.y, arrival.z + 2);
        tick(player);
        player.setPos(arrival.x, arrival.y, arrival.z);
        tick(player);
    }

    private static final class RecordingConnection extends Connection {
        final List<String> cues = new ArrayList<>();

        RecordingConnection() {
            super(PacketFlow.SERVERBOUND);
        }

        @Override
        public void send(Packet<?> packet, PacketSendListener listener) {
            if (packet instanceof ClientboundSoundPacket sound) {
                cues.add(sound.getSound().value().getLocation().toString());
            } else if (packet instanceof ClientboundStopSoundPacket stop) {
                cues.add("stop " + stop.getName());
            }
        }
    }
}
