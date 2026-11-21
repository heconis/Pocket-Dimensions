package io.github.heconis.pocketdimensions.mixin;

import io.github.heconis.pocketdimensions.PocketRoom;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.TheEndGatewayBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(TheEndGatewayBlockEntity.class)
abstract class EndGatewayMixin extends BlockEntity {
    protected EndGatewayMixin(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Inject(method = "teleportTick", at = @At("HEAD"), cancellable = true)
    private static void pocketDimensions$disableVanillaTeleport(Level level, BlockPos pos, BlockState state,
            TheEndGatewayBlockEntity gateway, CallbackInfo ci) {
        if (level.dimension().equals(PocketRoom.DIMENSION)) {
            ci.cancel();
        }
    }

    @Inject(method = {"isSpawning", "isCoolingDown"}, at = @At("HEAD"), cancellable = true, require = 2)
    private void pocketDimensions$hideBeams(CallbackInfoReturnable<Boolean> cir) {
        Level level = getLevel();
        if (level != null && level.dimension().equals(PocketRoom.DIMENSION)) {
            cir.setReturnValue(false);
        }
    }
}
