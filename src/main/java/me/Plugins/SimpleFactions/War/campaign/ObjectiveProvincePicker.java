package me.Plugins.SimpleFactions.War.campaign;

import java.util.HashSet;
import java.util.OptionalInt;
import java.util.Set;

import me.Plugins.SimpleFactions.Loaders.TitleLoader;
import me.Plugins.SimpleFactions.Managers.FactionManager;
import me.Plugins.SimpleFactions.Managers.ProvinceManager;
import me.Plugins.SimpleFactions.Managers.TitleManager;
import me.Plugins.SimpleFactions.Map.Provinces.Province;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.Tiers.Title;
import me.Plugins.SimpleFactions.War.core.War;
import me.Plugins.SimpleFactions.War.enums.WarGoalType;
import me.Plugins.SimpleFactions.War.declare.PillageEligibility;
import me.Plugins.SimpleFactions.settlement.Settlement;
import me.Plugins.SimpleFactions.settlement.handler.SettlementHandler;

public class ObjectiveProvincePicker {
	private final ProvinceManager pm;

	public ObjectiveProvincePicker(ProvinceManager pm) {
		this.pm = pm;
	}

	public OptionalInt pickObjective(War war, Faction defender) {
		if (war == null || war.getGoal() == null || defender == null) {
			return OptionalInt.empty();
		}

		return switch (war.getGoal()) {
			case DE_JURE_ANNEX -> pickForDeJure(war, defender);
			case SUBJUGATE, WAR, TRIBUTARY, USURP, OPEN_MARKET, CHANGE_GOVERNMENT,
					OVERTHROW, CHANGE_LAW, CHANGE_TAX, FORCE_PEACE -> pickFromProvinceSet(
					new HashSet<>(TitleManager.getProvinces(defender)),
					defender);
			case TRANSFER_SUBJECT -> pickForTransferSubject(war);
			case PILLAGE -> pickForPillage(war);
		};
	}

	private OptionalInt pickForDeJure(War war, Faction defender) {
		String titleId = war.getTargetTitleId();
		if (titleId == null || titleId.isBlank()) {
			return OptionalInt.empty();
		}

		Title title = TitleLoader.getById(titleId);
		if (title == null) {
			return OptionalInt.empty();
		}

		Faction owner = TitleManager.getOwner(title);
		if (owner == null) {
			owner = defender;
		}

		return pickFromProvinceSet(new HashSet<>(TitleManager.getProvinces(title)), owner);
	}

	private OptionalInt pickForTransferSubject(War war) {
		String subjectId = war.getSubjectFactionId();
		if (subjectId == null || subjectId.isBlank()) {
			return OptionalInt.empty();
		}

		Faction subject = FactionManager.getByString(subjectId);
		if (subject == null) {
			return OptionalInt.empty();
		}

		return pickFromProvinceSet(new HashSet<>(TitleManager.getProvinces(subject)), subject);
	}

	private OptionalInt pickForPillage(War war) {
		Settlement settlement = PillageEligibility.findSettlement(war.getTargetSettlementId());
		if (settlement == null || settlement.getCenterProvince() <= 0) {
			return OptionalInt.empty();
		}
		return OptionalInt.of(settlement.getCenterProvince());
	}

	private OptionalInt pickFromProvinceSet(Set<Integer> provinceSet, Faction targetFaction) {
		if (provinceSet == null || provinceSet.isEmpty()) {
			return OptionalInt.empty();
		}

		int capital = targetFaction.getCapital();
		if (capital > 0 && provinceSet.contains(capital)) {
			return OptionalInt.of(capital);
		}

		OptionalInt settlement = pickLargestSettlement(provinceSet, targetFaction);
		if (settlement.isPresent()) {
			return settlement;
		}

		return OptionalInt.of(geometricCenterProvince(provinceSet));
	}

	private OptionalInt pickLargestSettlement(Set<Integer> provinceSet, Faction targetFaction) {
		SettlementHandler handler = targetFaction.getSettlementHandler();
		if (handler == null) {
			return OptionalInt.empty();
		}

		int capital = targetFaction.getCapital();
		Settlement best = null;
		int bestPopulation = -1;
		boolean bestIsCapitalSettlement = false;

		for (Settlement settlement : handler.getAll()) {
			if (!provinceSet.contains(settlement.getCenterProvince())) {
				continue;
			}

			boolean isCapitalSettlement = capital > 0 && settlement.getCenterProvince() == capital;
			int population = handler.getPopulation(settlement).size();

			if (isCapitalSettlement) {
				if (!bestIsCapitalSettlement
						|| population > bestPopulation
						|| (population == bestPopulation
								&& settlement.getCenterProvince() < best.getCenterProvince())) {
					best = settlement;
					bestPopulation = population;
					bestIsCapitalSettlement = true;
				}
				continue;
			}

			if (bestIsCapitalSettlement) {
				continue;
			}

			if (best == null
					|| population > bestPopulation
					|| (population == bestPopulation
							&& settlement.getCenterProvince() < best.getCenterProvince())) {
				best = settlement;
				bestPopulation = population;
			}
		}

		return best == null ? OptionalInt.empty() : OptionalInt.of(best.getCenterProvince());
	}

	private int geometricCenterProvince(Set<Integer> provinceSet) {
		long sumX = 0;
		long sumZ = 0;
		int count = 0;

		for (int provinceId : provinceSet) {
			Province province = pm.get(provinceId);
			if (!province.isValid()) {
				continue;
			}
			sumX += province.getCenterX();
			sumZ += province.getCenterZ();
			count++;
		}

		if (count == 0) {
			return provinceSet.stream().min(Integer::compareTo).orElse(0);
		}

		double avgX = (double) sumX / count;
		double avgZ = (double) sumZ / count;

		int bestId = -1;
		double bestDistance = Double.MAX_VALUE;
		for (int provinceId : provinceSet) {
			Province province = pm.get(provinceId);
			if (!province.isValid()) {
				continue;
			}

			double dx = province.getCenterX() - avgX;
			double dz = province.getCenterZ() - avgZ;
			double distance = dx * dx + dz * dz;
			if (distance < bestDistance || (distance == bestDistance && provinceId < bestId)) {
				bestDistance = distance;
				bestId = provinceId;
			}
		}

		if (bestId > 0) {
			return bestId;
		}
		return provinceSet.stream().min(Integer::compareTo).orElse(0);
	}
}
