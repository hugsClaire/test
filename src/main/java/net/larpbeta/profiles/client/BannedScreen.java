package net.larpbeta.profiles.client;

import net.larpbeta.profiles.Protocol;
import net.minecraft.client.gui.ButtonElement;
import net.minecraft.client.gui.Screen;
import org.lwjgl.input.Keyboard;

/** A read-only list of the player's banned characters. */
public class BannedScreen extends Screen {
	private static final int ID_BACK = 1;

	private final Protocol.SlotList list;

	public BannedScreen(Protocol.SlotList list) {
		this.list = list;
	}

	@Override
	public void init() {
		buttons.clear();
		buttons.add(new ButtonElement(ID_BACK, width / 2 - 100, height - 24, 200, 20, "Back"));
	}

	@Override
	protected void buttonClicked(ButtonElement button) {
		if (button.id == ID_BACK) {
			mc.displayScreen(new PickerScreen(list));
		}
	}

	@Override
	public void keyPressed(char eventChar, int eventKey, int mouseX, int mouseY) {
		if (eventKey == Keyboard.KEY_ESCAPE) {
			mc.displayScreen(new PickerScreen(list));
		}
	}

	@Override
	public void tick() {
		ClientState.tickConnection();
	}

	@Override
	public void render(int mouseX, int mouseY, float partialTick) {
		renderBackground();
		drawStringCenteredShadow(fontRenderer, "Banned characters", width / 2, 12, 0xFFFFFF);
		drawStringCenteredShadow(fontRenderer, "These cannot be played, edited or deleted. Ask the server owner about an unban.", width / 2, 25, 0xA0A0A0);
		java.util.List<String> names = new java.util.ArrayList<>();
		for (Protocol.SlotInfo slot : list.slots) {
			if (slot.banned) {
				names.add(slot.name);
			}
		}
		int y = 46;
		int rows = Math.max(2, (height - 24 - 8 - y) / 14); // what fits above the Back button
		boolean clipped = names.size() > rows;
		int shown = clipped ? rows - 1 : names.size();
		for (int i = 0; i < shown; i++) {
			drawStringCenteredShadow(fontRenderer, names.get(i), width / 2, y, 0xFF5555);
			y += 14;
		}
		if (clipped) {
			drawStringCenteredShadow(fontRenderer, "...and " + (names.size() - shown) + " more", width / 2, y, 0xA0A0A0);
		}
		super.render(mouseX, mouseY, partialTick);
	}
}
