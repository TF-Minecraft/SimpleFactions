package net.tfminecraft.simplefactions.integration.rpcharacters.chat;

import java.util.Collections;
import java.util.Set;

import org.bukkit.entity.Player;

import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.rpcharacters.chat.ChatChannel;
import net.tfminecraft.rpcharacters.chat.ChatRecipientFilters;
import net.tfminecraft.rpcharacters.chat.ChatRecipientResolver;

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
