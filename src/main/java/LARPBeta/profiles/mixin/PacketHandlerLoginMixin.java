package LARPBeta.profiles.mixin;

import LARPBeta.profiles.ProfileRegistry;
import LARPBeta.profiles.Profiles;
import net.minecraft.core.net.packet.PacketLogin;
import net.minecraft.server.net.handler.PacketHandlerLogin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * doLogin is the single point where the server turns a verified login into a player entity:
 * it passes loginPacket.username and loginPacket.uuid to playerList.getPlayerForLogin and then
 * loads that player's saved data. By replacing both fields before that happens, the rest of the
 * login (data loading, join message, spawn packets to other players) only ever sees the character.
 */
@Mixin(PacketHandlerLogin.class)
public class PacketHandlerLoginMixin {

	@Inject(method = "doLogin", at = @At("HEAD"), cancellable = true, remap = false)
	private void profiles$swapIdentity(PacketLogin loginPacket, CallbackInfo ci) {
		PacketHandlerLogin self = (PacketHandlerLogin) (Object) this;
		try {
			ProfileRegistry registry = Profiles.registry();
			if (registry == null) {
				throw new IllegalStateException("the profile registry is not loaded");
			}
			ProfileRegistry.Slot slot = registry.getOrCreateActiveSlot(loginPacket.uuid);
			Profiles.LOGGER.info("Account {} ({}) is joining as character {}", loginPacket.username, loginPacket.uuid, slot);
			loginPacket.username = slot.name;
			loginPacket.uuid = slot.id;
		} catch (Exception e) {
			// Fail closed: never let someone in under their real account name.
			Profiles.LOGGER.error("Could not assign a character to {}; disconnecting them.", loginPacket.username, e);
			self.kickUser("Profile error - please tell the server owner.");
			ci.cancel();
		}
	}
}
