package com.b2tmapper.mixin;

import com.b2tmapper.client.MapStreamingService;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.ChunkDataS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public class ChunkDataMixin {

    @Inject(method = "onChunkData", at = @At("HEAD"))
    private void mcmapper_markChunkAsServerVerified(ChunkDataS2CPacket packet, CallbackInfo ci) {
        int chunkX = packet.getChunkX();
        int chunkZ = packet.getChunkZ();
        MapStreamingService.markChunkAsServerVerified(chunkX, chunkZ);
    }
}
