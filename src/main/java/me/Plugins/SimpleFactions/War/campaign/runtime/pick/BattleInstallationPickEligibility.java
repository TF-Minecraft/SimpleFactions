package me.Plugins.SimpleFactions.War.campaign.runtime.pick;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.War.core.Side;
import me.Plugins.SimpleFactions.War.core.War;
import me.Plugins.SimpleFactions.War.pathfinder.BelligerentTerritory;
import me.Plugins.SimpleFactions.War.pathfinder.TitleManagerProvinceOwnerLookup;
import me.Plugins.SimpleFactions.installation.Installation;
import me.Plugins.SimpleFactions.installation.InstallationKind;
import me.Plugins.SimpleFactions.installation.handler.InstallationHandler;

public final class BattleInstallationPickEligibility {
	private static final Set<InstallationKind> PICKABLE_KINDS = EnumSet.of(
			InstallationKind.PORT,
			InstallationKind.AIRPORT);

	private BattleInstallationPickEligibility() {}

	public static boolean isPickableKind(InstallationKind kind) {
		return kind != null && PICKABLE_KINDS.contains(kind);
	}

	public static boolean isUnderSideControl(War war, Faction faction, Installation installation) {
		if (war == null || faction == null || installation == null) {
			return false;
		}
		Side side = war.getSide(faction);
		if (side == null) {
			return false;
		}
		int province = installation.getProvince();
		if (province <= 0) {
			return false;
		}

		boolean attackerSide = side.equals(war.getAttackers());
		List<Integer> enemyOccupation = attackerSide
				? war.getOccupiedByDefender()
				: war.getOccupiedByAttacker();
		List<Integer> ourOccupation = attackerSide
				? war.getOccupiedByAttacker()
				: war.getOccupiedByDefender();

		if (containsProvince(enemyOccupation, province)) {
			return false;
		}
		if (containsProvince(ourOccupation, province)) {
			return true;
		}

		BelligerentTerritory territory = BelligerentTerritory.fromWar(war, new TitleManagerProvinceOwnerLookup());
		return attackerSide
				? territory.isAttackerSide(province)
				: territory.isDefenderSide(province);
	}

	public static boolean isPickable(War war, Faction faction, Installation installation) {
		if (!isPickableKind(installation != null ? installation.getKind() : null)) {
			return false;
		}
		return isUnderSideControl(war, faction, installation);
	}

	public static List<Installation> listPickableInstallations(War war, Faction faction) {
		if (war == null || faction == null) {
			return List.of();
		}
		InstallationHandler handler = faction.getInstallationHandler();
		if (handler == null) {
			return List.of();
		}
		List<Installation> pickable = new ArrayList<>();
		for (Installation installation : handler.getAll()) {
			if (isPickable(war, faction, installation)) {
				pickable.add(installation);
			}
		}
		pickable.sort(Comparator
				.comparing((Installation installation) -> installation.getKind().name())
				.thenComparing(Installation::getId));
		return List.copyOf(pickable);
	}

	private static boolean containsProvince(List<Integer> provinces, int provinceId) {
		return provinces != null && provinces.contains(provinceId);
	}
}
