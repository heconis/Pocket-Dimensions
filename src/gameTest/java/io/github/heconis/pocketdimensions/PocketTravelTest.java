package io.github.heconis.pocketdimensions;

import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(PocketDimensions.ID)
@PrefixGameTestTemplate(false)
public final class PocketTravelTest {
    @GameTest(template = "platform", timeoutTicks = 140)
    public static void roundTripKeepsRoomAndFindsSafeReturn(GameTestHelper helper) {
        ServerLevel source = helper.getLevel();
        ServerLevel pocket = source.getServer().getLevel(PocketRoom.DIMENSION);
        helper.assertTrue(pocket != null, "The pocket dimension must load from the mod data pack");

        Vec3 origin = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(3, 2, 3)));
        ServerPlayer first = mockPlayer(source, "PocketTestOne");
        ServerPlayer second = mockPlayer(source, "PocketTestTwo");
        first.moveTo(origin.x, origin.y, origin.z, 45, 10);
        Vec3 secondOrigin = origin.add(2, 0, 0);
        second.moveTo(secondOrigin.x, secondOrigin.y, secondOrigin.z, 0, 0);
        helper.assertTrue(SafeReturn.isSafe(first, source, origin), "The test origin needs a clear stone floor at " + origin
                + ", floor=" + source.getBlockState(BlockPos.containing(origin).below()));

        first.getCombatTracker().inCombat = true;
        PocketTravel.startCast(first);
        first.getCombatTracker().inCombat = false;
        PocketTravel.startCast(second);

        helper.runAfterDelay(20, () -> {
            tick(second);
            helper.assertTrue(second.level() == source, "The cast must wait 40 ticks");
        });
        helper.runAfterDelay(40, () -> {
            tick(first);
            tick(second);
            helper.assertTrue(first.level() == source, "Combat must block the first cast");
            helper.assertTrue(second.level() == pocket, "The second player must enter after 40 ticks");
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
            helper.assertTrue(restored.id == room.id && restored.generated && !restored.exitArmed
                            && room.returnPoint.equals(restored.returnPoint),
                    "Saved room ownership and return data must survive serialization and reload");
            Vec3 arrival = PocketRoom.entrance(room.id);
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

    private static void tick(ServerPlayer player) {
        PocketTravel.onPlayerTick(new TickEvent.PlayerTickEvent(TickEvent.Phase.END, player));
    }

    private static ServerPlayer mockPlayer(ServerLevel level, String name) {
        ServerPlayer player = new ServerPlayer(level.getServer(), level, new GameProfile(UUID.randomUUID(), name));
        // Supply a listener for vanilla teleport packets without running the Forge network login handshake.
        new ServerGamePacketListenerImpl(level.getServer(), new Connection(PacketFlow.SERVERBOUND), player);
        level.addNewPlayer(player);
        return player;
    }

    private static void stepThroughExit(ServerPlayer player, Vec3 arrival) {
        player.setPos(arrival.x, arrival.y, arrival.z + 2);
        tick(player);
        player.setPos(arrival.x, arrival.y, arrival.z);
        tick(player);
    }
}
