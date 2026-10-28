package net.larpbeta.profiles.mixin;

import net.larpbeta.profiles.CharacterService;
import net.larpbeta.profiles.ProfileRegistry;
import net.larpbeta.profiles.Profiles;
import net.larpbeta.profiles.Protocol;
import net.minecraft.core.net.packet.PacketCustomPayload;
import net.minecraft.server.net.handler.PacketHandlerServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.IOException;

/** Answers other players' clients when they ask for a character's skin. */
@Mixin(PacketHandlerServer.class)
public abstract class PacketHandlerServerMixin {
	@Shadow(remap = false)
	public abstract void sendPacket(net.minecraft.core.net.packet.Packet packet);

	@Inject(method = "handleCustomPayload", at = @At("HEAD"), cancellable = true, remap = false)
	private void profiles$skinRequests(PacketCustomPayload packet, CallbackInfo ci) {
		if (!Protocol.SKIN_REQUEST.equals(packet.channel)) {
			return;
		}
		ci.cancel();
		ProfileRegistry registry = Profiles.registry();
		if (registry == null || Profiles.skinStore() == null) {
			return;
		}
		try {
			CharacterService service = new CharacterService(registry, Profiles.skinStore(), id -> false);
			sendPacket(new PacketCustomPayload(Protocol.SKIN, Protocol.encodeSkin(service.skinOf(Protocol.decodeId(packet.data)))));
		} catch (IOException | RuntimeException e) {
			Profiles.LOGGER.warn("Ignoring a bad skin request", e);
		}
	}
}
