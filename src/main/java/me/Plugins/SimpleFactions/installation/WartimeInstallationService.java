package me.Plugins.SimpleFactions.installation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import me.Plugins.SimpleFactions.Managers.FactionManager;
import me.Plugins.SimpleFactions.Managers.RelationManager;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.War.campaign.progression.BelligerentRole;
import me.Plugins.SimpleFactions.War.campaign.schedule.ScheduledCampaignBattle;
import me.Plugins.SimpleFactions.War.core.Side;
import me.Plugins.SimpleFactions.War.core.War;
import me.Plugins.SimpleFactions.War.enums.CampaignBattleKind;
import me.Plugins.SimpleFactions.installation.handler.InstallationHandler;

public final class WartimeInstallationService {
	private WartimeInstallationService() {}

	public static void occupyLastBattle(War war, BelligerentRole winner) {
		if (war == null || winner == null) {
			return;
		}
		Faction occupyingLeader = occupyingLeader(war, winner);
		List<Integer> occupied = war.getLastBattleOccupied();
		if (occupyingLeader == null || occupied == null || occupied.isEmpty()) {
			return;
		}
		for (int province : occupied) {
			occupy(war, occupyingLeader, province);
		}
	}

	public static void occupySiegeFort(War war, BelligerentRole winner, ScheduledCampaignBattle slot) {
		if (war == null || winner == null || slot == null) {
			return;
		}
		if (slot.kind() != CampaignBattleKind.SIEGE || slot.fortInstallationId() == null) {
			return;
		}
		Installation fort = InstallationLookup.findById(slot.fortInstallationId());
		if (fort == null) {
			return;
		}
		int province = fort.getProvince();
		List<Integer> occupied = war.getLastBattleOccupied();
		if (occupied != null && occupied.contains(province)) {
			return;
		}
		Faction occupyingLeader = occupyingLeader(war, winner);
		occupy(war, occupyingLeader, province);
	}

	public static void occupy(War war, Faction occupyingLeader, int province) {
		if (war == null || occupyingLeader == null || province <= 0) {
			return;
		}
		Faction holder = InstallationLookup.findHolderOnProvince(province);
		if (holder == null) {
			return;
		}
		snapshotProvince(war, holder, province);
		Faction original = snapshotOriginal(war, holder, province);
		Faction target = occupyingLeader;
		if (original != null && onOccupyingSide(war, occupyingLeader, original)) {
			target = original;
		}
		InstallationTransferService.transfer(holder, target, province);
	}

	public static void revert(War war) {
		if (war == null) {
			return;
		}
		Map<String, String> snapshot = war.getWartimeInstallationOwners();
		if (snapshot == null || snapshot.isEmpty()) {
			return;
		}
		for (Map.Entry<String, String> entry : new ArrayList<>(snapshot.entrySet())) {
			if (entry == null || entry.getKey() == null || entry.getValue() == null) {
				continue;
			}
			Faction original = FactionManager.getByString(entry.getValue());
			Faction holder = InstallationLookup.findHolder(entry.getKey());
			Installation installation = holder != null
					? holder.getInstallationHandler().getById(entry.getKey())
					: InstallationLookup.findById(entry.getKey());
			if (original == null || holder == null || installation == null) {
				continue;
			}
			InstallationTransferService.transfer(holder, original, installation.getProvince());
		}
		war.clearWartimeInstallationOwners();
	}

	private static void snapshotProvince(War war, Faction holder, int province) {
		if (holder.getId() == null) {
			return;
		}
		for (Installation installation : installationsOnProvince(holder, province)) {
			war.putWartimeInstallationOwner(installation.getId(), holder.getId());
		}
	}

	private static Faction snapshotOriginal(War war, Faction holder, int province) {
		Map<String, String> snapshot = war.getWartimeInstallationOwners();
		if (snapshot == null) {
			snapshot = new LinkedHashMap<>();
		}
		for (Installation installation : installationsOnProvince(holder, province)) {
			String originalId = snapshot.get(installation.getId());
			if (originalId != null) {
				Faction original = FactionManager.getByString(originalId);
				if (original != null) {
					return original;
				}
			}
		}
		return holder;
	}

	private static boolean onOccupyingSide(War war, Faction occupyingLeader, Faction original) {
		if (occupyingLeader == null || original == null) {
			return false;
		}
		if (occupyingLeader.getId() != null
				&& occupyingLeader.getId().equalsIgnoreCase(original.getId())) {
			return true;
		}
		Side occupyingSide = war.getSide(occupyingLeader);
		Side originalSide = war.getSide(original);
		if (occupyingSide != null && occupyingSide == originalSide) {
			return true;
		}
		if (RelationManager.sameRealm(occupyingLeader, original)) {
			return true;
		}
		List<Faction> subjects = RelationManager.getSubjects(occupyingLeader);
		return subjects != null && subjects.contains(original);
	}

	private static List<Installation> installationsOnProvince(Faction holder, int province) {
		List<Installation> found = new ArrayList<>();
		InstallationHandler handler = holder.getInstallationHandler();
		if (handler == null) {
			return found;
		}
		for (Installation installation : handler.getAll()) {
			if (installation.getProvince() == province) {
				found.add(installation);
			}
		}
		return found;
	}

	private static Faction occupyingLeader(War war, BelligerentRole winner) {
		if (war == null || winner == null) {
			return null;
		}
		if (winner == BelligerentRole.ATTACKER) {
			return war.getAttackers() != null ? war.getAttackers().getLeader() : null;
		}
		return war.getDefenders() != null ? war.getDefenders().getLeader() : null;
	}
}
