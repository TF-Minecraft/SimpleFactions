package me.Plugins.SimpleFactions.Managers.Inventory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;

import me.Plugins.SimpleFactions.enums.FactionModifiers;
import me.Plugins.SimpleFactions.enums.GuildModifier;
import me.Plugins.SimpleFactions.Cache;
import me.Plugins.SimpleFactions.SimpleFactions;
import me.Plugins.SimpleFactions.Guild.Branch.Branch;
import me.Plugins.SimpleFactions.Guild.Branch.BranchModifier;
import me.Plugins.SimpleFactions.Guild.income.Cashflow;
import me.Plugins.SimpleFactions.Guild.income.Ledger;
import me.Plugins.SimpleFactions.Guild.loans.Loan;
import me.Plugins.SimpleFactions.Guild.loans.LoanHandler;
import me.Plugins.SimpleFactions.Guild.upgrade.Upgrade;
import me.Plugins.SimpleFactions.Guild.upgrade.UpgradeExpansion;
import me.Plugins.SimpleFactions.Loaders.RankLoader;
import me.Plugins.SimpleFactions.Loaders.RelationLoader;
import me.Plugins.SimpleFactions.Managers.FactionManager;
import me.Plugins.SimpleFactions.Managers.RelationManager;
import me.Plugins.SimpleFactions.Guild.Guild;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.Objects.FactionModifier;
import me.Plugins.SimpleFactions.Objects.Modifier;
import me.Plugins.SimpleFactions.REST.RestServer;
import me.Plugins.SimpleFactions.War.resolution.PillageTradeHit;
import me.Plugins.SimpleFactions.Utils.FactionRanker;
import me.Plugins.SimpleFactions.Utils.Formatter;
import me.Plugins.SimpleFactions.Utils.HomeSettlementNames;
import me.Plugins.SimpleFactions.Guild.income.DividendBreakdown;
import me.Plugins.SimpleFactions.enums.MenuItemType;
import me.Plugins.SimpleFactions.enums.RankType;
import me.Plugins.SimpleFactions.keys.Keys;
import me.Plugins.TLibs.Enums.APIType;
import me.Plugins.TLibs.Objects.API.ItemAPI;
import me.Plugins.TLibs.Objects.API.SubAPI.StringFormatter;
import me.Plugins.TLibs.Utils.TimeFormatter;
import me.Plugins.TLibs.TLibs;

public class GuildCreator {

	FactionRanker r = new FactionRanker();

	public ItemStack createListItem(Player p, Guild guild) {
		ItemStack i = new ItemStack(guild.getBanner());
		ItemMeta meta = i.getItemMeta();
		meta.setDisplayName("§f"+guild.getName());
		List<String> lore = new ArrayList<String>();
		lore.add(StringFormatter.formatHex("#7a706a§lType: "+guild.getType().getName()));
		if(guild.hasCapital()) lore.add(StringFormatter.formatHex("#c45749§lSize: #d4c9ae"+guild.getSize()));
		lore.add(StringFormatter.formatHex("#b8ae61Part of: "+guild.getFaction().getName()));
		lore.add(StringFormatter.formatHex("#b8ae61Based in: #d4c9ae" + HomeSettlementNames.of(guild)));
		lore.add(" ");
		lore.add(StringFormatter.formatHex("#9c9775Leader: #c2bea7"+guild.getLeader()));
		lore.add(StringFormatter.formatHex("#b8ae61Members: #7fbd73"+guild.getMembers().size()));
		lore.add(" ");
		if(guild.hasCapital()) {
			lore.add(StringFormatter.formatHex("#41b541Trade Power: #a4bc5c"+guild.getTradeBreakdown().getTradePower()));
			double income = guild.getLedger().getNetIncome();
			lore.add(StringFormatter.formatHex("#74ba74Estimated Income: "+(income > 0 ? "#5cbc5c" : "#c45749")+income+"d/day"));
		}
		lore.add(StringFormatter.formatHex("#d1b43fWealth: #ccbb76"+guild.getWealth()+"d #7a706a("+r.getWealthRank(guild)+")"));
		meta.setLore(lore);
		NamespacedKey id = new NamespacedKey(SimpleFactions.plugin, "id");
		meta.getPersistentDataContainer().set(id, PersistentDataType.STRING, guild.getId());
		i.setItemMeta(meta);
		return i;
	}

	public ItemStack createHostFactionItem(Guild guild) {
		Faction faction = guild.getFaction();
		ItemStack i = new ItemStack(faction.getBanner());
		ItemMeta meta = i.getItemMeta();
		meta.setDisplayName(faction.getName());
		List<String> lore = new ArrayList<String>();
		lore.add(StringFormatter.formatHex("#b8ae61Host faction"));
		lore.add(StringFormatter.formatHex("#28ed70Click to open faction"));
		meta.setLore(lore);
		NamespacedKey id = new NamespacedKey(SimpleFactions.plugin, "id");
		meta.getPersistentDataContainer().set(id, PersistentDataType.STRING, faction.getId());
		i.setItemMeta(meta);
		return i;
	}

    @SuppressWarnings("deprecation")
	public ItemStack createMenuItem(Player p, Guild guild, MenuItemType t) {
		ItemStack i = new ItemStack(Material.DIRT, 1);
		if(t.equals(MenuItemType.BANNER)) {
			i = new ItemStack(guild.getBanner());
			ItemMeta m = i.getItemMeta();
			m.setDisplayName(StringFormatter.formatHex("#d4c9ae§lBanner of "+guild.getName()));
            List<String> lore = new ArrayList<>();
            if(guild.isBase()) lore.add(StringFormatter.formatHex(guild.getType().getName()+" #b8ae61Guild of "+guild.getFaction().getName()));
			else lore.add(StringFormatter.formatHex("#b8ae61Part of: "+guild.getFaction().getName()));
            m.setLore(lore);
			i.setItemMeta(m);
		} else if(t.equals(MenuItemType.BANNER_GET)) {
			i = new ItemStack(Material.CHEST, 1);
			ItemMeta m = i.getItemMeta();
			m.setDisplayName(StringFormatter.formatHex("#82d461Get Banner"));
			List<String> lore = new ArrayList<>();
			lore.add("§7Click to get a banner");
			m.setLore(lore);
			i.setItemMeta(m);
		} else if(t.equals(MenuItemType.BANNER_RANDOM)) {
			ItemAPI api = (ItemAPI) TLibs.getApiInstance(APIType.ITEM_API);
			i = api.getCreator().getItemsAdderItem("mcicons:icon_refresh");
			ItemMeta m = i.getItemMeta();
			m.setDisplayName(StringFormatter.formatHex("#9161d4Randomise Banner"));
			List<String> lore = new ArrayList<>();
			lore.add("§7Click to randomise the banner patterns");
			m.setLore(lore);
			i.setItemMeta(m);
		} else if(t.equals(MenuItemType.LEADER)) {
			i = new ItemStack(Material.PLAYER_HEAD, 1);
			SkullMeta m = (SkullMeta) i.getItemMeta();
			m.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
			m.setDisplayName(StringFormatter.formatHex("#9c9775§lLeader: #c2bea7"+guild.getLeader()));
			m.setOwningPlayer(Bukkit.getOfflinePlayer(guild.getLeader()));
			i.setItemMeta(m);
		} else if(t.equals(MenuItemType.WEALTH)) {
			i = new ItemStack(Material.GOLD_NUGGET, 1);
			ItemMeta m = i.getItemMeta();
			m.setDisplayName(StringFormatter.formatHex("#d1b43fWealth: #ccbb76"+guild.getWealth()+"d"));
			List<String> lore = new ArrayList<String>();
			for(Modifier mod : guild.getWealthModifiers()) {
				lore.add(StringFormatter.formatHex("#93c9a7+"+mod.getAmount()+"d from "+mod.getType()));
			}
			m.setLore(lore);
			i.setItemMeta(m);
		} else if(t.equals(MenuItemType.TRADE_BREAKDOWN)) {
			i = new ItemStack(Material.EMERALD, 1);
			ItemMeta m = i.getItemMeta();
			m.setDisplayName(StringFormatter.formatHex("#338651Trade Breakdown"));
			List<String> lore = new ArrayList<String>();
			lore.add(StringFormatter.formatHex("#d4c9aeIncome from trade: #7fbd73"+guild.getTradeBreakdown().getIncome()));
			lore.add(StringFormatter.formatHex("#d4c9aeUpkeep from trade: #cb5b4f"+guild.getTradeBreakdown().getUpkeep()));
			lore.add(StringFormatter.formatHex("#d4c9aeTariffs Paid: #b23c2f"+guild.getTradeBreakdown().getTariffs()));
			lore.add(StringFormatter.formatHex("#d4c9aeTotal Trade Power: #a4bc5c"+guild.getTradeBreakdown().getTradePower()));
			String pillageLine = PillageTradeHit.breakdownLine(guild);
			if (pillageLine != null) {
				lore.add(StringFormatter.formatHex(pillageLine));
			}
			lore.add("");
			lore.add(StringFormatter.formatHex("#73adbfIncome Contributors:"));
			int x = 0;
			for(Faction f : guild.getTradeBreakdown().getFactionsByIncomeDesc()) {
				if(x == 10) break;
				x++;
				double tariffs = guild.getTradeBreakdown().getTariffsByFaction(f);
				String tariffString = tariffs > 0 ? " §7(#b23c2f"+tariffs+" #575150in Tariffs§7)" : "";
				lore.add(StringFormatter.formatHex("§f - "+f.getName()+"#d4c9ae: #7fbd73"+guild.getTradeBreakdown().getIncomeByFaction(f)+"d/day"+tariffString));
			}
			lore.add("");
			lore.add(StringFormatter.formatHex("#c95644Top 5 Guilds:"));
			x = 0;
			List<Guild> top = (new FactionRanker()).getRankedGuildList(RankType.INCOME);
			Collections.reverse(top);
			for(Guild g : top) {
				x++;
				lore.add(StringFormatter.formatHex("§f - §e"+x+". "+g.getName()+" §7["+g.getSize()+"§7]#d4c9ae: #7fbd73"+SimpleFactions.getInstance().getProvinceManager().getIncome(g)+"d/day"));
				if(x > 4) break;
			}
			m.setLore(lore);
			i.setItemMeta(m);
		} else if(t.equals(MenuItemType.MEMBERS)) {
				i = new ItemStack(Material.PLAYER_HEAD, 1);
				ItemMeta m = i.getItemMeta();
				m.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
				m.setDisplayName(StringFormatter.formatHex("#b8ae61Members: #7fbd73"+guild.getMembers().size()+"/"+Cache.maxMembers));
				List<String> lore = new ArrayList<>();
				for(String s : guild.getMembers()) {
					lore.add(StringFormatter.formatHex("#d4c9ae"+s));
				}
				m.setLore(lore);
				i.setItemMeta(m);
		}
		if(IconGetter.hasIcon(t.toString())) {
			ItemStack icon = IconGetter.getIcon(t.toString());
			i.setType(icon.getType());
			ItemMeta m = i.getItemMeta();
			m.setCustomModelData(icon.getItemMeta().getCustomModelData());
			if (i.getType() == Material.PLAYER_HEAD) {
				m.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
			}
			i.setItemMeta(m);
		}
		return i;
	}

	public ItemStack createUpgradesItem(Player p, Guild guild) {
		ItemStack i = new ItemStack(Material.GOLD_INGOT);
		ItemMeta meta = i.getItemMeta();
		meta.setDisplayName(StringFormatter.formatHex("#d979c2Upgrades"));

		List<String> lore = new ArrayList<>();
		for(Upgrade u : guild.getUpgrades()) {
			lore.add(StringFormatter.formatHex(" #adc7be- "+u.getName()+" §7["+u.getLevel()+"]"));
		}
		if(guild.isLeader(p)) {
			lore.add("");
			lore.add(StringFormatter.formatHex("#28ed70Click to view"));
		}
		meta.setLore(lore);
		i.setItemMeta(meta);
		return i;
	}

	public ItemStack createBranchItem(Player p, Guild guild, Branch branch) {
		ItemStack i = branch.getIconItem();
		ItemMeta meta = i.getItemMeta();
		meta.setDisplayName(branch.getName());

		List<String> lore = new ArrayList<>();
		lore.add(StringFormatter.formatHex("#575150[#d6cf69LVL " + branch.getLevel() + "#575150]"));
		lore.add("");
		lore.addAll(branch.getDescription());
		lore.add("");
		if(!guild.hasCapital()) {
				lore.add("");
				lore.add(StringFormatter.formatHex("#ed1313No capital!"));
		} else {
			lore.add(StringFormatter.formatHex("#a6c793Effects:"));

			for (GuildModifier m : branch.getModifierKeys()) {
				BranchModifier mod = branch.getModifier(m);
				if (mod == null) continue;

				lore.add(StringFormatter.formatHex(
					"§f - " + m.getName()
					+ "#d6cf69:"
					+ (m.isPositive() ? " #4fd945" : " #cf493a")
					+ (mod.getCurrent(branch.getLevel())
					+ " #575150("
					+ (m.isPositive() ? "#4fd945" : "#cf493a")
					+ mod.getPerLevel()
					+ "#87807f/level#575150)")
				));
			}
		}

		meta.getPersistentDataContainer().set(Keys.BRANCH_ID, PersistentDataType.STRING, branch.getId());
		meta.setLore(lore);
		i.setItemMeta(meta);
		return i;
	}
	public ItemStack createBranchUpgradeItem(Player p, Guild guild, Branch branch) {
		ItemStack i = TLibs.getItemAPI().getCreator().getItemsAdderItem("mcicons:icon_up_gray");
		ItemMeta meta = i.getItemMeta();
		meta.setDisplayName(StringFormatter.formatHex("#50e846§lUpgrade " + branch.getName()));

		List<String> lore = new ArrayList<>();
		lore.add(StringFormatter.formatHex("#575150Current Level: #d6cf69" + branch.getLevel()));
		lore.add("");

		lore.add(StringFormatter.formatHex(
			"#f2e5c2Upgrade Cost#d6cf69: #ccbb76" + guild.getExpansionCost() + "d"
		));
		if(!guild.hasCapital()) {
			lore.add("");
			lore.add(StringFormatter.formatHex("#ed1313No capital!"));
		} else {
			double deltaIncome =
				SimpleFactions.getInstance()
				.getProvinceManager()
				.previewUpgradeIncomeExact(guild, branch);

			lore.add("");
			lore.add(StringFormatter.formatHex("#d4c9aeCurrent Net Trade Income: #7fbd73"+guild.getTradeBreakdown().getNetTradeIncome()));
			lore.add(StringFormatter.formatHex(
				"#f2e5c2Estimated Income Change#d6cf69: "
				+ (deltaIncome >= 0 ? "#4fd945+" : "#cf493a")
				+ String.format("%.2f", deltaIncome)
				+ "d/day"
			));
		}

		lore.add("");
		lore.add(StringFormatter.formatHex("#50e846§lClick to Upgrade"));

		meta.getPersistentDataContainer().set(Keys.BRANCH_ID, PersistentDataType.STRING, branch.getId());
		meta.getPersistentDataContainer().set(Keys.BOOLEAN_FLAG, PersistentDataType.BOOLEAN, true);
		meta.setLore(lore);
		i.setItemMeta(meta);
		return i;
	}
	public ItemStack createBranchDowngradeItem(Player p, Guild guild, Branch branch) {
		ItemStack i = TLibs.getItemAPI().getCreator().getItemsAdderItem("mcicons:icon_down_gray");
		ItemMeta meta = i.getItemMeta();
		meta.setDisplayName(StringFormatter.formatHex("#cf493a§lDowngrade " + branch.getName()));

		List<String> lore = new ArrayList<>();

		// Lowest level guard
		if (branch.getLevel() <= 0) {
			lore.add(StringFormatter.formatHex("#575150Current Level: #d6cf69Lowest Level"));
			lore.add("");
			lore.add(StringFormatter.formatHex("#7a706aThis branch cannot be downgraded further."));
		} else {
			lore.add(StringFormatter.formatHex("#575150Current Level: #d6cf69" + branch.getLevel()));
			lore.add("");

			lore.add(StringFormatter.formatHex("#c95644Downgrade Effects:"));
			lore.add(StringFormatter.formatHex("#7a706aStats will decrease by one level."));
			lore.add("");
			if(!guild.hasCapital()) {
				lore.add("");
				lore.add(StringFormatter.formatHex("#ed1313No capital!"));
			} else {
				double deltaIncome =
					SimpleFactions.getInstance()
					.getProvinceManager()
					.previewDowngradeIncomeExact(guild, branch);
				lore.add(StringFormatter.formatHex("#d4c9aeCurrent Net Trade Income: #7fbd73"+guild.getTradeBreakdown().getNetTradeIncome()));
				lore.add(StringFormatter.formatHex(
					"#f2e5c2Estimated Income Change#d6cf69: "
					+ (deltaIncome >= 0 ? "#4fd945+" : "#cf493a")
					+ String.format("%.2f", deltaIncome)
					+ "d/day"
				));
			}

			lore.add("");

			lore.add(StringFormatter.formatHex(
				"#f2e5c2Refund#d6cf69: #ccbb76" + guild.getRefund() + "d"
			));

			lore.add("");
			lore.add(StringFormatter.formatHex("#cf493a§lClick to Downgrade"));
		}

		meta.getPersistentDataContainer().set(Keys.BRANCH_ID, PersistentDataType.STRING, branch.getId());
		meta.getPersistentDataContainer().set(Keys.BOOLEAN_FLAG, PersistentDataType.BOOLEAN, false);
		meta.setLore(lore);
		i.setItemMeta(meta);
		return i;
	}

	public ItemStack createLedgerItem(Player p, Guild g) {
		Ledger ledger = g.getLedger();
		ItemStack i = new ItemStack(Material.WRITABLE_BOOK, 1);
		ItemMeta meta = i.getItemMeta();
		meta.setDisplayName(StringFormatter.formatHex("#d6cf69Ledger"));
		List<String> lore = new ArrayList<>();
		lore.add(StringFormatter.formatHex("#4c5250§oAdded to the bank at the"));
		lore.add(StringFormatter.formatHex("#4c5250§ostart of a new day"));
		lore.add("");
		lore.add(StringFormatter.formatHex("#4fd945Income"));
		lore.add(StringFormatter.formatHex("#2f3b2f────────────"));
		boolean hasIncome = false;
		for (Cashflow cf : Cashflow.values()) {
			double value = ledger.getIncome(cf);
			boolean showZeroPillageTrade = cf == Cashflow.TRADE
					&& value == 0
					&& PillageTradeHit.percent(g) != 0;
			if (value <= 0 && !showZeroPillageTrade) continue;

			hasIncome = true;
			String suffix = cf == Cashflow.TRADE ? PillageTradeHit.ledgerSuffix(g) : "";
			lore.add(StringFormatter.formatHex(
				"#cfc7a2• "
				+ cf.getDisplay()
				+ "#d6cf69: #7fbd73"
				+ String.format("+%.2f", value)
				+ "d"
				+ suffix
			));
		}
		if (!hasIncome) lore.add(StringFormatter.formatHex("#7a706aNo income sources."));
		lore.add("");
		lore.add(StringFormatter.formatHex("#cf493aExpenses"));
		lore.add(StringFormatter.formatHex("#3b2f2f────────────"));
		boolean hasExpenses = false;
		for (Cashflow cf : Cashflow.values()) {
			double value = ledger.getIncome(cf);
			if (value >= 0) continue;

			hasExpenses = true;
			lore.add(StringFormatter.formatHex(
				"#cfc7a2• "
				+ cf.getDisplay()
				+ "#d6cf69: #cf493a"
				+ String.format("%.2f", value)
				+ "d"
			));
		}
		if (!hasExpenses) {
			lore.add(StringFormatter.formatHex("#7a706aNo expenses."));
		}
		lore.add("");
		double net = ledger.getNetIncome();		String netColor = net >= 0 ? "#4fd945" : "#cf493a";

		lore.add(StringFormatter.formatHex("#f2e5c2Net Income"));
		lore.add(StringFormatter.formatHex(
			"#d6cf69Total#7a706a: "
			+ netColor
			+ String.format("%+.2f", net)
			+ "d"
		));
		lore.add("");
		lore.add(StringFormatter.formatHex("#7a706aUse /ledger for your personal ledger"));
		meta.setLore(lore);
		i.setItemMeta(meta);
		return i;
	}

	public ItemStack createDividendItem(Player p, Guild g) {
		ItemStack i = new ItemStack(Material.GOLD_INGOT, 1);
		ItemMeta meta = i.getItemMeta();
		meta.setDisplayName(StringFormatter.formatHex("#c49e5cDividends"));
		List<String> lore = new ArrayList<>();
		if (g.isBase()) {
			lore.add(StringFormatter.formatHex("#7a706aThe capital treasury cannot pay dividends."));
			meta.setLore(lore);
			i.setItemMeta(meta);
			return i;
		}
		DividendBreakdown breakdown = g.getLedger().getDividendBreakdown();
		lore.add(StringFormatter.formatHex("#d4c9aeShare of profits paid equally to members."));
		lore.add(StringFormatter.formatHex("#d4c9aeRate: #ccbb76" + String.format("%.2f", g.getDividendPercent()) + "%"));
		lore.add(StringFormatter.formatHex("#d4c9aeBase: #7fbd73" + String.format("%.2f", breakdown.base()) + "d"));
		lore.add(StringFormatter.formatHex("#d4c9aePool: #ccbb76" + String.format("%.2f", breakdown.pool()) + "d"));
		lore.add(StringFormatter.formatHex("#d4c9aeTax withheld: #cf493a" + String.format("%.2f", breakdown.tax()) + "d"));
		lore.add(StringFormatter.formatHex("#d4c9aePer member: #7fbd73" + String.format("%.2f", breakdown.perMember()) + "d"));
		lore.add(StringFormatter.formatHex("#d4c9aeEligible members: #d4c9ae" + breakdown.eligibleCount()));
		if (g.getBank() != null && breakdown.pool() > g.getBank().getWealth()) {
			lore.add("");
			lore.add(StringFormatter.formatHex("#c74d32Payout will be limited by the guild bank."));
		}
		lore.add("");
		if (g.isLeader(p)) {
			lore.add(StringFormatter.formatHex("#28ed70Click to set the percentage in chat"));
		} else {
			lore.add(StringFormatter.formatHex("#7a706aOnly the guild leader can change this."));
		}
		meta.setLore(lore);
		i.setItemMeta(meta);
		return i;
	}

	public ItemStack createLedgerCitizensItem(Guild g) {
		Ledger ledger = g.getLedger();

		ItemStack i = new ItemStack(Material.PLAYER_HEAD);
		ItemMeta m = i.getItemMeta();
		m.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
		m.setDisplayName(StringFormatter.formatHex("#94b572Citizens"));

		List<String> lore = new ArrayList<>();
		lore.add(StringFormatter.formatHex("#7a706aTop contributors"));
		lore.add("");

		int count = 0;
		for (var entry : ledger.getCitizenTaxEntriesDescending()) {
			if (count++ >= 5) break;
			lore.add(StringFormatter.formatHex(
				"#d4c9ae" + entry.getKey()
				+ "#7a706a: #7fbd73+"
				+ String.format("%.2f", entry.getValue()) + "d"
			));
		}

		if (count == 0)
			lore.add(StringFormatter.formatHex("#7a706aNo citizen taxes."));

		m.setLore(lore);
		i.setItemMeta(m);
		return i;
	}

	public ItemStack createLedgerGuildsItem(Guild g) {
		ItemStack i = new ItemStack(Material.BARREL);
		ItemMeta m = i.getItemMeta();
		m.setDisplayName(StringFormatter.formatHex("#b89448Guild Taxes"));

		List<String> lore = new ArrayList<>();
		lore.add(StringFormatter.formatHex("#7a706aPaying guilds"));
		lore.add("");

		int count = 0;
		for (Guild sub : g.getFaction().getGuildHandler().getGuilds()) {
			if (sub.isBase()) continue;

			double paid = Math.abs(sub.getLedger().getIncome(Cashflow.GUILD_PAYMENTS));
			if (paid <= 0) continue;

			lore.add(StringFormatter.formatHex(
				"#d4c9ae" + sub.getName()
				+ "#7a706a: #7fbd73+"
				+ String.format("%.2f", paid) + "d"
			));

			if (++count >= 5) break;
		}

		if (count == 0)
			lore.add(StringFormatter.formatHex("#7a706aNo guild taxes."));

		m.setLore(lore);
		i.setItemMeta(m);
		return i;
	}

	public ItemStack createLedgerVassalsItem(Guild g) {
		ItemStack i = new ItemStack(Material.IRON_INGOT);
		ItemMeta m = i.getItemMeta();
		m.setDisplayName(StringFormatter.formatHex("#7299b5Vassals"));

		List<String> lore = new ArrayList<>();
		lore.add(StringFormatter.formatHex("#7a706aSubject contributions"));
		lore.add("");

		int count = 0;
		for (Faction v : RelationManager.getSubjects(g.getFaction())) {
			Guild vg = v.getOrCreateMainGuild();
			double paid = Math.abs(vg.getLedger().getIncome(Cashflow.OVERLORD_TAX));
			if (paid <= 0) continue;

			lore.add(StringFormatter.formatHex(
				"#d4c9ae" + v.getName()
				+ "#7a706a: #7fbd73+"
				+ String.format("%.2f", paid) + "d"
			));

			if (++count >= 5) break;
		}

		if (count == 0)
			lore.add(StringFormatter.formatHex("#7a706aNo vassal income."));

		m.setLore(lore);
		i.setItemMeta(m);
		return i;
	}

	public ItemStack createLedgerTributesItem(Guild g) {
		Faction receiver = g.getFaction();

		ItemStack i = new ItemStack(Material.GOLD_INGOT);
		ItemMeta m = i.getItemMeta();
		m.setDisplayName(StringFormatter.formatHex("#ab8568Tributes"));

		List<String> lore = new ArrayList<>();
		lore.add(StringFormatter.formatHex("#7a706aTop tribute payers"));
		lore.add("");

		// payerFaction -> amount
		HashMap<Faction, Double> received = new HashMap<>();

		for (Faction payer : FactionManager.getCopy()) { // or FactionManager.factions if you prefer
			if (payer == null) continue;
			if (payer.getId().equalsIgnoreCase(receiver.getId())) continue;

			Guild payerGuild = payer.getOrCreateMainGuild();
			if (payerGuild == null) continue;

			double base = payerGuild.getLedger().getGrossTaxableIncome();
			if (base <= 0) continue;

			double totalFromPayer = 0.0;

			for (FactionModifier mod : payer.getModifiers()) {
				if (mod.getFrom() == null) continue;
				if (!mod.getType().equals(FactionModifiers.TRIBUTE)) continue;
				if (!mod.getFrom().getId().equalsIgnoreCase(receiver.getId())) continue;

				totalFromPayer += base * (mod.getAmount() / 100.0);
			}

			if (totalFromPayer > 0) {
				received.put(payer, totalFromPayer);
			}
		}

		// sort by value desc
		List<Map.Entry<Faction, Double>> top = new ArrayList<>(received.entrySet());
		top.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));

		int shown = 0;
		for (var e : top) {
			lore.add(StringFormatter.formatHex(
				"#d4c9ae" + e.getKey().getName()
				+ "#7a706a: #7fbd73+"
				+ String.format("%.2f", e.getValue()) + "d"
			));
			if (++shown >= 5) break;
		}

		if (shown == 0) {
			lore.add(StringFormatter.formatHex("#7a706aNo tributes received."));
		}

		m.setLore(lore);
		i.setItemMeta(m);
		return i;
	}

	public ItemStack createLedgerTariffsItem(Guild g) {
		ItemStack i = new ItemStack(Material.EMERALD);
		ItemMeta m = i.getItemMeta();
		m.setDisplayName(StringFormatter.formatHex("#5cc46aTariffs"));

		List<String> lore = new ArrayList<>();
		lore.add(StringFormatter.formatHex("#7a706aTop tariff payers"));
		lore.add("");

		int count = 0;
		for (Faction f : g.getTradeBreakdown().getFactionsByIncomeDesc()) {
			double paid = g.getTradeBreakdown().getTariffsByFaction(f);
			if (paid <= 0) continue;

			lore.add(StringFormatter.formatHex(
				"#d4c9ae" + f.getName()
				+ "#7a706a: #7fbd73+"
				+ String.format("%.2f", paid) + "d"
			));

			if (++count >= 5) break;
		}

		if (count == 0)
			lore.add(StringFormatter.formatHex("#7a706aNo tariff income."));

		m.setLore(lore);
		i.setItemMeta(m);
		return i;
	}

	public ItemStack createUpgradeItem(Player p, Guild guild, Upgrade upgrade) {
		ItemStack i = upgrade.getIconItem();
		ItemMeta meta = i.getItemMeta();
		meta.setDisplayName(upgrade.getName());

		List<String> lore = new ArrayList<>();
		lore.add(StringFormatter.formatHex("#575150[#d6cf69LVL " + upgrade.getLevel() + "#575150]"));
		lore.add("");
		lore.addAll(upgrade.getDescription());
		lore.add("");
		lore.add(StringFormatter.formatHex("#a6c793Effects:"));

		for (GuildModifier m : upgrade.getModifierKeys()) {
			BranchModifier mod = upgrade.getModifier(m);
			if (mod == null) continue;

			lore.add(StringFormatter.formatHex(
				"§f - " + m.getName()
				+ "#d6cf69:"
				+ (m.isPositive() ? " #4fd945" : " #cf493a")
				+ (mod.getCurrent(upgrade.getLevel())
				+ " #575150("
				+ (m.isPositive() ? "#4fd945" : "#cf493a")
				+ mod.getPerLevel()
				+ "#87807f/level#575150)")
			));
		}

		lore.add("");
		double totalUpkeep = upgrade.getUpkeep() * upgrade.getLevel();
		lore.add(StringFormatter.formatHex("#d4c9aeUpkeep: #cb5b4f" + String.format("%.2f", totalUpkeep) + "d/day"));

		meta.getPersistentDataContainer().set(Keys.STRING_KEY, PersistentDataType.STRING, upgrade.getId());
		meta.setLore(lore);
		i.setItemMeta(meta);
		return i;
	}

	public ItemStack createUpgradeUpgradeItem(Player p, Guild guild, Upgrade upgrade) {
		ItemStack i = TLibs.getItemAPI().getCreator().getItemsAdderItem("mcicons:icon_up_gray");
		ItemMeta meta = i.getItemMeta();
		meta.setDisplayName(StringFormatter.formatHex("#50e846Upgrade " + upgrade.getName()));

		List<String> lore = new ArrayList<>();
		lore.add(StringFormatter.formatHex("#575150Current Level: #d6cf69" + upgrade.getLevel()));
		lore.add("");
		
		lore.add("§7Time: §e" + TimeFormatter.formatTime(upgrade.getExpansionTime()));
		lore.add("");
		
		double upkeepIncrease = upgrade.getUpkeep();
		lore.add(StringFormatter.formatHex("#d4c9aeUpkeep Change: #cf493a+" + String.format("%.2f", upkeepIncrease) + "d/day"));

		meta.getPersistentDataContainer().set(Keys.STRING_KEY, PersistentDataType.STRING, upgrade.getId());
		meta.getPersistentDataContainer().set(Keys.BOOLEAN_FLAG, PersistentDataType.BOOLEAN, true);
		meta.setLore(lore);
		i.setItemMeta(meta);
		return i;
	}

	public ItemStack createUpgradeDowngradeItem(Player p, Guild guild, Upgrade upgrade) {
		ItemStack i = TLibs.getItemAPI().getCreator().getItemsAdderItem("mcicons:icon_down_gray");
		ItemMeta meta = i.getItemMeta();
		meta.setDisplayName(StringFormatter.formatHex("#cf493aDowngrade " + upgrade.getName()));

		List<String> lore = new ArrayList<>();
		lore.add(StringFormatter.formatHex("#575150Current Level: #d6cf69" + upgrade.getLevel()));
		lore.add("");
		
		if (upgrade.getLevel() <= 0) {
			lore.add(StringFormatter.formatHex("#7a706aThis upgrade cannot be downgraded further."));
		} else {
			double upkeepDecrease = upgrade.getUpkeep();
			lore.add(StringFormatter.formatHex("#d4c9aeUpkeep Change: #4fd945-" + String.format("%.2f", upkeepDecrease) + "d/day"));
		}

		meta.getPersistentDataContainer().set(Keys.STRING_KEY, PersistentDataType.STRING, upgrade.getId());
		meta.getPersistentDataContainer().set(Keys.BOOLEAN_FLAG, PersistentDataType.BOOLEAN, false);
		meta.setLore(lore);
		i.setItemMeta(meta);
		return i;
	}

	public ItemStack createUpgradeQueueItem(UpgradeExpansion expansion, int index, Guild guild) {
		ItemStack i = expansion.getUpgrade().getIconItem().clone();
		ItemMeta meta = i.getItemMeta();
		meta.setDisplayName(StringFormatter.formatHex("#d979c2Upgrading " + expansion.getUpgrade().getName()));
		
		List<String> lore = new ArrayList<>();
		if(index == 0) {
			lore.add("§7Time Left: §e" + TimeFormatter.formatTime(expansion.getTimeLeft()));
		} else {
			lore.add(StringFormatter.formatHex("#857e59Queued..."));
		}
		lore.add("§cClick to cancel");
		
		meta.setLore(lore);
		meta.getPersistentDataContainer().set(
				Keys.QUEUE_CANCEL,
				PersistentDataType.STRING,
				QueueCancelPayload.guildUpgrade(guild.getId(), index));
		i.setItemMeta(meta);
		return i;
	}

	public ItemStack createRelocateItem(Player p, Faction target, Guild guild) {
		boolean factionChange = !guild.getFaction().getId().equalsIgnoreCase(target.getId());
		int province = RestServer.getProvince(p);
		boolean newProvince = !target.hasProvince(province);
		ItemStack i = new ItemStack(Material.FILLED_MAP);
		ItemMeta meta = i.getItemMeta();
		meta.setDisplayName(StringFormatter.formatHex(factionChange ? 
			"#d4c9aeRelocate Guild to "+target.getName() : 
			"#d4c9aeRelocate Guild within "+guild.getFaction().getName()));
		List<String> lore = new ArrayList<>();
		if(factionChange) {
			lore.add(StringFormatter.formatHex("#d4c9aeRelocate your guild to "+target.getName()));
			lore.add(StringFormatter.formatHex("#e15757This changes the faction you are part of!"));
		} else {
			if(newProvince) {
				lore.add(StringFormatter.formatHex("#d4c9aeRelocate your guild to a new province outside "+target.getName()));
				lore.add(StringFormatter.formatHex("#d4c9aeborders, thus expanding the realm."));
			} else {
				lore.add(StringFormatter.formatHex("#d4c9aeRelocate your guild within "+target.getName()));
			}
		}
		lore.add("");
		double cost = guild.getRelocationCost(province);
		lore.add(StringFormatter.formatHex("#d4c9aeCost: #ccbb76"+cost+"d"));
		lore.add("");
		lore.add(StringFormatter.formatHex("#50e846Click to Relocate"));
		meta.setLore(lore);
		i.setItemMeta(meta);
		return i;
	}

	public ItemStack createElevationItem(Player p, Guild guild) {
		ItemStack i = IconGetter.getIconOrDefault("guild_elevate", Material.BLACK_DYE);
		ItemMeta meta = i.getItemMeta();
		meta.setDisplayName(StringFormatter.formatHex("#6ade9cElevate Guild to Faction Status"));
		List<String> lore = new ArrayList<>();
		lore.add(StringFormatter.formatHex("#d4c9aeThis will make the guild an "+RelationLoader.getElevationTarget().getName()));
		lore.add(StringFormatter.formatHex("#d4c9aeof our faction, giving them their own laws and government."));
		lore.add(StringFormatter.formatHex("#d4c9aethey also get the ability to have guilds and vassals of their own"));
		lore.add(StringFormatter.formatHex("#e15757The guild capital province will be transferred to the new faction!"));
		lore.add("");
		double cost = guild.getElevationCost();
		lore.add(StringFormatter.formatHex("#d4c9aeCost: §e"+Formatter.formatDouble(cost)+" Administrative Power"));
		lore.add("");
		if(guild.canBeElevated(null)) {
			lore.add(StringFormatter.formatHex("#50e846Click to Elevate"));
		} else {
			lore.add(StringFormatter.formatHex("#e15757Unavailable"));
		}
		meta.setLore(lore);
		i.setItemMeta(meta);
		return i;
	}

	public ItemStack createEvictionItem(Player p, Guild guild) {
		ItemStack i = TLibs.getItemAPI().getCreator().getItemsAdderItem("mcicons:icon_cancel");
		ItemMeta meta = i.getItemMeta();
		meta.setDisplayName(StringFormatter.formatHex("#deb46aEvict guild from faction"));
		List<String> lore = new ArrayList<>();
		lore.add(StringFormatter.formatHex("#d4c9aeThis will remove the guild from the faction."));
		lore.add(StringFormatter.formatHex("#d4c9aeThe guild will no longer be part of the faction."));
		lore.add(StringFormatter.formatHex("#d4c9aeThey will become their own landless faction."));
		lore.add("");
		double cost = guild.getEvictionCost();
		lore.add(StringFormatter.formatHex("#d4c9aeCost: §e"+Formatter.formatDouble(cost)+" Administrative Power"));
		lore.add(StringFormatter.formatHex("#e15757Will reduce stability by §4"+Formatter.formatDouble(guild.getStabilityEffect())+"%! §7(disappears over time)"));
		lore.add("");
		if(guild.canBeEvicted(null)) {
			lore.add(StringFormatter.formatHex("#50e846Click to Evict"));
		} else {
			lore.add(StringFormatter.formatHex("#e15757Unavailable"));
		}
		meta.setLore(lore);
		i.setItemMeta(meta);
		return i;
	}

	public ItemStack createLoansItem(Player p, Guild guild) {
		ItemStack i = IconGetter.getIconOrDefault("guild_loans", Material.BLACK_DYE);
		ItemMeta meta = i.getItemMeta();
		meta.setDisplayName(StringFormatter.formatHex("#e8c65fLoans"));
		List<String> lore = new ArrayList<>();
		LoanHandler handler = guild.getLoanHandler();
		lore.add(StringFormatter.formatHex("#d4c9aeLoans Given: #a19a87"+handler.getLoansGiven().size()));
		lore.add(StringFormatter.formatHex("#d4c9aeLoans Taken: #a19a87"+handler.getLoansTaken().size()));
		lore.add("");
		lore.add(StringFormatter.formatHex("#d4c9aeTotal Lent: #a8db8a"+Formatter.formatDouble(handler.getTotalLent())+"d"));
		lore.add(StringFormatter.formatHex("#d4c9aeTotal Owed: #de7657"+Formatter.formatDouble(handler.getTotalOwed())+"d"));
		lore.add(StringFormatter.formatHex("#d4c9aeDaily Interest: #de7657"+Formatter.formatDouble(handler.getDailyInterestChange())+"d"));
		lore.add("");
		lore.add(StringFormatter.formatHex("#c2be99Credit Score: "+handler.getCreditScoreString()));
		lore.add(StringFormatter.formatHex("#5d5959Lenders may give higher interest rates if your"));
		lore.add(StringFormatter.formatHex("#5d5959score is low, or lower rates if it's high"));
		lore.add("");
		lore.add(StringFormatter.formatHex("#50e846Click to View Details"));
		meta.setLore(lore);
		i.setItemMeta(meta);
		return i;
	}
}
