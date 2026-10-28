package net.larpbeta.profiles;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The messages the client and server exchange (as BTA custom payload packets) for the character picker and skins.
 * Plain Java, so it works on both sides and can be tested alone.
 *
 * Server to client: SLOTS (the character list, sent while the login is held), SKIN (a character's skin, on request).
 * Client to server: HELLO (the picker is showing), SELECT, CREATE, EDIT, DELETE while picking, SKIN_REQUEST at any time.
 */
public final class Protocol {
	private Protocol() {
	}

	/** Client to server, sent once the picker is showing, so the server knows this client has the mod. */
	public static final String HELLO = "larp:hello";
	public static final String SLOTS = "larp:slots";
	public static final String SELECT = "larp:select";
	public static final String CREATE = "larp:create";
	public static final String EDIT = "larp:edit";
	public static final String DELETE = "larp:delete";
	public static final String SKIN_REQUEST = "larp:skin_request";
	public static final String SKIN = "larp:skin";

	/** BTA allows 32 KB per payload; skins are kept well below that. */
	public static final int MAX_SKIN_BYTES = 24 * 1024;
	/** Longest message text we send or accept. */
	public static final int MAX_MESSAGE_LENGTH = 200;

	public static boolean isOurs(String channel) {
		return channel != null && channel.startsWith("larp:");
	}

	/** One line of the character list. */
	public static final class SlotInfo {
		public final UUID id;
		public final String name;
		public final boolean banned;
		public final boolean hasSkin;
		public final boolean slim;

		public SlotInfo(UUID id, String name, boolean banned, boolean hasSkin, boolean slim) {
			this.id = id;
			this.name = name;
			this.banned = banned;
			this.hasSkin = hasSkin;
			this.slim = slim;
		}
	}

	/** The whole character list message. */
	public static final class SlotList {
		/** A line to show the player ("Created Bob.", or why a request failed). May be empty. */
		public final String message;
		/** False if {@code message} explains a request that was refused. */
		public final boolean ok;
		public final int maxSlots;
		public final List<SlotInfo> slots;

		public SlotList(String message, boolean ok, int maxSlots, List<SlotInfo> slots) {
			this.message = message;
			this.ok = ok;
			this.maxSlots = maxSlots;
			this.slots = slots;
		}
	}

	/** A skin picture sent to the client. {@code png} is empty if the character has no custom skin. */
	public static final class SkinData {
		public final UUID id;
		public final boolean slim;
		public final byte[] png;

		public SkinData(UUID id, boolean slim, byte[] png) {
			this.id = id;
			this.slim = slim;
			this.png = png;
		}
	}

	/** Create request. */
	public static final class Create {
		public final String name;
		public final boolean slim;
		public final byte[] png;

		public Create(String name, boolean slim, byte[] png) {
			this.name = name;
			this.slim = slim;
			this.png = png;
		}
	}

	/** Edit request. If {@code updateSkin} is false the skin file is left alone; if true, {@code png} replaces it (empty removes it). */
	public static final class Edit {
		public final UUID id;
		public final String name;
		public final boolean updateSkin;
		public final boolean slim;
		public final byte[] png;

		public Edit(UUID id, String name, boolean updateSkin, boolean slim, byte[] png) {
			this.id = id;
			this.name = name;
			this.updateSkin = updateSkin;
			this.slim = slim;
			this.png = png;
		}
	}

	private interface Writer {
		void write(DataOutputStream out) throws IOException;
	}

	private static byte[] build(Writer writer) {
		try {
			ByteArrayOutputStream bytes = new ByteArrayOutputStream();
			DataOutputStream out = new DataOutputStream(bytes);
			writer.write(out);
			out.flush();
			return bytes.toByteArray();
		} catch (IOException e) {
			throw new IllegalStateException(e); // cannot happen: writing to memory
		}
	}

	private static void writeUuid(DataOutputStream out, UUID id) throws IOException {
		out.writeLong(id.getMostSignificantBits());
		out.writeLong(id.getLeastSignificantBits());
	}

	private static UUID readUuid(DataInputStream in) throws IOException {
		return new UUID(in.readLong(), in.readLong());
	}

	private static void writeBytes(DataOutputStream out, byte[] data) throws IOException {
		out.writeInt(data.length);
		out.write(data);
	}

	private static byte[] readBytes(DataInputStream in) throws IOException {
		int length = in.readInt();
		if (length < 0 || length > MAX_SKIN_BYTES) {
			throw new IOException("bad length " + length);
		}
		byte[] data = new byte[length];
		in.readFully(data);
		return data;
	}

	private static String readText(DataInputStream in, int max) throws IOException {
		String text = in.readUTF();
		if (text.length() > max) {
			throw new IOException("text too long");
		}
		return text;
	}

	private static DataInputStream open(byte[] data) {
		return new DataInputStream(new ByteArrayInputStream(data));
	}

	// ---- SLOTS ----

	public static byte[] encodeSlots(SlotList list) {
		return build(out -> {
			out.writeUTF(list.message == null ? "" : list.message);
			out.writeBoolean(list.ok);
			out.writeInt(list.maxSlots);
			out.writeInt(list.slots.size());
			for (SlotInfo slot : list.slots) {
				writeUuid(out, slot.id);
				out.writeUTF(slot.name);
				out.writeBoolean(slot.banned);
				out.writeBoolean(slot.hasSkin);
				out.writeBoolean(slot.slim);
			}
		});
	}

	public static SlotList decodeSlots(byte[] data) throws IOException {
		DataInputStream in = open(data);
		String message = readText(in, MAX_MESSAGE_LENGTH);
		boolean ok = in.readBoolean();
		int maxSlots = in.readInt();
		int count = in.readInt();
		if (count < 0 || count > 500) {
			throw new IOException("bad slot count");
		}
		List<SlotInfo> slots = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			UUID id = readUuid(in);
			String name = readText(in, ProfileRegistry.MAX_NAME_LENGTH);
			slots.add(new SlotInfo(id, name, in.readBoolean(), in.readBoolean(), in.readBoolean()));
		}
		return new SlotList(message, ok, maxSlots, slots);
	}

	// ---- SELECT / DELETE / SKIN_REQUEST (just a UUID) ----

	public static byte[] encodeId(UUID id) {
		return build(out -> writeUuid(out, id));
	}

	public static UUID decodeId(byte[] data) throws IOException {
		return readUuid(open(data));
	}

	// ---- CREATE ----

	public static byte[] encodeCreate(Create create) {
		return build(out -> {
			out.writeUTF(create.name);
			out.writeBoolean(create.slim);
			writeBytes(out, create.png);
		});
	}

	public static Create decodeCreate(byte[] data) throws IOException {
		DataInputStream in = open(data);
		String name = readText(in, ProfileRegistry.MAX_NAME_LENGTH);
		boolean slim = in.readBoolean();
		return new Create(name, slim, readBytes(in));
	}

	// ---- EDIT ----

	public static byte[] encodeEdit(Edit edit) {
		return build(out -> {
			writeUuid(out, edit.id);
			out.writeUTF(edit.name);
			out.writeBoolean(edit.updateSkin);
			out.writeBoolean(edit.slim);
			writeBytes(out, edit.png);
		});
	}

	public static Edit decodeEdit(byte[] data) throws IOException {
		DataInputStream in = open(data);
		UUID id = readUuid(in);
		String name = readText(in, ProfileRegistry.MAX_NAME_LENGTH);
		boolean updateSkin = in.readBoolean();
		boolean slim = in.readBoolean();
		return new Edit(id, name, updateSkin, slim, readBytes(in));
	}

	// ---- SKIN ----

	public static byte[] encodeSkin(SkinData skin) {
		return build(out -> {
			writeUuid(out, skin.id);
			out.writeBoolean(skin.slim);
			writeBytes(out, skin.png);
		});
	}

	public static SkinData decodeSkin(byte[] data) throws IOException {
		DataInputStream in = open(data);
		UUID id = readUuid(in);
		boolean slim = in.readBoolean();
		return new SkinData(id, slim, readBytes(in));
	}
}
