package net.tfminecraft.simplefactions.war.stats;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.tfminecraft.simplefactions.identity.Members;
import net.tfminecraft.simplefactions.managers.WarManager;
import net.tfminecraft.simplefactions.mercenary.contract.MercenaryEngagements;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.war.battle.engine.core.Battle;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleSide;
import net.tfminecraft.simplefactions.war.core.War;

/** The saved result of one finished battle. Battle and war IDs are reused, so it has its own ID. */
public final class BattleRecord {
	public String id;
	public String battleId;
	public String name;
	public String type;
	public boolean naval;
	public boolean raid;
	public Integer warId;
	/** War ID plus the war's start, unique across reused war IDs; null outside a war. */
	public String warKey;
	public String warName;
	/** Where the battle was fought; null when unknown. */
	public Integer provinceId;
	public long startedAt;
	public long endedAt;
	public String winner;
	public String endReason;
	public List<SideRecord> sides = new ArrayList<>();
	public List<FighterStats> fighters = new ArrayList<>();

	public static final class SideRecord {
		public String id;
		/** ATTACKER or DEFENDER of the war, which a battle side can swap between battles; null outside a war. */
		public String warRole;
		public int lives;
		public int maxLives;
	}

	static BattleRecord of(Battle battle, BattleStats stats, String winner, String endReason, Instant endedAt) {
		BattleRecord record = new BattleRecord();
		record.battleId = battle.getId();
		record.id = safeId(battle.getId()) + "-" + endedAt.toEpochMilli();
		record.name = battle.getDisplayName();
		record.type = battle.getBattleType() == null ? null : battle.getBattleType().name();
		record.naval = battle.isNavalVariant();
		record.raid = battle.isCampaignRaid();
		record.warId = battle.getWarId();
		record.provinceId = battle.getProvinceId();
		War war = battle.getWarId() == null ? null : WarManager.getById(battle.getWarId());
		if (war != null) {
			record.warKey = warKey(war);
			record.warName = warName(war);
		}
		Instant started = battle.getStartedAt();
		record.startedAt = (started == null ? endedAt : started).toEpochMilli();
		record.endedAt = endedAt.toEpochMilli();
		record.winner = winner == null || winner.isBlank() ? null : winner;
		record.endReason = endReason;
		for (BattleSide side : battle.getSides()) {
			SideRecord saved = new SideRecord();
			saved.id = side.getId();
			var role = war == null ? null : net.tfminecraft.simplefactions.war.battle.campaign.CampaignBattleSides.roleFor(war, battle, side.getId());
			saved.warRole = role == null ? null : role.name();
			saved.lives = side.getLives();
			saved.maxLives = side.getMaxLives();
			record.sides.add(saved);
		}
		for (FighterStats fighter : stats.fighters.values()) {
			String current = Members.display(fighter.key);
			if (!"Unknown".equals(current)) {
				fighter.name = current;
			}
			if (war != null) {
				fighter.hirerFactionId = hirerId(war, fighter.key);
			}
			record.fighters.add(fighter);
		}
		return record;
	}

	/** The faction whose active contract brought this character into the war, or null. */
	private static String hirerId(War war, String key) {
		MercenaryEngagements.Engagement engagement = MercenaryEngagements.forPlayer(war, key);
		Faction hirer = engagement == null ? null : engagement.hirer();
		return hirer == null ? null : hirer.getId();
	}

	public static String warKey(War war) {
		Instant started = war.getStartedAt();
		return "w" + war.getId() + "-" + (started == null ? 0L : started.getEpochSecond());
	}

	private static String warName(War war) {
		try {
			return war.getName();
		} catch (RuntimeException missingLeader) {
			return "War " + war.getId();
		}
	}

	/** Lower case letters, digits, '_' and '-' only, so the ID is a safe file name. */
	static String safeId(String raw) {
		String cleaned = (raw == null ? "" : raw).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "_");
		return cleaned.isEmpty() ? "battle" : cleaned;
	}

	/** The winning side's role in the war, or null. */
	String winnerRole() {
		for (SideRecord side : sides) {
			if (side.id != null && side.id.equalsIgnoreCase(winner)) return side.warRole;
		}
		return null;
	}

	FighterStats fighter(String key) {
		for (FighterStats fighter : fighters) {
			if (fighter.key.equals(key)) {
				return fighter;
			}
		}
		return null;
	}
}
