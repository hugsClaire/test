package net.larpbeta.profiles;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * Remembers which character slots each real account owns.
 *
 * A slot is a made-up identity (its own UUID and name). When a player logs in, the server
 * creates their player entity from the slot's UUID and name instead of the account's, so
 * everything the game stores per player (inventory, position, and so on) is kept per slot.
 *
 * This class is plain Java with no Minecraft code, so it can be tested on its own.
 * Data lives in a tab-separated text file, one slot per line:
 * <pre>account-uuid TAB slot-uuid TAB slot-name</pre>
 * For now an account's active slot is simply its first one; a slot picker replaces that later.
 */
public final class ProfileRegistry {
	public static final int MAX_NAME_LENGTH = 16;
	private static final Pattern NAME_PATTERN = Pattern.compile("[A-Za-z0-9_]{1," + MAX_NAME_LENGTH + "}");

	public static final class Slot {
		public final UUID id;
		public final String name;

		Slot(UUID id, String name) {
			this.id = id;
			this.name = name;
		}

		@Override
		public String toString() {
			return name + " (" + id + ")";
		}
	}

	private final Path file;
	private final Consumer<String> warn;
	private final Map<UUID, List<Slot>> slotsByAccount = new LinkedHashMap<>();
	private final Set<String> usedNames = new HashSet<>(); // lower-case
	private final Set<UUID> usedIds = new HashSet<>();
	private final Map<UUID, UUID> accountBySlot = new HashMap<>();
	private final Random random = new Random();

	public ProfileRegistry(Path file, Consumer<String> warn) {
		this.file = file;
		this.warn = warn;
	}

	/** Slot names must be 1 to 16 characters: letters, digits and underscores. */
	public static boolean isValidName(String name) {
		return name != null && NAME_PATTERN.matcher(name).matches();
	}

	/** Reads the file if it exists. Bad lines are skipped with a warning. */
	public synchronized void load() throws IOException {
		slotsByAccount.clear();
		usedNames.clear();
		usedIds.clear();
		accountBySlot.clear();
		if (!Files.exists(file)) {
			return;
		}
		int lineNumber = 0;
		for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
			lineNumber++;
			if (line.isBlank() || line.startsWith("#")) {
				continue;
			}
			String[] parts = line.split("\t", -1);
			if (parts.length != 3) {
				warn.accept("slots file line " + lineNumber + " skipped: expected 3 tab-separated columns");
				continue;
			}
			try {
				UUID account = UUID.fromString(parts[0].trim());
				UUID slotId = UUID.fromString(parts[1].trim());
				String name = parts[2].trim();
				if (!isValidName(name)) {
					warn.accept("slots file line " + lineNumber + " skipped: invalid name '" + name + "'");
				} else if (usedIds.contains(slotId)) {
					warn.accept("slots file line " + lineNumber + " skipped: duplicate slot id");
				} else if (!usedNames.add(name.toLowerCase(Locale.ROOT))) {
					warn.accept("slots file line " + lineNumber + " skipped: duplicate name '" + name + "'");
				} else {
					usedIds.add(slotId);
					accountBySlot.put(slotId, account);
					slotsByAccount.computeIfAbsent(account, k -> new ArrayList<>()).add(new Slot(slotId, name));
				}
			} catch (IllegalArgumentException e) {
				warn.accept("slots file line " + lineNumber + " skipped: bad UUID");
			}
		}
	}

	/**
	 * The real account that owns a slot, or null if this UUID is not a slot.
	 * The server's ban, op and whitelist lists hold account UUIDs, so they translate slot UUIDs with this.
	 */
	public synchronized UUID accountOf(UUID slotId) {
		return accountBySlot.get(slotId);
	}

	/** The slot this account joins as: its first one. Null if it has none yet. */
	public synchronized Slot getActiveSlot(UUID account) {
		List<Slot> slots = slotsByAccount.get(account);
		return slots == null || slots.isEmpty() ? null : slots.get(0);
	}

	/** One of the account's own slots by name (ignoring case), or null. */
	public synchronized Slot findSlot(UUID account, String name) {
		List<Slot> slots = slotsByAccount.get(account);
		if (slots == null || name == null) {
			return null;
		}
		for (Slot slot : slots) {
			if (slot.name.equalsIgnoreCase(name)) {
				return slot;
			}
		}
		return null;
	}

	/**
	 * Makes the named slot the one this account joins as next time (it moves to the front of the list).
	 *
	 * @throws IllegalArgumentException if the account has no slot with that name
	 * @throws UncheckedIOException     if the file could not be written (nothing is changed in that case)
	 */
	public synchronized Slot setActiveSlot(UUID account, String name) {
		Slot slot = findSlot(account, name);
		if (slot == null) {
			throw new IllegalArgumentException("No slot named " + name);
		}
		List<Slot> slots = slotsByAccount.get(account);
		List<Slot> previousOrder = new ArrayList<>(slots);
		slots.remove(slot);
		slots.add(0, slot);
		try {
			save();
		} catch (IOException e) {
			slots.clear();
			slots.addAll(previousOrder);
			throw new UncheckedIOException("Could not save " + file, e);
		}
		return slot;
	}

	/** All slots an account owns: the active one first, the rest in the order they were created. */
	public synchronized List<Slot> getSlots(UUID account) {
		List<Slot> slots = slotsByAccount.get(account);
		return slots == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(slots));
	}

	/**
	 * The slot this account plays as. First-time accounts get a new slot with a generated name.
	 *
	 * @throws UncheckedIOException if the new slot could not be saved (nothing is changed in that case)
	 */
	public synchronized Slot getOrCreateActiveSlot(UUID account) {
		List<Slot> slots = slotsByAccount.get(account);
		if (slots != null && !slots.isEmpty()) {
			return slots.get(0);
		}
		return createSlot(account, generateName());
	}

	/**
	 * Adds a slot with a fresh random UUID.
	 *
	 * @throws IllegalArgumentException if the name is invalid or already used by any account
	 * @throws UncheckedIOException     if the file could not be written (nothing is changed in that case)
	 */
	public synchronized Slot createSlot(UUID account, String name) {
		if (!isValidName(name)) {
			throw new IllegalArgumentException("Invalid slot name: " + name);
		}
		String key = name.toLowerCase(Locale.ROOT);
		if (usedNames.contains(key)) {
			throw new IllegalArgumentException("Slot name already taken: " + name);
		}
		UUID id = UUID.randomUUID();
		while (usedIds.contains(id)) {
			id = UUID.randomUUID();
		}
		Slot slot = new Slot(id, name);
		List<Slot> slots = slotsByAccount.computeIfAbsent(account, k -> new ArrayList<>());
		slots.add(slot);
		usedNames.add(key);
		usedIds.add(id);
		accountBySlot.put(id, account);
		try {
			save();
		} catch (IOException e) {
			slots.remove(slot);
			if (slots.isEmpty()) {
				slotsByAccount.remove(account);
			}
			usedNames.remove(key);
			usedIds.remove(id);
			accountBySlot.remove(id);
			throw new UncheckedIOException("Could not save " + file, e);
		}
		return slot;
	}

	private String generateName() {
		for (int attempt = 0; attempt < 1000; attempt++) {
			String candidate = "Stranger" + (1000 + random.nextInt(9000));
			if (!usedNames.contains(candidate.toLowerCase(Locale.ROOT))) {
				return candidate;
			}
		}
		throw new IllegalStateException("Could not find a free generated name");
	}

	private void save() throws IOException {
		Path parent = file.toAbsolutePath().getParent();
		if (parent != null) {
			Files.createDirectories(parent);
		}
		Path temp = file.resolveSibling(file.getFileName() + ".tmp");
		try (BufferedWriter writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
			writer.write("# account-uuid<TAB>slot-uuid<TAB>slot-name\n");
			for (Map.Entry<UUID, List<Slot>> entry : slotsByAccount.entrySet()) {
				for (Slot slot : entry.getValue()) {
					writer.write(entry.getKey() + "\t" + slot.id + "\t" + slot.name + "\n");
				}
			}
		}
		try {
			Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (AtomicMoveNotSupportedException e) {
			Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
		}
	}
}
