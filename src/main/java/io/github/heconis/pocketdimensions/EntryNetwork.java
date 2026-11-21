package io.github.heconis.pocketdimensions;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

final class EntryNetwork {
    private static final String VERSION = "1";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(PocketDimensions.id("entry"),
            () -> VERSION, VERSION::equals, VERSION::equals);

    static void register() {
        CHANNEL.messageBuilder(Enter.class, 0, NetworkDirection.PLAY_TO_SERVER)
                .encoder((message, buffer) -> {})
                .decoder(buffer -> new Enter())
                .consumerMainThread((message, context) -> {
                    ServerPlayer player = context.get().getSender();
                    if (player != null) {
                        PocketTravel.startCast(player);
                    }
                })
                .add();
    }

    static void requestEntry() {
        CHANNEL.sendToServer(new Enter());
    }

    private record Enter() {}
}
