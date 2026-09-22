package me.Plugins.SimpleFactions.Objects;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

import org.bukkit.Bukkit;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.block.banner.Pattern;
import org.bukkit.block.banner.PatternType;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BannerMeta;

import me.Plugins.SimpleFactions.Army.Military;
import me.Plugins.SimpleFactions.Army.Regiment;
import me.Plugins.SimpleFactions.Cache;
import me.Plugins.SimpleFactions.Diplomacy.DiplomacyHandler;
import me.Plugins.SimpleFactions.Diplomacy.Relation;
import me.Plugins.SimpleFactions.Guild.Guild;
import me.Plugins.SimpleFactions.Loaders.GuildLoader;
import me.Plugins.SimpleFactions.Loaders.RankLoader;
import me.Plugins.SimpleFactions.Loaders.TierLoader;
import me.Plugins.SimpleFactions.Loaders.TitleLoader;
import me.Plugins.SimpleFactions.Managers.FactionManager;
import me.Plugins.SimpleFactions.Managers.ProvinceManager;
import me.Plugins.SimpleFactions.Managers.RelationManager;
import me.Plugins.SimpleFactions.Managers.TitleManager;
import me.Plugins.SimpleFactions.Map.Provinces.Province;
import me.Plugins.SimpleFactions.Objects.Handler.GuildHandler;
import me.Plugins.SimpleFactions.Objects.Handler.LawHandler;
import me.Plugins.SimpleFactions.Objects.Handler.ProvinceHandler;
import me.Plugins.SimpleFactions.Objects.Handler.TaxHandler;
import me.Plugins.SimpleFactions.REST.RestServer;
import me.Plugins.SimpleFactions.SimpleFactions;
import me.Plugins.SimpleFactions.Tiers.Tier;
import me.Plugins.SimpleFactions.Tiers.Title;
import me.Plugins.SimpleFactions.War.resolution.CouncilPeaceService;
import me.Plugins.SimpleFactions.War.resolution.WarReparationsObligation;
import me.Plugins.SimpleFactions.Utils.BracketToTaxTarget;
import me.Plugins.SimpleFactions.Utils.Formatter;
import me.Plugins.SimpleFactions.Utils.RandomRGB;
import me.Plugins.SimpleFactions.enums.Brackets;
import me.Plugins.SimpleFactions.enums.FactionModifiers;
import me.Plugins.SimpleFactions.enums.GuildModifier;
import me.Plugins.SimpleFactions.enums.Member;
import me.Plugins.SimpleFactions.enums.Region;
import me.Plugins.SimpleFactions.enums.Rules;
import me.Plugins.SimpleFactions.enums.Scope;
import me.Plugins.SimpleFactions.government.Government;
import me.Plugins.SimpleFactions.government.election.Candidate;
import me.Plugins.SimpleFactions.government.movement.Action;
import me.Plugins.SimpleFactions.government.movement.CoupService;
import me.Plugins.SimpleFactions.government.movement.PoliticalAction;
import me.Plugins.SimpleFactions.government.movement.cause.Cause;
import me.Plugins.SimpleFactions.government.proposal.Proposal;
import me.Plugins.SimpleFactions.government.proposal.TaxTarget;
import me.Plugins.SimpleFactions.laws.Law;
import me.Plugins.SimpleFactions.laws.LawEffect;
import me.Plugins.SimpleFactions.laws.LawGroup;
import me.Plugins.SimpleFactions.installation.InstallationTransferService;
import me.Plugins.SimpleFactions.installation.handler.InstallationHandler;
import me.Plugins.SimpleFactions.settlement.handler.SettlementHandler;
import me.Plugins.TLibs.Objects.API.SubAPI.StringFormatter;

public class Faction {
	private Formatter format = new Formatter();
	private String id;
	private String name;
	private PrestigeRank rank;
	private String governmentType;
	private String culture;
	private String religion;
	private String rgb;
	private ItemStack banner;
	private List<String> bannerPatterns = new ArrayList<>();
	private List<String> invited = new ArrayList<>();
	private Double wealth;
	private Double prestige;
	private String rulerTitle;
	private String leader;
	private Integer extraNodeCapacity;
	private List<Modifier> prestigeModifiers = new ArrayList<>();
	// Epoch seconds. Faction ids come from the name, so a recycled name reuses the id;
	// the chronicle export pairs id with this to tell reincarnations apart.
	private long foundedAt;
	
	
	private TaxHandler taxHandler;

	private Tier tier;
	
	//Diplomacy
	private final DiplomacyHandler diplomacyHandler;
	
	//Military
	private Military military;
	
	//Titles
	private List<Title> titles = new ArrayList<>();

	//Guilds
	private GuildHandler guildHandler;

	//Government
	private final Government government;

	//Laws
	private final LawHandler lawHandler;

	//Realm
	private final ProvinceHandler provinceHandler;

	//Settlements
	private final SettlementHandler settlementHandler;

	//Installations
	private final InstallationHandler installationHandler;

	private final List<WarReparationsObligation> warReparationsObligations = new ArrayList<>();
	
	public Faction(String id, String leader) {
		this.id = Formatter.formatId(id);
		this.name = StringFormatter.formatHex(Formatter.formatName(id));
		this.diplomacyHandler = new DiplomacyHandler(this);
		this.leader = leader;
		this.rulerTitle = "Leader";
		this.bannerPatterns = RestServer.fetchBannerList();
		this.rank = RankLoader.getLowest();
		this.foundedAt = System.currentTimeMillis()/1000L;
		this.governmentType = "Community";
		this.culture = "Multicultural";
		this.religion = "Religious Diversity";
		this.wealth = 0.0;
		this.prestige = 0.0;
		this.extraNodeCapacity = 0;
		this.rgb = RandomRGB.random();
		this.lawHandler = new LawHandler(this);
		this.provinceHandler = new ProvinceHandler(this);
		while(!RandomRGB.isFree(rgb)) {
			this.rgb = RandomRGB.random();
		}
		this.military = new Military(this);
		this.government = new Government(this);
		this.settlementHandler = new SettlementHandler(this);
		this.installationHandler = new InstallationHandler(this);
		this.taxHandler = new TaxHandler(this, 5, 5, 5, 5, 5);
		lawHandler.apply();
		this.guildHandler = new GuildHandler(this);
		guildHandler.addGuild(new Guild(this));
		createBanner(bannerPatterns);
		updatePrestige();
		updateTier();
	}
	public Faction(Guild guild) {
		this.id = guild.getId();
		this.name = guild.getName();
		this.diplomacyHandler = new DiplomacyHandler(this);
		this.leader = guild.getLeader();
		this.rulerTitle = "Leader";
		this.bannerPatterns = guild.getBannerPatterns();
		this.rank = RankLoader.getLowest();
		this.foundedAt = System.currentTimeMillis()/1000L;
		this.governmentType = "Community";
		this.culture = guild.getFaction().getCulture();
		this.religion = guild.getFaction().getReligion();
		this.wealth = 0.0;
		this.prestige = 0.0;
		this.extraNodeCapacity = 0;
		this.rgb = guild.getRGB();
		this.lawHandler = new LawHandler(this);
		this.provinceHandler = new ProvinceHandler(this);
		while(!RandomRGB.isFree(rgb)) {
			this.rgb = RandomRGB.random();
		}
		this.military = new Military(this);
		this.government = new Government(this);
		this.settlementHandler = new SettlementHandler(this);
		this.installationHandler = new InstallationHandler(this);
		this.taxHandler = new TaxHandler(this, 5, 5, 5, 5, 5);
		this.guildHandler = new GuildHandler(this);
		guildHandler.addGuild(guild);
		guild.setHost(this);
		guild.convert(GuildLoader.getBaseType());
		createBanner(bannerPatterns);
		updatePrestige();
		updateTier();
	}
	public Faction(
		String id, 
		String rgb, 
		List<Integer> provinces, 
		List<Title> titles, 
		String leader, 
		String name, 
		String rulerTitle, 
		List<String> patterns, 
		String governmentType, 
		String culture, 
		String religion, 
		int exCap, 
		List<Modifier> prestigeModifiers, 
		double citizenTax, 
		double guildTax, 
		double vassalTax, 
		double dividendTax, 
		double tariffs, 
		HashMap<String, HashMap<String, Double>> specificTaxes, 
		int capital, 
		List<String> laws,
		me.Plugins.SimpleFactions.Database.GovernmentData governmentData
	) {
		this.id = id;
		this.name = name;
		this.leader = leader;
		this.diplomacyHandler = new DiplomacyHandler(this);
		this.rulerTitle = rulerTitle;
		this.bannerPatterns = patterns;
		this.rank = RankLoader.getLowest();
		this.governmentType = governmentType;
		this.culture = culture;
		this.religion = religion;
		this.wealth = 0.0;
		this.prestige = 0.0;
		this.extraNodeCapacity = exCap;
		this.prestigeModifiers = prestigeModifiers;
		this.rgb = rgb;
		this.provinceHandler = new ProvinceHandler(this, capital, provinces);
		this.titles = titles;
		this.military = new Military(this);
		this.guildHandler = new GuildHandler(this);
		this.taxHandler = new TaxHandler(this, citizenTax, guildTax, vassalTax, dividendTax, tariffs);
		this.lawHandler = new LawHandler(this, laws);
		if (specificTaxes != null) {
			for (Map.Entry<String, HashMap<String, Double>> entry : specificTaxes.entrySet()) {
				try {
					me.Plugins.SimpleFactions.government.proposal.TaxTarget target = me.Plugins.SimpleFactions.government.proposal.TaxTarget.valueOf(entry.getKey());
					if (entry.getValue() != null) {
						for (Map.Entry<String, Double> taxEntry : entry.getValue().entrySet()) {
							taxHandler.setSpecificTax(target, taxEntry.getKey(), taxEntry.getValue());
						}
					}
				} catch (IllegalArgumentException e) {
					// Ignore invalid tax targets
				}
			}
		}
		this.government = governmentData != null ? new Government(this, governmentData) : new Government(this);
		this.settlementHandler = new SettlementHandler(this);
		this.installationHandler = new InstallationHandler(this);
		lawHandler.apply();
		createBanner(bannerPatterns);
		updateTier();
	}

	private void createBanner(List<String> patterns) {
		ItemStack item = new ItemStack(
			Material.valueOf(patterns.get(0).split("\\.")[0].toUpperCase() + "_BANNER"),
			1
		);

		BannerMeta meta = (BannerMeta) item.getItemMeta();
		meta.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
		for (int i = 1; i < patterns.size(); i++) {
			String p = patterns.get(i);
			String[] split = p.split("\\.");

			if (split.length != 2) continue;

			String colourName = split[0].toUpperCase();
			String patternName = split[1].toLowerCase();

			DyeColor dye;
			try {
				dye = DyeColor.valueOf(colourName);
			} catch (IllegalArgumentException e) {
				Bukkit.getLogger().warning("Invalid dye color: " + colourName);
				continue;
			}

			PatternType patternType = null;

			// 1️⃣ Try vanilla (minecraft namespace)
			NamespacedKey vanillaKey = NamespacedKey.minecraft(patternName);
			patternType = Registry.BANNER_PATTERN.get(vanillaKey);

			// 2️⃣ Try custom namespace (tfmc)
			if (patternType == null) {
				NamespacedKey customKey = new NamespacedKey("tfmc", patternName);
				patternType = Registry.BANNER_PATTERN.get(customKey);
			}

			if (patternType == null) {
				Bukkit.getLogger().warning("Invalid banner pattern: " + patternName);
				continue;
			}

			meta.addPattern(new Pattern(dye, patternType));
		}
		item.setItemMeta(meta);
		
		this.banner = item;
	}


	public Guild getOrCreateMainGuild() {
		Guild g = guildHandler.getGuild(id);
		if (g == null) {
			g = new Guild(this);
			guildHandler.addGuild(g);
		}
		return g;
	}

	public boolean hasCapital() {
		return provinceHandler.hasCapital();
	}

	public int getCapital() {
		return provinceHandler.getCapital();
	}

	public void setCapital(int i) {
		setCapital(i, false);
	}

	public void setCapital(int i, boolean force) {
		setCapital(i, force, true);
	}

	public void setCapital(int i, boolean force, boolean notifySettlement) {
		provinceHandler.setCapital(i, force, notifySettlement);
	}

	public double getTaxRate(TaxTarget target, String id, boolean effective) {
		return taxHandler.getTaxRate(target, id, effective);
	}

	public double getOverlordTaxRate(Faction f) {
		double taxRate = 0;
		Faction overlord = getOverlord();
		if(overlord == null) return taxRate;
		taxRate = overlord.getTaxRate(TaxTarget.VASSALS, id, false);
		for(FactionModifier mod : getModifiers()) {
			if(!mod.getType().equals(FactionModifiers.TAX_MULTIPLIER)) continue;
			double mult = 1+mod.getAmount()/100.0;
			taxRate *= mult;
		}
		return taxRate/100.0;
	}

	public double getTotalForeignTaxRate() {
		double taxRate = 0;
		for(FactionModifier mod : getModifiers()) {
			if(mod.getFrom() == null) continue;
			if(mod.getFrom().getBank() == null) continue;
			if(!mod.getType().equals(FactionModifiers.TRIBUTE)) continue;
			taxRate+=mod.getAmount();
		}
		return taxRate;
	}

	public Bracket getBracket(Brackets bracket) {
		return null;
	}

	public void giveTax(String player, double amount) {
    	getOrCreateMainGuild().getLedger().addCitizenTaxEntry(player, amount);
	}

	public double setTaxRate(double d) {
		taxHandler.setCitizenTax(d);
		return d;
	}
	
	public double getTaxRate() {
		return taxHandler.getCitizenTax();
	}

	public void setVassalTaxRate(double d) {
		taxHandler.setVassalTax(Math.max(20, Math.min(100, d)));
	}

	public double getVassalTaxRate() {
		return taxHandler.getVassalTax();
	}
	
	public HashMap<String, Relation> getRelations(){
		return diplomacyHandler.getRelations();
	}
	
	public Relation getRelation(String s) {
		return diplomacyHandler.getRelation(s);
	}
	
	public Military getMilitary() {
		return military;
	}

	public TaxHandler getTaxHandler() {
		return taxHandler;
	}
	
	public void tick() {
		//taxation fix, doubt this will be neccesary
		double tax = getTotalForeignTaxRate();
		if(getTaxRate() + tax > 100) setTaxRate(100-tax);
		
		military.tick();
		installationHandler.tick();
		for(Guild guild : guildHandler.getGuilds()) {
			guild.tick();
		}
		/*
		for(FactionModifier m : getModifiers()) {
			if(!m.isTimed()) continue;
			if(m.tick()) removeModifier(m);
		}
		*/
		government.tick();
		if (Cache.provincesEnabled) {
			settlementHandler.validate();
			installationHandler.validate();
		}
	}

	public SettlementHandler getSettlementHandler() {
		return settlementHandler;
	}

	public InstallationHandler getInstallationHandler() {
		return installationHandler;
	}

	public ProvinceHandler getProvinceHandler() {
		return provinceHandler;
	}

	public boolean hasProvince(int i) {
		return provinceHandler.hasProvince(i);
	}

	public boolean ownsProvince(int provinceId) {
		return provinceHandler.hasProvince(provinceId);
	}
	
	public void addProvince(int i) {
		provinceHandler.addProvince(i);
	}
	
	public void removeProvince(int i, boolean destroyTitles) {
		provinceHandler.removeProvince(i, destroyTitles);
	}
	public List<Integer> getProvinces(){
		return provinceHandler.getProvinces();
	}
	public List<String> getInvited() {
		return invited;
	}
	public void setInvited(List<String> invited) {
		this.invited = invited;
	}
	public boolean isInvited(String name) {
		return Guild.findIgnoreCase(invited, name) != null;
	}
	public void invite(String name) {
		if(name == null || isInvited(name)) return;
		invited.add(name);
	}
	public boolean consumeInvite(String name) {
		String stored = Guild.findIgnoreCase(invited, name);
		if(stored == null) return false;
		invited.remove(stored);
		return true;
	}
	public boolean isMemberIgnoreCase(String name) {
		return Guild.findIgnoreCase(getMembers(), name) != null;
	}
	public List<Modifier> getWealthModifiers() {
		List<Modifier> list = new ArrayList<>(getOrCreateMainGuild().getWealthModifiers());
		for(Guild guild : guildHandler.getGuilds()) {
			if(guild.isBase()) continue;
			if(guild.getWealth() == 0) continue;
			list.add(new Modifier(guild.getName()+" #a39ba8("+guild.getType().getName()+"#a39ba8)", guild.getWealth(), false));
		}
		return list;
	}
	public void setRGB(String rgb) {
		this.rgb = rgb;
	}
	public String getRGB() {
		return this.rgb;
	}
	public String getRulerTitle() {
		return rulerTitle;
	}
	public void setRulerTitle(String rulerTitle) {
		this.rulerTitle = rulerTitle;
	}
	public PrestigeRank getRank() {
		return rank;
	}
	public void setRank(PrestigeRank rank) {
		this.rank = rank;
	}
	public long getFoundedAt() {
		return foundedAt;
	}
	public void setFoundedAt(long foundedAt) {
		this.foundedAt = foundedAt;
	}
	public String getGovernmentString() {
		return governmentType;
	}
	public void setGovernment(String governmentType) {
		this.governmentType = governmentType;
	}
	public String getCulture() {
		return culture;
	}
	public void setCulture(String culture) {
		this.culture = culture;
	}
	public String getReligion() {
		return religion;
	}
	public void setReligion(String religion) {
		this.religion = religion;
	}
	public ItemStack getBanner() {
		return banner;
	}
	public void addPersistentPrestigeModifier(Modifier p) {
		for(int i = 0; i<prestigeModifiers.size(); i++) {
			if(prestigeModifiers.get(i).getType().equalsIgnoreCase(p.getType())) {
				p.setAmount(prestigeModifiers.get(i).getAmount()+p.getAmount());
				if(Double.compare(p.getAmount(), 0) == 0) {
					prestigeModifiers.remove(i);
					i--;
				} else {
					prestigeModifiers.set(i, p);
				}
				return;
			}
		}
		if(p.getAmount() != 0) {
			prestigeModifiers.add(p);
		}
	}
	public void setPersistentPrestigeModifier(String type, double amount) {
		for(int i = 0; i < prestigeModifiers.size(); i++) {
			if(prestigeModifiers.get(i).getType().equalsIgnoreCase(type)) {
				if(Double.compare(amount, 0) == 0) {
					prestigeModifiers.remove(i);
				} else {
					prestigeModifiers.set(i, new Modifier(type, amount, true));
				}
				return;
			}
		}
		if(Double.compare(amount, 0) != 0) {
			prestigeModifiers.add(new Modifier(type, amount, true));
		}
	}
	public void setBanner(ItemStack banner) {
		BannerMeta b = (BannerMeta) banner.getItemMeta();
		this.bannerPatterns.clear();
		this.bannerPatterns.add(banner.getType().toString().replace("_BANNER", ".BASE"));
		for(Pattern p : b.getPatterns()) {
			NamespacedKey key = p.getPattern().getKey();
			if (key == null) continue;
			this.bannerPatterns.add(p.getColor().name() + "." + key.getKey().toUpperCase());
		}
		createBanner(bannerPatterns);
	}
	public List<String> getBannerPatterns() {
		return bannerPatterns;
	}
	public void setBannerPatterns(List<String> bannerPatterns) {
		this.bannerPatterns = bannerPatterns;
		createBanner(bannerPatterns);
	}
	public String getId() {
		return id;
	}
	public void setId(String id) {
		this.id = id;
	}
	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}
	public List<String> getMembers() {
		return guildHandler.getAllMembers();
	}
	public List<String> getVassalMembers() {
		List<String> members = new ArrayList<>();
		for(Faction vassal : RelationManager.getSubjects(this)) {
			if(vassal == null) continue;
			members.addAll(vassal.getMembers());
		}
		return members;
	}
	public List<String> getCompleteMemberList() {
		List<String> members = new ArrayList<>();
		members.addAll(getMembers());
		for(Faction vassal : RelationManager.getSubjects(this)) {
			if(vassal == null) continue;
			members.addAll(vassal.getCompleteMemberList());
		}
		return members;
	}

	public void addMember(String m) {
		getOrCreateMainGuild().addMember(m);
	}
	public void forceRemoveMember(String m) {
		guildHandler.forceKick(m);
	}
	public boolean isInGuild(String member) {
		if(!getMembers().contains(member)) return false;
		return !getOrCreateMainGuild().isMember(member);
	}
	public boolean isMember(String member) {
		return getMembers().contains(member);
	}
	public boolean isLeader(String member) {
		return getOrCreateMainGuild().isLeader(member);
	}
	public boolean canBecomeLeader(String member) {
		if(isLeader(member)) return false;
		if(!isMember(member)) return false;
		for(Guild g : guildHandler.getGuilds()) {
			if(g.isLeader(member)) return false;
		}
		return true;
	}

	public boolean canRemainLeader(String name) {
		return isMember(name);
	}

	public boolean canBeCleanKicked(String p) {
		if(leader.equalsIgnoreCase(p)) return false;
		return !guildHandler.isGuildLeader(p);
	}
	public Double getWealth() {
		return wealth;
	}
	public double getVassalWealth() {
		double total = 0.0;
		for(Faction vassal : RelationManager.getSubjects(this)) {
			total += vassal.getWealth();
		}
		return Formatter.formatDouble(total);
	}

	public double getTotalTradePower() {
		double total = getGuildHandler().getTotalTradePower();
		total += getVassalTradePower();
		return Formatter.formatDouble(total);
	}

	public double getVassalTradePower() {
		double total = 0.0;
		for(Faction vassal : RelationManager.getSubjects(this)) {
			total += vassal.getGuildHandler().getTotalTradePower();
		}
		return Formatter.formatDouble(total);
	}
	public void setWealth(Double wealth) {
		this.wealth = wealth;
	}
	public Double getPrestige() {
		return prestige;
	}
	public void setPrestige(Double prestige) {
		this.prestige = prestige;
	}
	public Bank getBank() {
		return getOrCreateMainGuild().getBank();
	}
	public void setBank(Bank bank) {
		getOrCreateMainGuild().setBank(bank);
	}
	public String getLeader() {
		return leader;
	}
	public void setLeader(String leader) {
		getOrCreateMainGuild().setLeader(leader);
		this.leader = leader;
		me.Plugins.SimpleFactions.mercenary.contract.MercenaryLoyaltyWatcher.onGovernmentChanged(this);
	}
	public void promoteToLeader(String name) {
		if (!canBecomeLeader(name)) return;

		// Remove from any non-base guild
		Guild g = guildHandler.getGuildByMember(name);
		if (g != null && !g.isBase()) {
			g.kick(name);
		}

		// Ensure member is in main guild
		getOrCreateMainGuild().addMember(name);

		setLeader(name);
	}

	public List<Modifier> getPrestigeModifiers() {
		return prestigeModifiers;
	}
	public void setPrestigeModifiers(List<Modifier> prestigeModifiers) {
		this.prestigeModifiers = prestigeModifiers;
	}
	public Integer getExtraNodeCapacity() {
		return extraNodeCapacity;
	}
	public void setExtraNodeCapacity(Integer extraNodeCapacity) {
		this.extraNodeCapacity = extraNodeCapacity;
	}
	public boolean canPurchaseCapacity() {
		return this.extraNodeCapacity < Cache.maxExtraNodeCapacity;
	}
	public GuildHandler getGuildHandler() {
		return guildHandler;
	}
	public Guild getGuild(String player) {
		return guildHandler.getGuildByMember(player);
	}
	public void updatePrestige() {
		double branchPrestige = 0.0;
		if (guildHandler != null) {
			for (Guild guild : guildHandler.getGuilds()) {
				if (guild == null) continue;
				branchPrestige += guild.getModifier(GuildModifier.PRESTIGE);
			}
		}
		setPersistentPrestigeModifier("Branches", branchPrestige);

		// Two halves: headcount, plus what each member's online time is worth.
		List<String> roster = guildHandler.getAllMembers();
		double members = Math.pow(roster.size()+4, 1.8)+5
				+ me.Plugins.SimpleFactions.prestige.MemberPlaytime.totalFor(roster);

		double wealthAmount = 0.0;
		if(wealth > 0 && FactionManager.getGlobalWealth() > 0) {
			wealthAmount = wealth/FactionManager.getGlobalWealth()*Cache.maxWealthPrestige;
			if(wealthAmount > wealth) {
				wealthAmount = wealth;
			}
		}

		double tradeAmount = me.Plugins.SimpleFactions.prestige.TradePrestige.fromTradePower(
				guildHandler.getTotalTradePower());

		int provincePrestige = TierLoader.getByString("province").getPrestige();
		double provinceAmount = (double) (provincePrestige*provinceHandler.getProvinces().size());

		double titleAmount = titles.size() > 0 ? getHighestTitle().getTier().getPrestige() : 0.0;

		double fromSubjects = 0.0;
		for(Faction s : RelationManager.getSubjects(this)) {
			if(s == null) continue;
			double added = getRelation(s.getId()).getGiveModifier(FactionModifiers.PRESTIGE);
			if(added > 0) {
				fromSubjects += s.getPrestige()*(added/100.0);
			}
		}

		double bonusPercent = getModifier(FactionModifiers.PRESTIGE_BONUS).getAmount();

		prestigeModifiers = PrestigeBreakdown.build(
				prestigeModifiers, members, wealthAmount, tradeAmount, provinceAmount, titleAmount, fromSubjects, bonusPercent);
		prestige = PrestigeBreakdown.total(prestigeModifiers);
		
		if(this.rank.getLevel() < RankLoader.getRanks().size()) {
			Double rankUpAmount = FactionManager.getRankUpAmount(RankLoader.getByLevel(this.rank.getLevel()+1));
			if(prestige >= rankUpAmount) {
				this.rank = RankLoader.getByLevel(this.rank.getLevel()+1);
			}
		}
		if(this.rank.getLevel() != 1) {
			Double rankDownAmount = FactionManager.getRankUpAmount(RankLoader.getByLevel(this.rank.getLevel()));
			rankDownAmount = rankDownAmount*0.95;
			if(prestige < rankDownAmount) {
				this.rank = RankLoader.getByLevel(this.rank.getLevel()-1);
			}
		}
		String overlord = RelationManager.getOverlord(this);
		if(overlord != null) {
			Faction o = FactionManager.getByString(overlord);
			if(o != null) o.updatePrestige();
		}
	}
	public void updateWealth() {
		wealth = 0.0;
		for(Guild guild : guildHandler.getGuilds()) {
			wealth += guild.getWealth();
		}
		wealth = Formatter.formatDouble(wealth);
		FactionManager.updateAllPrestige();
	}
	
	public void setRelation(Faction f, Relation r) {
		diplomacyHandler.setRelation(f, r);
	}
	
	public void updateRelations() {
		diplomacyHandler.updateRelations();
	}
	
	//Titles
	public void countyCheck() {
		List<Title> counties = getTitles(TierLoader.getByString("county"));
		Random rand = new Random();

		while (counties.size() > guildHandler.getAllMembers().size()) {
			int index = rand.nextInt(counties.size()); // pick random index
			removeTitle(counties.get(index));          // remove that county
			counties.remove(index);                    // keep local list in sync
		}
		for(Title t : new ArrayList<>(getTitles())) {
			if(!hasTitle(t)) continue;
			t.destroy(this, TitleManager.getProvinces(this), TitleManager.getTitles(this));
		}
	}
	public void resetTitles(List<Title> list) {
		titles = list;
		updatePrestige();
	}
	
	public List<Integer> getUntitledProvinces() {
		return provinceHandler.getUntitledProvinces();
	}
	
	public List<Title> getFreeTitles(Tier tier) {
		List<Title> freeTitles = new ArrayList<>();
		for(Title t : titles) {
			if(!t.getTier().getId().equalsIgnoreCase(tier.getId())) continue;
			if(TitleLoader.getByTitle(t) == null) freeTitles.add(t);
		}
		for(Faction vassal : RelationManager.getSubjects(this)) {
			freeTitles.addAll(vassal.getFreeTitles(tier));
		}
		return freeTitles;
	}
	
	public boolean hasTitle(Title t) {
		return titles.contains(t);
	}
	
	public void addTitle(Title t) {
		if(hasTitle(t)) return;
		titles.add(t);
		updateTier();
	}

	public void stripTitle(Title t) {
		if(!hasTitle(t)) return;
		titles.remove(t);
		updateTier();
	}

	public void removeTitle(Title t) {
		if(!hasTitle(t)) return;
		titles.remove(t);
		t.destroy(this, TitleManager.getProvinces(this), TitleManager.getTitles(this));
		updateTier();
	}
	
	public List<Title> getTitles() {
		return titles;
	}
	
	public List<Title> getTitles(Tier t) {
		List<Title> list = new ArrayList<>();
		for(Title title : titles) {
			if(title.getTier().getTier() == t.getTier()) list.add(title);
		}
		return list;
	}
	
	public Tier getTier() {
		return tier;
	}
	
	public List<Title> getRankedTitles() {
	    return titles.stream()
	                 .sorted((a, b) -> Integer.compare(b.getTier().getTier(), a.getTier().getTier()))
	                 .collect(Collectors.toList());
	}

	
	public Title getHighestTitle() {
		return titles.stream()
	            .sorted((a, b) -> Integer.compare(b.getTier().getTier(), a.getTier().getTier()))
	            .findFirst()
	            .orElse(null);
	}
	
	public void updateTier() {
		Tier temp = null;
	    if (provinceHandler.getProvinces().size() == 0 && titles.size() == 0) {
	    	temp = TierLoader.getLowest();
	    } else if (provinceHandler.getProvinces().size() > 0 && titles.size() == 0) {
	    	temp = TierLoader.getByString("province");
	    } else {
	        Title highest = titles.stream()
	            .sorted((a, b) -> Integer.compare(b.getTier().getTier(), a.getTier().getTier()))
	            .findFirst()
	            .orElse(null);

	        if (highest != null) {
	        	temp = highest.getTier();
	        } else {
	        	temp = TierLoader.getLowest();
	        }
	    }
	    if(tier == null || !tier.getId().equalsIgnoreCase(temp.getId())) tier = new Tier(temp, -1);
		Player p = Bukkit.getPlayerExact(leader);
		for(Faction subject : RelationManager.getSubjects(this)) {
			if(subject.getTier().getTier() > tier.getTier()) {
				RelationManager.endVassalage(subject, this, false);
				if(p != null && p.isOnline()) {
					p.sendMessage("§cLost the subject "+subject.getName()+" §cdue to rank difference!");
				}
			}
		}
		updatePrestige();
	}

	//Government

	public Government getGovernment() {
		return government;
	}

	public void ping() {
		government.ping();
	}
	public boolean canVote(String p) {
		if(isMember(p)) return true;
		if(hasFactionRule(Rules.VASSAL_VOTING_RIGHTS)) {
			if(getVassalMembers().contains(p)) return true;
		}
		return false;
	}

	//Laws
	public LawHandler getLawHandler() { return lawHandler; }

	public void applyLaw(Law law, LawGroup group) {
		group.setCurrent(law);

		LawEffect effect = law.getScopedEffects().get(Scope.FACTION);
		if (effect == null) {
			cancelInvalidElections();
			return;
		}

		// --- existing tax logic ---
		if (effect.hasBrackets()) {
			for (Map.Entry<Brackets, Bracket> entry : effect.getBrackets().entrySet()) {
				taxHandler.applyBracket(
					BracketToTaxTarget.convert(entry.getKey()),
					entry.getValue()
				);
			}
		}

		if (effect.hasRules()) {
			for (Map.Entry<Rules, Boolean> entry : effect.getRules().entrySet()) {
				Rules rule = entry.getKey();
				Boolean value = entry.getValue();

				switch (rule) {
					case CITIZEN_TAX:
						if (!value)
							taxHandler.applyBracket(TaxTarget.CITIZENS, new Bracket(0, 0));
						break;
					case VASSAL_TAX:
						if (!value)
							taxHandler.applyBracket(TaxTarget.VASSALS, new Bracket(0, 0));
						break;
					case GUILD_TAX:
						if (!value)
							taxHandler.applyBracket(TaxTarget.GUILDS, new Bracket(0, 0));
						break;
					case DIVIDEND_TAX:
						if (!value)
							taxHandler.applyBracket(TaxTarget.DIVIDENDS, new Bracket(0, 0));
						break;
					case TARIFFS:
						if (!value)
							taxHandler.applyBracket(TaxTarget.TARIFFS, new Bracket(0, 0));
						break;
					default:
						break;
				}
			}
		}

		// --- council structure ---
		if (effect.affectsCouncilSize() || effect.affectsCouncilType()) {
			government.getCouncil().reorganize();
		}

		// --- 🔴 ELECTION CANCELLATION LOGIC ---
		cancelInvalidElections();

		// --- vassal logic ---
		if (effect.prohibitsVassals() && hasVassals()) {
			for (Faction vassal : getVassals()) {
				RelationManager.endVassalage(vassal, this, false);
			}
		}
	}

	public void applyPoliticalAction(Cause cause, Proposal proposal) {
		if(!proposal.isPoliticalActionProposal()) return;
		PoliticalAction politicalAction = proposal.getPoliticalAction();
		Action action = politicalAction.getAction();
		switch (action) {
			case CHANGE_LEADER:
				CoupService.apply(this, proposal.getTarget());
				break;
			case DISSOLVE:
				dissolve(getVassals(), getGuildHandler().getGuilds());
				break;
			case INDEPENDENCE:
				if(cause == null) return; //no cause = no members to give independence to
				for(Guild guild : cause.getPool().getGuilds()) {
					if(guild.canBeElevated(null)) {
						guild.elevate(false);
					} else {
						guild.toLandless(false);
					}
				}
				for(Faction vassal : getVassals()) {
					RelationManager.endVassalage(vassal, this, false);
				}
				break;
			case NATIONHOOD:
				if(cause == null) return; //no cause = no members to give nationhood
				for(Guild guild : cause.getPool().getGuilds()) {
					if(guild.canBeElevated(null)) {
						guild.elevate(true);
					} else {
						guild.toLandless(true);
					}
				}
				break;
            case SNAP_ELECTIONS:
                government.getElection().start();
                break;
            case WHITE_PEACE:
            case SURRENDER:
                CouncilPeaceService.apply(this, proposal);
                break;
            //Handled elsewhere
			case NONE:
			case LAW_CHANGE:
			case TAX_CHANGE:
			default:
				break;
		}
	}

	private void cancelInvalidElections() {
		Government gov = getGovernment();

		// Leader elections disabled
		if (!hasFactionRule(Rules.LEADER_ELECTIONS)) {
			gov.cancelElections(Candidate.LEADER);
		}

		// Council elections disabled
		if (!hasFactionRule(Rules.ELECTED_COUNCIL) || getCouncilType() != Rules.ELECTED_COUNCIL) {
			gov.cancelElections(Candidate.COUNCIL);
		}

		// No elections at all
		if (!gov.hasElections()) {
			gov.cancelAllElections();
		}
	}


	public boolean hasVassals() {
		return RelationManager.getSubjects(this).size() > 0;
	}

	public List<Faction> getVassals() {
		return RelationManager.getSubjects(this);
	}

	public boolean canHaveVassals() {
		return hasFactionRule(Rules.CAN_HAVE_VASSALS);
	}

	public int getCouncilSize() {
		if(!hasFactionRule(Rules.HAS_COUNCIL)) return 0;
		for (Law law : lawHandler.getCurrentLaws()) {

			// Law does not define this scope → ignore
			if (!law.getScopedEffects().containsKey(Scope.FACTION)) continue;

			LawEffect effect = law.getScopedEffects().get(Scope.FACTION);
			if(effect.affectsCouncilSize()) return effect.getCouncilSize();
		}
		return 4;
	}

	public Rules getCouncilType() {
		if(!hasFactionRule(Rules.HAS_COUNCIL)) return Rules.NO_COUNCIL;
		for (Law law : lawHandler.getCurrentLaws()) {
			if (!law.getScopedEffects().containsKey(Scope.FACTION)) continue;
			LawEffect effect = law.getScopedEffects().get(Scope.FACTION);
			if(effect.affectsCouncilType()) return effect.getCouncilType();
		}
		return Rules.NO_COUNCIL;
	}

	//Rules
	public boolean hasFactionRule(Rules rule) {

		// 1️⃣ Check faction rules first
		Boolean factionRule = getExplicitRule(Scope.FACTION, rule);
		if (factionRule != null) {
			return factionRule;
		}

		// 2️⃣ Check overlord rules (NO defaults allowed here)
		Faction overlord = getOverlord();
		if (overlord != null) {
			Boolean overlordRule = overlord.getExplicitRule(Scope.VASSALS, rule);
			if (overlordRule != null) {
				return overlordRule;
			}
		}

		// 3️⃣ Only now apply default
		return rule.trueIfAbsent();
	}

	public Boolean getExplicitRule(Scope scope, Rules rule) {
		boolean foundTrue = false;

		for (Law law : lawHandler.getCurrentLaws()) {

			if (!law.getScopedEffects().containsKey(scope)) continue;

			LawEffect effect = law.getScopedEffects().get(scope);

			if (!effect.hasRules()) continue;
			if (!effect.getRules().containsKey(rule)) continue;

			boolean value = effect.getRules().get(rule);

			if (!value) return Boolean.FALSE; // explicit false always wins
			foundTrue = true;
		}

		if (foundTrue) return Boolean.TRUE;

		return null; // not defined
	}

	
	//Modifiers

	public double getModifier(FactionModifiers m, String id, Scope scope, Region region) {
		for(FactionModifier mod : getModifiers(id, scope, region)) {
			if(mod.getType() == m) return 1+mod.getAmount()/100.0;
		}
		return 1.0;
	}

	public List<FactionModifier> getModifiers(String id, Scope scope, Region region) {
		return lawHandler.getLawModifiers(id, scope, region);
	}

	public Faction getOverlord() {
		String id = RelationManager.getOverlord(this);
		if(id == null) return null;
		return FactionManager.getByString(id);
	}

	public List<WarReparationsObligation> getWarReparationsObligations() {
		return warReparationsObligations;
	}

	public void addWarReparationsObligation(WarReparationsObligation obligation) {
		if (obligation != null) {
			warReparationsObligations.add(obligation);
		}
	}
	
	public Collection<FactionModifier> getModifiers() {
	    List<FactionModifier> all = new ArrayList<>();
		all.addAll(getModifiers(null, Scope.FACTION, null));
		all.addAll(diplomacyHandler.getModifiers());
		Faction overlord = getOverlord();
		if(overlord != null) {
			all.addAll(overlord.getModifiers(null, Scope.VASSALS, null));
		}
		if(rank.hasModifiers()) {
			all.addAll(rank.getModifiers());
		}
		double branchPrestigeBonus = 0.0;
		if (guildHandler != null) {
			for (Guild guild : guildHandler.getGuilds()) {
				if (guild == null) continue;
				branchPrestigeBonus += guild.getModifier(GuildModifier.PRESTIGE_BONUS);
			}
		}
		if (branchPrestigeBonus != 0.0) {
			all.add(new FactionModifier(FactionModifiers.PRESTIGE_BONUS, branchPrestigeBonus));
		}
	    return all;
	}
	
	public Collection<FactionModifier> getCombinedModifiers() {
	    Map<FactionModifiers, Double> combined = new HashMap<>();

	    for (FactionModifier mod : getModifiers()) {
			combined.merge(mod.getType(), mod.resolve(this), Double::sum);
		}

	    List<FactionModifier> result = new ArrayList<>();
	    for (Map.Entry<FactionModifiers, Double> entry : combined.entrySet()) {
	        double total = entry.getValue();
	        if (total != 0) {
	            result.add(new FactionModifier(entry.getKey(), total));
	        }
	    }

	    return result;
	}
	
	public FactionModifier getModifier(FactionModifiers m) {
	    double totalAmount = 0;
	    for (FactionModifier mod : getModifiers()) {
			if(mod.getType() != m) continue;
	        totalAmount += mod.resolve(this);
	    }
	    return new FactionModifier(m, totalAmount);
	}

    public void newDay() {
        double armyCost = military.getTotalUpkeep();
		if(armyCost > 0 && getBank() == null){
			for(Regiment r : military.getRegiments()){
				if(r.isLevy()) continue;
				while(r.getCurrentSlots() > r.getFreeSlots()){
					r.sizeDecrease();
				}
			}
		}
		while(armyCost > 0 && getBank().getWealth() < armyCost) {
			for(Regiment r : military.getRegiments()) {
				if(r.isLevy()) continue;
				if(r.getCurrentSlots() > r.getFreeSlots()) {
					r.sizeDecrease();
					break;
				}
			}
			armyCost = military.getTotalUpkeep();
		}
		if(armyCost > 0) {
			getBank().withdraw(armyCost);
		}
		installationHandler.payDailyUpkeep();
		provinceCap();
		for(Guild guild : guildHandler.getGuilds()) {
			guild.newDay();
		}
		if (government != null) {
			government.applyDailyOrganizationGain();
		}
    }

	public void provinceCap() {
		provinceHandler.provinceCap();
	}

	public int numOnline() {
		int count = 0;
		for(String m : guildHandler.getAllMembers()){
			Player p = Bukkit.getPlayerExact(m);
			if(p != null && p.isOnline()) count++;
		}
		return count;
	}

	public double getProsperity() {
		double amount = 0;
		for(int p : provinceHandler.getProvinces()) {
			Province province = SimpleFactions.getInstance().getProvinceManager().get(p);
			if(province == null)  continue;
			amount += province.getProsperity();
		}
		return Formatter.formatDouble(amount);
	}

	public double getPenalty() {
		double p = 0;
		if(government.getMaxPower() < 0) {
			p += Math.abs(government.getMaxPower());
		}
		if(diplomacyHandler.getAvailableCapacity() < 0) {
			p += Math.abs(diplomacyHandler.getAvailableCapacity());
		}
		return p;
	}
	public DiplomacyHandler getDiplomacyHandler() {
		return diplomacyHandler;
	}

	//dissolution

	public List<Faction> getSubjects() {
		return RelationManager.getSubjects(this);
	}

	public boolean canDissolve() {
		Faction overlord = getOverlord();
		if(overlord != null) return true;
		if(guildHandler.getGuilds().size() > 1) return true;
		if(getSubjects().size() > 0) return true;
		return false;
	}

	public Faction dissolve(List<Faction> vassals, List<Guild> guilds) {
		Faction overlord = getOverlord();
		if(overlord != null) {
			for(int i : provinceHandler.getProvinces()) {
				overlord.getProvinceHandler().addProvince(i);
			}
		}
		
		if(overlord != null) {
			for(Guild guild : guildHandler.getGuilds()) {
				if(guild.isBase()) continue;
				guild.relocate(overlord, guild.getCapital());
			}
		} else {
			for(Guild guild : guilds) {
				if(guild.isBase()) continue;
				if(guild.canBeElevated(null)){
					guild.elevate(false);
				} else {
					guild.toLandless(false);
				}
			}
		}
		for(Faction vassal : vassals) {
			if(overlord != null) {
				RelationManager.transferSubject(vassal, overlord);
			} else {
				RelationManager.endVassalage(vassal, this, false);
			}
		}
		if(overlord != null) {
			for(int i : new ArrayList<>(provinceHandler.getProvinces())) {
				InstallationTransferService.transfer(this, overlord, i);
				provinceHandler.removeProvince(i, true);
			}
			Guild base = getOrCreateMainGuild();
			base.convert(GuildLoader.getDefaultType());
			overlord.getGuildHandler().addGuild(base);
			//failsafe relocation
			for(Guild g : FactionManager.getAllGuilds()) {
				if(g.getFaction().getId().equalsIgnoreCase(id)) {
					g.relocate(overlord, g.getCapital());
				}
			}
			FactionManager.deleteFaction(this);
			return overlord;
		}
		return this;
	}

	public Member getRelationToFaction(String member) {
		if(isLeader(member)) return Member.LEADER;
		if(getOrCreateMainGuild().isMember(member)) return Member.MEMBER;
		for(Guild g : guildHandler.getGuilds()) {
			if(g.isBase()) continue;
			if(g.isLeader(member)) return Member.GUILD_LEADER;
			if(g.isMember(member)) return Member.GUILD_MEMBER;
		}
		for(Faction vassal : getSubjects()) {
			if(vassal.isLeader(member)) return Member.VASSAL_LEADER;
			if(vassal.isMember(member)) return Member.VASSAL_MEMBER;
		}
		return Member.FOREIGNER;
	}
}
