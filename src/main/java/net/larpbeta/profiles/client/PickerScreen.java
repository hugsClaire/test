package net.larpbeta.profiles.client;

import net.larpbeta.profiles.Protocol;
import net.minecraft.client.gui.ButtonElement;
import net.minecraft.client.gui.Screen;
import net.minecraft.client.gui.ScreenMainMenu;
import net.minecraft.client.gui.server.ScreenSelectServer;
import org.lwjgl.input.Keyboard;

import java.util.List;

/**
 * Shown right after connecting, while the server holds the login: pick which character to play,
 * or create / edit one. Banned characters are greyed out and cannot be picked or edited, only deleted.
 */
public class PickerScreen extends Screen {
	private static final int ID_NEW = 1;
	private static final int ID_DISCONNECT = 2;
	private static final int ID_SELECT = 100;
	private static final int ID_EDIT = 200;

	private Protocol.SlotList list;
	private boolean busy;
	/** The banned character whose Delete button was clicked once and now asks "Sure?". */
	private java.util.UUID confirmDelete;
	private String busyText = "";

	public PickerScreen(Protocol.SlotList list) {
		this.list = list;
	}

	/** The server sent a fresh list (after a create, edit or delete, or because a request was refused). */
	public void update(Protocol.SlotList newList) {
		this.list = newList;
		this.busy = false;
		this.confirmDelete = null;
		init();
	}

	@Override
	public void init() {
		buttons.clear();
		List<Protocol.SlotInfo> slots = list.slots;
		int cx = width / 2;
		int top = 44;
		int bottom = height - 30;
		int rowHeight = Math.min(24, Math.max(14, (bottom - top - 4) / Math.max(slots.size(), 1)));
		int buttonHeight = Math.min(20, rowHeight - 2);
		for (int i = 0; i < slots.size(); i++) {
			Protocol.SlotInfo slot = slots.get(i);
			int y = top + i * rowHeight;
			ButtonElement select = new ButtonElement(ID_SELECT + i, cx - 100, y, 150, buttonHeight, slot.banned ? slot.name + " (banned)" : slot.name);
			select.enabled = !slot.banned && !busy;
			buttons.add(select);
			// A banned character cannot be edited, only deleted.
			String sideLabel = !slot.banned ? "Edit" : slot.id.equals(confirmDelete) ? "Sure?" : "Delete";
			ButtonElement side = new ButtonElement(ID_EDIT + i, cx + 54, y, 46, buttonHeight, sideLabel);
			side.enabled = !busy;
			buttons.add(side);
		}
		ButtonElement create = new ButtonElement(ID_NEW, cx - 100, height - 24, 98, 20, "New character");
		create.enabled = !busy && slots.size() < list.maxSlots;
		buttons.add(create);
		buttons.add(new ButtonElement(ID_DISCONNECT, cx + 2, height - 24, 98, 20, "Disconnect"));
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
		} else if (button.id >= ID_EDIT && button.id < ID_EDIT + 100) {
			Protocol.SlotInfo slot = list.slots.get(button.id - ID_EDIT);
			if (!slot.banned) {
				mc.displayScreen(new EditScreen(list, slot));
			} else if (!slot.id.equals(confirmDelete)) {
				confirmDelete = slot.id; // first click: ask again
				init();
			} else {
				confirmDelete = null;
				busy = true;
				busyText = "Deleting " + slot.name + "...";
				ClientState.send(Protocol.DELETE, Protocol.encodeId(slot.id));
				init();
			}
		} else if (button.id >= ID_SELECT && button.id < ID_SELECT + 100) {
			Protocol.SlotInfo slot = list.slots.get(button.id - ID_SELECT);
			ClientState.selected = slot.id;
			busy = true;
			busyText = "Joining as " + slot.name + "...";
			ClientState.send(Protocol.SELECT, Protocol.encodeId(slot.id));
			init();
		}
	}

	private void disconnect() {
		if (ClientState.handler != null) {
			ClientState.handler.disconnect();
		}
		mc.displayScreen(new ScreenSelectServer(new ScreenMainMenu()));
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
		if (list.slots.isEmpty() && !busy) {
			drawStringCenteredShadow(fontRenderer, "You have no characters yet. Create one to start.", width / 2, height / 2 - 10, 0xFFFFFF);
		}
		String text = busy ? busyText : list.message;
		if (text != null && !text.isEmpty()) {
			int color = busy || list.ok ? 0x55FF55 : 0xFF5555;
			drawStringCenteredShadow(fontRenderer, text, width / 2, height - 38, color);
		}
		super.render(mouseX, mouseY, partialTick);
	}
}
