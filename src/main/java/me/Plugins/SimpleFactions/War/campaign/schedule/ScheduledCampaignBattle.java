package me.Plugins.SimpleFactions.War.campaign.schedule;

import me.Plugins.SimpleFactions.War.battle.enums.BattleType;
import me.Plugins.SimpleFactions.War.enums.CampaignBattleKind;

public record ScheduledCampaignBattle(
		int provinceId,
		CampaignBattleKind kind,
		boolean required,
		String fortInstallationId,
		String portInstallationId,
		Integer chronologyProvinceId) {

	public ScheduledCampaignBattle(
			int provinceId,
			CampaignBattleKind kind,
			boolean required,
			String fortInstallationId) {
		this(provinceId, kind, required, fortInstallationId, null, null);
	}

	public ScheduledCampaignBattle(
			int provinceId,
			CampaignBattleKind kind,
			boolean required,
			String fortInstallationId,
			String portInstallationId) {
		this(provinceId, kind, required, fortInstallationId, portInstallationId, null);
	}

	public ScheduledCampaignBattle {
		if (kind == null) {
			kind = CampaignBattleKind.FIELD;
		}
		if (fortInstallationId != null && fortInstallationId.isBlank()) {
			fortInstallationId = null;
		}
		if (portInstallationId != null && portInstallationId.isBlank()) {
			portInstallationId = null;
		}
		if (chronologyProvinceId != null && chronologyProvinceId <= 0) {
			chronologyProvinceId = null;
		}
	}

	public int sortProvinceId() {
		return chronologyProvinceId != null ? chronologyProvinceId : provinceId;
	}

	public BattleType battleType() {
		return kind == CampaignBattleKind.SIEGE ? BattleType.SIEGE : BattleType.FIELD;
	}
}
