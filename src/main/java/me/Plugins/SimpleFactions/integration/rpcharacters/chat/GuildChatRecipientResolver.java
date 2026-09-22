package me.Plugins.SimpleFactions.integration.rpcharacters.chat;

import java.util.Collections;
import java.util.Set;

import org.bukkit.entity.Player;

import me.Plugins.SimpleFactions.Guild.Guild;
import me.Plugins.SimpleFactions.Managers.FactionManager;
import net.tfminecraft.RPCharacters.chat.ChatChannel;
import net.tfminecraft.RPCharacters.chat.ChatRecipientFilters;
import net.tfminecraft.RPCharacters.chat.ChatRecipientResolver;

public final class GuildChatRecipientResolver implements ChatRecipientResolver {

	@Override
	public Set<Player> resolve(Player sender, ChatChannel channel) {
		if (sender == null || channel == null) {
			return Collections.emptySet();
		}

		Guild guild = FactionManager.getGuildByMember(sender.getName());
		if (guild == null) {
			return Collections.emptySet();
		}

		return ChatRecipientFilters.filterCandidates(
				sender,
				channel,
				OrgChatMemberCollector.onlinePlayersNamed(guild.getMembers()));
	}
}
