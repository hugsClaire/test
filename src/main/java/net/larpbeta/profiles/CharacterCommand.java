package net.larpbeta.profiles;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.ArgumentTypeString;
import com.mojang.brigadier.builder.ArgumentBuilderLiteral;
import com.mojang.brigadier.builder.ArgumentBuilderRequired;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.core.entity.player.Player;
import net.minecraft.core.net.command.CommandManager;
import net.minecraft.core.net.command.CommandSource;
import net.minecraft.server.entity.player.PlayerServer;

import java.io.UncheckedIOException;
import java.util.UUID;

/**
 * In-game commands for managing your characters:
 * <pre>
 * /character                 list your characters
 * /character list            same
 * /character new &lt;name&gt;      create a character and switch to it
 * /character switch &lt;name&gt;   switch to one of your characters
 * </pre>
 * The character is chosen when you log in, so creating or switching saves your current character,
 * disconnects you, and you rejoin as the new one. Everything here is private to the person typing it.
 *
 * Server only: this class touches server-only BTA classes, so Profiles registers it only on a dedicated server.
 */
public final class CharacterCommand {
	private CharacterCommand() {
	}

	public static void register() {
		CommandManager.registerServerCommand(CharacterCommand::registerOn);
	}

	private static void registerOn(CommandDispatcher<CommandSource> dispatcher) {
		dispatcher.register(
			ArgumentBuilderLiteral.<CommandSource>literal("character")
				.executes(CharacterCommand::list)
				.then(ArgumentBuilderLiteral.<CommandSource>literal("list")
					.executes(CharacterCommand::list))
				.then(ArgumentBuilderLiteral.<CommandSource>literal("switch")
					.then(ArgumentBuilderRequired.<CommandSource, String>argument("name", ArgumentTypeString.word())
						.executes(context -> switchTo(context, ArgumentTypeString.getString(context, "name")))))
				.then(ArgumentBuilderLiteral.<CommandSource>literal("new")
					.then(ArgumentBuilderRequired.<CommandSource, String>argument("name", ArgumentTypeString.word())
						.executes(context -> create(context, ArgumentTypeString.getString(context, "name")))))
		);
	}

	private static int list(CommandContext<CommandSource> context) {
		CommandSource source = context.getSource();
		PlayerServer player = playerOf(source);
		UUID account = accountOf(player);
		if (account == null) {
			source.sendMessage("Only players with a character can use this command.");
			return 0;
		}
		source.sendMessage("Your characters:");
		for (ProfileRegistry.Slot slot : Profiles.registry().getSlots(account)) {
			source.sendMessage("  " + slot.name + (slot.id.equals(player.uuid) ? "   (you are playing this one)" : ""));
		}
		source.sendMessage("Use /character switch <name> or /character new <name>.");
		return Command.SINGLE_SUCCESS;
	}

	private static int switchTo(CommandContext<CommandSource> context, String name) {
		CommandSource source = context.getSource();
		PlayerServer player = playerOf(source);
		UUID account = accountOf(player);
		if (account == null) {
			source.sendMessage("Only players with a character can use this command.");
			return 0;
		}
		ProfileRegistry registry = Profiles.registry();
		ProfileRegistry.Slot slot = registry.findSlot(account, name);
		if (slot == null) {
			source.sendMessage("You don't have a character called " + name + ". Try /character list.");
			return 0;
		}
		if (slot.id.equals(player.uuid)) {
			source.sendMessage("You are already playing " + slot.name + ".");
			return 0;
		}
		try {
			registry.setActiveSlot(account, slot.name);
		} catch (UncheckedIOException e) {
			Profiles.LOGGER.error("Could not save the character switch", e);
			source.sendMessage("Something went wrong saving that. Please tell the server owner.");
			return 0;
		}
		player.playerNetServerHandler.kickPlayer("Switching to " + slot.name + ". Rejoin the server to play as them.");
		return Command.SINGLE_SUCCESS;
	}

	private static int create(CommandContext<CommandSource> context, String name) {
		CommandSource source = context.getSource();
		PlayerServer player = playerOf(source);
		UUID account = accountOf(player);
		if (account == null) {
			source.sendMessage("Only players with a character can use this command.");
			return 0;
		}
		if (!ProfileRegistry.isValidName(name)) {
			source.sendMessage("Names are 1 to " + ProfileRegistry.MAX_NAME_LENGTH + " letters, numbers or underscores.");
			return 0;
		}
		ProfileRegistry registry = Profiles.registry();
		try {
			registry.createSlot(account, name);
			registry.setActiveSlot(account, name);
		} catch (IllegalArgumentException e) {
			source.sendMessage("That name is already taken.");
			return 0;
		} catch (UncheckedIOException e) {
			Profiles.LOGGER.error("Could not save the new character", e);
			source.sendMessage("Something went wrong saving that. Please tell the server owner.");
			return 0;
		}
		player.playerNetServerHandler.kickPlayer("Created " + name + ". Rejoin the server to start as them.");
		return Command.SINGLE_SUCCESS;
	}

	/** The player who typed the command, or null if it came from somewhere else (like the console). */
	private static PlayerServer playerOf(CommandSource source) {
		Player sender = source.getSender();
		return sender instanceof PlayerServer ? (PlayerServer) sender : null;
	}

	/** The real account behind a character, or null if this is not a character or the registry is not loaded. */
	private static UUID accountOf(PlayerServer player) {
		ProfileRegistry registry = Profiles.registry();
		return player == null || registry == null ? null : registry.accountOf(player.uuid);
	}
}
