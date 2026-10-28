package net.larpbeta.profiles.mixin;

import net.larpbeta.profiles.Protocol;
import net.larpbeta.profiles.client.ClientPackets;
import net.minecraft.client.net.handler.PacketHandlerClient;
import net.minecraft.core.net.packet.PacketCustomPayload;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Routes the server's character list and skin messages to the picker. Everything else is left to BTA. */
@Mixin(PacketHandlerClient.class)
public class ClientPacketHandlerMixin {
	@Inject(method = "handleCustomPayload", at = @At("HEAD"), cancellable = true, remap = false)
	private void profiles$ourMessages(PacketCustomPayload packet, CallbackInfo ci) {
		if (Protocol.isOurs(packet.channel)) {
			ci.cancel();
			ClientPackets.handle((PacketHandlerClient) (Object) this, packet.channel, packet.data);
		}
	}
}
