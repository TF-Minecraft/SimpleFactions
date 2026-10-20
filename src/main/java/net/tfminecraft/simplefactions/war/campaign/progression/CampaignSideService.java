package net.tfminecraft.simplefactions.war.campaign.progression;


import net.tfminecraft.simplefactions.war.campaign.ui.CampaignPushTarget;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.war.core.Side;
import net.tfminecraft.simplefactions.war.core.War;
import net.tfminecraft.simplefactions.war.enums.CampaignPhase;
import net.tfminecraft.simplefactions.war.enums.ObjectiveHolder;

public final class CampaignSideService {
	public static final int SCHEMA_VERSION = 3;

	private CampaignSideService() {}

	public static Side toSide(War war, CampaignSideRole coalition) {
		if (war == null || coalition == null) {
			return null;
		}
		return coalition == CampaignSideRole.AGGRESSOR ? war.getAttackers() : war.getDefenders();
	}

	public static CampaignSideRole coalitionOf(War war, Side side) {
		if (war == null || side == null) {
			return null;
		}
		if (side == war.getAttackers()) {
			return CampaignSideRole.AGGRESSOR;
		}
		if (side == war.getDefenders()) {
			return CampaignSideRole.DEFENDER;
		}
		return null;
	}

	public static CampaignSideRole opposing(War war, CampaignSideRole coalition) {
		if (coalition == null) {
			return null;
		}
		return coalition.opposing();
	}

	public static CampaignSideRole getInitiativeHolderCoalition(War war) {
		if (war == null) {
			return CampaignSideRole.AGGRESSOR;
		}
		CampaignSideRole coalition = war.getInitiativeHolderCoalition();
		if (coalition != null) {
			return coalition;
		}
		return belligerentRoleToCoalition(war.getInitiativeHolder());
	}

	public static void setInitiativeHolderCoalition(War war, CampaignSideRole coalition) {
		if (war == null || coalition == null) {
			return;
		}
		war.setInitiativeHolderCoalition(coalition);
		war.setInitiativeHolder(coalitionToBelligerentRole(coalition));
	}

	public static int getFuel(War war, CampaignSideRole coalition) {
		if (war == null || coalition == null) {
			return 0;
		}
		return coalition == CampaignSideRole.AGGRESSOR
				? war.getInitiativeAttacker()
				: war.getInitiativeDefender();
	}

	public static void setFuel(War war, CampaignSideRole coalition, int fuel) {
		if (war == null || coalition == null) {
			return;
		}
		if (coalition == CampaignSideRole.AGGRESSOR) {
			war.setInitiativeAttacker(Math.max(0, fuel));
		} else {
			war.setInitiativeDefender(Math.max(0, fuel));
		}
	}

	public static void spendFuel(War war, CampaignSideRole coalition) {
		if (war == null || coalition == null) {
			return;
		}
		setFuel(war, coalition, getFuel(war, coalition) - 1);
	}

	public static BelligerentRole coalitionToBelligerentRole(CampaignSideRole coalition) {
		if (coalition == null) {
			return BelligerentRole.ATTACKER;
		}
		return coalition == CampaignSideRole.AGGRESSOR
				? BelligerentRole.ATTACKER
				: BelligerentRole.DEFENDER;
	}

	public static CampaignSideRole belligerentRoleToCoalition(BelligerentRole role) {
		if (role == BelligerentRole.DEFENDER) {
			return CampaignSideRole.DEFENDER;
		}
		return CampaignSideRole.AGGRESSOR;
	}

	public static boolean isWarLeader(War war, Faction faction) {
		if (war == null || faction == null || faction.getId() == null) {
			return false;
		}
		String id = faction.getId();
		return id.equalsIgnoreCase(war.getAttackerLeaderId())
				|| id.equalsIgnoreCase(war.getDefenderLeaderId());
	}

	public static boolean isCoalitionWarLeader(War war, Faction faction, CampaignSideRole coalition) {
		if (war == null || faction == null || coalition == null || faction.getId() == null) {
			return false;
		}
		String leaderId = coalition == CampaignSideRole.AGGRESSOR
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

	public static boolean isWhitePeaceProposed(War war, CampaignSideRole coalition) {
		if (war == null || coalition == null) {
			return false;
		}
		return coalition == CampaignSideRole.AGGRESSOR
				? war.isWhitePeaceProposedByAttacker()
				: war.isWhitePeaceProposedByDefender();
	}

	public static void setWhitePeaceProposed(War war, CampaignSideRole coalition, boolean proposed) {
		if (war == null || coalition == null) {
			return;
		}
		if (coalition == CampaignSideRole.AGGRESSOR) {
			war.setWhitePeaceProposedByAttacker(proposed);
		} else {
			war.setWhitePeaceProposedByDefender(proposed);
		}
	}

	/**
	 * Declare-time war coalition on the campaign axis (maps to {@code war.attackers} / {@code war.defenders}).
	 * Not the battle-template attacker/defender side ids.
	 */
	public enum CampaignSideRole {
		AGGRESSOR("aggressor"),
		DEFENDER("defender");

		private final String jsonId;

		CampaignSideRole(String jsonId) {
			this.jsonId = jsonId;
		}

		public String toJson() {
			return jsonId;
		}

		public static CampaignSideRole fromJson(String value) {
			if (value == null || value.isBlank()) {
				return null;
			}
			for (CampaignSideRole coalition : values()) {
				if (coalition.jsonId.equalsIgnoreCase(value)) {
					return coalition;
				}
			}
			return null;
		}

		public CampaignSideRole opposing() {
			return this == AGGRESSOR ? DEFENDER : AGGRESSOR;
		}
	}
}
