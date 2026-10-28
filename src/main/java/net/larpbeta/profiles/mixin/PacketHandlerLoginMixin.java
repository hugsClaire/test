package net.larpbeta.profiles.mixin;

import net.larpbeta.profiles.CharacterService;
import net.larpbeta.profiles.ProfileRegistry;
import net.larpbeta.profiles.Profiles;
import net.larpbeta.profiles.Protocol;
import net.minecraft.core.net.NetworkManager;
import net.minecraft.core.net.packet.Packet;
import net.minecraft.core.net.packet.PacketCustomPayload;
import net.minecraft.core.net.packet.PacketLogin;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.net.handler.PacketHandlerLogin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

/**
 * doLogin is the single point where the server turns a verified login into a player entity:
 * it passes loginPacket.username and loginPacket.uuid to playerList.getPlayerForLogin and then
 * loads that player's saved data.
 *
 * The first time doLogin runs for a connection we HOLD the login: we remember the verified packet
 * and send the client its character list. The client answers with picker messages (select, create,
 * edit, delete); once it selects a character we replace username and uuid in the packet with the
 * character's and run doLogin again, so the rest of the login only ever sees the character.
 */
@Mixin(PacketHandlerLogin.class)
public abstract class PacketHandlerLoginMixin {
	@Shadow(remap = false)
	public NetworkManager netManager;
	@Shadow(remap = false)
	@Final
	private MinecraftServer mcServer;

	@Shadow(remap = false)
	public abstract void kickUser(String reason);

	@Shadow(remap = false)
	public abstract void doLogin(PacketLogin packet);

	@Shadow(remap = false)
	public abstract void handleInvalidPacket(Packet packet);

	/** The verified login we are holding while the player picks a character. */
	@Unique
	private PacketLogin profiles$pending;
	/** The real account (verified) behind this connection. Never changes once set. */
	@Unique
	private UUID profiles$account;
	/** The character picked; set just before the login is resumed. */
	@Unique
	private ProfileRegistry.Slot profiles$chosen;

	/** Whether the client has told us it has the mod, and how long we have been waiting to hear. */
	@Unique
	private boolean profiles$greeted;
	@Unique
	private int profiles$waitTicks;

	@Unique
	private CharacterService profiles$service() {
		ProfileRegistry registry = Profiles.registry();
		if (registry == null) {
			throw new IllegalStateException("the profile registry is not loaded");
		}
		java.util.Set<UUID> bans = ((PlayerListAccessor) mcServer.playerList).profiles$bans();
		return new CharacterService(registry, Profiles.skinStore(), id -> {
			synchronized (mcServer.playerList) {
				return bans.contains(id);
			}
		});
	}

	@Unique
	private void profiles$send(CharacterService.Reply reply) {
		netManager.addToSendQueue(new PacketCustomPayload(reply.channel, reply.data));
	}

	@Inject(method = "doLogin", at = @At("HEAD"), cancellable = true, remap = false)
	private void profiles$pickCharacter(PacketLogin loginPacket, CallbackInfo ci) {
		try {
			if (profiles$chosen != null) {
				// Second pass: the player picked. Become the character.
				Profiles.LOGGER.info("Account {} ({}) is joining as character {}", loginPacket.username, loginPacket.uuid, profiles$chosen);
				loginPacket.username = profiles$chosen.name;
				loginPacket.uuid = profiles$chosen.id;
				return;
			}
			// First pass: hold the login and show the picker.
			if (profiles$pending != null) {
				ci.cancel();
				return;
			}
			CharacterService service = profiles$service();
			profiles$pending = loginPacket;
			profiles$account = loginPacket.uuid;
			profiles$send(service.slotList(profiles$account, "", true));
			Profiles.LOGGER.info("Sent the character list to {}; waiting for the client's mod to answer.", loginPacket.username);
			ci.cancel();
		} catch (Exception e) {
			// Fail closed: never let someone in under their real account name.
			Profiles.LOGGER.error("Could not set up the character picker for {}; disconnecting them.", loginPacket.username, e);
			kickUser("Profile error - please tell the server owner.");
			ci.cancel();
		}
	}

	/** A client without the mod never answers; do not leave it hanging on "Logging in..." for half an hour. */
	@Inject(method = "tryLogin", at = @At("HEAD"), remap = false)
	private void profiles$waitForMod(CallbackInfo ci) {
		if (profiles$pending != null && !profiles$greeted && ++profiles$waitTicks > 400) {
			Profiles.LOGGER.info("Disconnecting {}: the BETA LARP mod did not answer.", profiles$account);
			kickUser("This server needs the BETA LARP mod. Install the server's modpack and try again.");
		}
	}

	/**
	 * The login handler normally rejects custom payloads. While the login is held we accept the
	 * picker messages; anything else is rejected as before.
	 */
	public void handleCustomPayload(PacketCustomPayload packet) {
		if (profiles$pending == null || profiles$chosen != null || !Protocol.isOurs(packet.channel)) {
			handleInvalidPacket(packet);
			return;
		}
		if (Protocol.HELLO.equals(packet.channel)) {
			profiles$greeted = true;
			Profiles.LOGGER.info("The BETA LARP mod on {}'s client answered.", profiles$account);
			return;
		}
		try {
			CharacterService.Outcome outcome = profiles$service().handle(profiles$account, packet.channel, packet.data);
			for (CharacterService.Reply reply : outcome.replies) {
				profiles$send(reply);
			}
			if (outcome.joinAs != null) {
				profiles$chosen = outcome.joinAs;
				doLogin(profiles$pending);
			}
		} catch (Exception e) {
			Profiles.LOGGER.error("Picker error for account {}", profiles$account, e);
			kickUser("Profile error - please tell the server owner.");
		}
	}
}
