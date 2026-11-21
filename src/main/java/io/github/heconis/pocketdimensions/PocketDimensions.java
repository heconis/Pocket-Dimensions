package io.github.heconis.pocketdimensions;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.PushReaction;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

@Mod(PocketDimensions.ID)
public final class PocketDimensions {
    public static final String ID = "pocket_dimensions";
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, ID);
    static final RegistryObject<Block> EXIT = BLOCKS.register("exit", () -> new Block(BlockBehaviour.Properties.of()
            .noCollission().strength(-1.0F, 3_600_000.0F).noLootTable()
            .pushReaction(PushReaction.BLOCK).lightLevel(state -> 12)));

    public PocketDimensions(FMLJavaModLoadingContext context) {
        BLOCKS.register(context.getModEventBus());
        EntryNetwork.register();
    }

    static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(ID, path);
    }
}
