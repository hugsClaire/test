package net.larpbeta.profiles.mixin;

import net.minecraft.server.net.PlayerList;
import net.minecraft.server.net.command.commands.CommandUnban;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.UUID;

/**
 * BTA 8.0.1 bug: {@code /unban username <name>} calls banPlayer instead of pardonPlayer, so it bans
 * (again) instead of unbanning. This makes it do what it says. (The /unban ip command is fine.)
 */
@Mixin(CommandUnban.class)
public class CommandUnbanMixin {
	@Redirect(
		method = "lambda$register$2",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/server/net/PlayerList;banPlayer(Ljava/util/UUID;)V", remap = false),
		remap = false)
	private static void profiles$pardonInstead(PlayerList playerList, UUID id) {
		playerList.pardonPlayer(id);
	}
}
