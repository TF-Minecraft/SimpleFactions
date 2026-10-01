package net.tfminecraft.simplefactions.government.stability;

import net.tfminecraft.simplefactions.government.stability.StabilityFacts.Body;

/**
 * Legitimacy, the size of the state, and the status that names the result.
 * Stance changes legitimacy only. It does not add or subtract stability on its own.
 */
public final class StabilityMath {

	private StabilityMath() {}

	public static StabilityReport assess(StabilityFacts facts) {
		return assess(facts, StabilityTuning.get());
	}

	public static StabilityReport assess(StabilityFacts facts, StabilityTuning tuning) {
		StabilityReport report = new StabilityReport();
		if (facts == null) {
			report.status = StabilityStatus.FAILED;
			report.lines.add("Failed State");
			return report;
		}
		endorsement(facts, tuning, report);
		weakState(facts, tuning, report);
		double over = 0;
		if ("community".equals(id(facts.government))) {
			int past = Math.max(0, facts.provinces - tuning.communityFreeProvinces);
			over = past * tuning.communityProvinceCost;
		}
		report.overextension = round(over);
		double raw = 100 - curve(report.legitimacy) - report.weakStateMalus - report.overextension + facts.temporary;
		if (facts.bankrupt) {
			raw = 0;
		}
		report.stability = round(clamp(raw));
		report.status = StabilityStatus.fromStability(report.stability);
		explain(facts, tuning, report);
		return report;
	}

	private static void endorsement(StabilityFacts facts, StabilityTuning tuning, StabilityReport report) {
		double base = baseLegitimacy(facts, tuning);
		int population = 0;
		double endorsed = 0;
		for (Body body : facts.guilds) {
			if (body == null || body.realm) continue;
			population += Math.max(0, body.members);
			endorsed += Math.max(0, body.members) * weight(body.stance, tuning);
		}
		for (Body vassal : facts.vassals) {
			if (vassal == null) continue;
			population += Math.max(0, vassal.members);
			endorsed += Math.max(0, vassal.members) * weight(vassal.stance, tuning);
		}
		report.singleBody = population == 0;
		if (population == 0) {
			report.legitimacy = 100;
			report.supportShare = 1;
		} else {
			report.supportShare = endorsed / population;
			report.legitimacy = round(clamp(base + (100 - base) * report.supportShare));
		}
		report.illegitimate = report.legitimacy < tuning.illegitimateBelow;
	}

	private static void weakState(StabilityFacts facts, StabilityTuning tuning, StabilityReport report) {
		int realm = 0;
		int largest = 0;
		int other = 0;
		for (Body body : facts.guilds) {
			if (body == null) continue;
			if (body.realm) {
				realm = body.branchLevels;
			} else {
				int levels = Math.max(0, body.branchLevels);
				other += levels;
				if (levels > largest) largest = levels;
			}
		}
		report.realmLevels = realm;
		report.otherLevels = other;
		report.leadingLevels = largest;
		double required = largest * sizeRequirement(facts, tuning);
		report.requiredLevels = required;
		double malus = 0;
		if (required > 0) {
			double shortfall = Math.max(0, 1 - realm / required);
			malus = shortfall * shortfall * 100;
		}
		report.weakStateMalus = round(malus);
	}

	public static double sizeRequirement(StabilityFacts facts, StabilityTuning tuning) {
		double ratio = governmentExpectation(facts.government, tuning);
		if (facts.electedLeadership && !"democracy".equals(id(facts.government))) {
			ratio *= tuning.electedWeakStateFactor;
		}
		return ratio;
	}

	/** Share of the largest guild the state is expected to match. */
	public static double governmentExpectation(String government, StabilityTuning tuning) {
		return switch (id(government)) {
			case "community" -> tuning.weakStateCommunity;
			case "democracy" -> tuning.weakStateDemocracy;
			case "oligarchy" -> tuning.weakStateOligarchy;
			case "plutocracy" -> tuning.weakStatePlutocracy;
			default -> tuning.weakStateAutocracy;
		};
	}

	public static double curve(double score) {
		double missing = Math.max(0, 100 - score);
		return missing * missing / 100.0;
	}

	public static double baseLegitimacy(StabilityFacts facts, StabilityTuning tuning) {
		if (facts.electedLeadership || "democracy".equals(id(facts.government))) {
			return tuning.legitimacyElection;
		}
		return switch (id(facts.government)) {
			case "community" -> tuning.legitimacyCommunity;
			case "oligarchy", "plutocracy" -> tuning.legitimacyCouncil;
			default -> tuning.legitimacyAutocracy;
		};
	}

	public static double weight(String stance, StabilityTuning tuning) {
		if (stance == null) return 1;
		return switch (stance.toUpperCase()) {
			case "OPPOSE" -> 0;
			case "NEUTRAL" -> tuning.neutralWeight;
			default -> 1;
		};
	}

	private static void explain(StabilityFacts facts, StabilityTuning tuning, StabilityReport report) {
		report.lines.add(report.status.getLabel());
		report.lines.add("Stability " + num(report.stability));
		report.lines.add("Legitimacy " + num(report.legitimacy)
				+ (report.illegitimate ? "  Illegitimate" : ""));
		if (report.singleBody) {
			report.lines.add("No other guilds. The nation and the state are the same.");
		} else {
			report.lines.add("Guilds endorse " + num(report.supportShare * 100) + "% of their members.");
			report.lines.add("Support counts in full. Neutral counts "
					+ num(tuning.neutralWeight * 100) + "%. Oppose counts nothing.");
		}
		if (report.weakStateMalus > 0) {
			report.lines.add("Weak state -" + num(report.weakStateMalus));
			report.lines.add("Realm " + report.realmLevels + " branch levels. This government needs "
					+ num(report.requiredLevels) + ".");
		}
		report.lines.add("Legitimacy costs " + num(curve(report.legitimacy)) + ".");
		if (report.overextension > 0) {
			int past = Math.max(0, facts.provinces - tuning.communityFreeProvinces);
			report.lines.add("Community overextension -" + num(report.overextension));
			report.lines.add(facts.provinces + " provinces. "
					+ tuning.communityFreeProvinces + " sit comfortably. "
					+ past + " past that, at " + num(tuning.communityProvinceCost) + " each.");
		}
		if (report.stability < 100) {
			report.lines.add("State output " + num(report.stability) + "%.");
		}
		if (report.status.getTaxFactor() < 1) {
			report.lines.add("Tax collection " + num(report.status.getTaxFactor() * 100) + "%");
		}
		double foreign = StabilityDebuffs.foreignTradeBonus(report.stability, tuning);
		if (foreign > 0) {
			report.lines.add("Foreign guilds +" + num(foreign * 100) + "% trade power here");
		}
		double admin = StabilityDebuffs.adminFactor(report.stability, tuning);
		if (admin < 1) {
			report.lines.add("Admin power and diplomatic capacity " + num(admin * 100) + "%");
		}
		double upkeep = StabilityDebuffs.upkeepFactor(report.stability, tuning);
		if (upkeep > 1) {
			report.lines.add("Law upkeep +" + num((upkeep - 1) * 100) + "%");
		}
		double deJure = StabilityDebuffs.deJureBonus(report.stability, tuning);
		if (deJure > 0) {
			report.lines.add("De jure requirement +" + num(deJure));
		}
		double malus = StabilityDebuffs.prestigeMalus(report.stability, tuning);
		if (malus > 0) {
			report.lines.add("Prestige malus -" + num(malus) + "%");
		}
		if (!report.status.canWageWar()) {
			report.lines.add("Cannot declare war or form titles.");
		}
		if (report.illegitimate) {
			report.lines.add("Movements organize at "
					+ num(tuning.movementGainMultiplier) + "x speed.");
		}
	}

	private static String id(String value) {
		return value == null ? "" : value.toLowerCase();
	}

	private static double clamp(double value) {
		if (value < 0) return 0;
		if (value > 100) return 100;
		return value;
	}

	private static double round(double value) {
		return Math.round(value * 10.0) / 10.0;
	}

	private static String num(double value) {
		double rounded = round(value);
		if (rounded == (long) rounded) {
			return Long.toString((long) rounded);
		}
		return Double.toString(rounded);
	}
}
