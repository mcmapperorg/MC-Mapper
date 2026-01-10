package com.b2tmapper.mixin;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.MapColor;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractBlock.AbstractBlockState.class)
public abstract class CryingObsidianMapColorMixin {

    @Inject(method = "getMapColor(Lnet/minecraft/world/BlockView;Lnet/minecraft/util/math/BlockPos;)Lnet/minecraft/block/MapColor;", at = @At("HEAD"), cancellable = true)
    private void mcmapper_overrideCryingObsidianColor(BlockView world, BlockPos pos, CallbackInfoReturnable<MapColor> cir) {
        BlockState self = (BlockState) (Object) this;
        
        if (self.isOf(Blocks.CRYING_OBSIDIAN)) {
            cir.setReturnValue(MapColor.PURPLE);
        }
    }
}
