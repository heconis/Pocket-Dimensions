package io.github.heconis.pocketdimensions;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = PocketDimensions.ID, value = Dist.CLIENT)
public final class EntryKey {
    private static final KeyMapping ENTER = new KeyMapping("key.pocket_dimensions.enter", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_P, "key.categories.pocket_dimensions");

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) {
            return;
        }

        boolean pressed = false;
        while (ENTER.consumeClick()) {
            pressed = true;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (pressed && minecraft.player != null && minecraft.getConnection() != null
                && ENTER.getKeyConflictContext().isActive()) {
            // Minecraft also defaults Social Interactions to P. Keep the pocket action usable on that shared key.
            KeyMapping social = minecraft.options.keySocialInteractions;
            if (social.getKey().equals(ENTER.getKey())) {
                while (social.consumeClick()) {}
            }
            EntryNetwork.requestEntry();
        }
    }

    @Mod.EventBusSubscriber(modid = PocketDimensions.ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        @SubscribeEvent
        public static void registerKeys(RegisterKeyMappingsEvent event) {
            event.register(ENTER);
        }
    }
}
