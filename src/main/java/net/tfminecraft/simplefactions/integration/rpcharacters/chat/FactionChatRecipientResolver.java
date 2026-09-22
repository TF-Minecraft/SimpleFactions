package net.tfminecraft.simplefactions.integration.rpcharacters.chat;

import java.util.Collections;
import java.util.Set;

import org.bukkit.entity.Player;

import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.rpcharacters.chat.ChatChannel;
import net.tfminecraft.rpcharacters.chat.ChatRecipientFilters;
import net.tfminecraft.rpcharacters.chat.ChatRecipientResolver;

public final class FactionChatRecipientResolver implements ChatRecipientResolver {

	@Override
	public Set<Player> resolve(Player sender, ChatChannel channel) {
		if (sender == null || channel == null) {
			return Collections.emptySet();
		}

		Faction faction = FactionManager.getByMember(sender.getName());
		if (faction == null) {
			return Collections.emptySet();
		}

		return ChatRecipientFilters.filterCandidates(
				sender,
				channel,
				OrgChatMemberCollector.onlinePlayersNamed(faction.getMembers()));
	}
}
