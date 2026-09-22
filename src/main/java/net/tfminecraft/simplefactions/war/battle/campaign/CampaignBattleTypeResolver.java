package net.tfminecraft.simplefactions.war.battle.campaign;

import java.util.Optional;

import net.tfminecraft.simplefactions.war.core.War;
import net.tfminecraft.simplefactions.war.battle.enums.BattleType;
import net.tfminecraft.simplefactions.war.campaign.schedule.CampaignScheduleService;
import net.tfminecraft.simplefactions.war.campaign.schedule.ScheduledCampaignBattle;

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
