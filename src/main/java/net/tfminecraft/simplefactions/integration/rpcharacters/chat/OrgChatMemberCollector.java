package net.tfminecraft.simplefactions.integration.rpcharacters.chat;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import net.tfminecraft.simplefactions.identity.Members;

final class OrgChatMemberCollector {

	private OrgChatMemberCollector() {}

	/** Players currently playing one of the member characters. */
	static List<Player> onlinePlayersNamed(Collection<String> members) {
		if (Bukkit.getServer() == null) {
			return List.of();
		}
		return onlinePlayersNamed(members, Members::player);
	}

	static List<Player> onlinePlayersNamed(Collection<String> memberNames, Function<String, Player> lookup) {
		if (memberNames == null || memberNames.isEmpty() || lookup == null) {
			return List.of();
		}

		List<Player> online = new ArrayList<>();
		Set<Player> seen = new HashSet<>();
		for (String memberName : memberNames) {
			if (memberName == null || memberName.isBlank()) {
				continue;
			}
			Player player = lookup.apply(memberName);
			if (player != null && player.isOnline() && seen.add(player)) {
				online.add(player);
			}
		}
		return List.copyOf(online);
	}
}
