package LARPBeta.profiles.mixin;

import LARPBeta.profiles.ProfileRegistry;
import LARPBeta.profiles.Profiles;
import net.minecraft.server.net.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Set;
import java.util.UUID;

/**
 * BTA's ban, op and whitelist lists hold UUIDs. Characters have their own UUIDs, so without this
 * a banned account would slip past its ban, and ops and whitelisted players would lose their status.
 * Every list method below translates a character UUID back to the owning account's UUID first,
 * which keeps all of those lists keyed by real account (what the server owner manages).
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
		method = {"isOp", "isAllowedToLogin", "opPlayer", "deopPlayer", "banPlayer", "pardonPlayer", "addToWhiteList", "removeFromWhiteList"},
		at = @At("HEAD"), argsOnly = true, remap = false)
	private UUID profiles$characterToAccount(UUID id) {
		return profiles$toAccount(id);
	}

	/** getPlayerForLogin's first Set.contains call is the ban check (the second is the IP ban check). */
	@Redirect(method = "getPlayerForLogin",
		at = @At(value = "INVOKE", target = "Ljava/util/Set;contains(Ljava/lang/Object;)Z", ordinal = 0), remap = false)
	private boolean profiles$banCheckByAccount(Set<?> bans, Object id) {
		return bans.contains(id instanceof UUID ? profiles$toAccount((UUID) id) : id);
	}
}
