package LARPBeta.profiles.mixin;

import LARPBeta.profiles.ProfileRegistry;
import LARPBeta.profiles.Profiles;
import net.minecraft.server.net.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.UUID;

/**
 * BTA's op and whitelist lists hold UUIDs. Characters have their own UUIDs, so without this,
 * ops would lose their status and a whitelisted player's characters would be turned away.
 * The methods below translate a character UUID back to the owning account's UUID first, so
 * ops and the whitelist stay tied to real accounts (what the server owner manages).
 *
 * Bans are deliberately NOT translated: they apply to the character's own UUID, so banning a
 * character punishes that character, not the person behind it.
 */
@Mixin(PlayerList.class)
public class PlayerListMixin {

	@Unique
	private static UUID profiles$toAccount(UUID id) {
		ProfileRegistry registry = Profiles.registry();
		if (registry == null || id == null) {
			return id;
		}
		UUID account = registry.accountOf(id);
		return account != null ? account : id;
	}

	/** Each of these methods takes a single UUID argument; swap in the account's UUID before it runs. */
	@ModifyVariable(
		method = {"isOp", "isAllowedToLogin", "opPlayer", "deopPlayer", "addToWhiteList", "removeFromWhiteList"},
		at = @At("HEAD"), argsOnly = true, remap = false)
	private UUID profiles$characterToAccount(UUID id) {
		return profiles$toAccount(id);
	}
}
