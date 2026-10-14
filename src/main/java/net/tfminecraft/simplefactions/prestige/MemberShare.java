package net.tfminecraft.simplefactions.prestige;

import java.util.ArrayList;
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
 * Who counts toward the Members prestige term, and for how much. Each account is
 * one member in total, split between its living characters by their
 * {@link RecentPlaytime}. A member with no share adds neither headcount nor
 * playtime, so an alt that is never played adds nothing to any faction.
 *
 * <p>An account with no recent time splits by lifetime playtime instead, and evenly
 * when it has none. A dead member counts nothing. A member the character directory
 * cannot place, or that is missing from its account's list, counts as one.
 */
public final class MemberShare {

	/** A roster's summed shares and the members whose share is above zero. */
	public record Headcount(double heads, List<String> counted) {
	}

	private static final class Pass {
		final Map<String, Double> members = new HashMap<>();
		final Map<UUID, Map<String, Double>> accounts = new HashMap<>();
	}

	private static final ThreadLocal<Pass> pass = new ThreadLocal<>();

	private MemberShare() {
	}

	/** The headcount the prestige curve uses and the members whose playtime counts. */
	public static Headcount count(Collection<String> members) {
		if (members == null || members.isEmpty()) return new Headcount(0.0, List.of());
		double[] heads = {0.0};
		List<String> counted = new ArrayList<>();
		withinPass(() -> {
			for (String member : members) {
				double share = shareOf(member);
				heads[0] += share;
				if (share > 0.0) counted.add(member);
			}
		});
		return new Headcount(heads[0], List.copyOf(counted));
	}

	/** The part of its account this member stands for, from 0 to 1. */
	public static double shareOf(String member) {
		Pass current = pass.get();
		if (current != null && current.members.containsKey(member)) return current.members.get(member);
		double share = lookUp(member);
		if (current != null) current.members.put(member, share);
		return share;
	}

	private static double lookUp(String member) {
		Optional<CharacterInfo> info = Members.info(member);
		if (info.isEmpty()) return 1.0;
		CharacterInfo character = info.get();
		if (!character.alive()) return 0.0;
		Map<String, Double> shares = sharesFor(character.ref().ownerId());
		if (shares == null) return 1.0;
		return shares.getOrDefault(Members.key(character.ref()), 1.0);
	}

	/** Shares of every living character on the account, or null when it cannot be read. */
	private static Map<String, Double> sharesFor(UUID owner) {
		Pass current = pass.get();
		if (current != null && current.accounts.containsKey(owner)) return current.accounts.get(owner);
		Map<String, Double> shares = Members.characters(owner).map(MemberShare::split).orElse(null);
		if (current != null) current.accounts.put(owner, shares);
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

	/** Runs a prestige pass that looks each member and account up once; nested passes share the outer answers. */
	public static void withinPass(Runnable work) {
		if (pass.get() != null) {
			work.run();
			return;
		}
		pass.set(new Pass());
		try {
			work.run();
		} finally {
			pass.remove();
		}
	}
}
