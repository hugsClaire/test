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

	/**
	 * BTA's own login timer counts calls to tryLogin, and the server calls that about a thousand times
	 * a second, so its fixed limit of 30000 is only around 30 to 60 seconds. While a player is at the
	 * picker we keep it at zero and use real clock time instead.
	 */
	@Shadow(remap = false)
	private int loginTimer;

	/**
	 * The name BTA prints in its own log lines for this connection ("... logged in", "... lost connection").
	 * BTA sets it to the real account name; we replace it so the account name never reaches the log.
	 */
	@Shadow(remap = false)
	private String username;

	/** How long a client may take to answer the character list at all, i.e. to show it has the mod. */
	@Unique
	private static final long PROFILES$MOD_ANSWER_NANOS = 20_000_000_000L;
	/** How long a player may sit in the picker without doing anything. */
	@Unique
	private static final long PROFILES$IDLE_NANOS = 30L * 60L * 1_000_000_000L;

	/** Whether the client has told us it has the mod. */
	@Unique
	private boolean profiles$greeted;
	/** When we sent the list, and when the player last did something in the picker (System.nanoTime). */
	@Unique
	private long profiles$sentAt;
	@Unique
	private long profiles$lastActivity;

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
				Profiles.LOGGER.info("A player (account {}) is joining as character {}", loginPacket.uuid, profiles$chosen);
				username = profiles$chosen.name;
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
			profiles$sentAt = System.nanoTime();
			profiles$lastActivity = profiles$sentAt;
			profiles$account = loginPacket.uuid;
			username = null; // BTA's log lines then show only the address, not the account name
			profiles$send(service.slotList(profiles$account, "", true));
			Profiles.LOGGER.info("Sent the character list to a connecting player; waiting for the client's mod to answer.");
			ci.cancel();
		} catch (Exception e) {
			// Fail closed: never let someone in under their real account name.
			Profiles.LOGGER.error("Could not set up the character picker; disconnecting the player.", e);
			kickUser("Profile error - please tell the server owner.");
			ci.cancel();
		}
	}

	/**
	 * Runs every time BTA checks this connection. While the login is held, keeps BTA's own (much too short)
	 * login timer from firing, and enforces real-time limits instead.
	 */
	@Inject(method = "tryLogin", at = @At("HEAD"), remap = false)
	private void profiles$holdLogin(CallbackInfo ci) {
		if (profiles$pending == null || profiles$chosen != null) {
			return;
		}
		loginTimer = 0;
		long now = System.nanoTime();
		if (!profiles$greeted && now - profiles$sentAt > PROFILES$MOD_ANSWER_NANOS) {
			Profiles.LOGGER.info("Disconnecting {}: the BETA LARP mod did not answer.", profiles$account);
			kickUser("This server needs the BETA LARP mod. Install the server's modpack and try again.");
		} else if (now - profiles$lastActivity > PROFILES$IDLE_NANOS) {
			Profiles.LOGGER.info("Disconnecting {}: idle in the character picker for too long.", profiles$account);
			kickUser("You were idle in the character picker for too long. Reconnect to try again.");
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
			// Also sent every few seconds as a keep-alive, so the connection is not dropped for being silent.
			if (!profiles$greeted) {
				Profiles.LOGGER.info("The BETA LARP mod on {}'s client answered.", profiles$account);
			}
			profiles$greeted = true;
			return;
		}
		profiles$lastActivity = System.nanoTime();
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
