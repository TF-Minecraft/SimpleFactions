package me.Plugins.SimpleFactions.War.battle.ui;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.time.Instant;

import me.Plugins.SimpleFactions.Managers.FactionManager;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.Cache;
import me.Plugins.SimpleFactions.War.battle.campaign.warband.BattleWarbandRetreatService.Messages;
import me.Plugins.SimpleFactions.War.battle.campaign.warband.BattleWarbandRetreatService;
import me.Plugins.SimpleFactions.War.battle.campaign.warband.BattleWarbandRetreatService.RetreatResult;
import me.Plugins.SimpleFactions.War.battle.campaign.CampaignBattleJoinService;
import me.Plugins.SimpleFactions.War.battle.campaign.warband.CampaignWarbandBattleService;
import me.Plugins.SimpleFactions.War.battle.campaign.warband.CampaignWarbandSignupService;
import me.Plugins.SimpleFactions.War.core.WarDevMode;
import me.Plugins.SimpleFactions.War.battle.engine.core.Battle;
import me.Plugins.SimpleFactions.War.battle.engine.capture.BattleCapturePoints;
import me.Plugins.SimpleFactions.War.battle.engine.core.BattleSideSetupService;
import me.Plugins.SimpleFactions.War.battle.engine.core.BattleContestSetup;
import me.Plugins.SimpleFactions.War.battle.engine.core.BattleFactory;
import me.Plugins.SimpleFactions.War.battle.engine.core.BattleJoinService;
import me.Plugins.SimpleFactions.War.battle.engine.core.BattleManager;
import me.Plugins.SimpleFactions.War.battle.engine.core.BattlePlacementValidator;
import me.Plugins.SimpleFactions.War.battle.engine.raid.BattleRaidSetup;
import me.Plugins.SimpleFactions.War.battle.engine.core.BattleSide;
import me.Plugins.SimpleFactions.War.battle.persistence.BattlePersistenceService;
import me.Plugins.SimpleFactions.War.battle.engine.capture.CapturePoint;
import me.Plugins.SimpleFactions.War.battle.enums.BattleType;
import me.Plugins.SimpleFactions.War.battle.warband.Warband;
import me.Plugins.SimpleFactions.War.battle.warband.WarbandManager;
import me.Plugins.SimpleFactions.War.battle.ui.BattlePermissions;

public class BattleCommandManager implements CommandExecutor{
	public String cmd1 = "warband";
	public String cmd2 = "battle";
	
	@Override
	public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
		if(sender instanceof Player) {
			Player p = (Player) sender;
			if(cmd.getName().equalsIgnoreCase(cmd1) && args.length < 1) {
				p.sendMessage("§a[Battle]§c Error with command format, use the gameplay guide for a list of commands");
				return true;
			}
			if(cmd.getName().equalsIgnoreCase(cmd2) && args[0].equalsIgnoreCase("list") && args.length == 1) {
				Warband w = WarbandManager.getByPlayer(p);
				if(w == null) {
					p.sendMessage("§cYou need to lead a warband to view battles");
					return true;
				}
				if (!p.getUniqueId().equals(w.getLeaderId())) {
					p.sendMessage("§cOnly the leader can view battles!");
					return true;
				}
				BattleInventoryManager inv = new BattleInventoryManager();
				inv.battleList(p);
				return true;
			}
			if(cmd.getName().equalsIgnoreCase(cmd2) && args[0].equalsIgnoreCase("join") && args.length == 3) {
				Battle b = BattleManager.getByString(args[1]);
				if(b == null) {
					p.sendMessage("§cThere is no battle with this id");
					return true;
				}
				String joinError = BattleJoinService.join(p, b, args[2]);
				if (joinError != null) {
					p.sendMessage("§c" + joinError);
					return true;
				}
				p.sendMessage("§aJoined "+args[2]+" in the battle "+b.getId());
				return true;
			}
			if(cmd.getName().equalsIgnoreCase(cmd2) && !BattlePermissions.isAdmin(sender)) {
				p.sendMessage("§a[Battle]§4You do not have access to this command");
				return true;
			}
			if(cmd.getName().equalsIgnoreCase(cmd1) && args[0].equalsIgnoreCase("create") && args.length == 2) {
				if(WarbandManager.getByPlayer(p) != null) {
					p.sendMessage("§cYou already have a warband!");
					return true;
				}
				Warband w = new Warband(args[1], p);
				WarDevMode.seedDummyMembersIfEnabled(w);
				WarbandManager.addWarband(w);
				BattlePersistenceService.persistWarband(w);
				p.sendMessage("§aWarband "+w.getId()+" §acreated!");
				return true;
			} else if(cmd.getName().equalsIgnoreCase(cmd1) && args[0].equalsIgnoreCase("delete") && args.length == 2) {
				Warband w = WarbandManager.getByString(args[1]);
				if(w == null) {
					p.sendMessage("§a[Battle]§c Error! Warband does not exist!");
					return true;
				}
				if(!BattlePermissions.isAdmin(sender)) {
					if (w.isFaction()) {
						p.sendMessage("§cCampaign warbands cannot be deleted");
						return true;
					}
					if (!w.hasMember(p)) {
						p.sendMessage("§cCannot delete a warband you are not part of!");
						return true;
					}
					if (!p.getUniqueId().equals(w.getLeaderId())) {
						p.sendMessage("§cOnly the warband leader can delete the warband!");
						return true;
					}
				}
				BattlePersistenceService.deleteWarband(w);
				p.sendMessage("§aWarband "+w.getId()+" §adeleted!");
			} else if(cmd.getName().equalsIgnoreCase(cmd1) && args[0].equalsIgnoreCase("toggleopen") && args.length == 1) {
				Warband w = WarbandManager.getByLeader(p);
				if(w == null) {
					p.sendMessage("§cYou need to lead a warband to open/close it");
					return true;
				}
				w.setLocked(!w.isLocked());
				if(w.isLocked()) {
					p.sendMessage("§cWarband is now invite only");
				} else {
					p.sendMessage("§aWarband is now open");
				}
				BattlePersistenceService.persistWarband(w);
			} else if(cmd.getName().equalsIgnoreCase(cmd1) && args[0].equalsIgnoreCase("list") && args.length == 1) {
				BattleInventoryManager inv = new BattleInventoryManager();
				inv.warbandList(p);
				return true;
			} else if(cmd.getName().equalsIgnoreCase(cmd1) && args[0].equalsIgnoreCase("kick") && args.length == 2) {
				Warband w = WarbandManager.getByPlayer(p);
				if(w == null) {
					p.sendMessage("§cYou need to have a warband to kick someone");
					return true;
				}
				if (!p.getUniqueId().equals(w.getLeaderId())) {
					p.sendMessage("§cOnly the leader can kick players!");
					return true;
				}
				Player kickTarget = Bukkit.getPlayerExact(args[1]);
				if (kickTarget != null && kickTarget.getUniqueId().equals(w.getLeaderId())) {
					p.sendMessage("§cCant kick the leader!");
					return true;
				}
				if (kickTarget == null || !w.hasMember(kickTarget)) {
					p.sendMessage("§cPlayer is not a member");
					return true;
				}
				w.removePlayer(kickTarget);;
				BattlePersistenceService.persistWarband(w);
				p.sendMessage("§aKicked "+args[1]);
				for(Player pl : Bukkit.getOnlinePlayers()) {
					if(pl.getName().equalsIgnoreCase(args[1])) {
						pl.sendMessage("§a"+p.getName()+ " kicked you from "+w.getId());
					}
				}
				return true;
			} else if(cmd.getName().equalsIgnoreCase(cmd1) && args[0].equalsIgnoreCase("setleader") && args.length == 2) {
				Warband w = WarbandManager.getByPlayer(p);
				if(w == null) {
					p.sendMessage("§cYou need to have a warband to change leader");
					return true;
				}
				if (!p.getUniqueId().equals(w.getLeaderId())) {
					p.sendMessage("§cOnly the leader can set a new leader!");
					return true;
				}
				Player newLeader = Bukkit.getPlayerExact(args[1]);
				if (newLeader == null || !w.hasMember(newLeader)) {
					p.sendMessage("§cPlayer is not in the warband");
					return true;
				}
				if (newLeader.getUniqueId().equals(w.getLeaderId())) {
					p.sendMessage("§cPlayer is already the leader");
					return true;
				}
				w.setLeader(newLeader);
				BattlePersistenceService.persistWarband(w);
				for(Player pl : Bukkit.getOnlinePlayers()) {
					if (w.hasMember(pl)) {
						pl.sendMessage("§a"+args[1]+ " is the new warband leader!");
					}
				}
				return true;
			} else if(cmd.getName().equalsIgnoreCase(cmd1) && args[0].equalsIgnoreCase("invite") && args.length == 2) {
				Warband w = WarbandManager.getByPlayer(p);
				if(w == null) {
					p.sendMessage("§cYou need to have a warband to invite someone");
					return true;
				}
				if (!p.getUniqueId().equals(w.getLeaderId())) {
					p.sendMessage("§cOnly the leader can invite players!");
					return true;
				}
				if (w.hasMember(Bukkit.getPlayerExact(args[1]))) {
					p.sendMessage("§cPlayer is already a member");
					return true;
				}
				w.invite(Bukkit.getPlayerExact(args[1]));
				BattlePersistenceService.persistWarband(w);
				p.sendMessage("§aInvited "+args[1]);
				for(Player pl : Bukkit.getOnlinePlayers()) {
					if(pl.getName().equalsIgnoreCase(args[1])) {
						pl.sendMessage("§a"+p.getName()+ " invited you to "+w.getId());
						pl.sendMessage("§aType /warband join "+w.getId() +"§a to join");
					}
				}
				return true;
			} else if(cmd.getName().equalsIgnoreCase(cmd1) && args[0].equalsIgnoreCase("join") && args.length == 2) {
				if(WarbandManager.getByPlayer(p) != null) {
					p.sendMessage("§cAlready in a warband, leave your current warband first!");
					return true;
				}
				Warband w = WarbandManager.getByString(args[1]);
				if (w == null) {
					p.sendMessage("§cWarband not found");
					return true;
				}
				if (w.isLocked() && !w.isFaction()) {
					if (!w.isInvited(p)) {
						p.sendMessage("§cYou need to be invited to this warband by the leader first!");
						return true;
					}
					w.uninvite(p);
				}
				Faction playerFaction = FactionManager.getByMember(p.getName());
				if (w.isFaction()) {
					if (playerFaction == null) {
						p.sendMessage("§cThis is a faction warband, you need a faction to join");
						return true;
					}
				}
				String signupError = CampaignWarbandSignupService.signup(p, w, playerFaction);
				if (signupError != null) {
					p.sendMessage("§c" + signupError);
					return true;
				}
				p.sendMessage("§aJoined "+w.getId());
				return true;
			} else if(cmd.getName().equalsIgnoreCase(cmd1) && args[0].equalsIgnoreCase("leave") && args.length == 1) {
				Warband w = WarbandManager.getByPlayer(p);
				if(w == null) {
					p.sendMessage("§cYou are not in a warband");
					return true;
				}
				if(WarbandManager.getByLeader(p) != null && !w.isFaction()) {
					p.sendMessage("§cCant leave if you are the leader");
					p.sendMessage("§cUse /warband delete first");
					return true;
				}
				CampaignWarbandBattleService.processLeave(p, w, true);
				p.sendMessage("§aLeft "+w.getId());
				return true;
			} else if (cmd.getName().equalsIgnoreCase(cmd1) && args[0].equalsIgnoreCase("retreat") && args.length == 1) {
				Instant now = Instant.now();
				RetreatResult rejection = BattleWarbandRetreatService.retreatRejection(p, now);
				if (rejection != null) {
					String message = Messages.messageForResult(rejection, p, now);
					if (message != null) {
						p.sendMessage(message);
					}
					return true;
				}
				Warband warband = WarbandManager.getByLeader(p);
				CampaignBattleJoinService.CampaignBattleContext ctx =
						CampaignBattleJoinService.findCampaignBattleForWarband(warband);
				Faction faction = FactionManager.getByMember(p.getName());
				FactionManager.inv.confirming.put(p, faction);
				FactionManager.inv.confirmBattleRetreatView(p, ctx.battle().getId());
				p.playSound(p, Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
				return true;
			}
			if(cmd.getName().equalsIgnoreCase(cmd2) && args[0].equalsIgnoreCase("create") && args.length == 3) {
				if (BattleManager.hasManualBattle()) {
					p.sendMessage("§cOnly one manual battle allowed. Delete the existing one first.");
					return true;
				}
				if(BattleManager.getByString(args[2]) != null) {
					p.sendMessage("§cThere already exists a battle with this id");
					return true;
				}
				BattleType type = BattleType.fromJson(args[1]);
				if (type == null) {
					p.sendMessage("§cInvalid battle type. Use field, siege, or raid.");
					return true;
				}
				try {
					Battle b = BattleFactory.createBlank(type, args[2]);
					BattleManager.addBattle(b);
					BattlePersistenceService.persistBattle(b);
					p.sendMessage("§aBattle "+b.getId()+" §acreated!");
					BattleManager.currentBattle.put(p, b);
					BattleInventoryManager inv = new BattleInventoryManager();
					inv.battleView(p, b);
				} catch (IllegalArgumentException e) {
					p.sendMessage("§c" + e.getMessage());
				}
				return true;
			} else if(cmd.getName().equalsIgnoreCase(cmd2) && args[0].equalsIgnoreCase("edit") && args.length == 2) {
				if(BattleManager.getByString(args[1]) == null) {
					p.sendMessage("§cThere is no battle with this id");
					return true;
				}
				Battle b = BattleManager.getByString(args[1]);
				BattleManager.currentBattle.put(p, b);
				BattleInventoryManager inv = new BattleInventoryManager();
				inv.battleView(p, b);
				return true;
			} else if(cmd.getName().equalsIgnoreCase(cmd2) && args[0].equalsIgnoreCase("delete") && args.length == 2) {
				Battle b = BattleManager.getByString(args[1]);
				if (b == null) {
					p.sendMessage("§cNo battle with id §e" + args[1]);
					return true;
				}
				if (b.getWarId() != null) {
					p.sendMessage("§cCampaign battles cannot be deleted with §e/battle delete§c.");
					p.sendMessage("§7Reset setup with §e/war admin schedule " + b.getWarId() + " battledelete§7.");
					return true;
				}
				if (b.hasStarted()) {
					p.sendMessage("§cCannot delete a battle while it is running.");
					p.sendMessage("§7Stop it first via §e/battle edit §7-> End Battle.");
					return true;
				}
				BattlePersistenceService.deleteManualBattle(b);
				BattleManager.clearEditorSessions(b);
				BattleManager.currentBattle.remove(p);
				BattleManager.currentSideEdit.remove(p);
				p.sendMessage("§aManual battle §e" + b.getId() + " §adeleted.");
				return true;
			} else if(cmd.getName().equalsIgnoreCase(cmd2) && args[0].equalsIgnoreCase("addside") && args.length == 3) {
				if(BattleManager.getByString(args[1]) == null) {
					p.sendMessage("§cThere is no battle with this id");
					return true;
				}
				Battle b = BattleManager.getByString(args[1]);
				if(b.getSideById(args[2]) != null) {
					p.sendMessage("§cAlready a side with this id");
					return true;
				}
				BattleSide s = new BattleSide(args[2], b.getLifeType(), b.getLives());
				b.addSide(s);
				BattlePersistenceService.persistBattle(b);
				p.sendMessage("§aSide §e"+s.getId()+" §acreated!");
				return true;
			} else if(cmd.getName().equalsIgnoreCase(cmd2) && args[0].equalsIgnoreCase("addpoint") && args.length == 3) {
				if(BattleManager.getByString(args[1]) == null) {
					p.sendMessage("§cThere is no battle with this id");
					return true;
				}
				Battle b = BattleManager.getByString(args[1]);
				if(b.getSideById(args[2]) == null) {
					p.sendMessage("§cNo side with this id");
					return true;
				}
				CapturePoint point;
				try {
					point = BattleSideSetupService.addCapturePoint(
							b, b.getSideById(args[2]), p.getLocation());
				} catch (IllegalArgumentException | IllegalStateException ex) {
					p.sendMessage("§c" + ex.getMessage());
					return true;
				}
				BattlePersistenceService.persistBattle(b);
				p.sendMessage("§aPoint §e"+point.getId()+" §acreated!");
				return true;
			} else if(cmd.getName().equalsIgnoreCase(cmd2) && args[0].equalsIgnoreCase("addpoint") && args.length == 4) {
				p.sendMessage("§cCustom point ids are no longer supported. Use: /battle addpoint <battleId> <side>");
				return true;
			} else if(cmd.getName().equalsIgnoreCase(cmd2) && args[0].equalsIgnoreCase("setlives") && args.length == 3) {
				if(BattleManager.getByString(args[1]) == null) {
					p.sendMessage("§cThere is no battle with this id");
					return true;
				}
				Battle b = BattleManager.getByString(args[1]);
				if (b.getWarId() != null) {
					p.sendMessage("§cCampaign battle lives are computed from war commitment and roster size.");
					p.sendMessage("§7Adjust regiments in the war pool or roster signups instead.");
					return true;
				}
				b.setLives(Integer.parseInt(args[2]));
				BattlePersistenceService.persistBattle(b);
				p.sendMessage("§aLives set to "+args[2]);
				return true;
			} else if(cmd.getName().equalsIgnoreCase(cmd2) && args[0].equalsIgnoreCase("setspawn") && args.length == 3) {
				if(BattleManager.getByString(args[1]) == null) {
					p.sendMessage("§cThere is no battle with this id");
					return true;
				}
				Battle b = BattleManager.getByString(args[1]);
				if(b.getSideById(args[2]) == null) {
					p.sendMessage("§cNo side with this id");
					return true;
				}
				BattleSide s = b.getSideById(args[2]);
				try {
					BattleSideSetupService.setSpawn(b, s, p.getLocation());
				} catch (IllegalArgumentException ex) {
					p.sendMessage("§c" + ex.getMessage());
					return true;
				}
				BattlePersistenceService.persistBattle(b);
				p.sendMessage("§aSide §e"+s.getId()+" §aspawn set!");
				return true;
			} else if(cmd.getName().equalsIgnoreCase(cmd2) && args[0].equalsIgnoreCase("setjail") && args.length == 3) {
				if(BattleManager.getByString(args[1]) == null) {
					p.sendMessage("§cThere is no battle with this id");
					return true;
				}
				Battle b = BattleManager.getByString(args[1]);
				if(b.getSideById(args[2]) == null) {
					p.sendMessage("§cNo side with this id");
					return true;
				}
				BattleSide s = b.getSideById(args[2]);
				try {
					BattleSideSetupService.setJail(b, s, p.getLocation());
				} catch (IllegalArgumentException ex) {
					p.sendMessage("§c" + ex.getMessage());
					return true;
				}
				BattlePersistenceService.persistBattle(b);
				p.sendMessage("§aSide §e"+s.getId()+" §ajail set!");
				return true;
			} else if(cmd.getName().equalsIgnoreCase(cmd2) && args[0].equalsIgnoreCase("setcontestmin") && args.length == 2) {
				Battle b = BattleManager.getByString(args[1]);
				if(b == null) {
					p.sendMessage("§cThere is no battle with this id");
					return true;
				}
				warnIfNotSiege(p, b);
				try {
					BattleContestSetup.setContestMin(b, p.getLocation());
				} catch (IllegalArgumentException ex) {
					p.sendMessage("§c" + ex.getMessage());
					return true;
				}
				BattlePersistenceService.persistBattle(b);
				p.sendMessage("§aContest area min set!");
				return true;
			} else if(cmd.getName().equalsIgnoreCase(cmd2) && args[0].equalsIgnoreCase("setcontestmax") && args.length == 2) {
				Battle b = BattleManager.getByString(args[1]);
				if(b == null) {
					p.sendMessage("§cThere is no battle with this id");
					return true;
				}
				warnIfNotSiege(p, b);
				try {
					BattleContestSetup.setContestMax(b, p.getLocation());
				} catch (IllegalArgumentException ex) {
					p.sendMessage("§c" + ex.getMessage());
					return true;
				}
				BattlePersistenceService.persistBattle(b);
				p.sendMessage("§aContest area max set!");
				return true;
			} else if(cmd.getName().equalsIgnoreCase(cmd2) && args[0].equalsIgnoreCase("setcontestduration") && args.length == 3) {
				Battle b = BattleManager.getByString(args[1]);
				if(b == null) {
					p.sendMessage("§cThere is no battle with this id");
					return true;
				}
				warnIfNotSiege(p, b);
				int seconds = Integer.parseInt(args[2]);
				if (seconds < 1) {
					p.sendMessage("§cDuration must be at least 1 second");
					return true;
				}
				b.setContestDurationSeconds(seconds);
				BattlePersistenceService.persistBattle(b);
				p.sendMessage("§aContest duration set to "+seconds+"s");
				return true;
			} else if(cmd.getName().equalsIgnoreCase(cmd2) && args[0].equalsIgnoreCase("setraidtarget") && args.length == 2) {
				Battle b = BattleManager.getByString(args[1]);
				if(b == null) {
					p.sendMessage("§cThere is no battle with this id");
					return true;
				}
				warnIfNotRaid(p, b);
				BattleRaidSetup.setRaidTarget(b, p.getLocation());
				BattlePersistenceService.persistBattle(b);
				p.sendMessage("§aRaid target set!");
				return true;
			} else if(cmd.getName().equalsIgnoreCase(cmd2) && args[0].equalsIgnoreCase("setdefenderlives") && args.length == 3) {
				Battle b = BattleManager.getByString(args[1]);
				if(b == null) {
					p.sendMessage("§cThere is no battle with this id");
					return true;
				}
				warnIfNotRaid(p, b);
				int lives = Integer.parseInt(args[2]);
				if (lives < 1) {
					p.sendMessage("§cDefender lives must be at least 1");
					return true;
				}
				b.setDefenderLives(lives);
				BattlePersistenceService.persistBattle(b);
				p.sendMessage("§aDefender lives set to "+lives);
				return true;
			}
			p.sendMessage("§a[Battle]§c Error with command format, use the gameplay guide for a list of commands");
		}
		return false;
	}

	private void warnIfNotSiege(Player p, Battle b) {
		if (b.getBattleType() != BattleType.SIEGE) {
			p.sendMessage("§eWarning: battle type is not siege");
		}
	}

	private void warnIfNotRaid(Player p, Battle b) {
		if (b.getBattleType() != BattleType.RAID) {
			p.sendMessage("§eWarning: battle type is not raid");
		}
	}
}
