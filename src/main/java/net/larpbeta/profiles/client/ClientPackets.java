package net.larpbeta.profiles.client;

import net.larpbeta.profiles.Profiles;
import net.larpbeta.profiles.Protocol;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Screen;
import net.minecraft.client.net.handler.PacketHandlerClient;

import java.io.IOException;

/** Reads the messages the server sends the client (called from the packet handler, on the game thread). */
public final class ClientPackets {
	private ClientPackets() {
	}

	public static void handle(PacketHandlerClient handler, String channel, byte[] data) {
		Profiles.LOGGER.info("BETA LARP: got {} from the server ({} bytes)", channel, data == null ? 0 : data.length);
		try {
			if (Protocol.SLOTS.equals(channel)) {
				onSlots(handler, Protocol.decodeSlots(data));
			} else if (Protocol.SKIN.equals(channel)) {
				ClientState.onSkin(Protocol.decodeSkin(data));
			}
		} catch (IOException | RuntimeException | LinkageError e) {
			Profiles.LOGGER.warn("Ignoring a bad message on {}", channel, e);
		}
	}

	private static void onSlots(PacketHandlerClient handler, Protocol.SlotList list) {
		Profiles.LOGGER.info("BETA LARP: showing the character picker ({} characters)", list.slots.size());
		ClientState.onConnection(handler);
		Minecraft mc = Minecraft.getMinecraft();
		Screen current = mc.currentScreen;
		if (current instanceof PickerScreen) {
			((PickerScreen) current).update(list);
		} else if (current instanceof EditScreen) {
			((EditScreen) current).onReply(list);
		} else {
			mc.displayScreen(new PickerScreen(list));
		}
	}
}
