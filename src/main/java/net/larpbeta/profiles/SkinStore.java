package net.larpbeta.profiles;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * The server's copy of each character's skin: {@code <folder>/<character uuid>.png}, plus an empty
 * {@code <uuid>.slim} marker file when the character uses the slim (thin-arm) model.
 * Files are named by UUID only, so nothing a player types ever becomes part of a path.
 */
public final class SkinStore {
	private final Path folder;

	public SkinStore(Path folder) {
		this.folder = folder;
	}

	/** Thrown for a picture we will not store; the message is safe to show the player. */
	public static final class BadSkinException extends Exception {
		public BadSkinException(String message) {
			super(message);
		}
	}

	/**
	 * Checks that bytes are a 64x64 picture and re-encodes them as a fresh PNG, so nothing else
	 * (extra data, odd formats) is ever stored or sent on to other players.
	 */
	public static byte[] sanitize(byte[] raw) throws BadSkinException {
		if (raw == null || raw.length == 0) {
			throw new BadSkinException("That skin file is empty.");
		}
		BufferedImage image;
		try {
			image = ImageIO.read(new ByteArrayInputStream(raw));
		} catch (IOException | RuntimeException e) {
			throw new BadSkinException("That file is not a PNG picture.");
		}
		if (image == null) {
			throw new BadSkinException("That file is not a PNG picture.");
		}
		if (image.getWidth() != 64 || image.getHeight() != 64) {
			throw new BadSkinException("Skins must be 64x64 pixels (this one is " + image.getWidth() + "x" + image.getHeight() + ").");
		}
		BufferedImage clean = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
		clean.getGraphics().drawImage(image, 0, 0, null);
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try {
			ImageIO.write(clean, "png", out);
		} catch (IOException e) {
			throw new BadSkinException("Could not read that picture.");
		}
		byte[] png = out.toByteArray();
		if (png.length > Protocol.MAX_SKIN_BYTES) {
			throw new BadSkinException("That skin file is too big once saved (limit " + (Protocol.MAX_SKIN_BYTES / 1024) + " KB).");
		}
		return png;
	}

	private Path png(UUID id) {
		return folder.resolve(id + ".png");
	}

	private Path slimMarker(UUID id) {
		return folder.resolve(id + ".slim");
	}

	public boolean has(UUID id) {
		return Files.isRegularFile(png(id));
	}

	public boolean isSlim(UUID id) {
		return Files.exists(slimMarker(id));
	}

	/** The stored PNG, or null if the character has no custom skin. */
	public byte[] read(UUID id) {
		try {
			return has(id) ? Files.readAllBytes(png(id)) : null;
		} catch (IOException e) {
			return null;
		}
	}

	/** Stores a skin that already passed {@link #sanitize}. An empty or null {@code png} removes the skin. */
	public void write(UUID id, byte[] png) throws IOException {
		if (png == null || png.length == 0) {
			Files.deleteIfExists(png(id));
			return;
		}
		Files.createDirectories(folder);
		Path temp = folder.resolve(id + ".png.tmp");
		Files.write(temp, png);
		Files.move(temp, png(id), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
	}

	public void setSlim(UUID id, boolean slim) throws IOException {
		if (slim) {
			Files.createDirectories(folder);
			if (!Files.exists(slimMarker(id))) {
				Files.createFile(slimMarker(id));
			}
		} else {
			Files.deleteIfExists(slimMarker(id));
		}
	}

	public void delete(UUID id) {
		try {
			Files.deleteIfExists(png(id));
			Files.deleteIfExists(slimMarker(id));
		} catch (IOException ignored) {
			// A leftover file is harmless: it is never served for a UUID that is not a character.
		}
	}
}
