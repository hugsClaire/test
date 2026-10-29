package net.larpbeta.profiles.mixin;

import net.larpbeta.profiles.Profiles;
import net.minecraft.core.entity.Entity;
import net.minecraft.core.entity.Mob;
import net.minecraft.core.entity.player.Player;
import net.minecraft.core.lang.I18n;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Method;

/**
 * Makes character deaths reach Discord when the Better With Discord mod is installed.
 *
 * That mod is supposed to post death messages itself, but on this server it never does, even though its
 * webhook is ready (join and leave messages work). So after the game announces a death, we hand the very
 * same message (the one players saw, with the character's name) to the Discord mod's own post routine,
 * and log what happened. If the Discord mod is not installed this does nothing. The Discord mod is not
 * modified in any way.
 */
@Mixin(Mob.class)
public abstract class DiscordDeathRelayMixin {
	private static final String ANNOUNCE =
		"Lnet/minecraft/core/world/World;sendGlobalMessageTranslated(Lnet/minecraft/core/net/command/TextFormatting$Base;Ljava/lang/String;[Ljava/lang/String;)V";

	private static final ThreadLocal<String> profiles$key = new ThreadLocal<>();
	private static final ThreadLocal<String[]> profiles$args = new ThreadLocal<>();

	@ModifyArg(method = "onDeath", at = @At(value = "INVOKE", target = ANNOUNCE), index = 1, remap = false)
	private String profiles$captureKey(String key) {
		profiles$key.set(key);
		return key;
	}

	@ModifyArg(method = "onDeath", at = @At(value = "INVOKE", target = ANNOUNCE), index = 2, remap = false)
	private String[] profiles$captureArgs(String[] args) {
		profiles$args.set(args);
		return args;
	}

	@Inject(method = "onDeath", at = @At("RETURN"), remap = false)
	private void profiles$relayToDiscord(Entity killer, CallbackInfo ci) {
		String key = profiles$key.get();
		String[] args = profiles$args.get();
		profiles$key.remove();
		profiles$args.remove();
		Mob self = (Mob) (Object) this;
		if (key == null || args == null || !(self instanceof Player)) return;
		try {
			Class<?> relay = Class.forName("com.sajmonoriginal.betterwithdiscord.server.DiscordChatRelay");
			Method send = relay.getMethod("sendDeathMessage", String.class, String[].class);
			Profiles.LOGGER.info("Sending death message to Discord: {}", I18n.getInstance().translateKeyAndFormat(key, (Object[]) args));
			send.invoke(null, key, args);
		} catch (ClassNotFoundException e) {
			// Better With Discord is not installed.
		} catch (Throwable t) {
			Profiles.LOGGER.error("Could not send the death message to Discord", t);
		}
	}
}
