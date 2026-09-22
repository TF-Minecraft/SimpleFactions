package me.Plugins.SimpleFactions.integration.rpcharacters.chat;

import org.bukkit.Bukkit;

import net.tfminecraft.RPCharacters.chat.ChatRecipientResolverRegistry;

public final class RpCharactersChatIntegration {

	public static final String GUILD_RESOLVER_ID = "simplefactions:guild";
	public static final String FACTION_RESOLVER_ID = "simplefactions:faction";

	private static final GuildChatRecipientResolver GUILD_RESOLVER = new GuildChatRecipientResolver();
	private static final FactionChatRecipientResolver FACTION_RESOLVER = new FactionChatRecipientResolver();

	private RpCharactersChatIntegration() {}

	public static void register() {
		if (Bukkit.getServer() == null
				|| Bukkit.getPluginManager() == null
				|| !Bukkit.getPluginManager().isPluginEnabled("RPCharacters")) {
			return;
		}
		ChatRecipientResolverRegistry.register(GUILD_RESOLVER_ID, GUILD_RESOLVER);
		ChatRecipientResolverRegistry.register(FACTION_RESOLVER_ID, FACTION_RESOLVER);
	}

	public static void unregister() {
		ChatRecipientResolverRegistry.unregister(GUILD_RESOLVER_ID);
		ChatRecipientResolverRegistry.unregister(FACTION_RESOLVER_ID);
	}
}
