package me.Plugins.SimpleFactions.Map.export;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntFunction;

import com.google.gson.JsonArray;

import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.War.core.War;

/**
 * Flatten wartime occupation for ProvinceSystem (province_data + nation overlay queue).
 */
public final class OccupationMapExport {
	private OccupationMapExport() {
	}

	public static Map<Integer, String> occupierByProvince(List<War> wars) {
		return occupierByProvince(wars, null);
	}

	public static Map<Integer, String> occupierByProvince(
			List<War> wars,
			IntFunction<Faction> deJureByProvince) {
		Map<Integer, String> occupiers = new LinkedHashMap<>();
		if (wars == null) {
			return occupiers;
		}
		for (War war : wars) {
			if (!WarMapExporter.shouldExport(war)) {
				continue;
			}
			putAll(occupiers, war.getOccupiedByAttacker(), war.getAttackerLeaderId(), deJureByProvince);
			putAll(occupiers, war.getOccupiedByDefender(), war.getDefenderLeaderId(), deJureByProvince);
		}
		return occupiers;
	}

	public static Set<Integer> occupiedProvinceIds(War war) {
		LinkedHashSet<Integer> ids = new LinkedHashSet<>();
		if (war == null) {
			return ids;
		}
		addAll(ids, war.getOccupiedByAttacker());
		addAll(ids, war.getOccupiedByDefender());
		addAll(ids, war.getLastBattleOccupied());
		return ids;
	}

	public static List<String> nationRgbsToEnqueue(War war, IntFunction<Faction> deJureByProvince) {
		LinkedHashSet<String> rgbs = new LinkedHashSet<>();
		if (war == null) {
			return new ArrayList<>();
		}
		addRgb(rgbs, war.getAttackers() != null ? war.getAttackers().getLeader() : null);
		addRgb(rgbs, war.getDefenders() != null ? war.getDefenders().getLeader() : null);
		if (deJureByProvince != null) {
			for (int provinceId : occupiedProvinceIds(war)) {
				addRgb(rgbs, deJureByProvince.apply(provinceId));
			}
		}
		return new ArrayList<>(rgbs);
	}

	public static JsonArray toIntArray(Collection<Integer> values) {
		JsonArray array = new JsonArray();
		if (values == null) {
			return array;
		}
		for (Integer value : values) {
			if (value != null) {
				array.add(value);
			}
		}
		return array;
	}

	private static void putAll(
			Map<Integer, String> occupiers,
			List<Integer> provinceIds,
			String occupierId,
			IntFunction<Faction> deJureByProvince) {
		if (occupierId == null || occupierId.isEmpty() || provinceIds == null) {
			return;
		}
		for (Integer provinceId : provinceIds) {
			if (provinceId == null) {
				continue;
			}
			if (deJureByProvince != null) {
				Faction owner = deJureByProvince.apply(provinceId);
				if (owner != null && occupierId.equalsIgnoreCase(owner.getId())) {
					continue;
				}
			}
			occupiers.put(provinceId, occupierId);
		}
	}

	private static void addAll(Set<Integer> ids, List<Integer> source) {
		if (source == null) {
			return;
		}
		ids.addAll(source);
	}

	private static void addRgb(Set<String> rgbs, Faction faction) {
		if (faction == null) {
			return;
		}
		String rgb = faction.getRGB();
		if (rgb != null && !rgb.isBlank()) {
			rgbs.add(rgb);
		}
	}
}
