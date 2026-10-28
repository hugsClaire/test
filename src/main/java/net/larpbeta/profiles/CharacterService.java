package net.larpbeta.profiles;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * What the server does with the client's picker messages. Plain Java (no Minecraft classes) so it can be tested alone.
 *
 * Every request is checked against the real account that is logging in: you can only see, pick, edit
 * or delete your own characters, and a banned character cannot be picked or edited and a banned character cannot be deleted either.
 */
public final class CharacterService {
	private static final Logger LOGGER = LoggerFactory.getLogger("profiles");

	/** A packet to send back: a channel name and its bytes. */
	public static final class Reply {
		public final String channel;
		public final byte[] data;

		Reply(String channel, byte[] data) {
			this.channel = channel;
			this.data = data;
		}
	}

	/** What happened: the replies to send, and the character to log in as (null if still picking). */
	public static final class Outcome {
		public final List<Reply> replies = new ArrayList<>();
		public ProfileRegistry.Slot joinAs;
	}

	/** A request that cannot be done; the message is shown to the player. */
	private static final class Rejected extends Exception {
		Rejected(String message) {
			super(message);
		}
	}

	private final ProfileRegistry registry;
	private final SkinStore skins;
	private final Predicate<UUID> isBanned;

	public CharacterService(ProfileRegistry registry, SkinStore skins, Predicate<UUID> isBanned) {
		this.registry = registry;
		this.skins = skins;
		this.isBanned = isBanned;
	}

	/** The character list for an account, with an optional message to show above it. */
	public Reply slotList(UUID account, String message, boolean ok) {
		List<Protocol.SlotInfo> infos = new ArrayList<>();
		for (ProfileRegistry.Slot slot : registry.getSlots(account)) {
			infos.add(new Protocol.SlotInfo(slot.id, slot.name, isBanned.test(slot.id), skins.has(slot.id), skins.isSlim(slot.id)));
		}
		return new Reply(Protocol.SLOTS, Protocol.encodeSlots(new Protocol.SlotList(message, ok, ProfileRegistry.MAX_SLOTS_PER_ACCOUNT, infos)));
	}

	/** Handles one picker message from the account that is logging in. */
	public Outcome handle(UUID account, String channel, byte[] data) {
		Outcome outcome = new Outcome();
		String message = "";
		boolean ok = true;
		try {
			switch (channel) {
				case Protocol.SELECT:
					message = select(account, Protocol.decodeId(data), outcome);
					break;
				case Protocol.CREATE:
					message = create(account, Protocol.decodeCreate(data));
					break;
				case Protocol.EDIT:
					message = edit(account, Protocol.decodeEdit(data));
					break;
				case Protocol.DELETE:
					message = delete(account, Protocol.decodeId(data));
					break;
				default:
					message = "";
			}
		} catch (Rejected e) {
			message = e.getMessage();
			ok = false;
		} catch (IOException e) {
			message = "Something went wrong with that request.";
			ok = false;
		} catch (UncheckedIOException e) {
			LOGGER.error("Could not save character changes", e);
			message = "The server could not save that. Please tell the server owner.";
			ok = false;
		}
		if (outcome.joinAs == null) {
			outcome.replies.add(slotList(account, message, ok));
		}
		return outcome;
	}

	private String select(UUID account, UUID id, Outcome outcome) throws Rejected {
		ProfileRegistry.Slot slot = registry.findSlotById(account, id);
		if (slot == null) {
			throw new Rejected("That character no longer exists.");
		}
		if (isBanned.test(slot.id)) {
			throw new Rejected(slot.name + " is banned and cannot be played.");
		}
		registry.setActiveSlot(account, slot.name);
		outcome.joinAs = slot;
		return "";
	}

	private String create(UUID account, Protocol.Create request) throws Rejected {
		if (registry.getSlots(account).size() >= ProfileRegistry.MAX_SLOTS_PER_ACCOUNT) {
			throw new Rejected("You already have the maximum of " + ProfileRegistry.MAX_SLOTS_PER_ACCOUNT + " characters.");
		}
		if (!ProfileRegistry.isValidName(request.name)) {
			throw new Rejected("Names are 1 to " + ProfileRegistry.MAX_NAME_LENGTH + " letters, numbers or underscores.");
		}
		byte[] png = null;
		if (request.png.length > 0) {
			try {
				png = SkinStore.sanitize(request.png);
			} catch (SkinStore.BadSkinException e) {
				throw new Rejected(e.getMessage());
			}
		}
		ProfileRegistry.Slot slot;
		try {
			slot = registry.createSlot(account, request.name);
		} catch (IllegalArgumentException e) {
			throw new Rejected("That name is already taken.");
		}
		try {
			skins.write(slot.id, png);
			skins.setSlim(slot.id, request.slim);
		} catch (IOException e) {
			LOGGER.error("Could not save the skin for {}", slot, e);
			return "Created " + slot.name + ", but the skin could not be saved.";
		}
		return "Created " + slot.name + ".";
	}

	private String edit(UUID account, Protocol.Edit request) throws Rejected {
		ProfileRegistry.Slot slot = registry.findSlotById(account, request.id);
		if (slot == null) {
			throw new Rejected("That character no longer exists.");
		}
		if (isBanned.test(slot.id)) {
			throw new Rejected(slot.name + " is banned and cannot be changed.");
		}
		if (!ProfileRegistry.isValidName(request.name)) {
			throw new Rejected("Names are 1 to " + ProfileRegistry.MAX_NAME_LENGTH + " letters, numbers or underscores.");
		}
		byte[] png = null;
		if (request.updateSkin && request.png.length > 0) {
			try {
				png = SkinStore.sanitize(request.png);
			} catch (SkinStore.BadSkinException e) {
				throw new Rejected(e.getMessage());
			}
		}
		try {
			slot = registry.renameSlot(account, slot.id, request.name);
		} catch (IllegalArgumentException e) {
			throw new Rejected("That name is already taken.");
		}
		try {
			if (request.updateSkin) {
				skins.write(slot.id, png);
			}
			skins.setSlim(slot.id, request.slim);
		} catch (IOException e) {
			LOGGER.error("Could not save the skin for {}", slot, e);
			return "Saved the name, but the skin could not be saved.";
		}
		return "Saved " + slot.name + ".";
	}

	private String delete(UUID account, UUID id) throws Rejected {
		ProfileRegistry.Slot slot = registry.findSlotById(account, id);
		if (slot == null) {
			throw new Rejected("That character no longer exists.");
		}
		if (isBanned.test(slot.id)) {
			throw new Rejected(slot.name + " is banned and cannot be deleted.");
		}
		registry.deleteSlot(account, slot.id);
		skins.delete(slot.id);
		return "Deleted " + slot.name + ".";
	}

	/** The skin of any character, for other players' clients. Empty picture if it has none or is not a character. */
	public Protocol.SkinData skinOf(UUID id) {
		if (registry.accountOf(id) == null) {
			return new Protocol.SkinData(id, false, new byte[0]);
		}
		byte[] png = skins.read(id);
		return new Protocol.SkinData(id, skins.isSlim(id), png == null ? new byte[0] : png);
	}
}
