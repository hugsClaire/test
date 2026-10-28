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
		int y = 46;
		for (Protocol.SlotInfo slot : list.slots) {
			if (slot.banned) {
				drawStringCenteredShadow(fontRenderer, slot.name, width / 2, y, 0xFF5555);
				y += 14;
			}
		}
		super.render(mouseX, mouseY, partialTick);
	}
}
