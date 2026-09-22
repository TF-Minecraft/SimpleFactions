package net.tfminecraft.simplefactions.war.pathfinder;

import net.tfminecraft.simplefactions.managers.TitleManager;
import net.tfminecraft.simplefactions.objects.Faction;

public class TitleManagerProvinceOwnerLookup implements ProvinceOwnerLookup {
	@Override
	public String getOwnerFactionId(int provinceId) {
		Faction owner = TitleManager.getByProvince(provinceId);
		return owner == null ? null : owner.getId();
	}
}
