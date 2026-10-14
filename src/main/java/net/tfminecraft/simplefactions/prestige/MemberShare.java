package net.tfminecraft.simplefactions.prestige;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import net.tfminecraft.simplefactions.identity.Members;
import net.tfminecraft.tlibs.character.CharacterInfo;

/**
 * The headcount half of the Members prestige term. Each account is one member in
 * total, split between its living characters by their {@link RecentPlaytime}, so an
 * alt that is never played adds nothing to any faction.
 *
 * <p>An account with no recent time splits by lifetime playtime instead, and evenly
 * when it has none. A dead member counts nothing; one the character directory
 * cannot place counts as one.
 */
public final class MemberShare {

	/** Shares for the running prestige pass, by account. */
	private static final ThreadLocal<Map<UUID, Map<String, Double>>> pass = new ThreadLocal<>();

	private MemberShare() {
	}

	/** Summed shares across a roster: the headcount the prestige curve uses. */
	public static double headsFor(Collection<String> members) {
		if (members == null || members.isEmpty()) return 0.0;
		double heads = 0.0;
		for (String member : members) {
			heads += shareOf(member);
		}
		return heads;
	}

	/** The part of its account this member stands for, from 0 to 1. */
	public static double shareOf(String member) {
		Optional<CharacterInfo> info = Members.info(member);
		if (info.isEmpty()) return 1.0;
		CharacterInfo character = info.get();
		Map<String, Double> shares = sharesFor(character);
		if (shares == null) return 1.0;
		return shares.getOrDefault(Members.key(character.ref()), 0.0);
	}

	/** Shares of every living character on the member's account, or null when it cannot be read. */
	private static Map<String, Double> sharesFor(CharacterInfo member) {
		UUID owner = member.ref().ownerId();
		Map<UUID, Map<String, Double>> answers = pass.get();
		if (answers != null && answers.containsKey(owner)) return answers.get(owner);
		Map<String, Double> shares = Members.characters(owner).map(MemberShare::split).orElse(null);
		if (answers != null) answers.put(owner, shares);
		return shares;
	}

	private static Map<String, Double> split(List<CharacterInfo> all) {
		Map<String, CharacterInfo> candidates = new LinkedHashMap<>();
		for (CharacterInfo character : all) {
			if (character != null && character.alive()) candidates.put(Members.key(character.ref()), character);
		}
		if (candidates.isEmpty()) return Map.of();
		Map<String, Double> shares = byWeight(candidates, (key, character) -> RecentPlaytime.seconds(key));
		if (shares == null) shares = byWeight(candidates, (key, character) -> character.playtimeSeconds());
		if (shares == null) {
			shares = new HashMap<>();
			for (String key : candidates.keySet()) shares.put(key, 1.0 / candidates.size());
		}
		return shares;
	}

	private interface Weight {
		long of(String key, CharacterInfo character);
	}

	/** Each candidate's part of the total weight, or null when the total is zero. */
	private static Map<String, Double> byWeight(Map<String, CharacterInfo> candidates, Weight weight) {
		Map<String, Long> weights = new HashMap<>();
		long total = 0L;
		for (Map.Entry<String, CharacterInfo> entry : candidates.entrySet()) {
			long w = Math.max(0L, weight.of(entry.getKey(), entry.getValue()));
			weights.put(entry.getKey(), w);
			total = total > Long.MAX_VALUE - w ? Long.MAX_VALUE : total + w;
		}
		if (total <= 0L) return null;
		Map<String, Double> shares = new HashMap<>();
		for (Map.Entry<String, Long> entry : weights.entrySet()) {
			shares.put(entry.getKey(), (double) entry.getValue() / total);
		}
		return shares;
	}

	/** Runs a prestige pass that splits each account once; nested passes share the outer answers. */
	public static void withinPass(Runnable work) {
		if (pass.get() != null) {
			work.run();
			return;
		}
		pass.set(new HashMap<>());
		try {
			work.run();
		} finally {
			pass.remove();
		}
	}
}
