package net.larpbeta.profiles.mixin;

import net.larpbeta.profiles.Profiles;
import net.minecraft.core.entity.Mob;
import net.minecraft.core.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Writes one line to the server log whenever a character dies, so a death can be followed from the game to
 * the Better With Discord mod. It changes nothing; it only reads. If the Discord mod is installed it also
 * reports whether its webhook is ready, which is the thing that decides whether a death can be posted.
 */
@Mixin(Mob.class)
public abstract class DeathDiagnosticsMixin {
	@Inject(method = "onDeath", at = @At("HEAD"), remap = false)
	private void profiles$logDeath(net.minecraft.core.entity.Entity killer, CallbackInfo ci) {
		Mob self = (Mob) (Object) this;
		if (!(self instanceof Player) || self.world == null || self.world.isClientSide) return;
		Player player = (Player) self;
		String discord;
		try {
			Class<?> client = Class.forName("com.sajmonoriginal.betterwithdiscord.server.DiscordClient");
			java.lang.reflect.Field field = client.getDeclaredField("webhook");
			field.setAccessible(true);
			Object webhook = field.get(null);
			discord = webhook == null ? "installed, but its webhook is NOT ready (nothing can be posted)" : "installed, webhook ready";
		} catch (ClassNotFoundException e) {
			discord = "not installed";
		} catch (Throwable t) {
			discord = "installed (could not inspect: " + t + ")";
		}
		Profiles.LOGGER.info("Character {} died (killer: {}). Better With Discord: {}.", player.username,
			killer == null ? "none" : killer.getClass().getSimpleName(), discord);
	}
}
