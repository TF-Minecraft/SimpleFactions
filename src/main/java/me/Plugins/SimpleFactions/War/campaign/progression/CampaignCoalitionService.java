package me.Plugins.SimpleFactions.War.campaign.progression;


import me.Plugins.SimpleFactions.War.campaign.ui.CampaignPushTarget;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.War.core.Side;
import me.Plugins.SimpleFactions.War.core.War;
import me.Plugins.SimpleFactions.War.enums.CampaignPhase;
import me.Plugins.SimpleFactions.War.enums.ObjectiveHolder;

public final class CampaignCoalitionService {
	public static final int SCHEMA_VERSION = 3;

	private CampaignCoalitionService() {}

	public static Side toSide(War war, CampaignCoalition coalition) {
		if (war == null || coalition == null) {
			return null;
		}
		return coalition == CampaignCoalition.AGGRESSOR ? war.getAttackers() : war.getDefenders();
	}

	public static CampaignCoalition coalitionOf(War war, Side side) {
		if (war == null || side == null) {
			return null;
		}
		if (side == war.getAttackers()) {
			return CampaignCoalition.AGGRESSOR;
		}
		if (side == war.getDefenders()) {
			return CampaignCoalition.DEFENDER;
		}
		return null;
	}

	public static CampaignCoalition opposing(War war, CampaignCoalition coalition) {
		if (coalition == null) {
			return null;
		}
		return coalition.opposing();
	}

	public static CampaignCoalition getInitiativeHolderCoalition(War war) {
		if (war == null) {
			return CampaignCoalition.AGGRESSOR;
		}
		CampaignCoalition coalition = war.getInitiativeHolderCoalition();
		if (coalition != null) {
			return coalition;
		}
		return belligerentRoleToCoalition(war.getInitiativeHolder());
	}

	public static void setInitiativeHolderCoalition(War war, CampaignCoalition coalition) {
		if (war == null || coalition == null) {
			return;
		}
		war.setInitiativeHolderCoalition(coalition);
		war.setInitiativeHolder(coalitionToBelligerentRole(coalition));
	}

	public static int getFuel(War war, CampaignCoalition coalition) {
		if (war == null || coalition == null) {
			return 0;
		}
		return coalition == CampaignCoalition.AGGRESSOR
				? war.getInitiativeAttacker()
				: war.getInitiativeDefender();
	}

	public static void setFuel(War war, CampaignCoalition coalition, int fuel) {
		if (war == null || coalition == null) {
			return;
		}
		if (coalition == CampaignCoalition.AGGRESSOR) {
			war.setInitiativeAttacker(Math.max(0, fuel));
		} else {
			war.setInitiativeDefender(Math.max(0, fuel));
		}
	}

	public static void spendFuel(War war, CampaignCoalition coalition) {
		if (war == null || coalition == null) {
			return;
		}
		setFuel(war, coalition, getFuel(war, coalition) - 1);
	}

	public static BelligerentRole coalitionToBelligerentRole(CampaignCoalition coalition) {
		if (coalition == null) {
			return BelligerentRole.ATTACKER;
		}
		return coalition == CampaignCoalition.AGGRESSOR
				? BelligerentRole.ATTACKER
				: BelligerentRole.DEFENDER;
	}

	public static CampaignCoalition belligerentRoleToCoalition(BelligerentRole role) {
		if (role == BelligerentRole.DEFENDER) {
			return CampaignCoalition.DEFENDER;
		}
		return CampaignCoalition.AGGRESSOR;
	}

	public static boolean isWarLeader(War war, Faction faction) {
		if (war == null || faction == null || faction.getId() == null) {
			return false;
		}
		String id = faction.getId();
		return id.equalsIgnoreCase(war.getAttackerLeaderId())
				|| id.equalsIgnoreCase(war.getDefenderLeaderId());
	}

	public static boolean isCoalitionWarLeader(War war, Faction faction, CampaignCoalition coalition) {
		if (war == null || faction == null || coalition == null || faction.getId() == null) {
			return false;
		}
		String leaderId = coalition == CampaignCoalition.AGGRESSOR
				? war.getAttackerLeaderId()
				: war.getDefenderLeaderId();
		return faction.getId().equalsIgnoreCase(leaderId);
	}

	public static CampaignPushTarget derivePushTargetFromLegacyPhase(
			CampaignPhase phase,
			ObjectiveHolder objectiveHeldBy) {
		if (phase == null) {
			phase = CampaignPhase.INVASION;
		}
		return switch (phase) {
			case INVASION -> CampaignPushTarget.TOWARD_OBJECTIVE;
			case COUNTER_PUSH -> CampaignPushTarget.TOWARD_AGGRESSOR_CAPITAL;
			case RETAKE -> CampaignPushTarget.RETAKE_OBJECTIVE;
		};
	}

	public static CampaignPhase deriveLegacyPhaseFromPushTarget(
			CampaignPushTarget pushTarget,
			ObjectiveHolder objectiveHeldBy) {
		if (pushTarget == null) {
			pushTarget = CampaignPushTarget.TOWARD_OBJECTIVE;
		}
		return switch (pushTarget) {
			case TOWARD_OBJECTIVE -> CampaignPhase.INVASION;
			case TOWARD_AGGRESSOR_CAPITAL -> CampaignPhase.COUNTER_PUSH;
			case RETAKE_OBJECTIVE -> CampaignPhase.RETAKE;
		};
	}

	public static boolean isWhitePeaceProposed(War war, CampaignCoalition coalition) {
		if (war == null || coalition == null) {
			return false;
		}
		return coalition == CampaignCoalition.AGGRESSOR
				? war.isWhitePeaceProposedByAttacker()
				: war.isWhitePeaceProposedByDefender();
	}

	public static void setWhitePeaceProposed(War war, CampaignCoalition coalition, boolean proposed) {
		if (war == null || coalition == null) {
			return;
		}
		if (coalition == CampaignCoalition.AGGRESSOR) {
			war.setWhitePeaceProposedByAttacker(proposed);
		} else {
			war.setWhitePeaceProposedByDefender(proposed);
		}
	}

	/**
	 * Declare-time war coalition on the campaign axis (maps to {@code war.attackers} / {@code war.defenders}).
	 * Not the battle-template attacker/defender side ids.
	 */
	public enum CampaignCoalition {
		AGGRESSOR("aggressor"),
		DEFENDER("defender");

		private final String jsonId;

		CampaignCoalition(String jsonId) {
			this.jsonId = jsonId;
		}

		public String toJson() {
			return jsonId;
		}

		public static CampaignCoalition fromJson(String value) {
			if (value == null || value.isBlank()) {
				return null;
			}
			for (CampaignCoalition coalition : values()) {
				if (coalition.jsonId.equalsIgnoreCase(value)) {
					return coalition;
				}
			}
			return null;
		}

		public CampaignCoalition opposing() {
			return this == AGGRESSOR ? DEFENDER : AGGRESSOR;
		}
	}
}
