package me.Plugins.SimpleFactions.Managers;


import me.Plugins.SimpleFactions.War.civilwar.CivilWarCopy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

import me.Plugins.SimpleFactions.Army.Regiment;
import me.Plugins.SimpleFactions.Cache;
import me.Plugins.SimpleFactions.Guild.Guild;
import me.Plugins.SimpleFactions.Guild.loans.Loan;
import me.Plugins.SimpleFactions.Managers.Holder.CampaignInventoryHolder;
import me.Plugins.SimpleFactions.Managers.Holder.CampaignRaidLaunchHolder;
import me.Plugins.SimpleFactions.Managers.Holder.DeclareWarHolder;
import me.Plugins.SimpleFactions.Managers.Holder.SFCombinedInventoryHolder;
import me.Plugins.SimpleFactions.Managers.Holder.SFInventoryHolder;
import me.Plugins.SimpleFactions.Managers.Holder.WarInventoryHolder;
import me.Plugins.SimpleFactions.Managers.Inventory.CampaignInstallationPickView;
import me.Plugins.SimpleFactions.Managers.Inventory.CampaignRaidLaunchView;
import me.Plugins.SimpleFactions.Managers.Inventory.CampaignView;
import me.Plugins.SimpleFactions.Managers.Inventory.CompanyView;
import me.Plugins.SimpleFactions.Managers.Inventory.QueueCancelPayload;
import me.Plugins.SimpleFactions.Managers.Inventory.ContractView;
import me.Plugins.SimpleFactions.Managers.Inventory.MercenaryMarketView;
import me.Plugins.SimpleFactions.Managers.Inventory.DeclareWarView;
import me.Plugins.SimpleFactions.Managers.Inventory.DividendChange;
import me.Plugins.SimpleFactions.Managers.Inventory.ElectionView;
import me.Plugins.SimpleFactions.Managers.Inventory.FactionView;
import me.Plugins.SimpleFactions.Managers.Inventory.GovernmentView;
import me.Plugins.SimpleFactions.Managers.Inventory.GuildView;
import me.Plugins.SimpleFactions.Managers.Inventory.InstallationView;
import me.Plugins.SimpleFactions.Managers.Inventory.InventoryUpdater;
import me.Plugins.SimpleFactions.Managers.Inventory.LawView;
import me.Plugins.SimpleFactions.Managers.Inventory.LoanPayment;
import me.Plugins.SimpleFactions.Managers.Inventory.LoanView;
import me.Plugins.SimpleFactions.Managers.Inventory.MilitaryView;
import me.Plugins.SimpleFactions.Managers.Inventory.MovementView;
import me.Plugins.SimpleFactions.Managers.Inventory.PlayerLedgerView;
import me.Plugins.SimpleFactions.Managers.Inventory.RelationView;
import me.Plugins.SimpleFactions.Managers.Inventory.TaxChange;
import me.Plugins.SimpleFactions.Managers.Inventory.TaxView;
import me.Plugins.SimpleFactions.Managers.Inventory.TierTitleView;
import me.Plugins.SimpleFactions.Managers.Inventory.WarView;
import me.Plugins.SimpleFactions.War.battle.campaign.warband.BattleWarbandRetreatService.ConfirmHandler;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.Objects.Handler.TaxHandler;
import me.Plugins.SimpleFactions.SimpleFactions;
import me.Plugins.SimpleFactions.Tiers.Tier;
import me.Plugins.SimpleFactions.War.core.Participant;
import me.Plugins.SimpleFactions.War.core.War;
import me.Plugins.SimpleFactions.War.declare.WarDeclareRequest;
import me.Plugins.SimpleFactions.War.campaign.progression.WhitePeaceService;
import me.Plugins.SimpleFactions.enums.SFGUI;
import me.Plugins.SimpleFactions.installation.handler.ConstructResult;
import me.Plugins.SimpleFactions.government.Government;
import me.Plugins.SimpleFactions.government.movement.Movement;
import me.Plugins.SimpleFactions.government.movement.cause.Cause;
import me.Plugins.SimpleFactions.government.proposal.Proposal;
import me.Plugins.SimpleFactions.government.proposal.TaxLawChange;
import me.Plugins.SimpleFactions.government.proposal.TaxTarget;
import me.Plugins.SimpleFactions.keys.Keys;
import me.Plugins.TLibs.Objects.API.SubAPI.StringFormatter;

public class InventoryManager implements Listener{
	public HashMap<Player, Faction> confirming = new HashMap<>();
	public HashMap<Player, WarDeclareRequest> pendingWarDeclares = new HashMap<>();
	public HashMap<Player, Integer> campaignConfirmWar = new HashMap<>();
	public HashMap<Player, Boolean> installationConfirmFromCommand = new HashMap<>();
	public HashMap<Player, TaxChange> taxChange = new HashMap<>();
	public HashMap<Player, LoanPayment> loanPayments = new HashMap<>();
	public HashMap<Player, DividendChange> dividendChange = new HashMap<>();
	
	InventoryUpdater updater = new InventoryUpdater(this);
	
	public InventoryUpdater getUpdater() {
		return updater;
	}

	public void start() {
		guildView.setProvinceManager(
			SimpleFactions.getInstance().getProvinceManager()
		);
		new BukkitRunnable() {
			@SuppressWarnings("unchecked")
			@Override
			public void run() {
				for(Map.Entry<Player, TaxChange> entry : ((HashMap<Player, TaxChange>) taxChange.clone()).entrySet()) {
					if(entry.getValue().tick()) {
						taxChange.remove(entry.getKey());
						entry.getKey().sendMessage("§cTax change timed out.");
					}
				}
				for(Map.Entry<Player, LoanPayment> entry : ((HashMap<Player, LoanPayment>) loanPayments.clone()).entrySet()) {
					if(entry.getValue().tick()) {
						loanPayments.remove(entry.getKey());
						entry.getKey().sendMessage("§cLoan payment timed out.");
					}
				}
				for(Map.Entry<Player, DividendChange> entry : ((HashMap<Player, DividendChange>) dividendChange.clone()).entrySet()) {
					if(entry.getValue().tick()) {
						dividendChange.remove(entry.getKey());
						entry.getKey().sendMessage("§cDividend change timed out.");
					}
				}
			}
		}.runTaskTimer(SimpleFactions.plugin, 0, 20L);
	}
	
	/*
	 * This is not an ideal way to set up anything, however,
	 * this used to all be in one file which was 2000 lines
	 * so its a bit better albeit overly complicated!
	 */
	
	///WAAAAR
	public WarView warView = new WarView(this);
	public CampaignView campaignView = new CampaignView(this);

	public CampaignInstallationPickView campaignInstallationPickView = new CampaignInstallationPickView(this);
	public CampaignRaidLaunchView campaignRaidLaunchView = new CampaignRaidLaunchView(this);
	public DeclareWarView declareWarView = new DeclareWarView(this);

	public void warList(Player player) {
		warView.warList(player);
	}

	public void warList(Player player, Inventory i) {
		warView.warList(player, i);
	}

	public void populateWarList(Inventory i) {
		warView.populateWarList(i);
	}
	
	public void warView(Inventory i, Player player, War w, boolean open) {
		warView.warView(i, player, w, open);
	}
	
	public void participantView(Inventory i, Player player, War w, Participant p, boolean open) {
		warView.participantView(i, player, w, p, open); 
	}

	public void openCampaignView(Player player, War war) {
		if (war == null || !war.isActive()) {
			player.sendMessage("§cWar not found.");
			return;
		}
		WhitePeaceService.recalculateProposals(war);
		WarManager.persist(war);
		campaignView.campaignView(player, war, true);
	}

	public void openDeclareWarGoalPicker(Player player, Faction attacker, Faction defender) {
		declareWarView.openGoalPicker(player, attacker, defender);
	}
	
	//Factions
	public FactionView factionView = new FactionView(this);
	public void factionList(Player player) {
		factionView.factionList(player);
	}
	public void factionView(Player player, Faction f) {
		factionView.factionView(player, f);
	}

	//Guilds
	public GuildView guildView = new GuildView(this);
	public PlayerLedgerView playerLedgerView = new PlayerLedgerView(this);
	public void guildList(Player player) {
		guildView.guildList(player);
	}
	public void guildView(Player player, Guild guild) {
		guildView.guildView(player, guild);
	}
	public void guildView(Player player, Guild guild, Inventory i) {
		guildView.guildView(player, guild, i);
	}
	public void upgradeView(Player player, Guild guild) {
		guildView.upgradeView(player, guild);
	}
	public void upgradeView(Player player, Guild guild, Inventory i) {
		guildView.upgradeView(player, guild, i);
	}
	public void ledgerView(Player p, Guild guild, Inventory i) {
		guildView.ledgerView(p, guild, i);
	}

	//Mercenary companies
	public CompanyView companyView = new CompanyView(this);
	public void companyView(Player player, Guild guild) {
		companyView.companyView(player, guild);
	}
	public void companyView(Player player, Guild guild, Inventory i) {
		companyView.companyView(player, guild, i);
	}
	public void companySlotsView(Player player, Guild guild, Inventory i) {
		companyView.slotsView(player, guild, i);
	}
	public void companyRosterView(Player player, Guild guild, Inventory i) {
		companyView.rosterView(player, guild, i);
	}
	public void companyUpgradeView(Player player, Guild guild, Inventory i) {
		companyView.companyUpgradeView(player, guild, i);
	}

	//Mercenary contracts
	public ContractView contractView = new ContractView(this);
	public void contractListView(Player player, Guild guild) {
		contractView.listView(player, guild);
	}
	public void contractListView(Player player, Guild guild, Inventory i) {
		contractView.listView(player, guild, i);
	}
	public void contractDetailView(Player player, Guild guild, String contractId) {
		contractView.detailView(player, guild, contractId);
	}
	public void contractDetailView(Player player, Guild guild, Inventory i, String contractId) {
		contractView.detailView(player, guild, i, contractId);
	}

	public MercenaryMarketView mercenaryMarketView = new MercenaryMarketView(this);
	public void mercenaryMarketList(Player player) {
		mercenaryMarketView.marketList(player);
	}
	public void mercenaryMarketList(Player player, Inventory i) {
		mercenaryMarketView.marketList(player, i);
	}

	//Laws
	public LawView lawView = new LawView(this);
	public void lawView(Player player, Faction f, Inventory i) {
		lawView.lawView(player, f, i);
	}

	//Government
	public GovernmentView governmentView = new GovernmentView(this);
	public void governmentView(Player player, Faction f, Inventory i) {
		governmentView.governmentView(player, f, i);
	}
	public void proposalView(Player player, Faction f, Inventory i) {
		governmentView.proposalView(player, f, i);
	}
	
	//Movements
	public MovementView movementView = new MovementView(this);
	public void movementView(Player player, Faction f, me.Plugins.SimpleFactions.government.movement.Movement movement, Inventory i) {
		movementView.movementView(player, f, movement, i);
	}
	public void movementListView(Player player, Faction f, Inventory i) {
		movementView.movementListView(player, f, i);
	}
	public void causesView(Player player, Faction f, me.Plugins.SimpleFactions.government.movement.Movement movement, Inventory i) {
		movementView.causesView(player, f, movement, i);
	}
	public void causeView(Player player, Faction f, me.Plugins.SimpleFactions.government.movement.Movement movement, me.Plugins.SimpleFactions.government.movement.cause.Cause cause, Inventory i) {
		movementView.causeView(player, f, movement, cause, i);
	}
	
	//Tiers
	public TierTitleView tierTitleView = new TierTitleView(this);
	
	public void tierView(Inventory i, Player player, Faction f, boolean open) {
		tierTitleView.tierView(i, player, f, open);
	}
	public void titleView(Inventory i, Player player, Faction f, boolean open) {
		tierTitleView.titleView(i, player, f, open);
	}
	public void titleTypeView(Inventory i, Player player, Faction f, Tier tier, boolean open, int page) {
		tierTitleView.titleTypeView(i, player, f, tier, open, page);
	}
	
	//Military
	public MilitaryView militaryView = new MilitaryView(this);
	
	public void militaryView(Inventory i, Player player, Faction f, boolean open) {
		militaryView.militaryView(i, player, f, open);
	}

	//Installations
	public InstallationView installationView = new InstallationView(this);

	public void installationsView(Inventory i, Player player, Faction f, boolean open) {
		installationView.installationsView(i, player, f, open);
	}

	public void installationDetailView(Player player, Faction f, String id) {
		installationView.installationDetailView(player, f, id);
	}

	public void installationDetailView(Player player, Faction f, String id, Inventory i) {
		installationView.installationDetailView(player, f, id, i);
	}
	
	//Relations
	
	public RelationView relationView = new RelationView(this);
	
	public void diplomacyView(Inventory i, Player player, Faction f, boolean open) {
		relationView.diplomacyView(i, player, f, open);
	}
	public void diplomacyListView(Inventory i, Player player, Faction f, boolean open) {
		relationView.diplomacyListView(i, player, f, open);
	}
	public void attitudeView(Inventory i, Player player, Faction f, boolean open) {
		relationView.attitudeView(i, player, f, open);
	}
	public void relationView(Inventory i, Player player, Faction f, boolean open) {
		relationView.relationView(i, player, f, open);
	}

	//Election
	public ElectionView electionView = new ElectionView(this);

	public void electionView(Player p, Faction f) {
		electionView.electionView(p, f);
	}

	//Tax
	public TaxView taxView = new TaxView(this);

	public void taxView(Player p, Faction f) {
		taxView.taxView(p, f);
	}

	//Loans
	public LoanView loanView = new LoanView(this);

	public void loanMainView(Player p, Guild guild) {
		loanView.loanMainView(p, guild);
	}

	public void loanMainView(Player p, Guild guild, Inventory i) {
		loanView.loanMainView(p, guild, i);
	}

	public void loansGivenView(Player p, Guild guild) {
		loanView.loansGivenView(p, guild);
	}

	public void loansGivenView(Player p, Guild guild, Inventory i) {
		loanView.loansGivenView(p, guild, i);
	}

	public void loansTakenView(Player p, Guild guild) {
		loanView.loansTakenView(p, guild);
	}

	public void loansTakenView(Player p, Guild guild, Inventory i) {
		loanView.loansTakenView(p, guild, i);
	}

	public void loanDetailView(Player p, Guild guild, Loan loan, boolean isTaken, Inventory i) {
		loanView.loanDetailView(p, guild, loan, isTaken, i);
	}

	public boolean chatTrigger(Player p) {
		return taxChange.containsKey(p) || loanPayments.containsKey(p) || dividendChange.containsKey(p);
	}

	public void setChanging(Faction faction, Player p, TaxTarget target, String id) {
		taxChange.put(p, new TaxChange(faction, target, id));
	}

	public boolean isPayingLoan(Player p) {
		return loanPayments.containsKey(p);
	}

	public void setPayingLoan(Player p, Loan loan) {
		Guild guild = FactionManager.getGuildByLeader(p.getName());;
		if(guild == null) return;
		loanPayments.put(p, new LoanPayment(guild, loan));
		p.closeInventory();
		p.sendMessage(StringFormatter.formatHex("#d6cf69Enter the amount you want to pay (you have #87d65c" + 
			String.format("%.2f", loan.getBorrower().getBank().getWealth()) + "d#d6cf69, loan owes #d65c5c" + 
			String.format("%.2f", loan.getTotalOwed()) + "d#d6cf69):"));
	}

	public void setChangingDividend(Player p, Guild guild) {
		if (guild == null || p == null) {
			return;
		}
		dividendChange.put(p, new DividendChange(guild));
		p.closeInventory();
		p.sendMessage(StringFormatter.formatHex(
				"#d6cf69Enter the dividend percentage (currently #87d65c"
				+ String.format("%.2f", guild.getDividendPercent())
				+ "%#d6cf69). Type #c74d32cancel #d6cf69to abort."));
	}

	@EventHandler
	public void setRate(AsyncPlayerChatEvent e) {
		Player p = e.getPlayer();
		if(!chatTrigger(p)) return;
		e.setCancelled(true);
		new BukkitRunnable() {
			@Override
			public void run() {
				if(taxChange.containsKey(p)) taxChat(p, e);;
				if(loanPayments.containsKey(p)) loanPaymentChat(p, e);
				if(dividendChange.containsKey(p)) dividendChat(p, e);
			}
		}.runTask(SimpleFactions.plugin);
	}

	public void taxChat(Player p, AsyncPlayerChatEvent e) {
		Faction f = taxChange.get(p).getFaction();
		if(f == null) {
			taxChange.remove(p);
			return;
		}
		if(e.getMessage().equalsIgnoreCase("cancel")) {
			p.sendMessage("§cTax change cancelled.");
			taxChange.remove(p);
			governmentView.governmentView(p, f, null);
			return;
		}
		TaxChange change = taxChange.get(p);
		double amount = 0;
		try {
			amount = Double.parseDouble(e.getMessage());
		} catch (Exception ex) {
			p.sendMessage("§cError inputting the amount, use the format §e15.67 §cfor 15.67% tax (example)");
			p.sendMessage("§4Type 'cancel' to cancel.");
			return;
		}
		amount = Math.round(amount*100.0)/100.0;
		amount = Math.min(100.0, amount);
		TaxHandler handler = f.getTaxHandler();
		if(amount > handler.getMax(change.getTarget())) {
			p.sendMessage("§cThe maximum tax rate you can set for "+change.getTarget().getDisplayName()+" is §e"+handler.getMax(change.getTarget())+"%");
			p.sendMessage("§4Type 'cancel' to cancel.");
			return;
		} else if(amount < handler.getMin(change.getTarget())) {
			p.sendMessage("§cThe minimum tax rate you can set for "+change.getTarget().getDisplayName()+" is §e"+handler.getMin(change.getTarget())+"%");
			p.sendMessage("§4Type 'cancel' to cancel.");
			return;
		}
		Government gov = f.getGovernment();
		Proposal proposal = new Proposal(p.getName(), gov);
		TaxLawChange tax = new TaxLawChange(change.getTarget(), change.getId(), amount);
		proposal.setTaxProposal(tax);
		if(!gov.hasCouncil() && f.isLeader(p.getName())) {
			p.sendMessage("§aChange applied!");
			proposal.apply(null);
			p.playSound(p, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
			taxChange.remove(p);
			governmentView.governmentView(p, f, null);
			return;
		}
		if(gov.canBeProposed(proposal)) {
			if(gov.canPropose(p)) {
				gov.propose(proposal);
				p.sendTitle("", "§aProposal Added", 20, 80, 20);
				p.playSound(p, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
			} else if(gov.canProposeOrStartMovement(p)) {
				Movement movement = gov.getMovementByMember(p.getName());
				if(movement != null) {
					if(!gov.canBeProposed(proposal)) {
						p.playSound(p, Sound.ENTITY_VILLAGER_NO, 1f, 1f);
						return;
					}
					movement.createCause(p.getName(), proposal);
					p.sendMessage("§aProposal added to your movement! Rally support for your proposal by sharing it with your faction and allies!");
					p.playSound(p, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
					governmentView(p, f, null);
					return;
				}
				if (me.Plugins.SimpleFactions.War.civilwar.CivilWarHostMovementRules.blocksHostGuildStart(f, p.getName())) {
					p.playSound(p, Sound.ENTITY_VILLAGER_NO, 1f, 1f);
					p.sendMessage(me.Plugins.SimpleFactions.War.civilwar.CivilWarCopy.ONE_PROVINCE_HOST_GUILD);
					return;
				}
				gov.startMovement(p.getName(), proposal);
				p.sendTitle("", "§cMovement Started", 20, 80, 20);
				p.playSound(p, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
				p.sendMessage("§aMovement started! Rally support for your proposal by sharing it with your faction and allies!");
			} else {
				p.sendMessage("§cYou can no longer propose this change.");
				taxChange.remove(p);
				return;
			}
		} else {
			p.sendMessage("§cThere is already a proposal active for this target.");
			taxChange.remove(p);
			return;
		}
		//something changed so you cant do anything anymore :)
		taxChange.remove(p);
		governmentView.governmentView(p, f, null);
	}

	public void loanPaymentChat(Player p, AsyncPlayerChatEvent e) {
		Guild g = loanPayments.get(p).getGuild();
		Guild compare = FactionManager.getGuildByLeader(p.getName());
		if(compare == null) {
			loanPayments.remove(p); //player left guild
			return;
		}
		if(!g.getId().equalsIgnoreCase(compare.getId())) {
			loanPayments.remove(p); //player changed guild
			return;
		}

		if(e.getMessage().equalsIgnoreCase("cancel")) {
			p.sendMessage("§cLoan payment cancelled.");
			loanPayments.remove(p);
			loanView.loanMainView(p, g);
			return;
		}

		LoanPayment payment = loanPayments.get(p);
		Loan loan = payment.getLoan();
		double amount = 0;
		try {
			amount = Double.parseDouble(e.getMessage());
		} catch (Exception ex) {
			p.sendMessage("§cError inputting the amount, use the format §e1500.00 §cfor 1500.00d (example)");
			p.sendMessage("§4Type 'cancel' to cancel.");
			return;
		}

		amount = Math.round(amount*100.0)/100.0;
		if(amount <= 0) {
			p.sendMessage("§cYou must pay a positive amount.");
			p.sendMessage("§4Type 'cancel' to cancel.");
			return;
		}

		if(amount > g.getBank().getWealth()) {
			p.sendMessage("§cYour guild does not have enough funds to pay that amount.");
			p.sendMessage("§4Type 'cancel' to cancel.");
			return;
		}

		if(amount > loan.getTotalOwed()) {
			amount = loan.getTotalOwed();
		}

		double finalAmount = loan.makePayment(amount, true);
		g.getBank().withdraw(finalAmount);
		p.sendMessage(StringFormatter.formatHex("#d6cf69You have paid off #87d65c" + String.format("%.2f", finalAmount) + "d #d6cf69of the loan."));
		p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
		loanPayments.remove(p);
	}

	public void dividendChat(Player p, AsyncPlayerChatEvent e) {
		DividendChange change = dividendChange.get(p);
		Guild guild = change == null ? null : change.getGuild();
		if (guild == null) {
			dividendChange.remove(p);
			return;
		}
		if (e.getMessage().equalsIgnoreCase("cancel")) {
			p.sendMessage("§cDividend change cancelled.");
			dividendChange.remove(p);
			guildView(p, guild);
			return;
		}
		if (!guild.isLeader(p)) {
			p.sendMessage("§cOnly the guild leader can set dividends.");
			dividendChange.remove(p);
			return;
		}
		double amount;
		try {
			amount = Double.parseDouble(e.getMessage());
		} catch (Exception ex) {
			p.sendMessage("§cError inputting the amount, use the format §e20 §cfor 20% (example)");
			p.sendMessage("§4Type 'cancel' to cancel.");
			return;
		}
		amount = Math.round(amount * 100.0) / 100.0;
		if (amount < 0 || amount > 100) {
			p.sendMessage("§cDividend percentage must be between §e0 §cand §e100§c.");
			p.sendMessage("§4Type 'cancel' to cancel.");
			return;
		}
		guild.setDividendPercent(amount);
		p.sendMessage(StringFormatter.formatHex(
				"#d6cf69Dividend rate set to #87d65c" + String.format("%.2f", guild.getDividendPercent()) + "%"));
		p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
		dividendChange.remove(p);
		guildView(p, guild);
	}
	
	//Confirm
	public void confirmView(Player player, Faction f, String key, String data) {
		Inventory i = SimpleFactions.plugin.getServer().createInventory(null, 27, "§7Confirm Action");
		i.setItem(11, createButton("confirm", key, data));
		i.setItem(15, createButton("cancel", key, data));
		player.openInventory(i);
	}

	public void openQueueCancelConfirm(Player player, Faction f, String payload, String title) {
		confirming.put(player, f);
		Inventory i = SimpleFactions.plugin.getServer().createInventory(null, 27, "§7Confirm Action");
		ItemStack info = new ItemStack(Material.PAPER);
		ItemMeta infoMeta = info.getItemMeta();
		infoMeta.setDisplayName(title);
		infoMeta.setLore(java.util.List.of("§7This will remove the item from the queue."));
		info.setItemMeta(infoMeta);
		i.setItem(13, info);
		i.setItem(11, createButton("confirm", "queue_cancel", payload));
		i.setItem(15, createButton("cancel", "queue_cancel", payload));
		player.openInventory(i);
	}

	private void refreshAfterQueueCancel(Player player, QueueCancelPayload.Parsed parsed) {
		switch (parsed.type()) {
			case MILITARY -> {
				Faction f = FactionManager.getByString(parsed.ownerId());
				if (f != null) {
					militaryView(null, player, f, true);
				}
			}
			case GUILD_UPGRADE -> {
				Guild guild = FactionManager.getGuildByString(parsed.ownerId());
				if (guild != null) {
					guildView.upgradeView(player, guild);
				}
			}
			case INSTALLATION -> {
				Faction f = FactionManager.getByString(parsed.ownerId());
				if (f != null) {
					installationsView(null, player, f, true);
				}
			}
			case COMPANY_SLOT -> {
				Guild guild = FactionManager.getGuildByString(parsed.ownerId());
				if (guild != null) {
					companyView.slotsView(player, guild);
				}
			}
			case COMPANY_UPGRADE -> {
				Guild guild = FactionManager.getGuildByString(parsed.ownerId());
				if (guild != null) {
					companyView.companyUpgradeView(player, guild);
				}
			}
			default -> {}
		}
	}

	private boolean executeQueueCancel(QueueCancelPayload.Parsed parsed) {
		return switch (parsed.type()) {
			case MILITARY -> {
				Faction f = FactionManager.getByString(parsed.ownerId());
				yield f != null && f.getMilitary().cancelQueue(parsed.index());
			}
			case GUILD_UPGRADE -> {
				Guild guild = FactionManager.getGuildByString(parsed.ownerId());
				yield guild != null && guild.cancelUpgradeQueue(parsed.index());
			}
			case INSTALLATION -> {
				Faction f = FactionManager.getByString(parsed.ownerId());
				yield f != null && f.getInstallationHandler().deconstruct(parsed.detail()).isSuccess();
			}
			case COMPANY_SLOT -> {
				Guild guild = FactionManager.getGuildByString(parsed.ownerId());
				yield guild != null
						&& guild.getCompany() != null
						&& guild.getCompany().cancelSlotQueue(parsed.index());
			}
			case COMPANY_UPGRADE -> {
				Guild guild = FactionManager.getGuildByString(parsed.ownerId());
				yield guild != null
						&& guild.getCompany() != null
						&& guild.getCompany().cancelUpgradeQueue(parsed.index());
			}
		};
	}

	public void confirmCapitalMoveView(Player player, int provincesLost) {
		Inventory i = SimpleFactions.plugin.getServer().createInventory(null, 27, "§7Confirm Action");
		ItemStack info = new ItemStack(Material.PAPER);
		ItemMeta infoMeta = info.getItemMeta();
		infoMeta.setDisplayName("§eMove faction capital?");
		infoMeta.setLore(java.util.List.of(
				"§cYou will lose " + provincesLost + " provinces"));
		info.setItemMeta(infoMeta);
		i.setItem(13, info);
		i.setItem(11, createButton("confirm", "setcapital", "1"));
		i.setItem(15, createButton("cancel", "setcapital", "1"));
		player.openInventory(i);
	}

	public void confirmEndMovementView(Player player, Movement movement) {
		Inventory i = SimpleFactions.plugin.getServer().createInventory(null, 27, "§7Confirm Action");
		i.setItem(13, movementView.creator.createEndMovementConfirmItem(movement));
		i.setItem(11, createButton("confirm", "end_movement", movement.getId()));
		i.setItem(15, createButton("cancel", "end_movement", movement.getId()));
		player.openInventory(i);
	}

	public void confirmBattleRetreatView(Player player, String battleId) {
		Inventory i = SimpleFactions.plugin.getServer().createInventory(null, 27, "§7Confirm Action");
		ItemStack info = new ItemStack(Material.PAPER);
		ItemMeta infoMeta = info.getItemMeta();
		infoMeta.setDisplayName("§eRetreat from battle?");
		infoMeta.setLore(java.util.List.of(
				"§7Your side will lose the battle.",
				"§7Casualties already taken will apply."));
		info.setItemMeta(infoMeta);
		i.setItem(13, info);
		i.setItem(11, createButton("confirm", "warband_battle_retreat", battleId));
		i.setItem(15, createButton("cancel", "warband_battle_retreat", battleId));
		player.openInventory(i);
	}

	public void confirmWarDeclareView(Player player, WarDeclareRequest request) {
		pendingWarDeclares.put(player, request);
		confirming.put(player, request.getAttacker());
		Inventory i = SimpleFactions.plugin.getServer().createInventory(null, 27, "§7Confirm Action");
		i.setItem(13, declareWarView.creator.createConfirmSummaryItem(request));
		i.setItem(11, createButton("confirm", "war_declare", request.getDefender().getId()));
		i.setItem(15, createButton("cancel", "war_declare", request.getDefender().getId()));
		player.openInventory(i);
	}
	
	//Basic Items
	public ItemStack getFiller(Material mat) {
		ItemStack i = new ItemStack(mat, 1);
		ItemMeta m = i.getItemMeta();
		m.setDisplayName("§c");
		i.setItemMeta(m);
		return i;
	}
	
	public ItemStack createButton(String type, String key, String data) {
		ItemStack i = new ItemStack(Material.GREEN_CONCRETE);
		if(type.equalsIgnoreCase("cancel")) {
			i.setType(Material.RED_CONCRETE);
		}
		ItemMeta m = i.getItemMeta();
		if(type.equalsIgnoreCase("cancel")) {
			m.setDisplayName("§cCancel");
		} else {
			m.setDisplayName("§cConfirm");
		}
		NamespacedKey id = new NamespacedKey(SimpleFactions.plugin, key);
		m.getPersistentDataContainer().set(id, PersistentDataType.STRING, data);
		i.setItemMeta(m);
		return i;
	}
	
	public ItemStack createBackButton(SFGUI gui) {
		ItemStack i = new ItemStack(Material.BARRIER, 1);
		ItemMeta m = i.getItemMeta();
		m.setDisplayName("§cBack");
		NamespacedKey key = new NamespacedKey(SimpleFactions.plugin, "gui");
		m.getPersistentDataContainer().set(key, PersistentDataType.STRING, gui.toString());
		i.setItemMeta(m);
		return i;
	}
	
	@EventHandler
	public void dragInWarGui(InventoryDragEvent e) {
		Inventory inv = e.getView().getTopInventory();
		if (inv.getHolder() instanceof WarInventoryHolder
				|| inv.getHolder() instanceof CampaignInventoryHolder
				|| inv.getHolder() instanceof CampaignRaidLaunchHolder
				|| inv.getHolder() instanceof SFCombinedInventoryHolder
				|| inv.getHolder() instanceof DeclareWarHolder) {
			e.setCancelled(true);
		}
		if (inv.getHolder() instanceof SFInventoryHolder
				&& ((SFInventoryHolder) inv.getHolder()).getType() == SFGUI.PLAYER_LEDGER_VIEW) {
			e.setCancelled(true);
		}
	}

	@EventHandler
	public void clickButton(InventoryClickEvent e) {
		Player p = (Player) e.getWhoClicked();
		Inventory inv = e.getView().getTopInventory();
		if (inv.getHolder() instanceof SFInventoryHolder
				&& ((SFInventoryHolder) inv.getHolder()).getType() == SFGUI.PLAYER_LEDGER_VIEW) {
			e.setCancelled(true);
			return;
		}
		if (inv.getHolder() instanceof WarInventoryHolder
				|| inv.getHolder() instanceof CampaignInventoryHolder
				|| inv.getHolder() instanceof CampaignRaidLaunchHolder
				|| inv.getHolder() instanceof SFCombinedInventoryHolder
				|| inv.getHolder() instanceof DeclareWarHolder) {
			e.setCancelled(true);
		}
		if(e.getCurrentItem() == null) return;
		if(inv.getHolder() instanceof SFInventoryHolder) {
			e.setCancelled(true);
			SFInventoryHolder h = (SFInventoryHolder) inv.getHolder();
			Faction f = null;
			Guild g = null;
			Movement movement = null;
			
			// Determine faction, guild, and movement based on GUI type
			if (h.getType() == SFGUI.MOVEMENT_VIEW || h.getType() == SFGUI.CAUSES_VIEW || 
			    h.getType() == SFGUI.CAUSE_VIEW || h.getType() == SFGUI.TARGET_SELECT || 
			    h.getType() == SFGUI.MOVEMENT_DEMANDS || h.getType() == SFGUI.MOVEMENT_CRACKDOWN) {
				// These GUIs store movement ID in holder
				movement = FactionManager.getMovementById(h.getId());
				if (movement != null) {
					f = movement.getFaction();
				}
			} else if (h.getType() == SFGUI.MOVEMENT_LIST) {
				// MOVEMENT_LIST stores faction ID
				f = FactionManager.getByString(h.getId());
			} else {
				// Other GUIs use faction/guild ID
				f = FactionManager.getByString(h.getId());
				g = FactionManager.getGuildByString(h.getId());
			}
			
			if(e.getCurrentItem().getType().equals(Material.BARRIER)) {
				p.playSound(p, Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
				switch(h.getType()) {
					case ATTITUDE_VIEW:
						diplomacyView(null, p, f, true);
						break;
					case DIPLOMACY_LIST:
						factionView(p, f);
						break;
					case DIPLOMACY_VIEW:
						Faction viewer = FactionManager.getByMember(p.getName());
						if (viewer != null) {
							diplomacyListView(null, p, viewer, true);
						} else {
							factionView(p, f);
						}
						break;
					case FACTION_VIEW:
						factionList(p);
						break;
					case FACTION_GUILDS:
						factionView(p, f);
						break;
					case GUILD_VIEW:
						if (h.getFlag() && g != null && g.getFaction() != null) {
							factionView.factionGuildsView(p, g.getFaction(), null);
						} else {
							guildList(p);
						}
						break;
					case MILITARY_VIEW:
						factionView(p, f);
						break;
					case INSTALLATIONS_VIEW:
						factionView(p, f);
						break;
					case INSTALLATION_DETAIL_VIEW:
						installationsView(null, p, f, true);
						break;
					case RELATION_VIEW:
						diplomacyView(null, p, f, true);
						break;
					case TRADE_AGREEMENT_VIEW:
						diplomacyView(null, p, f, true);
						break;
					case TREATY_VIEW:
						diplomacyView(null, p, f, true);
						break;
					case TIER_VIEW:
						factionView(p, f);
						break;
					case TITLE_VIEW:
						factionView(p, f);
						break;
					case TITLE_TYPE_VIEW:
						titleView(null, p, f, true);
						break;
					case LAW_VIEW:
						factionView(p, f);
						break;
					case LAW_SELECT:
						lawView(p, f, null);
						break;
					case GOVERNMENT_VIEW:
						factionView(p, f);
						break;
					case PROPOSAL_VIEW:
						governmentView(p, f, null);
						break;
					case POLITICAL_PROPOSAL_VIEW:
						governmentView.proposalView(p, f, null);
						break;
					case WAR_PEACE_SELECT:
						if (h.getFlag()) {
							Movement causeMovement = f.getGovernment().getMovementByMember(p.getName());
							if (causeMovement != null && h.getPage() >= 0 && h.getPage() < causeMovement.getCauses().size()) {
								movementView.causeView(p, f, causeMovement, causeMovement.getCauses().get(h.getPage()), null);
							} else {
								governmentView(p, f, null);
							}
						} else {
							governmentView.politicalProposalView(p, f, null);
						}
						break;
					case LAW_PROPOSAL_VIEW:
						governmentView.proposalView(p, f, null);
						break;
					case LAW_PROPOSAL_SELECT:
						governmentView.lawProposalView(p, f, null);
						break;
					case TAX_PROPOSAL_VIEW:
						governmentView.proposalView(p, f, null);
						break;
					case SPECIFIC_TAX_PROPOSAL_VIEW:
						governmentView.taxProposalView(p, f, null);
						break;
					case PROPOSALS:
						governmentView(p, f, null);
						break;
					case COUNCIL_VIEW:
						governmentView(p, f, null);
						break;
					case COUNCIL_SELECT:
						governmentView.councilView(p, f, null);
						break;				
					case MOVEMENT_VIEW:
						movementListView(p, f, null);
						break;
					case MOVEMENT_LIST:
						governmentView(p, f, null);
						break;
					case CAUSES_VIEW:
						if (movement != null && f != null) movementView(p, f, movement, null);
						else if (f != null) governmentView(p, f, null);
						break;
					case CAUSE_VIEW:
						if (movement != null && f != null) causesView(p, f, movement, null);
						else if (f != null) governmentView(p, f, null);
						break;			
					case TARGET_SELECT:
						if (movement != null && f != null) {
							if(h.getPage() != -1) {
								Cause cause = movement.getCauses().get(h.getPage());
								if(cause != null) {
									causeView(p, f, movement, cause, inv);
								}
							} else {
								movementView(p, f, movement, null);
							}
						} else if (f != null) {
							governmentView(p, f, null);
						}
						break;			
					case LEDGER_VIEW:
						factionView(p, f);
						break;
					case TAX_VIEW_SPECIFIC:
						taxView(p, f);
						break;
					case TAX_VIEW:
						factionView(p, f);
						break;
					case UPGRADE_VIEW:
						guildView(p, g);
						break;
					case COMPANY_VIEW:
						guildView(p, g);
						break;
					case COMPANY_SLOTS_VIEW:
					case COMPANY_ROSTER_VIEW:
					case COMPANY_UPGRADE_VIEW:
					case CONTRACT_LIST_VIEW:
						companyView(p, g);
						break;
					case CONTRACT_DETAIL_VIEW:
						contractListView(p, g);
						break;
					case LOAN_MAIN_VIEW:
						guildView(p, g);
						break;
					case LOANS_GIVEN_VIEW:
						loanMainView(p, g);
						break;
					case LOANS_TAKEN_VIEW:
						loanMainView(p, g);
						break;
					case ISSUED_LOAN_DETAIL_VIEW:
						loansGivenView(p, g);
						break;
					case TAKEN_LOAN_DETAIL_VIEW:
						loansTakenView(p, g);
						break;
					case FAVOUR_REPRESS_SELECT:
						governmentView.favourRepressTypeView(p, f, h.getFlag(), null);
						break;
					case FAVOUR_REPRESS_TYPE:
						governmentView.favourRepressMainView(p, f, null);
						break;
					case FAVOUR_REPRESS_MAIN:
						governmentView(p, f, null);
						break;
					default:
						break;
				}
			}
			if(h.getType() == SFGUI.FACTION_LIST || h.getType() == SFGUI.FACTION_VIEW || h.getType() == SFGUI.FACTION_GUILDS) {
				factionView.click(e, inv, p);
			} else if(h.getType() == SFGUI.GUILD_LIST || h.getType() == SFGUI.GUILD_VIEW || h.getType() == SFGUI.UPGRADE_VIEW) {
				guildView.click(e, inv, p);
			} else if(h.getType() == SFGUI.COMPANY_VIEW
				|| h.getType() == SFGUI.COMPANY_SLOTS_VIEW
				|| h.getType() == SFGUI.COMPANY_ROSTER_VIEW
				|| h.getType() == SFGUI.COMPANY_UPGRADE_VIEW) {
				companyView.click(e, inv, p);
			} else if(h.getType() == SFGUI.CONTRACT_LIST_VIEW
				|| h.getType() == SFGUI.CONTRACT_DETAIL_VIEW) {
				contractView.click(e, inv, p);
			} else if(h.getType() == SFGUI.MERCENARY_MARKET_LIST) {
				mercenaryMarketView.click(e, inv, p);
			} else if(h.getType() == SFGUI.LOAN_MAIN_VIEW 
				|| h.getType() == SFGUI.LOANS_GIVEN_VIEW 
				|| h.getType() == SFGUI.LOANS_TAKEN_VIEW
				|| h.getType() == SFGUI.TAKEN_LOAN_DETAIL_VIEW
				|| h.getType() == SFGUI.ISSUED_LOAN_DETAIL_VIEW) {
				loanView.click(e, inv, p);
			} else if(h.getType() == SFGUI.LAW_VIEW || h.getType() == SFGUI.LAW_SELECT) {
				lawView.click(e, inv, p);
			} else if(h.getType() == SFGUI.MILITARY_VIEW) {
				militaryView.click(e, inv, p);
			} else if(h.getType() == SFGUI.INSTALLATIONS_VIEW
					|| h.getType() == SFGUI.INSTALLATION_DETAIL_VIEW) {
				installationView.click(e, inv, p);
			} else if(h.getType() == SFGUI.GOVERNMENT_VIEW 
				|| h.getType() == SFGUI.PROPOSAL_VIEW
				|| h.getType() == SFGUI.PROPOSALS
				|| h.getType() == SFGUI.LAW_PROPOSAL_VIEW
				|| h.getType() == SFGUI.LAW_PROPOSAL_SELECT
				|| h.getType() == SFGUI.TAX_PROPOSAL_VIEW
				|| h.getType() == SFGUI.SPECIFIC_TAX_PROPOSAL_VIEW
				|| h.getType() == SFGUI.POLITICAL_PROPOSAL_VIEW
				|| h.getType() == SFGUI.WAR_PEACE_SELECT
				|| h.getType() == SFGUI.COUNCIL_VIEW
				|| h.getType() == SFGUI.COUNCIL_SELECT
				|| h.getType() == SFGUI.FAVOUR_REPRESS_MAIN
				|| h.getType() == SFGUI.FAVOUR_REPRESS_TYPE
				|| h.getType() == SFGUI.FAVOUR_REPRESS_SELECT) {
				governmentView.click(e, inv, p);
			} else if(h.getType() == SFGUI.MOVEMENT_VIEW
				|| h.getType() == SFGUI.MOVEMENT_LIST
				|| h.getType() == SFGUI.CAUSES_VIEW
				|| h.getType() == SFGUI.CAUSE_VIEW
				|| h.getType() == SFGUI.MOVEMENT_DEMANDS
				|| h.getType() == SFGUI.MOVEMENT_CRACKDOWN
				|| h.getType() == SFGUI.TARGET_SELECT) {
				movementView.click(e, inv, p);
			} else if(h.getType() == SFGUI.DIPLOMACY_VIEW
					|| h.getType() == SFGUI.DIPLOMACY_LIST
					|| h.getType() == SFGUI.ATTITUDE_VIEW 
					|| h.getType() == SFGUI.RELATION_VIEW
					|| h.getType() == SFGUI.TRADE_AGREEMENT_VIEW
					|| h.getType() == SFGUI.TREATY_VIEW) {
				relationView.click(e, inv, p);
			} else if(h.getType() == SFGUI.ELECTION_VIEW || h.getType() == SFGUI.ELECTION_VOTING_VIEW) {
				electionView.click(e, inv, p);
			} else if(h.getType() == SFGUI.TIER_VIEW 
					|| h.getType() == SFGUI.TITLE_VIEW
					|| (inv.getHolder() instanceof SFInventoryHolder && ((SFInventoryHolder) inv.getHolder()).getType().equals(SFGUI.TITLE_TYPE_VIEW))) {
				tierTitleView.click(e, inv, p);
			} else if(h.getType() == SFGUI.WAR_LIST) {
				warView.click(e, inv, p);
			} else if(inv.getHolder() instanceof SFInventoryHolder && ((SFInventoryHolder) inv.getHolder()).getType().equals(SFGUI.TAX_VIEW)) {
				taxView.click(e, inv, p);
			} else if(inv.getHolder() instanceof SFInventoryHolder && ((SFInventoryHolder) inv.getHolder()).getType().equals(SFGUI.TAX_VIEW_SPECIFIC)) {
				taxView.click(e, inv, p);
			}
		} else if (inv.getHolder() instanceof WarInventoryHolder warHolder) {
			e.setCancelled(true);
			if (warHolder.getType() == SFGUI.WAR_VIEW) {
				warView.click(e, inv, p);
			}
		} else if (inv.getHolder() instanceof CampaignInventoryHolder campaignHolder) {
			e.setCancelled(true);
			if (campaignHolder.getType() == SFGUI.CAMPAIGN_VIEW) {
				campaignView.click(e, inv, p);
			} else if (campaignHolder.getType() == SFGUI.CAMPAIGN_INSTALLATION_PICK_VIEW) {
				campaignInstallationPickView.click(e, inv, p);
			}
		} else if (inv.getHolder() instanceof CampaignRaidLaunchHolder) {
			e.setCancelled(true);
			campaignRaidLaunchView.click(e, inv, p);
		} else if (inv.getHolder() instanceof SFCombinedInventoryHolder combinedHolder) {
			e.setCancelled(true);
			SFGUI combinedType = combinedHolder.getType();
			if (combinedType == SFGUI.PARTICIPANT_VIEW
					|| combinedType == SFGUI.MERCENARY_ENGAGEMENT_LIST) {
				warView.click(e, inv, p);
			}
		} else if (inv.getHolder() instanceof DeclareWarHolder) {
			declareWarView.click(e, inv, p);
		}
		if(e.getView().getTitle().equalsIgnoreCase("§7Confirm Action")) {
			e.setCancelled(true);
			if (e.getClickedInventory() == null
					|| e.getClickedInventory() != e.getView().getTopInventory()) {
				return;
			}
			if(!confirming.containsKey(p)) return;
			ItemStack item = e.getCurrentItem();
			ItemMeta m = item.getItemMeta();
			NamespacedKey key = new NamespacedKey(SimpleFactions.plugin, "queue_cancel");
			String data = m.getPersistentDataContainer().get(key, PersistentDataType.STRING);
			if (data != null) {
				var parsed = QueueCancelPayload.parse(data);
				if (parsed.isEmpty()) {
					confirming.remove(p);
					return;
				}
				QueueCancelPayload.Parsed cancel = parsed.get();
				if (item.getType().equals(Material.RED_CONCRETE)) {
					refreshAfterQueueCancel(p, cancel);
					confirming.remove(p);
					p.playSound(p, Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
					return;
				}
				if (executeQueueCancel(cancel)) {
					p.sendMessage("§aQueue item cancelled.");
					p.playSound(p, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
				} else {
					p.sendMessage("§cCould not cancel queue item.");
					p.playSound(p, Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 1f);
				}
				refreshAfterQueueCancel(p, cancel);
				confirming.remove(p);
				return;
			}
			key = new NamespacedKey(SimpleFactions.plugin, "regiment");
			data = m.getPersistentDataContainer().get(key, PersistentDataType.STRING);
			if(data != null) {
				Faction f = confirming.get(p);
				if(item.getType().equals(Material.RED_CONCRETE)) {
					militaryView(null, p, f, true);
					p.playSound(p, Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
					return;
				}
				Regiment r = f.getMilitary().getRegiment(data);
				r.sizeDecrease();
				p.sendMessage("§cDecrased size of "+r.getName());
				p.playSound(p, Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
				militaryView(null, p, f, true);
				return;
			}
			key = new NamespacedKey(SimpleFactions.plugin, "dissolve");
			data = m.getPersistentDataContainer().get(key, PersistentDataType.STRING);
			if(data != null) {
				Faction f = confirming.get(p);
				if(item.getType().equals(Material.RED_CONCRETE)) {
					factionView(p, f);
					p.playSound(p, Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
					return;
				}
				Boolean b = Boolean.parseBoolean(data);
				if(b) {
					if(!f.canDissolve()) return;
					Faction returnView = f.dissolve(f.getSubjects(), new ArrayList<>());
					factionView(p, returnView);
					p.playSound(p, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
				}
				return;
			}
			key = new NamespacedKey(SimpleFactions.plugin, "installation");
			data = m.getPersistentDataContainer().get(key, PersistentDataType.STRING);
			if(data != null) {
				Faction f = confirming.get(p);
				boolean fromCommand = installationConfirmFromCommand.getOrDefault(p, false);
				installationConfirmFromCommand.remove(p);
				if(item.getType().equals(Material.RED_CONCRETE)) {
					if(fromCommand) {
						installationsView(null, p, f, true);
					} else {
						installationDetailView(p, f, data);
					}
					p.playSound(p, Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
					return;
				}
				ConstructResult result = f.getInstallationHandler().deconstruct(data);
				p.sendMessage(result.getMessage());
				if(result.isSuccess()) {
					p.playSound(p, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
				} else {
					p.playSound(p, Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
				}
				installationsView(null, p, f, true);
				return;
			}
			key = new NamespacedKey(SimpleFactions.plugin, "campaign_push");
			data = m.getPersistentDataContainer().get(key, PersistentDataType.STRING);
			if (data != null) {
				boolean confirmed = item.getType().equals(Material.GREEN_CONCRETE);
				campaignView.handleConfirm(p, "campaign_push", data, confirmed);
				return;
			}
			key = new NamespacedKey(SimpleFactions.plugin, "campaign_hold");
			data = m.getPersistentDataContainer().get(key, PersistentDataType.STRING);
			if (data != null) {
				boolean confirmed = item.getType().equals(Material.GREEN_CONCRETE);
				campaignView.handleConfirm(p, "campaign_hold", data, confirmed);
				return;
			}
			key = new NamespacedKey(SimpleFactions.plugin, "campaign_attack");
			data = m.getPersistentDataContainer().get(key, PersistentDataType.STRING);
			if (data != null) {
				boolean confirmed = item.getType().equals(Material.GREEN_CONCRETE);
				campaignView.handleConfirm(p, "campaign_attack", data, confirmed);
				return;
			}
			key = new NamespacedKey(SimpleFactions.plugin, "campaign_loser_peace");
			data = m.getPersistentDataContainer().get(key, PersistentDataType.STRING);
			if (data != null) {
				boolean confirmed = item.getType().equals(Material.GREEN_CONCRETE);
				campaignView.handleConfirm(p, "campaign_loser_peace", data, confirmed);
				return;
			}
			key = new NamespacedKey(SimpleFactions.plugin, "warband_battle_retreat");
			data = m.getPersistentDataContainer().get(key, PersistentDataType.STRING);
			if (data != null) {
				boolean confirmed = item.getType().equals(Material.GREEN_CONCRETE);
				ConfirmHandler.handleConfirm(p, confirmed);
				return;
			}
			key = new NamespacedKey(SimpleFactions.plugin, "campaign_retreat");
			data = m.getPersistentDataContainer().get(key, PersistentDataType.STRING);
			if (data != null) {
				boolean confirmed = item.getType().equals(Material.GREEN_CONCRETE);
				campaignView.handleConfirm(p, "campaign_retreat", data, confirmed);
				return;
			}
			key = new NamespacedKey(SimpleFactions.plugin, "campaign_surrender");
			data = m.getPersistentDataContainer().get(key, PersistentDataType.STRING);
			if (data != null) {
				boolean confirmed = item.getType().equals(Material.GREEN_CONCRETE);
				campaignView.handleConfirm(p, "campaign_surrender", data, confirmed);
				return;
			}
			key = new NamespacedKey(SimpleFactions.plugin, "campaign_accept_peace");
			data = m.getPersistentDataContainer().get(key, PersistentDataType.STRING);
			if (data != null) {
				boolean confirmed = item.getType().equals(Material.GREEN_CONCRETE);
				campaignView.handleConfirm(p, "campaign_accept_peace", data, confirmed);
				return;
			}
			key = new NamespacedKey(SimpleFactions.plugin, "setcapital");
			data = m.getPersistentDataContainer().get(key, PersistentDataType.STRING);
			if (data != null) {
				if(!Cache.requireProvinces(p)) {
					p.closeInventory();
					return;
				}
				boolean confirmed = item.getType().equals(Material.GREEN_CONCRETE);
				CapitalMovePrompt.handleConfirm(p, confirmed);
				return;
			}
			key = new NamespacedKey(SimpleFactions.plugin, "end_movement");
			data = m.getPersistentDataContainer().get(key, PersistentDataType.STRING);
			if (data != null) {
				boolean confirmed = item.getType().equals(Material.GREEN_CONCRETE);
				movementView.handleEndConfirm(p, data, confirmed);
				return;
			}
			key = new NamespacedKey(SimpleFactions.plugin, "war_declare");
			data = m.getPersistentDataContainer().get(key, PersistentDataType.STRING);
			if (data != null) {
				WarDeclareRequest request = pendingWarDeclares.remove(p);
				confirming.remove(p);
				if (request == null) return;
				boolean confirmed = item.getType().equals(Material.GREEN_CONCRETE);
				declareWarView.handleConfirm(p, request, confirmed);
				return;
			}
		}
	}
}
