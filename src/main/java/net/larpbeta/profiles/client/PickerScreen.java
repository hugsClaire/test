package net.larpbeta.profiles.client;

import net.larpbeta.profiles.Protocol;
import net.minecraft.client.gui.ButtonElement;
import net.minecraft.client.gui.Screen;
import net.minecraft.client.gui.ScreenMainMenu;
import net.minecraft.client.gui.server.ScreenSelectServer;
import org.lwjgl.input.Keyboard;

import java.util.ArrayList;
import java.util.List;

/**
 * Shown right after connecting, while the server holds the login: pick which character to play,
 * or create / edit one. Banned characters are not listed here; the "Banned" button shows them.
 */
public class PickerScreen extends Screen {
	private static final int ID_NEW = 1;
	private static final int ID_DISCONNECT = 2;
	private static final int ID_BANNED = 3;
	private static final int ID_SELECT = 100;
	private static final int ID_EDIT = 200;

	private Protocol.SlotList list;
	/** The characters that can be played (everything that is not banned). */
	private List<Protocol.SlotInfo> playable = new ArrayList<>();
	private int bannedCount;
	private boolean busy;
	private String busyText = "";

	public PickerScreen(Protocol.SlotList list) {
		setList(list);
	}

	private void setList(Protocol.SlotList newList) {
		this.list = newList;
		playable = new ArrayList<>();
		bannedCount = 0;
		for (Protocol.SlotInfo slot : newList.slots) {
			if (slot.banned) {
				bannedCount++;
			} else {
				playable.add(slot);
			}
		}
	}

	/** The server sent a fresh list (after a create, edit or delete, or because a request was refused). */
	public void update(Protocol.SlotList newList) {
		setList(newList);
		this.busy = false;
		init();
	}

	@Override
	public void init() {
		buttons.clear();
		int cx = width / 2;
		int top = 44;
		int bottom = height - 66;
		int rowHeight = Math.min(24, Math.max(14, (bottom - top) / Math.max(playable.size(), 1)));
		int buttonHeight = Math.min(20, rowHeight - 2);
		for (int i = 0; i < playable.size(); i++) {
			Protocol.SlotInfo slot = playable.get(i);
			int y = top + i * rowHeight;
			ButtonElement select = new ButtonElement(ID_SELECT + i, cx - 100, y, 150, buttonHeight, slot.name);
			select.enabled = !busy;
			buttons.add(select);
			ButtonElement edit = new ButtonElement(ID_EDIT + i, cx + 54, y, 46, buttonHeight, "Edit");
			edit.enabled = !busy;
			buttons.add(edit);
		}
		ButtonElement create = new ButtonElement(ID_NEW, cx - 100, height - 48, 98, 20, "New character");
		create.enabled = !busy && playable.size() < list.maxSlots;
		buttons.add(create);
		ButtonElement banned = new ButtonElement(ID_BANNED, cx + 2, height - 48, 98, 20, "Banned (" + bannedCount + ")");
		banned.enabled = !busy && bannedCount > 0;
		buttons.add(banned);
		buttons.add(new ButtonElement(ID_DISCONNECT, cx - 100, height - 24, 200, 20, "Disconnect"));
	}

	@Override
	protected void buttonClicked(ButtonElement button) {
		if (!button.enabled) {
			return;
		}
		if (button.id == ID_DISCONNECT) {
			disconnect();
		} else if (button.id == ID_NEW) {
			mc.displayScreen(new EditScreen(list, null));
		} else if (button.id == ID_BANNED) {
			mc.displayScreen(new BannedScreen(list));
		} else if (button.id >= ID_EDIT && button.id < ID_EDIT + 100) {
			mc.displayScreen(new EditScreen(list, playable.get(button.id - ID_EDIT)));
		} else if (button.id >= ID_SELECT && button.id < ID_SELECT + 100) {
			Protocol.SlotInfo slot = playable.get(button.id - ID_SELECT);
			ClientState.selected = slot.id;
			busy = true;
			busyText = "Joining as " + slot.name + "...";
			ClientState.send(Protocol.SELECT, Protocol.encodeId(slot.id));
			init();
		}
	}

	static void disconnect(Screen from) {
		if (ClientState.handler != null) {
			ClientState.handler.disconnect();
		}
		from.mc.displayScreen(new ScreenSelectServer(new ScreenMainMenu()));
	}

	private void disconnect() {
		disconnect(this);
	}

	@Override
	public void keyPressed(char eventChar, int eventKey, int mouseX, int mouseY) {
		if (eventKey == Keyboard.KEY_ESCAPE) {
			disconnect();
		}
	}

	@Override
	public void tick() {
		ClientState.tickConnection();
	}

	@Override
	public void render(int mouseX, int mouseY, float partialTick) {
		renderBackground();
		drawStringCenteredShadow(fontRenderer, "Choose your character", width / 2, 12, 0xFFFFFF);
		drawStringCenteredShadow(fontRenderer, "Other players only ever see your character.", width / 2, 25, 0xA0A0A0);
		if (playable.isEmpty() && !busy) {
			String hint = list.slots.isEmpty() ? "You have no characters yet. Create one to start." : "All of your characters are banned. Create a new one to play.";
			drawStringCenteredShadow(fontRenderer, hint, width / 2, height / 2 - 10, 0xFFFFFF);
		}
		String text = busy ? busyText : list.message;
		if (text != null && !text.isEmpty()) {
			int color = busy || list.ok ? 0x55FF55 : 0xFF5555;
			drawStringCenteredShadow(fontRenderer, text, width / 2, height - 60, color);
		}
		super.render(mouseX, mouseY, partialTick);
	}
}
