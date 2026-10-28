package net.larpbeta.profiles.client;

import net.larpbeta.profiles.SkinStore;

import java.awt.FileDialog;
import java.awt.Frame;
import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
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

	/** Reads and checks a skin file (must be a 64x64 or 64x32 PNG). The returned bytes are already cleaned up and small enough to send. */
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

	/**
	 * Opens the computer's own file chooser (a separate program, so it works inside the game's window
	 * system) on a background thread. {@code onPicked} gets the chosen path; {@code onProblem} gets a
	 * message if no chooser could be opened; {@code onFinished} always runs last (also if the player cancels).
	 */
	static void browse(Consumer<String> onPicked, Consumer<String> onProblem, Runnable onFinished) {
		Thread thread = new Thread(() -> {
			try {
				String os = System.getProperty("os.name", "").toLowerCase();
				List<String[]> commands = new ArrayList<>();
				if (os.contains("win")) {
					commands.add(new String[]{"powershell", "-NoProfile", "-STA", "-Command",
						"[Console]::OutputEncoding = [System.Text.Encoding]::UTF8; "
							+ "Add-Type -AssemblyName System.Windows.Forms; "
							+ "$owner = New-Object System.Windows.Forms.Form; $owner.TopMost = $true; "
							+ "$d = New-Object System.Windows.Forms.OpenFileDialog; "
							+ "$d.Title = 'Choose a skin (64x64 or 64x32 PNG)'; $d.Filter = 'PNG images (*.png)|*.png'; "
							+ "if ($d.ShowDialog($owner) -eq [System.Windows.Forms.DialogResult]::OK) { Write-Output $d.FileName }"});
				} else if (os.contains("mac")) {
					commands.add(new String[]{"osascript", "-e",
						"POSIX path of (choose file with prompt \"Choose a skin (64x64 or 64x32 PNG)\" of type {\"png\"})"});
				} else {
					commands.add(new String[]{"zenity", "--file-selection", "--title=Choose a skin (64x64 or 64x32 PNG)", "--file-filter=*.png"});
					commands.add(new String[]{"kdialog", "--getopenfilename", ".", "*.png"});
				}
				boolean opened = false;
				for (String[] command : commands) {
					try {
						Process process = new ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.DISCARD).start();
						opened = true;
						String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
						process.waitFor();
						String path = output.replace("\uFEFF", "").trim();
						if (!path.isEmpty()) {
							onPicked.accept(path);
						}
						break;
					} catch (IOException e) {
						// That program is not on this computer; try the next one.
					}
				}
				if (!opened && !tryAwt(onPicked)) {
					onProblem.accept("Could not open a file window here. Paste the path to your skin file instead.");
				}
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			} catch (Throwable e) {
				onProblem.accept("Could not open a file window here. Paste the path to your skin file instead.");
			} finally {
				onFinished.run();
			}
		}, "BETA LARP file chooser");
		thread.setDaemon(true);
		thread.start();
	}

	/** Last resort: Java's own file dialog. Returns false if it cannot be used (for example a headless Java). */
	private static boolean tryAwt(Consumer<String> onPicked) {
		try {
			if (GraphicsEnvironment.isHeadless()) {
				return false;
			}
			FileDialog dialog = new FileDialog((Frame) null, "Choose a skin (64x64 or 64x32 PNG)", FileDialog.LOAD);
			dialog.setFilenameFilter((dir, name) -> name.toLowerCase().endsWith(".png"));
			dialog.setVisible(true);
			if (dialog.getFile() != null) {
				onPicked.accept(new java.io.File(dialog.getDirectory(), dialog.getFile()).getAbsolutePath());
			}
			return true;
		} catch (Throwable e) {
			return false;
		}
	}
}
