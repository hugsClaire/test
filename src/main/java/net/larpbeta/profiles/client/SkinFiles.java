package net.larpbeta.profiles.client;

import net.larpbeta.profiles.SkinStore;

import java.awt.FileDialog;
import java.awt.Frame;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.Consumer;

/** Picking and reading skin files from the player's own computer. */
final class SkinFiles {
	private SkinFiles() {
	}

	static final class Result {
		final byte[] png;
		final String error;

		private Result(byte[] png, String error) {
			this.png = png;
			this.error = error;
		}
	}

	/** Reads and checks a skin file (must be a 64x64 PNG). The returned bytes are already cleaned up and small enough to send. */
	static Result read(String pathText) {
		String text = pathText.trim();
		if (text.length() >= 2 && text.startsWith("\"") && text.endsWith("\"")) {
			text = text.substring(1, text.length() - 1);
		}
		try {
			Path path = Paths.get(text);
			if (!Files.isRegularFile(path)) {
				return new Result(null, "Can't find that skin file.");
			}
			if (Files.size(path) > 4 * 1024 * 1024) {
				return new Result(null, "That file is too big to be a skin.");
			}
			return new Result(SkinStore.sanitize(Files.readAllBytes(path)), null);
		} catch (SkinStore.BadSkinException e) {
			return new Result(null, e.getMessage());
		} catch (IOException | RuntimeException e) {
			return new Result(null, "Can't read that skin file.");
		}
	}

	/** Opens the system's file chooser on its own thread and passes the chosen path to {@code onPicked}. Does nothing if it is unavailable. */
	static void browse(Consumer<String> onPicked) {
		Thread thread = new Thread(() -> {
			try {
				FileDialog dialog = new FileDialog((Frame) null, "Choose a skin (64x64 PNG)", FileDialog.LOAD);
				dialog.setFilenameFilter((dir, name) -> name.toLowerCase().endsWith(".png"));
				dialog.setVisible(true);
				if (dialog.getFile() != null) {
					onPicked.accept(new java.io.File(dialog.getDirectory(), dialog.getFile()).getAbsolutePath());
				}
			} catch (Throwable ignored) {
				// No file chooser on this system: the player can type or paste the path instead.
			}
		}, "BETA LARP file chooser");
		thread.setDaemon(true);
		thread.start();
	}
}
