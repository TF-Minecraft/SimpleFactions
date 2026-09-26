package net.tfminecraft.simplefactions.war.campaign.ui;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import net.tfminecraft.simplefactions.army.Military;
import net.tfminecraft.simplefactions.army.Regiment;
import net.tfminecraft.simplefactions.loaders.VehiclesConfigLoader;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.war.campaign.runtime.pick.BattleInstallationPickService;
import net.tfminecraft.simplefactions.war.campaign.runtime.pick.BattleSiegeFortService;
import net.tfminecraft.simplefactions.war.campaign.runtime.CampaignClock;
import net.tfminecraft.simplefactions.war.campaign.ui.CampaignUiCopy;
import net.tfminecraft.simplefactions.war.core.Participant;
import net.tfminecraft.simplefactions.war.core.Side;
import net.tfminecraft.simplefactions.war.core.War;
import net.tfminecraft.simplefactions.vehicles.registry.PlayerVehicleRecord;
import net.tfminecraft.simplefactions.vehicles.registry.PlayerVehicleRegistry;
import net.tfminecraft.tlibs.objects.api.subapi.StringFormatter;

final class CampaignBattleIconLore {
	private static final List<String> VEHICLE_CATEGORY_ORDER = List.of("ships", "aircraft", "land_vehicles");

	private CampaignBattleIconLore() {}

	static void appendSoldiers(List<String> lore, War war) {
		if (lore == null || war == null) {
			return;
		}
		lore.add(StringFormatter.formatHex(
				CampaignUiCopy.LABEL + "Attackers: " + CampaignUiCopy.VALUE + countSoldiers(war.getAttackers())));
		lore.add(StringFormatter.formatHex(
				CampaignUiCopy.LABEL + "Defenders: " + CampaignUiCopy.VALUE + countSoldiers(war.getDefenders())));
	}

	static void appendVehiclesIfLocked(List<String> lore, War war) {
		if (lore == null || war == null) {
			return;
		}
		if (!BattleInstallationPickService.isLocked(war, CampaignClock.now())) {
			return;
		}
		if (SimpleFactions.getInstance() == null) {
			return;
		}
		PlayerVehicleRegistry registry = SimpleFactions.getVehicleRegistry();
		if (registry == null) {
			return;
		}
		Map<String, Map<String, Integer>> counts = new LinkedHashMap<>();
		for (String category : VEHICLE_CATEGORY_ORDER) {
			counts.put(category, new TreeMap<>());
		}
		for (Map.Entry<String, String> picked : inPlayInstallations(war)) {
			for (PlayerVehicleRecord record : registry.getByInstallation(picked.getKey(), picked.getValue())) {
				if (record == null || record.getVehicleTypeId() == null) {
					continue;
				}
				if (!VehiclesConfigLoader.showsOnUpcomingBattleIcon(record.getVehicleTypeId())) {
					continue;
				}
				String category = VehiclesConfigLoader.getCategoryId(record.getVehicleTypeId()).orElse("");
				Map<String, Integer> byType = counts.get(category);
				if (byType == null) {
					continue;
				}
				String typeId = record.getVehicleTypeId().toLowerCase(Locale.ROOT);
				byType.put(typeId, byType.getOrDefault(typeId, 0) + 1);
			}
		}
		boolean any = counts.values().stream().anyMatch(map -> !map.isEmpty());
		if (!any) {
			return;
		}
		lore.add(StringFormatter.formatHex(CampaignUiCopy.VALUE + "Vehicles"));
		for (String category : VEHICLE_CATEGORY_ORDER) {
			Map<String, Integer> byType = counts.get(category);
			if (byType == null || byType.isEmpty()) {
				continue;
			}
			lore.add(StringFormatter.formatHex(CampaignUiCopy.LABEL + categoryLabel(category)));
			for (Map.Entry<String, Integer> entry : byType.entrySet()) {
				lore.add(StringFormatter.formatHex(
						CampaignUiCopy.MUTED + "  " + entry.getKey() + ": " + CampaignUiCopy.VALUE + entry.getValue()));
			}
		}
	}

	static int countSoldiers(Side side) {
		if (side == null || side.getMainParticipants() == null) {
			return 0;
		}
		int total = 0;
		for (Participant participant : side.getMainParticipants()) {
			if (participant == null) {
				continue;
			}
			total += countMilitary(participant.getLeader());
			if (participant.getSubjects() != null) {
				for (Faction subject : participant.getSubjects()) {
					total += countMilitary(subject);
				}
			}
			for (Faction secondary : participant.getJoinedSecondaries()) {
				total += countMilitary(secondary);
			}
		}
		return total;
	}

	private static int countMilitary(Faction faction) {
		if (faction == null || faction.getMilitary() == null) {
			return 0;
		}
		Military military = faction.getMilitary();
		if (military.getRegiments() == null) {
			return 0;
		}
		int total = 0;
		for (Regiment regiment : military.getRegiments()) {
			if (regiment == null || regiment.isLevy() || regiment.isEquipment()) {
				continue;
			}
			total += Math.max(0, regiment.getCurrentSlots());
		}
		return total;
	}

	/** Faction id and installation id pairs. The siege fort has no faction key, so it is not filtered. */
	private static Set<Map.Entry<String, String>> inPlayInstallations(War war) {
		Set<Map.Entry<String, String>> picked = new LinkedHashSet<>();
		if (war.getBattleInstallationPicks() != null) {
			for (Map.Entry<String, ? extends Set<String>> factionPicks : war.getBattleInstallationPicks().entrySet()) {
				if (factionPicks.getValue() == null) {
					continue;
				}
				for (String installationId : factionPicks.getValue()) {
					picked.add(new java.util.AbstractMap.SimpleImmutableEntry<>(factionPicks.getKey(), installationId));
				}
			}
		}
		BattleSiegeFortService.currentSiegeFortInstallationId(war)
				.ifPresent(id -> picked.add(new java.util.AbstractMap.SimpleImmutableEntry<>(null, id)));
		return picked;
	}

	private static String categoryLabel(String categoryId) {
		return switch (categoryId) {
			case "ships" -> "Ships";
			case "aircraft" -> "Aircraft";
			case "land_vehicles" -> "Land";
			default -> categoryId;
		};
	}
}
