package net.larpbeta.profiles.client;

import net.larpbeta.profiles.Profiles;
import net.larpbeta.profiles.Protocol;
import net.minecraft.client.net.handler.PacketHandlerClient;
import net.minecraft.core.net.packet.PacketCustomPayload;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * What the client knows about the BETA LARP server it is connected to: which character it picked,
 * and the skins other characters' clients have asked the server for.
 */
public final class ClientState {
	private ClientState() {
	}

	/** True once the server has shown us the character picker, i.e. it is a BETA LARP server. */
	public static volatile boolean active;
	/** The character we are playing (the one picked in the picker). */
	public static volatile UUID selected;
	/** The connection the picker is running on. */
	public static volatile PacketHandlerClient handler;

	/** A character's skin as the server sent it. {@code image} is null when the character has no custom skin. */
	public static final class Skin {
		public final boolean slim;
		public final BufferedImage image;
		/** Changes when the picture changes, so the game does not reuse an old texture under the same name. */
		public final int version;

		Skin(boolean slim, BufferedImage image, int version) {
			this.slim = slim;
			this.image = image;
			this.version = version;
		}
	}

	private static final Map<UUID, Skin> skins = new ConcurrentHashMap<>();
	private static final Map<UUID, CountDownLatch> waiting = new ConcurrentHashMap<>();

	/** Called when the server sends the character list: remembers the connection and forgets old skins. */
	static void onConnection(PacketHandlerClient newHandler) {
		boolean first = handler != newHandler;
		active = true;
		handler = newHandler;
		if (first) {
			skins.clear();
			// Tell the server this client has the mod (it disconnects clients that never answer).
			send(Protocol.HELLO, new byte[0]);
		}
	}

	/** Called when a new connection starts: forget everything about the previous server. */
	public static void reset() {
		active = false;
		selected = null;
		handler = null;
		skins.clear();
		waiting.clear();
	}

	public static void send(String channel, byte[] data) {
		PacketHandlerClient h = handler;
		if (h != null) {
			h.addToSendQueue(new PacketCustomPayload(channel, data));
		}
	}

	static void onSkin(Protocol.SkinData data) {
		BufferedImage image = null;
		int version = 0;
		if (data.png.length > 0) {
			try {
				image = ImageIO.read(new ByteArrayInputStream(data.png));
				version = java.util.Arrays.hashCode(data.png);
			} catch (Exception e) {
				Profiles.LOGGER.warn("Could not read a skin from the server", e);
			}
		}
		skins.put(data.id, new Skin(data.slim, image, version));
		CountDownLatch latch = waiting.remove(data.id);
		if (latch != null) {
			latch.countDown();
		}
	}

	/** The skin if we already have it, else null. */
	public static Skin cachedSkin(UUID id) {
		return skins.get(id);
	}

	/**
	 * Gets a character's skin, asking the server if needed. Blocks, so only call from a background thread.
	 * Returns null if the server did not answer in time.
	 */
	public static Skin fetchSkin(UUID id) {
		Skin cached = skins.get(id);
		if (cached != null) {
			return cached;
		}
		CountDownLatch latch = waiting.computeIfAbsent(id, key -> {
			send(Protocol.SKIN_REQUEST, Protocol.encodeId(key));
			return new CountDownLatch(1);
		});
		try {
			latch.await(15, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
		return skins.get(id);
	}

	private static int keepAliveCounter;

	/**
	 * Gives the connection a chance to read incoming packets while a picker screen has replaced the
	 * "Connecting" screen. Also tells the server every few seconds that we are still here: BTA drops a
	 * connection that stays completely silent.
	 */
	static void tickConnection() {
		PacketHandlerClient h = handler;
		if (h != null) {
			h.tick();
			if (++keepAliveCounter >= 100) { // about every 5 seconds
				keepAliveCounter = 0;
				send(Protocol.HELLO, new byte[0]);
			}
		}
	}
}
