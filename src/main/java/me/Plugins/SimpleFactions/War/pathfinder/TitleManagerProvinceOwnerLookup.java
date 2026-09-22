package me.Plugins.SimpleFactions.War.pathfinder;

import me.Plugins.SimpleFactions.Managers.TitleManager;
import me.Plugins.SimpleFactions.Objects.Faction;

public class TitleManagerProvinceOwnerLookup implements ProvinceOwnerLookup {
	@Override
	public String getOwnerFactionId(int provinceId) {
		Faction owner = TitleManager.getByProvince(provinceId);
		return owner == null ? null : owner.getId();
	}
}
