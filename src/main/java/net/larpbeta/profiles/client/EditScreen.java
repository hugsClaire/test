package net.larpbeta.profiles.client;

import net.larpbeta.profiles.ProfileRegistry;
import net.larpbeta.profiles.Protocol;
import net.minecraft.client.gui.ButtonElement;
import net.minecraft.client.gui.Screen;
import net.minecraft.client.gui.TextFieldElement;
import org.lwjgl.input.Keyboard;

/**
 * Create a character (when {@code slot} is null) or edit one: name, skin file, and arm model.
 * Deleting asks for a second click.
 */
public class EditScreen extends Screen {
	private static final int ID_SAVE = 1;
	private static final int ID_CANCEL = 2;
	private static final int ID_BROWSE = 3;
	private static final int ID_MODEL = 4;
	private static final int ID_REMOVE_SKIN = 5;
	private static final int ID_DELETE = 6;

	private final Protocol.SlotList list;
	private final Protocol.SlotInfo slot;

	private TextFieldElement nameField;
	private TextFieldElement skinField;
	private ButtonElement modelButton;
	private ButtonElement removeSkinButton;
	private ButtonElement deleteButton;
	private ButtonElement saveButton;

	private boolean slim;
	private boolean removeSkin;
	private boolean confirmDelete;
	private boolean busy;
	private String error = "";
	private volatile String pickedPath;

	public EditScreen(Protocol.SlotList list, Protocol.SlotInfo slot) {
		this.list = list;
		this.slot = slot;
		this.slim = slot != null && slot.slim;
	}

	@Override
	public void init() {
		Keyboard.enableRepeatEvents(true);
		String oldName = nameField != null ? nameField.getText() : slot != null ? slot.name : "";
		String oldPath = skinField != null ? skinField.getText() : "";
		buttons.clear();
		int cx = width / 2;
		int top = Math.max(30, height / 2 - 85);

		nameField = new TextFieldElement(this, fontRenderer, cx - 100, top + 14, 200, 20, oldName, "Character name");
		nameField.setMaxStringLength(ProfileRegistry.MAX_NAME_LENGTH);
		nameField.isFocused = true;
		skinField = new TextFieldElement(this, fontRenderer, cx - 100, top + 54, 150, 20, oldPath, "Skin file (optional)");
		skinField.setMaxStringLength(260);

		buttons.add(new ButtonElement(ID_BROWSE, cx + 54, top + 54, 46, 20, "Browse"));
		modelButton = new ButtonElement(ID_MODEL, cx - 100, top + 80, 200, 20, modelLabel());
		buttons.add(modelButton);
		if (slot != null) {
			removeSkinButton = new ButtonElement(ID_REMOVE_SKIN, cx - 100, top + 104, 200, 20, removeSkinLabel());
			removeSkinButton.enabled = slot.hasSkin;
			buttons.add(removeSkinButton);
		}
		saveButton = new ButtonElement(ID_SAVE, cx - 100, top + 130, 98, 20, slot == null ? "Create" : "Save");
		buttons.add(saveButton);
		buttons.add(new ButtonElement(ID_CANCEL, cx + 2, top + 130, 98, 20, "Cancel"));
		if (slot != null) {
			deleteButton = new ButtonElement(ID_DELETE, cx - 100, top + 154, 200, 20, deleteLabel());
			buttons.add(deleteButton);
		}
		applyBusy();
	}

	private String modelLabel() {
		return slim ? "Arm model: Slim (Alex)" : "Arm model: Classic (Steve)";
	}

	private String removeSkinLabel() {
		return removeSkin ? "Skin will be removed (click to undo)" : "Remove current skin";
	}

	private String deleteLabel() {
		return confirmDelete ? "Click again to delete forever" : "Delete character";
	}

	private void applyBusy() {
		for (ButtonElement button : buttons) {
			if (button.id == ID_CANCEL) {
				continue;
			}
			if (busy) {
				button.enabled = false;
			}
		}
	}

	@Override
	public void removed() {
		Keyboard.enableRepeatEvents(false);
	}

	/** The server answered our create, edit or delete request. */
	public void onReply(Protocol.SlotList reply) {
		if (reply.ok) {
			mc.displayScreen(new PickerScreen(reply));
		} else {
			busy = false;
			confirmDelete = false;
			error = reply.message;
			init();
		}
	}

	@Override
	protected void buttonClicked(ButtonElement button) {
		if (!button.enabled) {
			return;
		}
		switch (button.id) {
			case ID_CANCEL:
				mc.displayScreen(new PickerScreen(list));
				break;
			case ID_BROWSE:
				SkinFiles.browse(path -> pickedPath = path);
				break;
			case ID_MODEL:
				slim = !slim;
				modelButton.displayString = modelLabel();
				break;
			case ID_REMOVE_SKIN:
				removeSkin = !removeSkin;
				if (removeSkin) {
					skinField.setText("");
				}
				removeSkinButton.displayString = removeSkinLabel();
				break;
			case ID_DELETE:
				if (!confirmDelete) {
					confirmDelete = true;
					deleteButton.displayString = deleteLabel();
				} else {
					busy = true;
					ClientState.send(Protocol.DELETE, Protocol.encodeId(slot.id));
					applyBusy();
				}
				break;
			case ID_SAVE:
				save();
				break;
			default:
				break;
		}
	}

	private void save() {
		String name = nameField.getText().trim();
		if (!ProfileRegistry.isValidName(name)) {
			error = "Names are 1 to " + ProfileRegistry.MAX_NAME_LENGTH + " letters, numbers or underscores.";
			return;
		}
		byte[] png = new byte[0];
		boolean haveFile = !skinField.getText().trim().isEmpty();
		if (haveFile) {
			SkinFiles.Result result = SkinFiles.read(skinField.getText());
			if (result.error != null) {
				error = result.error;
				return;
			}
			png = result.png;
		}
		error = "";
		busy = true;
		if (slot == null) {
			ClientState.send(Protocol.CREATE, Protocol.encodeCreate(new Protocol.Create(name, slim, png)));
		} else {
			boolean updateSkin = haveFile || removeSkin;
			ClientState.send(Protocol.EDIT, Protocol.encodeEdit(new Protocol.Edit(slot.id, name, updateSkin, slim, png)));
		}
		applyBusy();
	}

	@Override
	public void keyPressed(char eventChar, int eventKey, int mouseX, int mouseY) {
		if (eventKey == Keyboard.KEY_ESCAPE) {
			mc.displayScreen(new PickerScreen(list));
			return;
		}
		if (busy) {
			return;
		}
		if (eventKey == Keyboard.KEY_TAB) {
			boolean toSkin = nameField.isFocused;
			nameField.isFocused = !toSkin;
			skinField.isFocused = toSkin;
			return;
		}
		if (eventKey == Keyboard.KEY_RETURN || eventKey == Keyboard.KEY_NUMPADENTER) {
			save();
			return;
		}
		if (nameField.isFocused) {
			boolean typable = eventChar >= 32 && eventChar != 127;
			if (typable && !(eventChar < 128 && (Character.isLetterOrDigit(eventChar) || eventChar == '_'))) {
				return; // names only allow letters, numbers and underscores
			}
			nameField.textboxKeyTyped(eventChar, eventKey);
		} else if (skinField.isFocused) {
			skinField.textboxKeyTyped(eventChar, eventKey);
		}
	}

	@Override
	public void mouseClicked(int x, int y, int button) {
		super.mouseClicked(x, y, button);
		if (!busy) {
			nameField.mouseClicked(x, y, button);
			skinField.mouseClicked(x, y, button);
		}
	}

	@Override
	public void tick() {
		ClientState.tickConnection();
		nameField.updateCursorCounter();
		skinField.updateCursorCounter();
		String path = pickedPath;
		if (path != null) {
			pickedPath = null;
			skinField.setText(path);
			removeSkin = false;
			if (removeSkinButton != null) {
				removeSkinButton.displayString = removeSkinLabel();
			}
		}
	}

	@Override
	public void render(int mouseX, int mouseY, float partialTick) {
		renderBackground();
		int cx = width / 2;
		int top = Math.max(30, height / 2 - 85);
		drawStringCenteredShadow(fontRenderer, slot == null ? "New character" : "Edit " + slot.name, cx, top - 18, 0xFFFFFF);
		drawStringShadow(fontRenderer, "Name", cx - 100, top + 3, 0xA0A0A0);
		drawStringShadow(fontRenderer, "Skin: a 64x64 PNG from your computer", cx - 100, top + 43, 0xA0A0A0);
		nameField.drawTextBox();
		skinField.drawTextBox();
		String text = busy ? "Working..." : error;
		if (text != null && !text.isEmpty()) {
			drawStringCenteredShadow(fontRenderer, text, cx, top + 180, busy ? 0xFFFFFF : 0xFF5555);
		}
		super.render(mouseX, mouseY, partialTick);
	}
}
