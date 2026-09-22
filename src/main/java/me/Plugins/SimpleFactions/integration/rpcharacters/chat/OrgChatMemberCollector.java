package me.Plugins.SimpleFactions.integration.rpcharacters.chat;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

final class OrgChatMemberCollector {

	private OrgChatMemberCollector() {}

	static List<Player> onlinePlayersNamed(Collection<String> memberNames) {
		if (Bukkit.getServer() == null) {
			return List.of();
		}
		return onlinePlayersNamed(memberNames, Bukkit::getPlayerExact);
	}

	static List<Player> onlinePlayersNamed(Collection<String> memberNames, Function<String, Player> lookup) {
		if (memberNames == null || memberNames.isEmpty() || lookup == null) {
			return List.of();
		}

		List<Player> online = new ArrayList<>();
		Set<String> seen = new HashSet<>();
		for (String memberName : memberNames) {
			if (memberName == null || memberName.isBlank()) {
				continue;
			}
			String normalized = memberName.toLowerCase(Locale.ROOT);
			if (!seen.add(normalized)) {
				continue;
			}
			Player player = lookup.apply(memberName);
			if (player != null && player.isOnline()) {
				online.add(player);
			}
		}
		return List.copyOf(online);
	}
}
