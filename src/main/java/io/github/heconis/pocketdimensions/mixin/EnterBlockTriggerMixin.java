package io.github.heconis.pocketdimensions.mixin;

import io.github.heconis.pocketdimensions.PocketRoom;
import net.minecraft.advancements.critereon.EnterBlockTrigger;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EnterBlockTrigger.class)
abstract class EnterBlockTriggerMixin {
    @Inject(method = "trigger(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/world/level/block/state/BlockState;)V",
            at = @At("HEAD"), cancellable = true)
    private void pocketDimensions$skipReturnGateway(ServerPlayer player, BlockState state, CallbackInfo ci) {
        if (state.is(Blocks.END_GATEWAY) && PocketRoom.touchesReturnGateway(player)) {
            ci.cancel();
        }
    }
}
