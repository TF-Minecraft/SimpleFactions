package net.tfminecraft.simplefactions.war.battle.campaign;

import net.tfminecraft.simplefactions.loaders.TitleLoader;
import net.tfminecraft.simplefactions.utils.Formatter;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.managers.LogManager;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.tiers.Title;
import java.util.List;

import net.tfminecraft.simplefactions.war.core.War;
import net.tfminecraft.simplefactions.war.battle.engine.core.Battle;
import net.tfminecraft.simplefactions.war.battle.enums.BattleType;
import net.tfminecraft.simplefactions.war.battle.template.BattleTemplate;
import net.tfminecraft.simplefactions.war.enums.CampaignBattleKind;
import net.tfminecraft.simplefactions.war.campaign.schedule.CampaignScheduleService;
import net.tfminecraft.simplefactions.war.campaign.schedule.CampaignScheduleService.ScheduleLeg;
import net.tfminecraft.simplefactions.war.campaign.schedule.ScheduledCampaignBattle;
import net.tfminecraft.simplefactions.installation.Installation;
import net.tfminecraft.simplefactions.installation.InstallationKind;
import net.tfminecraft.simplefactions.settlement.Settlement;

public final class BattleNamingService {
	public static final String WILDERNESS = "Wilderness";
	private static final int MAX_SLUG_LENGTH = 48;

	private BattleNamingService() {
	}

	public static void applyCampaignName(Battle battle, War war, int provinceId, BattleType type) {
		applyCampaignName(battle, war, provinceId, type, null);
	}

	public static void applyCampaignName(
			Battle battle,
			War war,
			int provinceId,
			BattleType type,
			ScheduledCampaignBattle slot) {
		if (battle == null || type == null) {
			return;
		}
		if (slot != null && slot.kind() == CampaignBattleKind.SIEGE && slot.fortInstallationId() != null) {
			String fortName = CampaignScheduleService.resolveInstallationName(slot.fortInstallationId());
			String location = fortName != null && !fortName.isBlank()
					? fortName
					: resolveLocationDisplayName(provinceId);
			String key = locationKeyForSlot(slot, provinceId);
			int ordinal = war != null ? war.getLocationBattleCount(key) + 1 : 1;
			battle.setDisplayName(buildDisplayName(BattleType.SIEGE, location, ordinal));
			return;
		}
		LocationInfo location = resolveLocation(provinceId);
		int ordinal = war != null ? war.getLocationBattleCount(location.key()) + 1 : 1;
		battle.setDisplayName(buildDisplayName(type, location.displayName(), ordinal));
	}

	public static void applyManualName(Battle battle, Integer provinceId, BattleType type) {
		if (battle == null || type == null || provinceId == null) {
			return;
		}
		LocationInfo location = resolveLocation(provinceId);
		battle.setDisplayName(buildDisplayName(type, location.displayName(), 1));
	}

	public static void recordLocationBattle(War war, int provinceId) {
		recordLocationBattle(war, provinceId, null);
	}

	public static void recordLocationBattle(War war, int provinceId, ScheduledCampaignBattle slot) {
		if (war == null) {
			return;
		}
		war.recordLocationBattle(locationKeyForSlot(slot, provinceId));
	}

	public static String locationKeyForSlot(ScheduledCampaignBattle slot, int provinceId) {
		if (slot != null
				&& slot.kind() == CampaignBattleKind.SIEGE
				&& slot.fortInstallationId() != null) {
			return "fort:" + slot.fortInstallationId();
		}
		return resolveLocationKey(provinceId);
	}

	public static int resolveScheduledOrdinal(
			War war,
			ScheduleLeg leg,
			int slotIndex,
			ScheduledCampaignBattle slot) {
		if (war == null || leg == null || slot == null) {
			return 1;
		}
		String key = locationKeyForSlot(slot, slot.provinceId());
		int fought = war.getLocationBattleCount(key);
		int prior = 0;
		List<ScheduledCampaignBattle> schedule = CampaignScheduleService.scheduleListForLeg(war, leg);
		for (int i = 0; i < slotIndex && i < schedule.size(); i++) {
			ScheduledCampaignBattle priorSlot = schedule.get(i);
			if (key.equals(locationKeyForSlot(priorSlot, priorSlot.provinceId()))) {
				prior++;
			}
		}
		return fought + prior + 1;
	}

	public static String resolveScheduledDisplayName(
			War war,
			ScheduleLeg leg,
			int slotIndex,
			ScheduledCampaignBattle slot,
			int provinceId) {
		if (slot == null) {
			return buildDisplayName(BattleType.FIELD, resolveLocationDisplayName(provinceId), 1);
		}
		if (slot.kind() == CampaignBattleKind.SIEGE && slot.fortInstallationId() != null) {
			String fortName = CampaignScheduleService.resolveInstallationName(slot.fortInstallationId());
			String location = fortName != null && !fortName.isBlank()
					? fortName
					: resolveLocationDisplayName(provinceId);
			int ordinal = resolveScheduledOrdinal(war, leg, slotIndex, slot);
			return buildDisplayName(BattleType.SIEGE, location, ordinal);
		}
		String locationDisplay = resolveLocationDisplayName(provinceId);
		int ordinal = resolveScheduledOrdinal(war, leg, slotIndex, slot);
		return buildDisplayName(slot.battleType(), locationDisplay, ordinal);
	}

	public static String buildRaidDisplayName(War war, Installation target) {
		String location = target != null && target.getName() != null && !target.getName().isBlank()
				? target.getName()
				: WILDERNESS;
		String key = raidLocationKey(target);
		int ordinal = war != null ? war.getLocationBattleCount(key) + 1 : 1;
		return buildDisplayName(BattleType.RAID, location, ordinal);
	}

	public static String raidLocationKey(Installation target) {
		if (target == null || target.getId() == null || target.getId().isBlank()) {
			return "raid:unknown";
		}
		return "raid:" + target.getId();
	}

	public static String slugifyDisplayName(String displayName) {
		if (displayName == null || displayName.isBlank()) {
			return "wilderness";
		}
		String stripped = Formatter.formatId(displayName).trim();
		if (stripped.isBlank()) {
			return "wilderness";
		}
		String slug = stripped
				.toLowerCase()
				.replaceAll("[^a-z0-9]+", "_")
				.replaceAll("_+", "_")
				.replaceAll("^_|_$", "");
		if (slug.isBlank()) {
			return "wilderness";
		}
		if (slug.length() > MAX_SLUG_LENGTH) {
			slug = slug.substring(0, MAX_SLUG_LENGTH).replaceAll("_+$", "");
		}
		return slug.isBlank() ? "wilderness" : slug;
	}

	public static String campaignWarbandId(String displayName, String battleSideId) {
		String slug = slugifyDisplayName(displayName);
		if (BattleTemplate.DEFENDER_SIDE.equals(battleSideId)) {
			return slug + "_defender";
		}
		return slug + "_attacker";
	}

	public static String buildDisplayName(BattleType type, String locationName, int ordinal) {
		String safeLocation = locationName != null && !locationName.isBlank() ? locationName : WILDERNESS;
		if (type == BattleType.RAID) {
			return ordinalPrefix(ordinal) + safeLocation + " Raid";
		}
		String prefix = ordinalPrefix(ordinal);
		if (type == BattleType.SIEGE) {
			return prefix + "Siege of " + safeLocation;
		}
		return prefix + "Battle of " + safeLocation;
	}

	public static String resolveLocationKey(int provinceId) {
		return resolveLocation(provinceId).key();
	}

	public static String resolveLocationDisplayName(int provinceId) {
		return resolveLocation(provinceId).displayName();
	}

	static LocationInfo resolveLocation(int provinceId) {
		Settlement exact = findSettlement(provinceId);
		if (exact != null && exact.getName() != null && !exact.getName().isBlank()) {
			logLocation(provinceId, "settlement-exact", exact.getName(), null);
			return new LocationInfo("settlement:" + exact.getName(), exact.getName());
		}
		Installation fort = findInstallation(provinceId, InstallationKind.FORT);
		if (fort != null && fort.getName() != null && !fort.getName().isBlank()) {
			logLocation(provinceId, "fort", fort.getName(), null);
			return new LocationInfo("fort:" + fort.getName(), fort.getName());
		}
		Title title = TitleLoader.getByProvince(provinceId);
		if (title != null && title.getName() != null && !title.getName().isBlank()) {
			logLocation(provinceId, "title", title.getName(), title.getId());
			return new LocationInfo("county:" + title.getName(), title.getName());
		}
		logLocation(provinceId, "wilderness", WILDERNESS, null);
		return new LocationInfo("wilderness:" + provinceId, WILDERNESS);
	}

	private static void logLocation(int provinceId, String path, String display, String titleId) {
		LogManager.war(
				"NAME province=%d path=%s display=%s titleId=%s",
				provinceId,
				path,
				display,
				titleId != null ? titleId : "-");
	}

	private static Settlement findSettlement(int provinceId) {
		for (Faction faction : FactionManager.getCopy()) {
			if (faction == null || faction.getSettlementHandler() == null) {
				continue;
			}
			Settlement settlement = faction.getSettlementHandler().getByProvince(provinceId);
			if (settlement != null) {
				return settlement;
			}
		}
		return null;
	}

	private static Installation findInstallation(int provinceId, InstallationKind kind) {
		for (Faction faction : FactionManager.getCopy()) {
			if (faction == null || faction.getInstallationHandler() == null) {
				continue;
			}
			Installation installation = faction.getInstallationHandler().getByProvince(kind, provinceId);
			if (installation != null) {
				return installation;
			}
		}
		return null;
	}

	static String ordinalPrefix(int ordinal) {
		if (ordinal <= 1) {
			return "";
		}
		return switch (ordinal) {
			case 2 -> "Second ";
			case 3 -> "Third ";
			default -> ordinal + "th ";
		};
	}

	record LocationInfo(String key, String displayName) {
	}
}
