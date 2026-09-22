package me.Plugins.SimpleFactions.War.battle.campaign;

import java.util.Optional;

import me.Plugins.SimpleFactions.War.core.War;
import me.Plugins.SimpleFactions.War.battle.enums.BattleType;
import me.Plugins.SimpleFactions.War.campaign.schedule.CampaignScheduleService;
import me.Plugins.SimpleFactions.War.campaign.schedule.ScheduledCampaignBattle;

public final class CampaignBattleTypeResolver {
	private CampaignBattleTypeResolver() {
	}

	public static BattleType resolve(War war, ScheduledCampaignBattle slot) {
		if (slot == null) {
			return BattleType.FIELD;
		}
		return slot.battleType();
	}

	public static BattleType resolve(War war, int scheduledBattleProvinceId) {
		Optional<ScheduledCampaignBattle> slot = CampaignScheduleService.currentSlot(war)
				.filter(current -> current.provinceId() == scheduledBattleProvinceId);
		return resolve(war, slot.orElse(null));
	}
}
