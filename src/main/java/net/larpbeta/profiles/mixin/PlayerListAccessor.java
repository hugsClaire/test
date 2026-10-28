package net.larpbeta.profiles.mixin;

import net.minecraft.server.net.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Set;
import java.util.UUID;

/** Lets us read the server's ban list (it is private) so banned characters can be greyed out in the picker. */
@Mixin(PlayerList.class)
public interface PlayerListAccessor {
	@Accessor(value = "bans", remap = false)
	Set<UUID> profiles$bans();
}
