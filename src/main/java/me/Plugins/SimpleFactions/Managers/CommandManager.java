package me.Plugins.SimpleFactions.Managers;

import me.Plugins.SimpleFactions.vehicles.berth.VehicleFindMessages;
import me.Plugins.SimpleFactions.vehicles.maintenance.VehicleMaintenanceMessages;
import me.Plugins.SimpleFactions.vehicles.VehicleFactionCommands;
import java.util.Arrays;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;

import me.Plugins.SimpleFactions.Army.ExpandResult;
import me.Plugins.SimpleFactions.Army.Regiment;
import me.Plugins.SimpleFactions.Cache;
import me.Plugins.SimpleFactions.Database.Database;
import me.Plugins.SimpleFactions.Diplomacy.RelationType;
import me.Plugins.SimpleFactions.enums.Stance;
import me.Plugins.SimpleFactions.Events.FactionCreateEvent;
import me.Plugins.SimpleFactions.Events.FactionDeleteEvent;
import me.Plugins.SimpleFactions.Guild.Guild;
import me.Plugins.SimpleFactions.Loaders.LawLoader;
import me.Plugins.SimpleFactions.Loaders.RelationLoader;
import me.Plugins.SimpleFactions.Loaders.TitleLoader;
import me.Plugins.SimpleFactions.Map.Provinces.Province;
import me.Plugins.SimpleFactions.Objects.Bank;
import me.Plugins.SimpleFactions.Objects.BankPlacementValidator;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.Objects.Modifier;
import me.Plugins.SimpleFactions.REST.RestServer;
import me.Plugins.SimpleFactions.SimpleFactions;
import me.Plugins.SimpleFactions.installation.Installation;
import me.Plugins.SimpleFactions.installation.InstallationKind;
import me.Plugins.SimpleFactions.installation.handler.ConstructResult;
import me.Plugins.SimpleFactions.settlement.handler.CapitalResult;
import me.Plugins.SimpleFactions.Tiers.Title;
import me.Plugins.SimpleFactions.Utils.DisplayNameGate;
import me.Plugins.SimpleFactions.Utils.DisplayNameGate.NameOperation;
import me.Plugins.SimpleFactions.Utils.Formatter;
import me.Plugins.SimpleFactions.Utils.Permissions;
import me.Plugins.SimpleFactions.laws.Law;
import me.Plugins.SimpleFactions.laws.LawGroup;
import me.Plugins.SimpleFactions.enums.Rules;
import me.Plugins.SimpleFactions.enums.Terrain;
import me.Plugins.TLibs.Objects.API.SubAPI.StringFormatter;
import me.Plugins.SimpleFactions.vehicles.VehicleFactionCommands.VehicleCommandRoute;
import net.tfminecraft.DenarEconomy.Data.Account;
import net.tfminecraft.DenarEconomy.DenarEconomy;

public class CommandManager implements Listener, CommandExecutor{
	private Formatter format = new Formatter();
	public String cmd1 = "faction";
	public String cmd2 = "guild";
	
	@Override
	public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
		if(sender instanceof Player) {
			Player p = (Player) sender;
			if((cmd.getName().equalsIgnoreCase(cmd1) || cmd.getName().equalsIgnoreCase(cmd2)) && args.length < 1) {
				p.sendMessage("§a[SimpleFactions]§c Error with command format, use the gameplay guide for a list of commands");
				return true;
			}
			if(cmd.getName().equalsIgnoreCase(cmd2) && args[0].equalsIgnoreCase("create") && args.length == 2) {
				Faction f = FactionManager.getByMember(p.getName());
				if(f == null) {
					p.sendMessage("§cYou need to be in a faction to make a guild");
					return true;
				}
				if(f.isInGuild(p.getName())) {
					p.sendMessage("§cYou are already in a guild");
					return true;
				}
				if(f.getLeader().equalsIgnoreCase(p.getName())) {
					p.sendMessage("§cYou are the leader of the faction");
					return true;
				}
				String id = Formatter.formatId(args[1]);
				if(FactionManager.guildExists(id)) {
					p.sendMessage("§cThis guild already exists");
					return true;
				}
				if(DisplayNameGate.check(p, NameOperation.GUILD_CREATE, args[1])
						== DisplayNameGate.Result.NEEDS_CONFIRM) {
					return true;
				}
				Guild guild = new Guild(args[1], p, f, -1);
				f.getGuildHandler().addGuild(guild);
				p.sendMessage("§aGuild "+guild.getName()+" §acreated!");
				return true;
			} else if(cmd.getName().equalsIgnoreCase(cmd2) && args[0].equalsIgnoreCase("delete") && args.length == 2) {
				String id = Formatter.formatId(args[1]);
				Guild guild = FactionManager.getGuildByString(id);
				if(guild == null) {
					p.sendMessage("§cNo guild by the id "+args[1]);
					return true;
				}
				if(!guild.getLeader().equalsIgnoreCase(p.getName()) && !Permissions.isAdmin(sender)) {
					p.sendMessage("§cYou are not the leader of this guild");
					return true;
				}
				if(guild.isBase()) {
					p.sendMessage("§cYou cannot delete the base guild of a faction");
					return true;
				}
				guild.getFaction().getGuildHandler().removeGuild(guild.getId());
				guild.getFaction().getProvinceHandler().revalidateClaims();
				for(String member : guild.getMembers()) {
					Player pl = Bukkit.getPlayerExact(member);
					if(pl != null && !member.equalsIgnoreCase(guild.getLeader())) {
						pl.sendMessage("§cYour guild has been deleted!");
						pl.sendMessage("§aJoined "+guild.getFaction().getName());
					}
					guild.getFaction().addMember(member);
				}
				p.sendMessage("§cGuild "+guild.getName()+" §cdeleted!");
				return true;
			} else if(cmd.getName().equalsIgnoreCase(cmd2) && args[0].equalsIgnoreCase("dummyLeader") && args.length == 1) {
				Guild guild = FactionManager.getGuildByMember(p.getName());
				if(!Permissions.isAdmin(sender)) {
					p.sendMessage("§cYou do not have access to this command");
					return true;
				}
				if(guild == null) {
					p.sendMessage("§cYou are not in a guild");
					return true;
				}
				guild.dummyLeader(p);
				return true;
			} else if(cmd.getName().equalsIgnoreCase(cmd2) && args[0].equalsIgnoreCase("dummify") && args.length == 1) {
				Guild guild = FactionManager.getGuildByMember(p.getName());
				if(!Permissions.isAdmin(sender)) {
					p.sendMessage("§cYou do not have access to this command");
					return true;
				}
				if(guild == null) {
					p.sendMessage("§cYou are not in a guild");
					return true;
				}
				guild.dummify(p);
				return true;
			} else if(cmd.getName().equalsIgnoreCase(cmd2) && args[0].equalsIgnoreCase("menu") && args.length == 1) {
				Guild guild = FactionManager.getGuildByMember(p.getName());
				if(guild == null) {
					p.sendMessage("§cYou are not in a guild");
					return true;
				}
				InventoryManager inv = new InventoryManager();
				inv.guildView(p, guild);
				return true;
			} else if(cmd.getName().equalsIgnoreCase(cmd2) && args[0].equalsIgnoreCase("list") && args.length == 1) {
				InventoryManager i = new InventoryManager();
				i.guildList(p);
				return true;
			} else if(cmd.getName().equalsIgnoreCase(cmd2) && args[0].equalsIgnoreCase("invite") && args.length == 2) {
				Guild guild = FactionManager.getGuildByLeader(p.getName());
				if(guild == null) {
					p.sendMessage("§cYou are not the leader of a guild");
					return true;
				}
				if(!guild.isLeader(p)) {
					p.sendMessage("§cOnly the guild leader can invite players!");
					return true;
				}
				if(guild.isBase()) {
					p.sendMessage("§cThis is the base guild, use /faction invite instead");
					return true;
				}
				String invitee = args[1];
				if(invitee.equalsIgnoreCase(p.getName())) {
					p.sendMessage("§cCannot invite yourself");
					return true;
				}
				Player invited = Bukkit.getPlayer(invitee);
				if(invited == null) {
					p.sendMessage("§cCould not find the player "+invitee);
					return true;
				}
				String name = invited.getName();
				if(guild.isInvited(name)) {
					p.sendMessage("§cPlayer is already invited");
					return true;
				}
				if(guild.isMember(name)) {
					p.sendMessage("§cPlayer is already a member");
					return true;
				}
				Guild otherGuild = FactionManager.getGuildByMember(name);
				if(otherGuild != null) {
					if(!otherGuild.getFaction().equals(guild.getFaction())) {
						p.sendMessage("§cPlayer is already in a guild ("+otherGuild.getName()+") in another faction");
						return true;
					}
					if(!otherGuild.isBase()) {
						p.sendMessage("§cPlayer is already in a guild ("+otherGuild.getName()+") in the same faction");
						return true;
					}
					if(otherGuild.isLeader(name)) {
						p.sendMessage("§cPlayer is the leader of the faction, cannot invite");
						return true;
					}
				}
				boolean inThisFaction = guild.getFaction().isMemberIgnoreCase(name)
						|| (otherGuild != null && otherGuild.getFaction().equals(guild.getFaction()));
				if(!inThisFaction && guild.getFaction().hasFactionRule(Rules.CLOSED_BORDERS) && !guild.getFaction().isLeader(p.getName())) {
					p.sendMessage("§cThe faction has closed borders, cannot invite members from outside the faction");
					return true;
				} 
				guild.invite(name);
				invited.sendMessage("§aYou have been invited to the guild "+guild.getName());
				return true;
			} else if(cmd.getName().equalsIgnoreCase(cmd2) && args[0].equalsIgnoreCase("join") && args.length == 2) {
				if(FactionManager.getGuildByLeader(p.getName()) != null) {
					p.sendMessage("§cYou are the leader of a guild");
					return true;
				}
				if(!FactionManager.canJoinGuild(p)) {
					p.sendMessage("§cYou are already in a guild");
					return true;
				}
				Guild g = FactionManager.getGuildByString(args[1]);
				if(!g.consumeInvite(p.getName())) {
					p.sendMessage("§cYou need to be invited to this guild by the leader first!");
					return true;
				}
				Guild previous = FactionManager.getGuildByMember(p.getName());
				if(previous != null) {
					previous.kick(p.getName());
				}
				g.addMember(p.getName());
				p.sendMessage("§aJoined "+g.getName());
				g.getFaction().updatePrestige();
				for(Player pl : Bukkit.getOnlinePlayers()) {
					if(g.getFaction().getMembers().contains(pl.getName())) {
						pl.sendMessage("§a"+p.getName()+ " joined the faction!");
					}
				}
				return true;
			} else if(cmd.getName().equalsIgnoreCase(cmd2) && args[0].equalsIgnoreCase("leave") && args.length == 1) {
				if(FactionManager.getGuildByMember(p.getName()) == null) {
					p.sendMessage("§cYou are not in a guild");
					return true;
				}
				if(FactionManager.getGuildByLeader(p.getName()) != null) {
					p.sendMessage("§cCant leave if you are the leader");
					p.sendMessage("§cUse /guild delete first");
					return true;
				}
				Guild g = FactionManager.getGuildByMember(p.getName());
				g.kick(p.getName());
				p.sendMessage("§aLeft "+g.getName());
				return true;
			} else if(cmd.getName().equalsIgnoreCase(cmd2) && args[0].equalsIgnoreCase("delete") && args.length == 2) {
				Guild g = FactionManager.getGuildByString(args[1]);
				if(g == null) {
					p.sendMessage("§a[SimpleFactions]§c Error! guild does not exist!");
					return true;
				}
				if(!Permissions.isAdmin(sender)) {
					if(!g.getMembers().contains(p.getName())) {
						p.sendMessage("§cCannot delete a guild you are not part of!");
						return true;
					}
					if(g.isBankrupt()) {
						p.sendMessage("§cCannot delete a guild that is in bankruptcy!");
						return true;
					}
					if(!p.getName().equalsIgnoreCase(g.getLeader())) {
						p.sendMessage("§cOnly the guild leader can delete the guild!");
						return true;
					}
					if(g.getBank() != null && g.getBank().getWealth() > 0){
						p.sendMessage("§cCannot delete a guild while the bank balance is above 0");
						return true;
					}
					if(g.getLoanHandler().getLoansTaken().size() > 0) {
						p.sendMessage("§cCannot delete a guild with active loans");
						return true;
					}
				}

				Faction host = g.getFaction();
				host.getGuildHandler().removeGuild(g.getId());
				p.sendMessage("§aGuild "+g.getName()+" §adeleted!");
				return true;
			} else if(cmd.getName().equalsIgnoreCase(cmd2) && args[0].equalsIgnoreCase("setbank") && args.length == 1) {
				if(FactionManager.getGuildByLeader(p.getName()) != null) {
					Guild g = FactionManager.getGuildByLeader(p.getName());
					String bankBlockReason = BankPlacementValidator.failureReasonForGuild(g, p.getLocation());
					if(bankBlockReason != null) {
						p.sendMessage(bankBlockReason);
						return true;
					}
					if(g.getBank() != null) {
						Bank bank = g.getBank();
						bank.setChunk(p.getLocation().getChunk());
						p.sendMessage("§aBank Chunk Moved");
					} else {
						g.setBank(new Bank(g, 0, p.getLocation().getChunk()));
						p.sendMessage("§aBank Chunk Set");
					}
					p.playSound(p, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 1f, 1f);
				} else {
					p.sendMessage("§cYou need to be a guild or faction leader to place the bank location");
				}
				return true;
			} else if(cmd.getName().equalsIgnoreCase(cmd2) && args[0].equalsIgnoreCase("deposit") && args.length == 2) {
				if(FactionManager.getGuildByMember(p.getName()) != null) {
					Guild g = FactionManager.getGuildByMember(p.getName());
					Bank b = g.getBank();
					if(b == null) {
						p.sendMessage("§cYour guild has no bank chunk");
						return false;
					}
					if(!p.getLocation().getChunk().equals(g.getBank().getChunk())) {
						p.sendMessage("§cYou need to be in the Guild Bank Chunk to deposit money");
						return false;
					}
					double amount = Double.parseDouble(args[1]);
					Account pouch = DenarEconomy.getPlayerManager().get(p).getPouch();
					if(amount <= 0) {
						p.sendMessage("§cAmount must be greater than 0");
						return false;
					}
					if(pouch.getBal() < amount) {
						p.sendMessage("§cNot enough funds");
						return false;
					}
					pouch.change(amount*-1);
					g.getBank().deposit(amount);
					p.sendMessage("§e============§6[Bank Report]§e==============");
					p.sendMessage(StringFormatter.formatHex("#6ab05aDeposited: #b39122"+amount+"#dbaf1dd"));
					p.sendMessage(StringFormatter.formatHex("#3ce8c9New Guild Balance: #b39122"+b.getWealth()+"#dbaf1dd"));
					p.sendMessage(StringFormatter.formatHex("#3ce8c9New Pouch Balance: #b39122"+pouch.getBal()+"#dbaf1dd"));
					p.sendMessage("§e=====================================");
					p.playSound(p, Sound.BLOCK_NOTE_BLOCK_CHIME, 1f, 1f);
				} else {
					p.sendMessage("§cYou need to be a in a guild to deposit money into the guild bank");
				}
				return true;
			} else if(cmd.getName().equalsIgnoreCase(cmd2) && args[0].equalsIgnoreCase("withdraw") && args.length == 2) {
				if(FactionManager.getGuildByMember(p.getName()) != null) {
					Guild g = FactionManager.getGuildByMember(p.getName());
					Bank b = g.getBank();
					if(b == null) {
						p.sendMessage("§cYour guild has no bank chunk");
						return false;
					}
					if(!p.getLocation().getChunk().equals(b.getChunk())) {
						p.sendMessage("§cYou need to be in the Guild Bank Chunk to withdraw money");
						return false;
					}
					double amount = Double.parseDouble(args[1]);
					Account pouch = DenarEconomy.getPlayerManager().get(p).getPouch();
					if(amount <= 0) {
						p.sendMessage("§cAmount must be greater than 0");
						return false;
					}
					if(b.getWealth() < amount) {
						p.sendMessage("§cNot enough funds in the guild bank");
						return false;
					}
					pouch.change(amount);
					g.getBank().withdraw(amount);
					p.sendMessage("§e============§6[Bank Report]§e==============");
					p.sendMessage(StringFormatter.formatHex("#6ab05aWithdrew: #b39122"+amount+"#dbaf1dd"));
					p.sendMessage(StringFormatter.formatHex("#3ce8c9New Guild Balance: #b39122"+b.getWealth()+"#dbaf1dd"));
					p.sendMessage(StringFormatter.formatHex("#3ce8c9New Pouch Balance: #b39122"+pouch.getBal()+"#dbaf1dd"));
					p.sendMessage("§e=====================================");
					p.playSound(p, Sound.BLOCK_NOTE_BLOCK_CHIME, 1f, 1f);
				} else {
					p.sendMessage("§cYou need to be a guild leader to withdraw from the guild bank");
				}
				return true;
			} else if(cmd.getName().equalsIgnoreCase(cmd2) && args[0].equalsIgnoreCase("setcapital") && (args.length == 1 || args.length == 2)) {
				if(!Cache.requireProvinces(p)) {
					return true;
				}
				Guild g = FactionManager.getGuildByLeader(p.getName());
				if(g == null) {
					p.sendMessage("§cYou must be the leader of a guild to set the capital");
					return true;
				}
				int claim = RestServer.getProvince(p);
				if(claim == -2) {
					p.sendMessage("§a[SimpleFactions] §cError! could not resolve province");
					return true;
				}
				if(claim == 0) {
					p.sendMessage("§cThis location has no province!");
					return true;
				}
				String name = args.length == 2 ? args[1] : null;
				if(name != null && g.getFaction().getSettlementHandler().requiresFoundingName(claim)
						&& DisplayNameGate.check(p, NameOperation.SETTLEMENT_FOUND, name)
						== DisplayNameGate.Result.NEEDS_CONFIRM) {
					return true;
				}
				if(!tryClaimForCapital(p, g.getFaction(), claim)) {
					return true;
				}
				CapitalResult result = g.getFaction().getSettlementHandler()
						.resolveGuildCapital(p, g, claim, name);
				p.sendMessage(result.getMessage());
				if(result.isSuccess()) {
					g.setCapital(claim);
					p.playSound(p, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
				}
				return true;
			} else if(cmd.getName().equalsIgnoreCase(cmd2) && args[0].equalsIgnoreCase("setbanner") && args.length == 1) {
				Guild g = FactionManager.getGuildByLeader(p.getName());
				if(g == null) {
					p.sendMessage("§cYou must be the leader of a guild to change the banner!");
					return true;
				}
				if(g.isBase()) {
					p.sendMessage("§cYou cannot change the banner of the base guild!");
					p.sendMessage("§cUse §e/faction setbanner §cto change the faction banner");
					return true;
				}
				ItemStack i = new ItemStack(p.getInventory().getItemInMainHand());
				if(i == null || !i.getType().toString().contains("BANNER")) {
					p.sendMessage("§a[SimpleFactions]§c Error! You must be holding a banner in your main hand!");
					return true;
				}
				i.setAmount(1);
				g.setBanner(i);
				p.sendMessage("§aGuild banner changed!");
				return true;
			} else if(cmd.getName().equalsIgnoreCase(cmd2) && args[0].equalsIgnoreCase("setleader") && args.length == 2) {
				Guild g = FactionManager.getGuildByMember(p.getName());
				if(g == null) {
					p.sendMessage("§cYou need to have a guild to change leader");
					return true;
				}
				if(!p.getName().equalsIgnoreCase(g.getLeader())) {
					p.sendMessage("§cOnly the leader can set a new leader!");
					return true;
				}
				if(!g.isMember(args[1])) {
					p.sendMessage("§cPlayer is not in the guild");
					return true;
				}
				if(args[1].equalsIgnoreCase(g.getLeader())) {
					p.sendMessage("§cPlayer is already the leader");
					return true;
				}
				g.setLeader(args[1]);
				for(Player pl : Bukkit.getOnlinePlayers()) {
					if(g.isMember(pl.getName())) {
						pl.sendMessage("§a"+args[1]+ " is the new guild leader!");
					}
				}
				return true;
			} else if(cmd.getName().equalsIgnoreCase(cmd2) && args[0].equalsIgnoreCase("rename") && args.length == 2) {
				Guild g = FactionManager.getGuildByLeader(p.getName());
				if(g == null) {
					p.sendMessage("§a[SimpleFactions]§c Error! You must be the leader of a guild to rename one!");
					return true;
				}
				if(DisplayNameGate.check(p, NameOperation.GUILD_RENAME, args[1])
						== DisplayNameGate.Result.NEEDS_CONFIRM) {
					return true;
				}
				Formatter format = new Formatter();
				g.setName(StringFormatter.formatHex(format.formatName(args[1])));
				p.sendMessage("§aGuild renamed to "+g.getName());
				return true;
			} 
			if(cmd.getName().equalsIgnoreCase(cmd1) && args[0].equalsIgnoreCase("create") && args.length == 2) {
				if(FactionManager.getByMember(p.getName()) != null) {
					p.sendMessage("§cYou already have a faction!");
					return true;
				}
				if(DisplayNameGate.check(p, NameOperation.FACTION_CREATE, args[1])
						== DisplayNameGate.Result.NEEDS_CONFIRM) {
					return true;
				}
				Faction f = new Faction(args[1], p.getName());
				FactionCreateEvent factionCreateEvent = new FactionCreateEvent(p, f);
				Bukkit.getPluginManager().callEvent(factionCreateEvent);
				if(!factionCreateEvent.isCancelled()) {
					FactionManager.addFaction(f);
					p.sendMessage("§aFaction "+f.getName()+" §acreated!");
					if(Cache.provincesEnabled) {
						p.sendMessage("§7Use §e/faction setcapital <name> §7to claim your first province and found your capital.");
					}
				}
				return true;
			} else if(cmd.getName().equalsIgnoreCase(cmd1) && args[0].equalsIgnoreCase("claim") && args.length == 1) {
				if(!Cache.requireProvinces(p)) {
					return true;
				}
				if(FactionManager.getByLeader(p.getName()) != null) {
					Faction f = FactionManager.getByMember(p.getName());
					if(f.getProvinces().isEmpty()) {
						p.sendMessage("§cUse §e/faction setcapital <name> §cto claim your first province and found your capital city.");
						return true;
					}
					int claim = RestServer.getProvince(p);
					if(claim == -2) {
						p.sendMessage("§a[SimpleFactions] §cError! could not resolve province");
					} else {
						FactionManager.getMap().claim(p, f, claim, false);
					}
				} else {
					p.sendMessage("§cYou need to be a faction leader to claim land");
				}
				return true;
			} else if(cmd.getName().equalsIgnoreCase(cmd1) && args[0].equalsIgnoreCase("construct")) {
				if(!Cache.requireProvinces(p)) {
					return true;
				}
				Faction f = FactionManager.getByLeader(p.getName());
				if(f == null) {
					p.sendMessage("§cYou need to be a faction leader to construct installations");
					return true;
				}
				if(args.length == 1) {
					p.sendMessage("§cUsage: §e/faction construct <fort|port|airport> <name>");
					return true;
				}
				InstallationKind kind = InstallationKind.fromCommand(args[1]);
				if(kind == null) {
					p.sendMessage("§cUnknown installation type. Use: §efort§7, §eport§7, or §eairport");
					return true;
				}
				if(args.length == 2) {
					p.sendMessage("§cName required: §e/faction construct " + kind.getCommandName() + " <name>");
					return true;
				}
				int province = RestServer.getProvince(p);
				if(province == -2) {
					p.sendMessage("§a[SimpleFactions] §cError! could not resolve province");
					return true;
				}
				if(province == 0) {
					p.sendMessage("§cThis location has no province!");
					return true;
				}
				String name = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
				ConstructResult result = f.getInstallationHandler().construct(
						kind,
						name,
						province,
						p.getLocation().getBlockX(),
						p.getLocation().getBlockZ());
				p.sendMessage(result.getMessage());
				if(result.isSuccess()) {
					p.playSound(p, Sound.BLOCK_ANVIL_USE, 1f, 1f);
				}
				return true;
			} else if(cmd.getName().equalsIgnoreCase(cmd1) && args[0].equalsIgnoreCase("deconstruct")) {
				if(!Cache.requireProvinces(p)) {
					return true;
				}
				Faction f = FactionManager.getByLeader(p.getName());
				if(f == null) {
					p.sendMessage("§cYou need to be a faction leader to deconstruct installations");
					return true;
				}
				InventoryManager inv = new InventoryManager();
				if(args.length == 1) {
					inv.installationsView(null, p, f, true);
					p.playSound(p, Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
					return true;
				}
				String id = args[1];
				var handler = f.getInstallationHandler();
				boolean exists = handler.getById(id) != null
						|| (handler.getPendingConstruction() != null
								&& handler.getPendingConstruction().getId().equalsIgnoreCase(id));
				if(!exists) {
					p.sendMessage("§cNo installation with id §f" + id);
					return true;
				}
				inv.confirming.put(p, f);
				inv.installationConfirmFromCommand.put(p, true);
				inv.confirmView(p, f, "installation", id);
				p.playSound(p, Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
				return true;
			} else if(cmd.getName().equalsIgnoreCase(cmd1) && args[0].equalsIgnoreCase("vehicle")) {
				if(VehicleCommandRoute.isMaintenancePay(args)) {
					me.Plugins.SimpleFactions.vehicles.VehicleFactionCommands.armMaintenancePay(p);
					return true;
				}
				String transferId = me.Plugins.SimpleFactions.vehicles.VehicleFactionCommands.VehicleCommandRoute.transferInstallationId(args);
				if(transferId != null) {
					me.Plugins.SimpleFactions.vehicles.VehicleFactionCommands.armTransfer(
							p, transferId.isBlank() ? null : transferId);
					return true;
				}
				if(args.length >= 2 && args[1].equalsIgnoreCase("maintenance")) {
					p.sendMessage(me.Plugins.SimpleFactions.vehicles.maintenance.VehicleMaintenanceMessages.payUsage());
					return true;
				}
				p.sendMessage(me.Plugins.SimpleFactions.vehicles.maintenance.VehicleMaintenanceMessages.vehicleUsage());
				return true;
			} else if(cmd.getName().equalsIgnoreCase(cmd1) && args[0].equalsIgnoreCase("transfervehicle")) {
				String transferId = me.Plugins.SimpleFactions.vehicles.VehicleFactionCommands.VehicleCommandRoute.transferInstallationId(args);
				me.Plugins.SimpleFactions.vehicles.VehicleFactionCommands.armTransfer(
						p, transferId == null || transferId.isBlank() ? null : transferId);
				return true;
			} else if(cmd.getName().equalsIgnoreCase(cmd1) && args[0].equalsIgnoreCase("findvehicles")) {
				Faction f = FactionManager.getByLeader(p.getName());
				if(f == null) {
					p.sendMessage(me.Plugins.SimpleFactions.vehicles.berth.VehicleFindMessages.notLeader());
					return true;
				}
				if(args.length < 2) {
					p.sendMessage(me.Plugins.SimpleFactions.vehicles.berth.VehicleFindMessages.usage());
					return true;
				}
				var handler = f.getInstallationHandler();
				Installation installation =
						me.Plugins.SimpleFactions.vehicles.berth.VehicleFindMessages.resolveInstallation(
								handler, args[1]);
				if(installation == null) {
					p.sendMessage(me.Plugins.SimpleFactions.vehicles.berth.VehicleFindMessages.unknownInstallation());
					return true;
				}
				me.Plugins.SimpleFactions.vehicles.berth.VehicleFindMessages.sendInstallationVehicles(
						p, installation);
				return true;
			} else if(cmd.getName().equalsIgnoreCase(cmd1) && args[0].equalsIgnoreCase("unclaim") && args.length >= 1) {
				if(!Cache.requireProvinces(p)) {
					return true;
				}
				Faction f = null;
				if(args.length == 1) {
					if(FactionManager.getByLeader(p.getName()) != null) {
						f = FactionManager.getByMember(p.getName());
					} else {
						p.sendMessage("§cYou need to be a faction leader to unclaim land");
						return true;
					}
				} else if(args.length == 2) {
					if(!Permissions.isAdmin(sender)) {
						p.sendMessage("§a[SimpleFactions]§c You do not have access to this command");
						return true;
					}
					f = FactionManager.getByString(args[1]);
					if(f == null) {
						p.sendMessage("§cNo faction by the id "+args[1]);
						return true;
					}
				}
				int claim = RestServer.getProvince(p);
				if(claim == -2) {
					p.sendMessage("§a[SimpleFactions] §cError! could not resolve province");
				} else {
					FactionManager.getMap().unclaim(p, f, claim);
				}
				
				return true;
			} else if(cmd.getName().equalsIgnoreCase(cmd1) && args[0].equalsIgnoreCase("accept") && args.length == 1) {
				if(!RequestManager.hasRequest(p)) {
					p.sendMessage("§cYou have no requests to accept");
					return true;
				}
				RequestManager.accept(p);
				return true;
			} else if(cmd.getName().equalsIgnoreCase(cmd1) && args[0].equalsIgnoreCase("setcolour") && args.length == 2) {
			    if(FactionManager.getByLeader(p.getName()) != null) {
			        String rgb = args[1];
			        int result = FactionManager.validateRGB(rgb);

			        if(result == 0) {
			            Faction f = FactionManager.getByLeader(p.getName());
			            FactionManager.getMap().enqueue("nation", f.getRGB());
			            f.setRGB(rgb);
			            FactionManager.getMap().enqueue("nation", f.getRGB());
			            p.sendMessage("§aFaction colour updated to §f" + rgb);
			        } else if(result == 1) {
			            p.sendMessage("§cInvalid format. Use: R,G,B (e.g. 255,0,0)");
			        } else if(result == 2) {
			            p.sendMessage("§cRGB values must be numbers (e.g. 128,128,128)");
			        } else if(result == 3) {
			            p.sendMessage("§cEach RGB value must be between 0 and 255");
			        }

			    } else {
			        p.sendMessage("§cYou need to be a faction leader to change colour");
			    }
			    return true;
			} else if(cmd.getName().equalsIgnoreCase(cmd1) && args[0].equalsIgnoreCase("delete") && args.length == 2) {
				Faction f = FactionManager.getByString(args[1]);
				if(f == null) {
					p.sendMessage("§a[SimpleFactions]§c Error! faction does not exist!");
					return true;
				}
				if(!Permissions.isAdmin(sender)) {
					if(!f.getMembers().contains(p.getName())) {
						p.sendMessage("§cCannot delete a faction you are not part of!");
						return true;
					}
					if(f.getOrCreateMainGuild().isBankrupt()) {
						p.sendMessage("§cCannot delete a faction in bankruptcy!");
						return true;
					}
					if(f.getOrCreateMainGuild().getLoanHandler().getLoansTaken().size() > 0) {
						p.sendMessage("§cCannot delete a faction with active loans");
						return true;
					}
					if(!p.getName().equalsIgnoreCase(f.getLeader())) {
						p.sendMessage("§cOnly the faction leader can delete the faction!");
						return true;
					}
					if(f.getBank() != null && f.getBank().getWealth() > 0){
						p.sendMessage("§cCannot delete a faction while the bank balance is above 0");
						return true;
					}
				}
				FactionDeleteEvent factionDeleteEvent = new FactionDeleteEvent(p, f);
				Bukkit.getPluginManager().callEvent(factionDeleteEvent);
				if(!factionDeleteEvent.isCancelled()) {
					FactionManager.deleteFaction(f);
					if(f.getBank() != null) {
						BankManager.banks.remove(f.getBank());
					}
					for(Faction fac : FactionManager.factions) {
						if(fac.getRelations().containsKey(f.getId())) fac.getRelations().remove(f.getId());
					}
					p.sendMessage("§aFaction "+f.getName()+" §adeleted!");
				}
				return true;
			} else if(cmd.getName().equalsIgnoreCase(cmd1) && args[0].equalsIgnoreCase("list") && args.length == 1) {
				InventoryManager i = new InventoryManager();
				i.factionList(p);
				return true;
			} else if(cmd.getName().equalsIgnoreCase(cmd1) && args[0].equalsIgnoreCase("menu") && args.length == 1) {
				Faction f = FactionManager.getByMember(p.getName());
				if(f == null) {
					p.sendMessage("§cYou are not in a faction");
					return true;
				}
				InventoryManager i = new InventoryManager();
				i.factionView(p, f);
				return true;
			} else if(cmd.getName().equalsIgnoreCase(cmd1) && args[0].equalsIgnoreCase("kick") && args.length == 2) {
				Faction f = FactionManager.getByMember(p.getName());
				if(f == null) {
					p.sendMessage("§cYou need to have a faction to kick someone");
					return true;
				}
				if(!p.getName().equalsIgnoreCase(f.getLeader())) {
					p.sendMessage("§cOnly the leader can kick players!");
					return true;
				}
				if(args[1].equalsIgnoreCase(f.getLeader())) {
					p.sendMessage("§cCant kick the leader!");
					return true;
				}
				if(!f.getMembers().contains(args[1])) {
					p.sendMessage("§cPlayer is not a member");
					return true;
				}
				if(f.isInGuild(args[1])) {
					p.sendMessage("§cPlayer is a member of a guild");
					return true;
				}
				f.forceRemoveMember(args[1]);;
				p.sendMessage("§aKicked "+args[1]);
				for(Player pl : Bukkit.getOnlinePlayers()) {
					if(pl.getName().equalsIgnoreCase(args[1])) {
						pl.sendMessage("§a"+p.getName()+ " kicked you from "+f.getName());
					}
				}
				return true;
			} else if(cmd.getName().equalsIgnoreCase(cmd1) && args[0].equalsIgnoreCase("setleader") && args.length == 2) {
				Faction f = FactionManager.getByMember(p.getName());
				if(f == null) {
					p.sendMessage("§cYou need to have a faction to change leader");
					return true;
				}
				if(!p.getName().equalsIgnoreCase(f.getLeader())) {
					p.sendMessage("§cOnly the leader can set a new leader!");
					return true;
				}
				if(f.getGovernment().hasLeaderElections()) {
					p.sendMessage("§cCannot set leader in a democracy!");
					return true;
				}
				if(!f.getMembers().contains(args[1])) {
					p.sendMessage("§cPlayer is not in the faction");
					return true;
				}
				if(args[1].equalsIgnoreCase(f.getLeader())) {
					p.sendMessage("§cPlayer is already the leader");
					return true;
				}
				if(!f.canBecomeLeader(args[1])) {
					p.sendMessage("§cPlayer is not eligble to be leader (perhaps they are a guild leader?)");
					return true;
				}
				f.setLeader(args[1]);
				for(Player pl : Bukkit.getOnlinePlayers()) {
					if(f.getMembers().contains(pl.getName())) {
						pl.sendMessage("§a"+args[1]+ " is the new faction leader!");
					}
				}
				return true;
			} else if(cmd.getName().equalsIgnoreCase(cmd1) && args[0].equalsIgnoreCase("invite") && args.length == 2) {
				Faction f = FactionManager.getByMember(p.getName());
				if(f == null) {
					p.sendMessage("§cYou need to have a faction to invite someone");
					return true;
				}
				if(!p.getName().equalsIgnoreCase(f.getLeader())) {
					p.sendMessage("§cOnly the leader can invite players!");
					return true;
				}
				Player invited = Bukkit.getPlayer(args[1]);
				if(invited == null) {
					p.sendMessage("§cNo player found by that IGN");
					return true;
				}
				String name = invited.getName();
				if(f.isMemberIgnoreCase(name)) {
					p.sendMessage("§cPlayer is already a member");
					return true;
				}
				if(FactionManager.getByMember(name) != null) {
					p.sendMessage("§cPlayer is a member of another faction");
					return true;
				}
				if(f.getOrCreateMainGuild().getMembers().size() == Cache.maxMembers) {
					p.sendMessage("§cMain faction guild already has the maximum amount of members");
					return true;
				}
				f.invite(name);
				p.sendMessage("§aInvited "+name);
				invited.sendMessage("§a"+p.getName()+ " invited you to "+f.getName());
				invited.sendMessage("§aType /faction join "+f.getId() +"§a to join");
				return true;
			} else if(cmd.getName().equalsIgnoreCase(cmd1) && args[0].equalsIgnoreCase("join") && args.length == 2) {
				if(FactionManager.getByMember(p.getName()) != null) {
					p.sendMessage("§cAlready in a faction, leave your current faction first!");
					return true;
				}
				Faction f = FactionManager.getByString(args[1]);
				if(!f.consumeInvite(p.getName())) {
					p.sendMessage("§cYou need to be invited to this faction by the leader first!");
					return true;
				}
				f.addMember(p.getName());
				p.sendMessage("§aJoined "+f.getName());
				f.updatePrestige();
				for(Player pl : Bukkit.getOnlinePlayers()) {
					if(f.getMembers().contains(pl.getName())) {
						pl.sendMessage("§a"+p.getName()+ " joined the faction!");
					}
				}
				return true;
			} else if(cmd.getName().equalsIgnoreCase(cmd1) && args[0].equalsIgnoreCase("leave") && args.length == 1) {
				if(FactionManager.getByMember(p.getName()) == null) {
					p.sendMessage("§cYou are not in a faction");
					return true;
				}
				if(FactionManager.getByLeader(p.getName()) != null) {
					p.sendMessage("§cCant leave if you are the leader");
					p.sendMessage("§cUse /faction delete first");
					return true;
				}
				Faction f = FactionManager.getByMember(p.getName());
				if(f.isInGuild(p.getName())) {
					p.sendMessage("§cYou are in a guild, use /guild leave instead");
					return true;
				}
				f.forceRemoveMember(p.getName());
				p.sendMessage("§aLeft "+f.getName());
				f.updatePrestige();
				return true;
			} else if(cmd.getName().equalsIgnoreCase(cmd1) && args[0].equalsIgnoreCase("rename") && args.length == 2) {
				Faction f = FactionManager.getByLeader(p.getName());
				if(f == null) {
					p.sendMessage("§a[SimpleFactions]§c Error! You must be the leader of a faction to rename one!");
					return true;
				}
				if(DisplayNameGate.check(p, NameOperation.FACTION_RENAME, args[1])
						== DisplayNameGate.Result.NEEDS_CONFIRM) {
					return true;
				}
				Formatter format = new Formatter();
				f.setName(StringFormatter.formatHex(format.formatName(args[1])));
				FactionManager.getMap().enqueue("nation", f.getRGB());
				p.sendMessage("§aFaction renamed to "+f.getName());
				return true;
			} else if(cmd.getName().equalsIgnoreCase(cmd1) && args[0].equalsIgnoreCase("setrulertitle") && args.length == 2) {
				Faction f = FactionManager.getByLeader(p.getName());
				if(f == null) {
					p.sendMessage("§cYou must be the leader of a faction to change ruler title");
					return true;
				}
				String s = args[1].replace("_", " ");
				f.setRulerTitle(s);
				p.sendMessage("§aFaction ruler title changed to "+f.getRulerTitle());
				return true;
			} else if(cmd.getName().equalsIgnoreCase(cmd1) && args[0].equalsIgnoreCase("setcapital") && (args.length == 1 || args.length == 2)) {
				if(!Cache.requireProvinces(p)) {
					return true;
				}
				Faction f = FactionManager.getByLeader(p.getName());
				if(f == null) {
					p.sendMessage("§cYou must be the leader of a faction to set the capital");
					return true;
				}
				int claim = RestServer.getProvince(p);
				if(claim == -2) {
					p.sendMessage("§a[SimpleFactions] §cError! could not resolve province");
					return true;
				}
				if(claim == 0) {
					p.sendMessage("§cThis location has no province!");
					return true;
				}
				if(args.length < 2 && f.getSettlementHandler().requiresFoundingName(claim)) {
					p.sendMessage("§cName required to found your capital city: §e/faction setcapital <name>");
					return true;
				}
				String name = args.length == 2 ? args[1] : null;
				if(name != null && f.getSettlementHandler().requiresFoundingName(claim)
						&& DisplayNameGate.check(p, NameOperation.SETTLEMENT_FOUND, name)
						== DisplayNameGate.Result.NEEDS_CONFIRM) {
					return true;
				}
				if(!tryClaimForCapital(p, f, claim)) {
					return true;
				}
				CapitalResult result = f.getSettlementHandler().validateFactionCapital(p, claim, name);
				if(!result.isSuccess()) {
					p.sendMessage(result.getMessage());
					return true;
				}
				if(f.hasCapital() && claim == f.getCapital()) {
					p.sendMessage("§aCapital is already set here.");
					return true;
				}
				if(f.hasCapital() && claim != f.getCapital()) {
					List<Integer> lost = f.getProvinceHandler().previewProvincesLostIfCapitalMoved(claim);
					if(!lost.isEmpty()) {
						CapitalMovePrompt.begin(p, f, claim, name, lost.size());
						return true;
					}
				}
				CapitalMovePrompt.applyFactionCapitalMove(p, f, claim, name);
				return true;
			} else if(cmd.getName().equalsIgnoreCase(cmd1) && args[0].equalsIgnoreCase("setrulingsystem") && args.length == 2) {
				Faction f = FactionManager.getByLeader(p.getName());
				if(f == null) {
					p.sendMessage("§cYou must be the leader of a faction to change ruling system!");
					return true;
				}
				String s = args[1].replace("_", " ");
				f.setGovernment(s);
				p.sendMessage("§aFaction ruling system changed to "+f.getGovernmentString());
				return true;
			} else if(cmd.getName().equalsIgnoreCase(cmd1) && args[0].equalsIgnoreCase("setculture") && args.length == 2) {
				Faction f = FactionManager.getByLeader(p.getName());
				if(f == null) {
					p.sendMessage("§cYou must be the leader of a faction to change culture!");
					return true;
				}
				String s = args[1].replace("_", " ");
				f.setCulture(s);
				p.sendMessage("§aFaction culture changed to "+f.getCulture());
				return true;
			} else if(cmd.getName().equalsIgnoreCase(cmd1) && args[0].equalsIgnoreCase("setreligion") && args.length == 2) {
				Faction f = FactionManager.getByLeader(p.getName());
				if(f == null) {
					p.sendMessage("§cYou must be the leader of a faction to change religion!");
					return true;
				}
				String s = args[1].replace("_", " ");
				f.setReligion(s);
				p.sendMessage("§aFaction religion changed to "+f.getReligion());
				return true;
			} else if(cmd.getName().equalsIgnoreCase(cmd1) && args[0].equalsIgnoreCase("setbanner") && args.length == 1) {
				Faction f = FactionManager.getByLeader(p.getName());
				if(f == null) {
					p.sendMessage("§cYou must be the leader of a faction to change the banner!");
					return true;
				}
				ItemStack i = new ItemStack(p.getInventory().getItemInMainHand());
				if(i == null || !i.getType().toString().contains("BANNER")) {
					p.sendMessage("§a[SimpleFactions]§c Error! You must be holding a banner in your main hand!");
					return true;
				}
				i.setAmount(1);
				f.setBanner(i);
				p.sendMessage("§aFaction banner changed!");
				return true;
			} else if(cmd.getName().equalsIgnoreCase(cmd1) && args[0].equalsIgnoreCase("setbank") && args.length == 1) {
				if(FactionManager.getByLeader(p.getName()) != null) {
					Faction f = FactionManager.getByMember(p.getName());
					String bankBlockReason = BankPlacementValidator.failureReasonForFaction(f, p.getLocation());
					if(bankBlockReason != null) {
						p.sendMessage(bankBlockReason);
						return true;
					}
					if(f.getBank() != null) {
						Bank bank = f.getBank();
						bank.setChunk(p.getLocation().getChunk());
						p.sendMessage("§aBank Chunk Moved");
					} else {
						f.setBank(new Bank(f.getOrCreateMainGuild(), 0, p.getLocation().getChunk()));
						p.sendMessage("§aBank Chunk Set");
					}
					p.playSound(p, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 1f, 1f);
				} else {
					p.sendMessage("§cYou need to be a faction leader to place the bank location");
				}
				return true;
			} else if(cmd.getName().equalsIgnoreCase(cmd1) && args[0].equalsIgnoreCase("deposit") && args.length == 2) {
				if(FactionManager.getByMember(p.getName()) != null) {
					Faction f = FactionManager.getByMember(p.getName());
					Bank b = f.getBank();
					if(b == null) {
						p.sendMessage("§cYour faction has no bank chunk");
						return false;
					}
					if(!p.getLocation().getChunk().equals(f.getBank().getChunk())) {
						p.sendMessage("§cYou need to be in the Bank Chunk to deposit money");
						return false;
					}
					double amount = Double.parseDouble(args[1]);
					Account pouch = DenarEconomy.getPlayerManager().get(p).getPouch();
					if(amount <= 0) {
						p.sendMessage("§cAmount must be greater than 0");
						return false;
					}
					if(pouch.getBal() < amount) {
						p.sendMessage("§cNot enough funds");
						return false;
					}
					pouch.change(amount*-1);
					f.getBank().deposit(amount);
					p.sendMessage("§e============§6[Bank Report]§e==============");
					p.sendMessage(StringFormatter.formatHex("#6ab05aDeposited: #b39122"+amount+"#dbaf1dd"));
					p.sendMessage(StringFormatter.formatHex("#3ce8c9New Faction Balance: #b39122"+b.getWealth()+"#dbaf1dd"));
					p.sendMessage(StringFormatter.formatHex("#3ce8c9New Pouch Balance: #b39122"+pouch.getBal()+"#dbaf1dd"));
					p.sendMessage("§e=====================================");
					p.playSound(p, Sound.BLOCK_NOTE_BLOCK_CHIME, 1f, 1f);
				} else {
					p.sendMessage("§cYou need to be a in a faction to deposit money into the faction bank");
				}
				return true;
			} else if(cmd.getName().equalsIgnoreCase(cmd1) && args[0].equalsIgnoreCase("withdraw") && args.length == 2) {
				if(FactionManager.getByLeader(p.getName()) != null) {
					Faction f = FactionManager.getByMember(p.getName());
					Bank b = f.getBank();
					if(b == null) {
						p.sendMessage("§cYour faction has no bank chunk");
						return false;
					}
					if(!p.getLocation().getChunk().equals(b.getChunk())) {
						p.sendMessage("§cYou need to be in the Bank Chunk to withdraw money");
						return false;
					}
					double amount = Double.parseDouble(args[1]);
					Account pouch = DenarEconomy.getPlayerManager().get(p).getPouch();
					if(amount <= 0) {
						p.sendMessage("§cAmount must be greater than 0");
						return false;
					}
					if(b.getWealth() < amount) {
						p.sendMessage("§cNot enough funds in the faction bank");
						return false;
					}
					pouch.change(amount);
					f.getBank().withdraw(amount);
					p.sendMessage("§e============§6[Bank Report]§e==============");
					p.sendMessage(StringFormatter.formatHex("#6ab05aWithdrew: #b39122"+amount+"#dbaf1dd"));
					p.sendMessage(StringFormatter.formatHex("#3ce8c9New Faction Balance: #b39122"+b.getWealth()+"#dbaf1dd"));
					p.sendMessage(StringFormatter.formatHex("#3ce8c9New Pouch Balance: #b39122"+pouch.getBal()+"#dbaf1dd"));
					p.sendMessage("§e=====================================");
					p.playSound(p, Sound.BLOCK_NOTE_BLOCK_CHIME, 1f, 1f);
				} else {
					p.sendMessage("§cYou need to be a faction leader to withdraw from the faction bank");
				}
				return true;
			}
			if(cmd.getName().equalsIgnoreCase(cmd1) && args[0].equalsIgnoreCase("addprestigemodifier") && args.length == 4) {
				if(!Permissions.isAdmin(sender)) {
					p.sendMessage("§a[SimpleFactions]§c You do not have access to this command");
					return true;
				}
				Faction f = FactionManager.getByString(args[1]);
				if(f == null) {
					p.sendMessage("§a[SimpleFactions]§c Error! Faction not found!");
					return true;
				}
				String type = args[2];
				Double amount = Double.parseDouble(args[3]);
				Modifier m = new Modifier(type, amount, true);
				f.addPersistentPrestigeModifier(m);
				f.updatePrestige();
				p.sendMessage("§aFaction prestige changed!");
				return true;
			} /*else if(cmd.getName().equalsIgnoreCase(cmd1) && args[0].equalsIgnoreCase("addwealthmodifier") && args.length == 4) {
				if(!Permissions.isAdmin(sender)) {
					p.sendMessage("§a[SimpleFactions]§c You do not have access to this command");
					return true;
				}
				Faction f = FactionManager.getByString(args[1]);
				if(f == null) {
					p.sendMessage("§a[SimpleFactions]§c Error! Faction not found!");
					return true;
				}
				String type = args[2];
				Double amount = Double.parseDouble(args[3]);
				Modifier m = new Modifier(type, amount);
				f.addPersistentWealthModifier(m);
				f.updateWealth();
				p.sendMessage("§aFaction wealth changed!");
				return true;
			} 
			else if(cmd.getName().equalsIgnoreCase(cmd1) && args[0].equalsIgnoreCase("loadall") && args.length == 1) {
				if(!Permissions.isAdmin(sender)) {
					p.sendMessage("§a[SimpleFactions]§c You do not have access to this command");
					return true;
				}
				Database db = new Database();
				db.loadFactions();
				p.sendMessage("§a[SimpleFactions] §eLoading factions...");
				return true;
			}
			*/
			else if(cmd.getName().equalsIgnoreCase(cmd1) && args[0].equalsIgnoreCase("forcedelete") && args.length == 2) {
				if(!Permissions.isAdmin(sender)) {
					p.sendMessage("§a[SimpleFactions]§c You do not have access to this command");
					return true;
				}
				Faction f = FactionManager.getByString(args[1]);
				if(f == null) {
					p.sendMessage("§a[SimpleFactions]§c Error! faction does not exist!");
					return true;
				}
				FactionDeleteEvent factionDeleteEvent = new FactionDeleteEvent(p, f);
				Bukkit.getPluginManager().callEvent(factionDeleteEvent);
				if(!factionDeleteEvent.isCancelled()) {
					FactionManager.deleteFaction(f);
					if(f.getBank() != null) {
						BankManager.banks.remove(f.getBank());
					}
					p.sendMessage("§aFaction "+f.getName()+" §adeleted!");
				}
				return true;
			} else if(cmd.getName().equalsIgnoreCase(cmd1) && args[0].equalsIgnoreCase("forceconstruct")) {
				if(!Permissions.isAdmin(sender)) {
					p.sendMessage("§a[SimpleFactions]§c You do not have access to this command");
					return true;
				}
				if(!Cache.requireProvinces(p)) {
					return true;
				}
				if(args.length < 2) {
					p.sendMessage("§cUsage: §e/faction forceconstruct <faction> <fort|port|airport> <name>");
					return true;
				}
				Faction f = FactionManager.getByString(args[1]);
				if(f == null) {
					p.sendMessage("§a[SimpleFactions]§c Error! faction does not exist!");
					return true;
				}
				if(args.length < 3) {
					p.sendMessage("§cUsage: §e/faction forceconstruct <faction> <fort|port|airport> <name>");
					return true;
				}
				InstallationKind kind = InstallationKind.fromCommand(args[2]);
				if(kind == null) {
					p.sendMessage("§cUnknown installation type. Use: §efort§7, §eport§7, or §eairport");
					return true;
				}
				if(args.length < 4) {
					p.sendMessage("§cName required: §e/faction forceconstruct " + args[1] + " " + kind.getCommandName() + " <name>");
					return true;
				}
				int province = RestServer.getProvince(p);
				if(province == -2) {
					p.sendMessage("§a[SimpleFactions] §cError! could not resolve province");
					return true;
				}
				if(province == 0) {
					p.sendMessage("§cThis location has no province!");
					return true;
				}
				String name = String.join(" ", Arrays.copyOfRange(args, 3, args.length));
				ConstructResult result = f.getInstallationHandler().constructInstant(
						kind,
						name,
						province,
						p.getLocation().getBlockX(),
						p.getLocation().getBlockZ());
				p.sendMessage(result.getMessage());
				if(result.isSuccess()) {
					p.playSound(p, Sound.BLOCK_ANVIL_USE, 1f, 1f);
				}
				return true;
			} else if(cmd.getName().equalsIgnoreCase(cmd1) && args[0].equalsIgnoreCase("forceregiment")) {
				if(!Permissions.isAdmin(sender)) {
					p.sendMessage("§a[SimpleFactions]§c You do not have access to this command");
					return true;
				}
				if(args.length < 4) {
					p.sendMessage("§cUsage: §e/faction forceregiment <faction> give|take <regiment> [amount]");
					return true;
				}
				Faction f = FactionManager.getByString(args[1]);
				if(f == null) {
					p.sendMessage("§a[SimpleFactions]§c Error! faction does not exist!");
					return true;
				}
				boolean give;
				if(args[2].equalsIgnoreCase("give")) {
					give = true;
				} else if(args[2].equalsIgnoreCase("take")) {
					give = false;
				} else {
					p.sendMessage("§cUsage: §e/faction forceregiment <faction> give|take <regiment> [amount]");
					return true;
				}
				String regimentId = args[3];
				int amount = 1;
				if(args.length >= 5) {
					try {
						amount = Integer.parseInt(args[4]);
					} catch (NumberFormatException e) {
						p.sendMessage("§cAmount must be a positive whole number.");
						return true;
					}
					if(amount <= 0) {
						p.sendMessage("§cAmount must be a positive whole number.");
						return true;
					}
				}
				int delta = give ? amount : -amount;
				ExpandResult result = f.getMilitary().adminAdjustSlots(regimentId, delta);
				if(!result.allowed()) {
					p.sendMessage("§c" + result.reason());
					return true;
				}
				Regiment regiment = f.getMilitary().getRegiment(regimentId);
				String regimentName = regiment != null ? regiment.getName() : regimentId;
				String slotWord = amount == 1 ? "slot" : "slots";
				if(give) {
					p.sendMessage("§aAdded " + amount + " " + slotWord + " to " + regimentName);
				} else {
					p.sendMessage("§aRemoved " + amount + " " + slotWord + " from " + regimentName);
				}
				return true;
			} else if(cmd.getName().equalsIgnoreCase(cmd1) && args[0].equalsIgnoreCase("forceleader") && args.length == 3) {
				if(!Permissions.isAdmin(sender)) {
					p.sendMessage("§a[SimpleFactions]§c You do not have access to this command");
					return true;
				}
				Faction f = FactionManager.getByString(args[1]);
				if(f == null) {
					p.sendMessage("§a[SimpleFactions]§c Error! faction does not exist!");
					return true;
				}
				if(!f.getMembers().contains(args[2])) {
					p.sendMessage("§cPlayer is not in the faction");
					return true;
				}
				if(args[2].equalsIgnoreCase(f.getLeader())) {
					p.sendMessage("§cPlayer is already the leader");
					return true;
				}
				if(!f.canBecomeLeader(args[2])) {
					p.sendMessage("§cPlayer is not eligble to be leader (perhaps they are a guild leader?)");
					return true;
				}
				f.setLeader(args[2]);
				for(Player pl : Bukkit.getOnlinePlayers()) {
					if(f.getMembers().contains(pl.getName())) {
						pl.sendMessage("§a"+args[2]+ " is the new faction leader!");
					}
				}
				return true;
			} else if(cmd.getName().equalsIgnoreCase(cmd1) && args[0].equalsIgnoreCase("forcejoin") && args.length == 3) {
				if(!Permissions.isAdmin(sender)) {
					p.sendMessage("§a[SimpleFactions]§c You do not have access to this command");
					return true;
				}
				Faction f = FactionManager.getByString(args[1]);
				if(f == null) {
					p.sendMessage("§a[SimpleFactions]§c Error! faction does not exist!");
					return true;
				}
				if(f.getMembers().contains(args[2])) {
					p.sendMessage("§cPlayer is already in the faction");
					return true;
				}
				if(FactionManager.getByMember(args[2]) != null) {
					p.sendMessage("§cPlayer is already in a faction");
					return true;
				}
				f.addMember(args[2]);;
				for(Player pl : Bukkit.getOnlinePlayers()) {
					if(f.getMembers().contains(pl.getName())) {
						pl.sendMessage("§a"+args[2]+ " joined the faction!");
					}
				}
				return true;
			} else if(cmd.getName().equalsIgnoreCase(cmd1) && args[0].equalsIgnoreCase("forcewithdraw") && args.length == 3) {
				if(!Permissions.isAdmin(sender)) {
					p.sendMessage("§a[SimpleFactions]§c You do not have access to this command");
					return true;
				}
				Faction f = FactionManager.getByString(args[1]);
				if(f == null) {
					p.sendMessage("§a[SimpleFactions]§c Error! faction does not exist!");
					return true;
				}
				double amount = 0.0;
				try {
					amount = Double.parseDouble(args[2]);
				} catch (Exception e) {
					p.sendMessage("Error reading amount, setting it to 0");
				}
				if(amount <= 0) {
					p.sendMessage("§cAmount must be greater than 0");
					return false;
				}

				if(f.getBank().getWealth() < amount) {
					p.sendMessage("§cBank does not have enough wealth");
					return true;
				}
				f.getBank().withdraw(amount);
				List<ItemStack> items = DenarEconomy.getMoneyManager().amountToItems(amount);
				for(ItemStack i : items) {
					p.getInventory().addItem(i);
				}
				p.sendMessage("§6"+amount+" §cwas withdrawn from the bank of "+f.getName());
				for(String s : f.getMembers()) {
					Player member = Bukkit.getPlayer(s);
					if(member != null && member.isOnline()) {
						member.sendMessage("§6"+amount+" §cwas withdrawn from the faction by admins, §cmake a ticket if you believe this was a mistake");
					}
				}
				return true;
			} else if(cmd.getName().equalsIgnoreCase(cmd1) && args[0].equalsIgnoreCase("refresh") && args.length == 1) {
				if(!Permissions.isAdmin(sender)) {
					p.sendMessage("§a[SimpleFactions]§c You do not have access to this command");
					return true;
				}
				for(Faction f : FactionManager.factions) {
					if(!f.getMembers().contains(f.getLeader())) {
						f.getMembers().add(f.getLeader());
					}
				}
				return true;
			} else if(cmd.getName().equalsIgnoreCase(cmd1) && args[0].equalsIgnoreCase("delbank") && args.length == 2) {
				if(!Permissions.isAdmin(sender)) {
					p.sendMessage("§a[SimpleFactions]§c You do not have access to this command");
					return true;
				}
				Faction f = FactionManager.getByString(args[1]);
				if(f == null) {
					p.sendMessage("§cFaction does not exist!");
					return true;
				}
				BankManager.banks.remove(f.getBank());
				f.setBank(null);
				p.sendMessage("§eBank removed");
				return true;
			} else if(cmd.getName().equalsIgnoreCase(cmd1) && args[0].equalsIgnoreCase("startelection") && args.length == 2) {
				if(!Permissions.isAdmin(sender)) {
					p.sendMessage("§a[SimpleFactions]§c You do not have access to this command");
					return true;
				}
				Faction f = FactionManager.getByString(args[1]);
				if(f == null) {
					p.sendMessage("§cFaction does not exist!");
					return true;
				}
				if(!f.getGovernment().hasElections()) {
					p.sendMessage("§cThis faction does not have elections enabled!");
					return true;
				}
				if(f.getGovernment().getElection().isActive()) {
					p.sendMessage("§cElection is already active!");
					return true;
				}
				f.getGovernment().getElection().start();
				p.sendMessage("§aElection started for " + f.getName() + "!");
				for(Player pl : Bukkit.getOnlinePlayers()) {
					if(f.getMembers().contains(pl.getName())) {
						pl.sendMessage("§a§lElection Started! §7Vote at voting booths!");
						pl.playSound(pl.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
					}
				}
				return true;
			} else if(cmd.getName().equalsIgnoreCase(cmd1) && args[0].equalsIgnoreCase("endelection") && args.length == 2) {
				if(!Permissions.isAdmin(sender)) {
					p.sendMessage("§a[SimpleFactions]§c You do not have access to this command");
					return true;
				}
				Faction f = FactionManager.getByString(args[1]);
				if(f == null) {
					p.sendMessage("§cFaction does not exist!");
					return true;
				}
				if(!f.getGovernment().getElection().isActive()) {
					p.sendMessage("§cNo active election for this faction!");
					return true;
				}
				f.getGovernment().getElection().end();
				p.sendMessage("§aElection ended for " + f.getName() + "!");
				for(Player pl : Bukkit.getOnlinePlayers()) {
					if(f.getMembers().contains(pl.getName())) {
						pl.sendMessage("§e§lElection Ended! §7Results have been applied!");
						pl.playSound(pl.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.5f);
					}
				}
				return true;
			} else if(cmd.getName().equalsIgnoreCase(cmd1) && args[0].equalsIgnoreCase("getglobalwealth") && args.length == 1) {
				if(!Permissions.isAdmin(sender)) {
					p.sendMessage("§a[SimpleFactions]§c You do not have access to this command");
					return true;
				}
				double globalWealth = format.formatDouble(FactionManager.getGlobalWealth()+FactionManager.getPouchWealth()+FactionManager.getBankWealth());
				p.sendMessage("§f======================================");
				p.sendMessage("§eGlobal Wealth: §6"+globalWealth+"d");
				p.sendMessage("§aTaken up by Nodes: §6"+FactionManager.getGlobalNodeWealth()+"d");
				p.sendMessage("§aLiquid Faction Capital: §6"+FactionManager.getGlobalLiquidWealth()+"d");
				p.sendMessage("§aLiquid Guild Capital: §6"+FactionManager.getGuildLiquidWealth()+"d");
				p.sendMessage("§aGuild Expansions: §6"+FactionManager.getGlobalGuildExpansions()+"d");
				p.sendMessage("§aPersonal Pouches: §6"+FactionManager.getPouchWealth()+"d");
				p.sendMessage("§aPersonal Banks: §6"+FactionManager.getBankWealth()+"d");
				p.sendMessage("§aNode Percentage: §f"+Math.round((FactionManager.getGlobalNodeWealth()/globalWealth)*100)+"% §aof global wealth");
				p.sendMessage("§bDaily Total Guild Income: §6"+FactionManager.getTotalGuildIncome()+"d§7/day");
				p.sendMessage("§f======================================");
				return true;
			} else if(cmd.getName().equalsIgnoreCase(cmd1) && args[0].equalsIgnoreCase("queueallnations") && args.length == 1) {
				if(!Permissions.isAdmin(sender)) {
					p.sendMessage("§a[SimpleFactions]§c You do not have access to this command");
					return true;
				}
				if(!Cache.requireProvinces(p)) {
					return true;
				}
				FactionManager.getMap().queueAllNations();
				p.sendMessage("§eQueued all nations and asked for regen");
				return true;
			} else if(cmd.getName().equalsIgnoreCase(cmd1) && args[0].equalsIgnoreCase("fullregen") && args.length == 2) {
				if(!Permissions.isAdmin(sender)) {
					p.sendMessage("§a[SimpleFactions]§c You do not have access to this command");
					return true;
				}
				if(!Cache.requireProvinces(p)) {
					return true;
				}
				if(!args[1].equalsIgnoreCase("i_love_tfmc")) {
					p.sendMessage("§a[SimpleFactions]§c Authentication Failed, incorrect passcode");
					return true;
				}
				FactionManager.getMap().fullRegen();
				p.sendMessage("§eFull regen started, this might take some time...");
				return true;
			} else if(cmd.getName().equalsIgnoreCase(cmd1) && args[0].equalsIgnoreCase("reloadtitles") && args.length == 1) {
				if(!Permissions.isAdmin(sender)) {
					p.sendMessage("§a[SimpleFactions]§c You do not have access to this command");
					return true;
				}
				if(!Cache.requireProvinces(p)) {
					return true;
				}
				SimpleFactions.reloadTitles();
				p.sendMessage("§eReloaded titles!");
				return true;
			} else if(cmd.getName().equalsIgnoreCase(cmd1) && args[0].equalsIgnoreCase("reloadconfigs") && args.length == 1) {
				if(!Permissions.isAdmin(sender)) {
					p.sendMessage("§a[SimpleFactions]§c You do not have access to this command");
					return true;
				}
				SimpleFactions.reloadConfigs();
				p.sendMessage("§eReloaded configs!");
				return true;
			} else if(cmd.getName().equalsIgnoreCase(cmd1) && args[0].equalsIgnoreCase("destroytitle") && args.length == 2) {
				if(!Permissions.isAdmin(sender)) {
					p.sendMessage("§a[SimpleFactions]§c You do not have access to this command");
					return true;
				}
				if(!Cache.requireProvinces(p)) {
					return true;
				}
				Title title = TitleLoader.getById(args[1]);
				if(title == null){
					p.sendMessage("§cNo title by that id");
					return false;
				}
				Faction owner = TitleManager.getOwner(title);
				if(owner == null){
					p.sendMessage("§cNo faction owns that title");
					return false;
				}
				owner.removeTitle(title);
				p.sendMessage("§aDestroyed title "+title.getName()+" §7("+title.getId()+")");
				return true;
			} else if(cmd.getName().equalsIgnoreCase(cmd1) && args[0].equalsIgnoreCase("granttitle") && args.length == 3) {
				if(!Permissions.isAdmin(sender)) {
					p.sendMessage("§a[SimpleFactions]§c You do not have access to this command");
					return true;
				}
				if(!Cache.requireProvinces(p)) {
					return true;
				}
				Faction reciever = FactionManager.getByString(args[1]);
				if(reciever == null) {
					p.sendMessage("§cNo faction by that id");
					return false;
				}
				Title title = TitleLoader.getById(args[2]);
				if(title == null){
					p.sendMessage("§cNo title by that id");
					return false;
				}
				if(TitleManager.getOwner(title) != null){
					p.sendMessage("§cA faction already owns that title, use usurp instead!");
					return false;
				}
				reciever.addTitle(title);
				p.sendMessage("§aGave "+reciever.getName()+" §athe title "+title.getName()+" §7("+title.getId()+")");
				return true;
			} else if(cmd.getName().equalsIgnoreCase(cmd1) && args[0].equalsIgnoreCase("transfersubject") && args.length == 3) {
				if(!Permissions.isAdmin(sender)) {
					p.sendMessage("§a[SimpleFactions]§c You do not have access to this command");
					return true;
				}
				Faction subject = FactionManager.getByString(args[1]);
				if(subject == null) {
					p.sendMessage("§cNo faction by the id "+args[1]);
					return false;
				}
				Faction recieving = FactionManager.getByString(args[2]);
				if(recieving == null) {
					p.sendMessage("§cNo faction by the id "+args[2]);
					return false;
				}
				String overlord = RelationManager.getOverlord(subject);
				if(overlord == null){
					p.sendMessage(subject.getName()+" §cis not a subject");
					return false;
				}
				if(recieving.getId().equalsIgnoreCase(overlord)){
					p.sendMessage(subject.getName()+" §cis already a subject of "+recieving.getName());
					return false;
				}
				if(RelationManager.isOnOverlordPath(recieving, subject)) {
					p.sendMessage("§cThis transfer would cause a loop");
					return false;
				}
				if(!recieving.canHaveVassals()) {
					p.sendMessage("§cYour faction cannot have vassals!");
					return false;
				}
				RelationManager.transferSubject(subject, recieving);
				p.sendMessage("§aTransfered subject");
				return true;
			} else if(cmd.getName().equalsIgnoreCase(cmd1) && args[0].equalsIgnoreCase("setrelation") && args.length == 4) {
				if(!Permissions.isAdmin(sender)) {
					p.sendMessage("§a[SimpleFactions]§c You do not have access to this command");
					return true;
				}
				Faction sending = FactionManager.getByString(args[1]);
				if(sending == null) {
					p.sendMessage("§cNo faction by the id "+args[1]);
					return false;
				}
				Faction recieving = FactionManager.getByString(args[2]);
				if(recieving == null) {
					p.sendMessage("§cNo faction by the id "+args[2]);
					return false;
				}
				RelationType type = RelationLoader.getType(args[3]);
				if(type == null) {
					p.sendMessage("§cNo relation with the id "+args[3]);
					return false;
				}
				if(type.isTradeAgreement() || type.isTreaty()) {
					p.sendMessage("§cThat is a treaty. Use /faction settreaty");
					return false;
				}
				RelationManager.setRelation(p, type, recieving, sending, false);
				return true;
			} else if(cmd.getName().equalsIgnoreCase(cmd1) && args[0].equalsIgnoreCase("settreaty") && args.length == 4) {
				if(!Permissions.isAdmin(sender)) {
					p.sendMessage("§a[SimpleFactions]§c You do not have access to this command");
					return true;
				}
				Faction sending = FactionManager.getByString(args[1]);
				if(sending == null) {
					p.sendMessage("§cNo faction by the id "+args[1]);
					return false;
				}
				Faction recieving = FactionManager.getByString(args[2]);
				if(recieving == null) {
					p.sendMessage("§cNo faction by the id "+args[2]);
					return false;
				}
				RelationType type = RelationLoader.getType(args[3]);
				if(type == null) {
					p.sendMessage("§cNo treaty with the id "+args[3]+". Use /faction setrelation for diplomatic relations");
					return false;
				}
				if(type.isTreaty()) {
					RelationManager.setTreatyRelationForced(type, recieving, sending);
					p.sendMessage(StringFormatter.formatHex("#a89977Set treaty to "+type.getName()));
					return true;
				}
				if(!type.isTradeAgreement()) {
					p.sendMessage("§cNo treaty with the id "+args[3]+". Use /faction setrelation for diplomatic relations");
					return false;
				}
				RelationManager.setTradeRelationForced(type, recieving, sending);
				p.sendMessage(StringFormatter.formatHex("#a89977Set treaty to "+type.getName()));
				return true;
			} else if(cmd.getName().equalsIgnoreCase(cmd1) && args[0].equalsIgnoreCase("setpower") && args.length == 3) {
				if(!Permissions.isAdmin(sender)) {
					p.sendMessage("§a[SimpleFactions]§c You do not have access to this command");
					return true;
				}
				Faction faction = FactionManager.getByString(args[1]);
				if(faction == null) {
					p.sendMessage("§cNo faction by the id "+args[1]);
					return false;
				}
				double amount;
				try {
					amount = Double.parseDouble(args[2]);
				} catch (NumberFormatException exception) {
					p.sendMessage("§cAmount must be a number");
					return false;
				}
				faction.getGovernment().setPower(amount);
				p.sendMessage("§aSet admin power of "+faction.getName()+" §ato "+faction.getGovernment().getPower());
				return true;
			} else if(cmd.getName().equalsIgnoreCase(cmd1) && args[0].equalsIgnoreCase("setlaw") && args.length == 4) {
				if(!Permissions.isAdmin(sender)) {
					p.sendMessage("§a[SimpleFactions]§c You do not have access to this command");
					return true;
				}
				Faction faction = FactionManager.getByString(args[1]);
				if(faction == null) {
					p.sendMessage("§cNo faction by the id "+args[1]);
					return false;
				}
				LawGroup template = LawLoader.getByString(args[2]);
				if(template == null) {
					p.sendMessage("§cNo law group with the id "+args[2]);
					return false;
				}
				Law law = template.getLaw(args[3]);
				if(law == null) {
					p.sendMessage("§cNo law "+args[3]+" in group "+args[2]);
					return false;
				}
				LawGroup factionGroup = faction.getLawHandler().getGroup(template.getId());
				if(factionGroup == null) {
					p.sendMessage("§cThat faction has no group "+args[2]);
					return false;
				}
				faction.applyLaw(law, factionGroup);
				p.sendMessage("§aSet "+faction.getName()+" §alaw "+template.getId()+" to "+law.getId());
				return true;
			} else if(cmd.getName().equalsIgnoreCase(cmd1) && args[0].equalsIgnoreCase("setstance") && args.length == 3) {
				if(!Permissions.isAdmin(sender)) {
					p.sendMessage("§a[SimpleFactions]§c You do not have access to this command");
					return true;
				}
				Guild guild = FactionManager.getGuildByString(args[1]);
				if(guild == null) {
					Faction faction = FactionManager.getByString(args[1]);
					if(faction != null) {
						guild = faction.getOrCreateMainGuild();
					}
				}
				if(guild == null) {
					p.sendMessage("§cNo guild or faction by the id "+args[1]);
					return false;
				}
				Stance stance;
				try {
					stance = Stance.valueOf(args[2].toUpperCase());
				} catch(IllegalArgumentException e) {
					p.sendMessage("§cStance must be oppose, neutral, or support");
					return false;
				}
				guild.setStance(stance);
				new Database().saveFaction(guild.getFaction());
				p.sendMessage("§aSet stance of "+guild.getId()+" to "+stance.name().toLowerCase());
				return true;
			} else if(cmd.getName().equalsIgnoreCase(cmd1) && args[0].equalsIgnoreCase("usurp") && args.length == 3) {
				if(!Permissions.isAdmin(sender)) {
					p.sendMessage("§a[SimpleFactions]§c You do not have access to this command");
					return true;
				}
				if(!Cache.requireProvinces(p)) {
					return true;
				}
				Faction usurping = FactionManager.getByString(args[1]);
				if(usurping == null) {
					p.sendMessage("§cNo faction by the id "+args[1]);
					return false;
				}
				Faction losing = FactionManager.getByString(args[2]);
				if(losing == null) {
					p.sendMessage("§cNo faction by the id "+args[2]);
					return false;
				}
				Title t = FactionManager.usurp(p, usurping, losing);
				if(t != null) p.sendMessage(usurping.getName()+" §ausurped "+t.getName());;
				return true;
			} else if(cmd.getName().equalsIgnoreCase(cmd1) && args[0].equalsIgnoreCase("provincecap") && args.length == 1) {
				if(!Permissions.isAdmin(sender)) {
					p.sendMessage("§a[SimpleFactions]§c You do not have access to this command");
					return true;
				}
				if(!Cache.requireProvinces(p)) {
					return true;
				}
				for(Faction f : FactionManager.factions) {
					f.provinceCap();
				}
				return true;
			}
			p.sendMessage("§a[SimpleFactions]§c Error with command format, use the gameplay guide for a list of commands");
		}
		return false;
	}

	private boolean tryClaimForCapital(Player p, Faction f, int claim) {
		Faction owner = resolveProvinceOwner(claim);
		if(owner != null && !owner.getId().equalsIgnoreCase(f.getId())) {
			p.sendMessage("§cThis province is already owned by another faction!");
			return false;
		}
		if(!f.getProvinces().isEmpty() && !f.ownsProvince(claim)) {
			p.sendMessage("§cYour faction doesn't own this province!");
			return false;
		}
		if(f.getProvinces().isEmpty() && !f.ownsProvince(claim)) {
			FactionManager.getMap().claimForCapital(p, f, claim, true);
		}
		return f.ownsProvince(claim);
	}

	private static Faction resolveProvinceOwner(int provinceId) {
		return FactionManager.getByProvince(provinceId);
	}

}
