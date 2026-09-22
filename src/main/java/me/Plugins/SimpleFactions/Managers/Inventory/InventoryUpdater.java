package me.Plugins.SimpleFactions.Managers.Inventory;

import java.util.EnumSet;
import java.util.Set;

import me.Plugins.SimpleFactions.War.battle.engine.core.Battle;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import me.Plugins.SimpleFactions.Guild.Guild;
import me.Plugins.SimpleFactions.Guild.loans.Loan;
import me.Plugins.SimpleFactions.Loaders.TierLoader;
import me.Plugins.SimpleFactions.Managers.FactionManager;
import me.Plugins.SimpleFactions.War.battle.ui.BattleInventoryManager;
import me.Plugins.SimpleFactions.Managers.Holder.CampaignInventoryHolder;
import me.Plugins.SimpleFactions.Managers.Holder.CampaignRaidLaunchHolder;
import me.Plugins.SimpleFactions.Managers.Holder.DeclareWarHolder;
import me.Plugins.SimpleFactions.Managers.Holder.SFCombinedInventoryHolder;
import me.Plugins.SimpleFactions.Managers.Holder.SFInventoryHolder;
import me.Plugins.SimpleFactions.Managers.Holder.WarInventoryHolder;
import me.Plugins.SimpleFactions.Managers.InventoryManager;
import me.Plugins.SimpleFactions.Managers.WarManager;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.Tiers.Tier;
import me.Plugins.SimpleFactions.War.core.War;
import me.Plugins.SimpleFactions.enums.SFGUI;
import me.Plugins.SimpleFactions.government.election.Candidate;
import me.Plugins.SimpleFactions.government.movement.Action;
import me.Plugins.SimpleFactions.government.movement.Movement;
import me.Plugins.SimpleFactions.government.movement.cause.Cause;
import me.Plugins.SimpleFactions.government.proposal.TaxTarget;
import me.Plugins.SimpleFactions.laws.LawGroup;

public class InventoryUpdater {
	InventoryManager inv;
	private final BattleInventoryManager battleInv = new BattleInventoryManager();

	public InventoryUpdater(InventoryManager inv) {
		this.inv = inv;
	}

	private static final String BATTLE_LIST_TITLE = "§7Battle List";

	private static final Set<SFGUI> SKIP_PERIODIC_REFRESH = EnumSet.of(
			SFGUI.GUILD_VIEW,
			SFGUI.GUILD_LIST,
			SFGUI.FACTION_GUILDS,
			SFGUI.LAW_PROPOSAL_SELECT,
			SFGUI.LAW_SELECT,
			SFGUI.FAVOUR_REPRESS_SELECT,
			SFGUI.PROPOSALS,
			SFGUI.CAUSES_VIEW,
			SFGUI.CAUSE_VIEW,
			SFGUI.LEDGER_VIEW,
			SFGUI.COMPANY_VIEW);

	static boolean skipsPeriodicRefresh(SFGUI type) {
		return type != null && SKIP_PERIODIC_REFRESH.contains(type);
	}

	public void updateInventory() {
		for (Player p : Bukkit.getOnlinePlayers()) {
			if (p.getOpenInventory().getTopInventory() == null) continue;
			Inventory i = p.getOpenInventory().getTopInventory();

			if (i.getHolder() instanceof SFInventoryHolder h) {
				refreshSFHolder(p, i, h);
			} else if (i.getHolder() instanceof WarInventoryHolder h) {
				War w = WarManager.getById(h.getId());
				if (w == null) continue;
				if (h.getType().equals(SFGUI.WAR_VIEW)) {
					inv.warView(i, p, w, false);
				}
			} else if (i.getHolder() instanceof SFCombinedInventoryHolder h) {
				if (h.getType().equals(SFGUI.MERCENARY_ENGAGEMENT_LIST)) {
					War w = WarManager.getById(h.getWarId());
					if (w == null) continue;
					inv.warView.populateEngagementList(i, p, w, h.getFactionId());
					continue;
				}
				Faction f = FactionManager.getByString(h.getFactionId());
				War w = WarManager.getById(h.getWarId());
				if (f == null || w == null) continue;
				if (h.getType().equals(SFGUI.PARTICIPANT_VIEW)) {
					inv.participantView(i, p, w, w.getParticipant(f), false);
				}
			} else if (i.getHolder() instanceof DeclareWarHolder h) {
				refreshDeclareWar(p, i, h);
			} else if (i.getHolder() instanceof CampaignInventoryHolder h) {
				refreshCampaignPick(p, i, h);
			} else if (i.getHolder() instanceof CampaignRaidLaunchHolder h) {
				refreshCampaignRaid(p, i, h);
			} else {
				// Battle list: match by title (no holder)
				String title = p.getOpenInventory().getTitle();
				if (BATTLE_LIST_TITLE.equals(title)) {
					battleInv.populateBattleList(i);
				}
			}
		}
	}

	private void refreshSFHolder(Player p, Inventory i, SFInventoryHolder h) {
		SFGUI type = h.getType();
		if (skipsPeriodicRefresh(type)) {
			return;
		}

		// Screens with no faction/guild id
		if (type == SFGUI.FACTION_LIST) {
			inv.factionView.populateFactionList(i, p);
			return;
		}
		if (type == SFGUI.GUILD_LIST) {
			inv.guildView.populateGuildList(i, p);
			return;
		}
		if (type == SFGUI.WAR_LIST) {
			inv.warView.populateWarList(i);
			return;
		}
		if (type == SFGUI.MERCENARY_MARKET_LIST) {
			inv.mercenaryMarketView.populateMarketList(i, p);
			return;
		}
		if (type == SFGUI.PLAYER_LEDGER_VIEW) {
			inv.playerLedgerView.open(p, i);
			return;
		}

		// Guild-keyed screens
		if (isGuildKeyed(type)) {
			Guild guild = FactionManager.getGuildByString(h.getId());
			if (guild == null) return;
			refreshGuildHolder(p, i, h, guild);
			return;
		}

		// Movement-keyed screens (MOVEMENT_LIST uses faction id, others use movement id)
		if (type == SFGUI.MOVEMENT_LIST) {
			Faction f = FactionManager.getByString(h.getId());
			if (f == null) return;
			inv.movementListView(p, f, i);
			return;
		}
		if (isMovementKeyed(type)) {
			Movement movement = FactionManager.getMovementById(h.getId());
			if (movement == null) return;
			Faction f = movement.getFaction();
			if (f == null) return;
			refreshMovementHolder(p, i, h, f, movement);
			return;
		}

		// Faction-keyed screens
		Faction f = FactionManager.getByString(h.getId());
		if (f == null) return;
		refreshFactionHolder(p, i, h, f);
	}

	private void refreshGuildHolder(Player p, Inventory i, SFInventoryHolder h, Guild guild) {
		switch (h.getType()) {
			case GUILD_VIEW -> inv.guildView(p, guild, i);
			case UPGRADE_VIEW -> inv.upgradeView(p, guild, i);
			case LEDGER_VIEW -> inv.ledgerView(p, guild, i);
			case COMPANY_VIEW -> inv.companyView(p, guild, i);
			case COMPANY_SLOTS_VIEW -> inv.companySlotsView(p, guild, i);
			case COMPANY_ROSTER_VIEW -> inv.companyRosterView(p, guild, i);
			case COMPANY_UPGRADE_VIEW -> inv.companyUpgradeView(p, guild, i);
			case CONTRACT_LIST_VIEW -> inv.contractListView(p, guild, i);
			case CONTRACT_DETAIL_VIEW -> {
				String contractId = h.getSecondaryId();
				if (contractId == null) return;
				if (guild.getCompany() == null
						|| guild.getCompany().getContractHandler().getById(contractId) == null) {
					// Contract gone - bounce to parent list
					inv.contractListView(p, guild, i);
					return;
				}
				inv.contractDetailView(p, guild, i, contractId);
			}
			case LOAN_MAIN_VIEW -> inv.loanMainView(p, guild, i);
			case LOANS_GIVEN_VIEW -> inv.loansGivenView(p, guild, i);
			case LOANS_TAKEN_VIEW -> inv.loansTakenView(p, guild, i);
			case TAKEN_LOAN_DETAIL_VIEW -> {
				String loanId = h.getSecondaryId();
				if (loanId == null) return;
				Loan loan = guild.getLoanHandler().getLoanById(loanId);
				if (loan == null) {
					// Loan gone - bounce to parent list
					inv.loansTakenView(p, guild, i);
					return;
				}
				inv.loanDetailView(p, guild, loan, true, i);
			}
			case ISSUED_LOAN_DETAIL_VIEW -> {
				String loanId = h.getSecondaryId();
				if (loanId == null) return;
				Loan loan = guild.getLoanHandler().getLoanById(loanId);
				if (loan == null) {
					inv.loansGivenView(p, guild, i);
					return;
				}
				inv.loanDetailView(p, guild, loan, false, i);
			}
			default -> {}
		}
	}

	private void refreshFactionHolder(Player p, Inventory i, SFInventoryHolder h, Faction f) {
		switch (h.getType()) {
			case FACTION_VIEW -> inv.factionView.factionView(p, f, i);
			case FACTION_GUILDS -> inv.factionView.factionGuildsView(p, f, i);
			case GOVERNMENT_VIEW -> inv.governmentView(p, f, i);
			case COUNCIL_VIEW -> inv.governmentView.councilView(p, f, i);
			case PROPOSALS -> inv.governmentView.proposalList(p, f, i);
			case PROPOSAL_VIEW -> inv.proposalView(p, f, i);
			case POLITICAL_PROPOSAL_VIEW -> inv.governmentView.politicalProposalView(p, f, i);
			case WAR_PEACE_SELECT -> {
				String sid = h.getSecondaryId();
				if (sid == null) {
					return;
				}
				try {
					Action action = Action.valueOf(sid);
					inv.governmentView.warPeaceSelectView(p, f, action, h.getFlag(), h.getPage(), i);
				} catch (IllegalArgumentException ex) { /* stale */ }
			}
			case TAX_PROPOSAL_VIEW -> inv.governmentView.taxProposalView(p, f, i);
			case SPECIFIC_TAX_PROPOSAL_VIEW -> {
				String sid = h.getSecondaryId();
				if (sid == null) return;
				try {
					TaxTarget target = TaxTarget.valueOf(sid);
					inv.governmentView.specificTaxProposalView(p, f, i, target);
				} catch (IllegalArgumentException ex) { /* stale */ }
			}
			case LAW_PROPOSAL_VIEW -> inv.governmentView.lawProposalView(p, f, i);
			case LAW_PROPOSAL_SELECT -> {
				String sid = h.getSecondaryId();
				if (sid == null) return;
				LawGroup group = f.getLawHandler().getGroup(sid);
				if (group == null) {
					inv.governmentView.lawProposalView(p, f, i);
					return;
				}
				inv.governmentView.lawProposalSelect(p, f, group, i);
			}
			case FAVOUR_REPRESS_MAIN -> inv.governmentView.favourRepressMainView(p, f, i);
			case FAVOUR_REPRESS_TYPE -> inv.governmentView.favourRepressTypeView(p, f, h.getFlag(), i);
			case FAVOUR_REPRESS_SELECT -> inv.governmentView.favourRepressSelectView(p, f, h.getFlag(), h.getPage() == 1, i);
			case MILITARY_VIEW -> inv.militaryView(i, p, f, false);
			case INSTALLATIONS_VIEW -> inv.installationsView(i, p, f, false);
			case INSTALLATION_DETAIL_VIEW -> {
				String sid = h.getSecondaryId();
				if (sid == null) return;
				inv.installationDetailView(p, f, sid, i);
			}
			case DIPLOMACY_VIEW -> inv.diplomacyView(i, p, f, false);
			case DIPLOMACY_LIST -> inv.diplomacyListView(i, p, f, false);
			case ATTITUDE_VIEW -> inv.attitudeView(i, p, f, false);
			case RELATION_VIEW -> inv.relationView(i, p, f, false);
			case TAX_VIEW -> inv.taxView.taxView(p, f, i);
			case TAX_VIEW_SPECIFIC -> {
				String sid = h.getSecondaryId();
				if (sid == null) return;
				try {
					TaxTarget target = TaxTarget.valueOf(sid);
					inv.taxView.specificTaxView(p, f, target, i);
				} catch (IllegalArgumentException ex) { /* stale */ }
			}
			case LAW_VIEW -> inv.lawView(p, f, i);
			case LAW_SELECT -> {
				String sid = h.getSecondaryId();
				if (sid == null) return;
				LawGroup group = f.getLawHandler().getGroup(sid);
				if (group == null) {
					inv.lawView(p, f, i);
					return;
				}
				inv.lawView.lawSelect(p, f, group, i);
			}
			case ELECTION_VIEW -> inv.electionView.electionView(p, f, i);
			case ELECTION_VOTING_VIEW -> {
				String sid = h.getSecondaryId();
				if (sid == null) return;
				try {
					Candidate candidateType = Candidate.valueOf(sid);
					inv.electionView.votingView(p, f, candidateType, i);
				} catch (IllegalArgumentException ex) { /* stale */ }
			}
			case TIER_VIEW -> inv.tierView(i, p, f, false);
			case TITLE_VIEW -> inv.titleView(i, p, f, false);
			case TITLE_TYPE_VIEW -> {
				String sid = h.getSecondaryId();
				if (sid == null) return;
				Tier tier = TierLoader.getByString(sid);
				if (tier == null) return;
				inv.titleTypeView(i, p, f, tier, false, h.getPage());
			}
			default -> {}
		}
	}

	private void refreshMovementHolder(Player p, Inventory i, SFInventoryHolder h, Faction f, Movement movement) {
		switch (h.getType()) {
			case MOVEMENT_VIEW -> inv.movementView(p, f, movement, i);
			case CAUSES_VIEW -> inv.causesView(p, f, movement, i);
			case CAUSE_VIEW -> {
				int index = h.getPage();
				java.util.List<Cause> causes = movement.getCauses();
				if (index < 0 || index >= causes.size()) {
					inv.causesView(p, f, movement, i);
					return;
				}
				inv.causeView(p, f, movement, causes.get(index), i);
			}
			case TARGET_SELECT -> {
				int index = h.getPage();
				java.util.List<Cause> causes = movement.getCauses();
				if (index < 0 || index >= causes.size()) return;
				inv.movementView.targetSelectionView(p, f, movement, causes.get(index), i);
			}
			case MOVEMENT_DEMANDS -> inv.movementView.demandsView(p, f, movement, i);
			case MOVEMENT_CRACKDOWN -> inv.movementView.crackdownView(p, f, movement, i);
			default -> {}
		}
	}

	private void refreshDeclareWar(Player p, Inventory i, DeclareWarHolder h) {
		Faction attacker = FactionManager.getByString(h.getAttackerId());
		Faction defender = FactionManager.getByString(h.getDefenderId());
		if (attacker == null || defender == null) return;
		switch (h.getStep()) {
			case WAR_DECLARE_GOAL -> inv.declareWarView.openGoalPicker(p, attacker, defender, i);
			case WAR_DECLARE_RELATION_TYPE -> inv.declareWarView.openRelationTypePicker(p, attacker, defender, i);
			case WAR_DECLARE_TITLE -> inv.declareWarView.openTitlePicker(p, attacker, defender, i);
			case WAR_DECLARE_SUBJECT -> inv.declareWarView.openSubjectPicker(p, attacker, defender, i);
			case WAR_DECLARE_SETTLEMENT -> inv.declareWarView.openSettlementPicker(p, attacker, defender, i);
			case WAR_DECLARE_GOVERNMENT -> inv.declareWarView.openGovernmentPicker(
					p, attacker, defender, h.getGovernmentLawId(), h.getLeadershipLawId(), i);
			default -> {}
		}
	}

	private void refreshCampaignPick(Player p, Inventory i, CampaignInventoryHolder h) {
		if (h.getType() != SFGUI.CAMPAIGN_INSTALLATION_PICK_VIEW) return;
		War war = WarManager.getById(h.getWarId());
		if (war == null || !war.isActive()) return;
		Faction viewerFaction = FactionManager.getByMember(p.getName());
		if (viewerFaction == null) return;
		if (!war.isParticipating(viewerFaction)) return;
		inv.campaignInstallationPickView.open(p, war, viewerFaction, false, i);
	}

	private void refreshCampaignRaid(Player p, Inventory i, CampaignRaidLaunchHolder h) {
		War war = WarManager.getById(h.getWarId());
		if (war == null || !war.isActive()) return;
		Faction viewerFaction = FactionManager.getByMember(p.getName());
		if (viewerFaction == null) return;
		if (h.isSourcePage()) {
			inv.campaignRaidLaunchView.openSourcePage(p, war, viewerFaction, false, i);
		} else {
			inv.campaignRaidLaunchView.openTargetPage(p, war, viewerFaction, h.getSourceInstallationId(), false, i);
		}
	}

	static boolean usesGuildBranch(SFGUI type) {
		return type == SFGUI.GUILD_VIEW || type == SFGUI.UPGRADE_VIEW;
	}

	private static boolean isGuildKeyed(SFGUI type) {
		return switch (type) {
			case GUILD_VIEW, UPGRADE_VIEW, LEDGER_VIEW,
				 LOAN_MAIN_VIEW, LOANS_GIVEN_VIEW, LOANS_TAKEN_VIEW,
				 TAKEN_LOAN_DETAIL_VIEW, ISSUED_LOAN_DETAIL_VIEW,
				 COMPANY_VIEW, COMPANY_SLOTS_VIEW, COMPANY_ROSTER_VIEW, COMPANY_UPGRADE_VIEW,
				 CONTRACT_LIST_VIEW, CONTRACT_DETAIL_VIEW -> true;
			default -> false;
		};
	}

	private static boolean isMovementKeyed(SFGUI type) {
		return switch (type) {
			case MOVEMENT_VIEW, CAUSES_VIEW, CAUSE_VIEW, TARGET_SELECT, MOVEMENT_DEMANDS, MOVEMENT_CRACKDOWN -> true;
			default -> false;
		};
	}

	public void inventorySound(String sound, SFGUI gui) {
		for (Player p : Bukkit.getOnlinePlayers()) {
			if (p.getOpenInventory().getTopInventory() == null) continue;
			Inventory i = p.getOpenInventory().getTopInventory();
			if (!(i.getHolder() instanceof SFInventoryHolder h)) continue;
			if (h.getType().equals(gui)) {
				p.playSound(p, sound, 1f, 1f);
			}
		}
	}
}
