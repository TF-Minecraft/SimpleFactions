package me.Plugins.SimpleFactions.War.battle.engine.core;


import me.Plugins.SimpleFactions.War.campaign.raid.fight.CampaignRaidBossBarService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitRunnable;

import net.tfminecraft.VehicleFramework.Events.VFExplosionEvent;
import me.Plugins.SimpleFactions.SimpleFactions;
import me.Plugins.SimpleFactions.War.battle.enums.BattleEndReason;
import me.Plugins.SimpleFactions.War.battle.enums.BattleType;
import me.Plugins.SimpleFactions.War.battle.enums.DefenderRespawnMode;
import me.Plugins.SimpleFactions.War.battle.military.BattleCasualtyLedger;
import me.Plugins.SimpleFactions.War.battle.template.BattleTemplate;
import me.Plugins.SimpleFactions.War.battle.engine.capture.BattleCapturePoints;
import me.Plugins.SimpleFactions.War.battle.engine.capture.CapturePoint;
import me.Plugins.SimpleFactions.War.battle.engine.raid.BattleRaidSetup;
import me.Plugins.SimpleFactions.War.battle.engine.raid.RaidAttackerEliminationService;
import me.Plugins.SimpleFactions.War.campaign.raid.intruder.CampaignRaidIntruderService;
import me.Plugins.SimpleFactions.War.battle.engine.raid.RaidRespawnService;
import me.Plugins.SimpleFactions.War.battle.engine.raid.RaidWinService;
import me.Plugins.SimpleFactions.War.battle.ui.BattleInventoryManager;
import me.Plugins.SimpleFactions.War.battle.persistence.BattlePersistenceService;
import me.Plugins.SimpleFactions.War.battle.warband.Warband;
import me.Plugins.SimpleFactions.War.battle.warband.WarbandManager;

public class BattleManager implements Listener{
	public static HashMap<Player, Battle> currentBattle = new HashMap<>();
	public static HashMap<Player, String> currentSideEdit = new HashMap<>();

	private static List<Battle> battles = new ArrayList<Battle>();
	
	public static Battle getByString(String s) {
		for(Battle b : battles) {
			if(b.getId().equalsIgnoreCase(s)) return b;
		}
		return null;
	}

	public static Battle getByWarId(int warId) {
		for (Battle b : battles) {
			if (b.getWarId() != null && b.getWarId() == warId && !b.isCampaignRaid()) {
				return b;
			}
		}
		return null;
	}

	public static List<Battle> getAllByWarId(int warId) {
		List<Battle> result = new ArrayList<>();
		for (Battle battle : battles) {
			if (battle.getWarId() != null && battle.getWarId() == warId) {
				result.add(battle);
			}
		}
		return result;
	}
	
	public static void addBattle(Battle b) {
		battles.add(b);
	}
	public static void deleteBattle(Battle b) {
		battles.remove(b);
	}

	public static void clearEditorSessions(Battle battle) {
		if (battle == null) {
			return;
		}
		List<Player> editing = new ArrayList<>();
		for (java.util.Map.Entry<Player, Battle> entry : currentBattle.entrySet()) {
			if (entry.getValue() == battle) {
				editing.add(entry.getKey());
			}
		}
		for (Player player : editing) {
			currentBattle.remove(player);
			currentSideEdit.remove(player);
		}
	}

	public static void shutdown() {
		for (Battle battle : new ArrayList<>(battles)) {
			if (!battle.hasStarted()) {
				continue;
			}
			battle.getPointManager().end(battle.getAllParticipants());
			if (battle.isCampaignRaid()) {
				me.Plugins.SimpleFactions.War.campaign.raid.fight.CampaignRaidBossBarService.clear(battle);
			}
			for (BattleSide side : battle.getSides()) {
				side.removeBossBar();
			}
		}
	}

	public static Battle getManualBattle() {
		for (Battle battle : battles) {
			if (battle.getWarId() == null) {
				return battle;
			}
		}
		return null;
	}

	public static boolean hasManualBattle() {
		return getManualBattle() != null;
	}
	public static List<Battle> get(){
		return battles;
	}

	public static void resetForTests() {
		battles.clear();
		currentBattle.clear();
		currentSideEdit.clear();
		BattleCasualtyLedger.resetForTests();
		BattleRespawnRouting.resetForTests();
	}
	private BattleSide getBySidePlayer(Player p) {
		for(Battle b : battles) {
			for(BattleSide s : b.getSides()) {
				for(Warband w : s.getBands()) {
					if (w.hasMember(p)) return s;
				}
			}
		}
		return null;
	}
	public static Battle getBattleByMemberId(java.util.UUID memberId) {
		for (Battle b : battles) {
			if (b.getSideByMemberId(memberId) != null) return b;
		}
		return null;
	}

	public static Battle getBattleByPlayer(Player p) {
		for(Battle b : battles) {
			if(b.getAllParticipants().contains(p)) return b;
		}
		return null;
	}
	private Battle getBattleByItem(ItemStack item) {
		ItemMeta m = item.getItemMeta();
		for(Battle b : battles) {
			if(m.getDisplayName().equals("§e"+b.getId())) return b;
		}
		return null;
	}
	private CapturePoint getPointByItem(Battle b, ItemStack item) {
		ItemMeta m = item.getItemMeta();
		for(CapturePoint p : b.getPointManager().getPoints()) {
			if(m.getDisplayName().equals("§a"+p.getId())) return p;
		}
		return null;
	}
	private BattleSide getBattleSideByItem(ItemStack item, Battle b) {
		if (item == null || !item.hasItemMeta() || b == null) {
			return null;
		}
		String sideId = BattleInventoryManager.getSideIdFromItem(item);
		if (sideId != null) {
			return b.getSideById(sideId);
		}
		ItemMeta m = item.getItemMeta();
		for(BattleSide s : b.getSides()) {
			if(m.getDisplayName().equals("§e"+s.getId()+": §f"+s.getAllParticipants())) return s;
		}
		return null;
	}
	public void start() {
		Bukkit.getLogger().info("[SimpleFactions] Starting Battle Manager");
		new BukkitRunnable()
		{
			public void run()
			{
				for(Battle b : battles) {
					if(b.hasStarted()){
						b.tick();
					}
				}
			}
		}.runTaskTimer(SimpleFactions.plugin, 0L, 4L);
	}

	public void end() {
		for(Battle b : battles) {
			if(b.hasStarted()) b.end();
		}
	}
	
	public void spawnTeleport(Player p, CapturePoint point) {
		new BukkitRunnable()
		{
			int i = 15;
			public void run()
			{
				if(i == 0) {
					p.teleport(point.getLoc());
					this.cancel();
				}
				p.sendTitle(" ", "§eTeleporting... §a"+i+"s", 0, 30, 0);
				i--;
			}
		}.runTaskTimer(SimpleFactions.plugin, 0L, 20L);
	}
	@EventHandler
	public void explode(VFExplosionEvent e) {
		Location loc = e.getLocation();
		for(Battle b : battles) {
			for(CapturePoint p : b.getPointManager().getPoints()) {
				if(loc.distanceSquared(p.getLoc()) < 36) {
					e.setBlockDamage(false);
				}
			}
			for(BattleSide s : b.getSides()) {
				if(s.getSpawn() != null) {
					if(loc.distanceSquared(s.getSpawn()) < 36) {
						e.setBlockDamage(false);
					}
				}
				if(s.getJail() != null) {
					if(loc.distanceSquared(s.getJail()) < 36) {
						e.setBlockDamage(false);
					}
				}
			}
		}
	}
	
	public static final String KEEP_POUCH_METADATA = "simplefactions.keep-pouch";

	@EventHandler(priority = EventPriority.LOWEST)
	public void playerDeath(PlayerDeathEvent e) {
		Player p = e.getEntity();
		if (CampaignRaidIntruderService.consumeIntruderDeath(p.getUniqueId())) {
			return;
		}
		Battle b = getBattleByPlayer(p);
		if(b == null) return;
		if(!b.hasStarted()) return;
		BattleSide s = getBySidePlayer(p);
		if(s == null) return;
		e.setKeepInventory(b.hasKeepInventory());
		if (b.hasKeepInventory()) {
			e.getDrops().clear();
		}
		markKeepPouch(p);
		if (b.getBattleType() == BattleType.RAID) {
			if (BattleTemplate.ATTACKER_SIDE.equalsIgnoreCase(s.getId())) {
				RaidAttackerEliminationService.markOut(b, p.getUniqueId());
			} else if (BattleRaidSetup.getEffectiveDefenderRespawnMode(b) == DefenderRespawnMode.INFINITE) {
				// Infinite defenders do not consume lives.
			} else {
				BattleRespawnRouting.scheduleJailRespawn(
						p.getUniqueId(), s.applyDeathAndNeedsJailRespawn());
			}
		} else {
			BattleRespawnRouting.scheduleJailRespawn(
					p.getUniqueId(), s.applyDeathAndNeedsJailRespawn());
			BattleCasualtyLedger.recordSideCasualty(b, s);
		}
	}

	private static void markKeepPouch(Player player) {
		SimpleFactions plugin = SimpleFactions.plugin;
		if (plugin == null) {
			return;
		}
		player.setMetadata(KEEP_POUCH_METADATA, new FixedMetadataValue(plugin, true));
		new BukkitRunnable() {
			@Override
			public void run() {
				player.removeMetadata(KEEP_POUCH_METADATA, plugin);
			}
		}.runTask(plugin);
	}
	
	@EventHandler
	public void playerQuit(PlayerQuitEvent e) {
		Player p = e.getPlayer();
		Battle b = getBattleByPlayer(p);
		if (b == null || !b.hasStarted()) {
			return;
		}
		BattleSide s = getBySidePlayer(p);
		if (s == null) {
			return;
		}
		if (b.getBattleType() == BattleType.RAID) {
			if (!BattleTemplate.ATTACKER_SIDE.equalsIgnoreCase(s.getId())) {
				return;
			}
			RaidAttackerEliminationService.markOut(b, p.getUniqueId());
			RaidWinService.checkRaidWin(b);
			return;
		}
		BattleCasualtyLedger.recordSideCasualty(b, s);
		BattleRespawnRouting.clear(p.getUniqueId());
	}
	
	@EventHandler
	public void playerRespawn(PlayerRespawnEvent e) {
		Player p = e.getPlayer();
		Battle b = getBattleByPlayer(p);
		if(b == null) return;
		if(!b.hasStarted()) return;
		BattleSide s = getBySidePlayer(p);
		if(s == null) return;
		new BukkitRunnable()
		{
			public void run()
			{
				p.setFoodLevel(20);

				// Run console commands for resource restoration
				Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
						"mmocore admin resource-health give " + p.getName() + " 100");
				Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
						"mmocore admin resource-mana give " + p.getName() + " 100");

				if (b.getBattleType() == BattleType.RAID && RaidRespawnService.applyRespawn(b, p, s)) {
					return;
				}

				if (BattleRespawnRouting.consumeJailRespawn(p.getUniqueId())) {
					p.teleport(s.getJail());
					return;
				}

				p.teleport(s.getSpawn());

				if (b.getBattleType() == BattleType.FIELD && b.getPointManager().getPoints().size() > 0) {
					BattleInventoryManager inv = new BattleInventoryManager();
					inv.spawnList(p, b);
				}
			}
		}.runTaskLater(SimpleFactions.plugin, 10L);
	}
	@EventHandler
	public void friendlyFire(EntityDamageByEntityEvent e) {
		if(!(e.getDamager() instanceof Player)) return;
		if(!(e.getEntity() instanceof Player)) return;
		Player p = (Player) e.getDamager();
		Player target = (Player) e.getEntity();
		Battle b = getBattleByPlayer(p);
		if(b == null) return;
		if(!b.hasStarted()) return;
		if(b.hasFriendlyFire()) return;
		BattleSide s = getBySidePlayer(p);
		if(s == null) return;
		if(s.hasPlayer(target)) e.setCancelled(true);
	}
	
	@EventHandler
	public void invenClick(InventoryClickEvent e) {
		Player p = (Player) e.getWhoClicked();
		if(e.getView().getTitle().equalsIgnoreCase("§7Battle View")) {
			if(!currentBattle.containsKey(p)) return;
			Battle b = currentBattle.get(p);
			BattleInventoryManager inv = new BattleInventoryManager();
			e.setCancelled(true);
			if(b.hasStarted() && e.getSlot() != 18) {
				p.sendMessage("§cCannot edit a battle while it has started");
				p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
				return;
			}
			if(e.getSlot() == 0) {
				inv.sideView(p, b);
			} else if(e.getSlot() == 1) {
				b.setLocked(!b.isLocked());
				p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
				inv.updateView(p, b, e.getClickedInventory());
				BattlePersistenceService.persistBattle(b);
			} else if(e.getSlot() == 2) {
				// Collective lives only; no alternate life mode.
			} else if(e.getSlot() == 3) {
				if (b.getBattleType() == BattleType.SIEGE && !b.hasStarted()) {
					int next = BattleInventoryManager.cycleContestDuration(
							BattleContestSetup.getEffectiveDurationSeconds(b));
					b.setContestDurationSeconds(next);
					p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
					inv.updateView(p, b, e.getClickedInventory());
				BattlePersistenceService.persistBattle(b);
				} else if (b.getBattleType() == BattleType.RAID && !b.hasStarted()) {
					DefenderRespawnMode current = BattleRaidSetup.getEffectiveDefenderRespawnMode(b);
					DefenderRespawnMode next = current == DefenderRespawnMode.INFINITE
							? DefenderRespawnMode.LIVES
							: DefenderRespawnMode.INFINITE;
					b.setDefenderRespawnMode(next);
					p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
					inv.updateView(p, b, e.getClickedInventory());
				BattlePersistenceService.persistBattle(b);
				}
			} else if(e.getSlot() == 4) {
				b.setFriendlyFire(!b.hasFriendlyFire());
				p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
				inv.updateView(p, b, e.getClickedInventory());
				BattlePersistenceService.persistBattle(b);
			} else if(e.getSlot() == 5) {
				b.setKeepInventory(!b.hasKeepInventory());
				p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
				inv.updateView(p, b, e.getClickedInventory());
				BattlePersistenceService.persistBattle(b);
			} else if(e.getSlot() == 6) {
				b.setTeleport(!b.hasTeleport());
				p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
				inv.updateView(p, b, e.getClickedInventory());
				BattlePersistenceService.persistBattle(b);
			} else if(e.getSlot() == 14) {
				b.setLootEnabled(!b.hasLootEnabled());
				p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
				inv.updateView(p, b, e.getClickedInventory());
				BattlePersistenceService.persistBattle(b);
			} else if(e.getSlot() == 7) {
				inv.templateView(p, b);
			} else if(e.getSlot() == 8) {
				if (b.getBattleType() == BattleType.RAID && !b.hasStarted()
						&& BattleRaidSetup.getEffectiveDefenderRespawnMode(b) == DefenderRespawnMode.LIVES) {
					int next = BattleInventoryManager.cycleDefenderLives(
							BattleRaidSetup.getEffectiveDefenderLives(b));
					b.setDefenderLives(next);
					p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
					inv.updateView(p, b, e.getClickedInventory());
				BattlePersistenceService.persistBattle(b);
					return;
				}
				if (b.getBattleType() != BattleType.FIELD) {
					return;
				}
				boolean enabling = !b.isSequentialCapture();
				b.setSequentialCapture(enabling);
				if (enabling) {
					BattleCapturePoints.syncLinearChain(b);
				} else {
					BattleCapturePoints.compressGlobalLetters(b);
				}
				p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
				inv.updateView(p, b, e.getClickedInventory());
				BattlePersistenceService.persistBattle(b);
			} else if(e.getSlot() == 18) {
				if(b.hasStarted()) {
					BattleEndSupport.endBattle(b, null, BattleEndReason.TIMER);
					if (b.getWarId() != null) {
						currentBattle.remove(p);
						p.closeInventory();
						p.sendMessage("§aBattle ended.");
					} else {
						BattlePersistenceService.persistBattle(b);
						inv.updateView(p, b, e.getClickedInventory());
					}
				} else {
					String startError = b.start();
					if (startError != null) {
						p.sendMessage("§c" + startError);
						p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
						return;
					}
					BattlePersistenceService.persistBattle(b);
					p.closeInventory();
				}
			} else if(e.getSlot() == 22) {
				if (b.getWarId() != null) {
					p.sendMessage("§cCampaign battles cannot be deleted from the battle editor.");
					p.sendMessage("§7Reset setup with §e/war admin schedule " + b.getWarId() + " battledelete§7.");
					p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
					return;
				}
				if (b.hasStarted()) {
					p.sendMessage("§cCannot delete a battle while it is running.");
					p.sendMessage("§7Stop it first via §e/battle edit §7-> End Battle.");
					p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
					return;
				}
				BattlePersistenceService.deleteManualBattle(b);
				clearEditorSessions(b);
				currentBattle.remove(p);
				currentSideEdit.remove(p);
				p.closeInventory();
				p.sendMessage("§aManual battle deleted.");
				p.playSound(p.getLocation(), Sound.ENTITY_ITEM_BREAK, 1f, 1f);
			} else if(e.getSlot() == 23) {
				if (b.isCapturePointsEnabled()) {
					inv.pointView(p, b);
				} else if (b.getBattleType() == BattleType.SIEGE) {
					inv.contestView(p, b);
				} else if (b.getBattleType() == BattleType.RAID) {
					inv.raidTargetView(p, b);
				}
			}
		} else if(e.getView().getTitle().equalsIgnoreCase("§7Side View")) {
			if(!currentBattle.containsKey(p)) return;
			Battle b = currentBattle.get(p);
			BattleInventoryManager inv = new BattleInventoryManager();
			e.setCancelled(true);
			if(e.getSlot() == 26) {
				currentSideEdit.remove(p);
				inv.battleView(p, b);
				return;
			}
			if(b.hasStarted()) {
				p.sendMessage("§cCannot edit a battle while it has started");
				p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
				return;
			}
			if(e.getCurrentItem() == null) return;
			BattleSide side = getBattleSideByItem(e.getCurrentItem(), b);
			if(side == null) return;
			currentSideEdit.put(p, side.getId());
			inv.sideEditView(p, b, side);
		} else if(e.getView().getTitle().equalsIgnoreCase(BattleInventoryManager.SIDE_EDIT_TITLE)) {
			if(!currentBattle.containsKey(p)) return;
			Battle b = currentBattle.get(p);
			BattleInventoryManager inv = new BattleInventoryManager();
			e.setCancelled(true);
			String sideId = currentSideEdit.get(p);
			BattleSide side = sideId != null ? b.getSideById(sideId) : null;
			if (side == null) {
				currentSideEdit.remove(p);
				inv.sideView(p, b);
				return;
			}
			if(e.getSlot() == 26) {
				currentSideEdit.remove(p);
				inv.sideView(p, b);
				return;
			}
			if(b.hasStarted()) {
				p.sendMessage("§cCannot edit a battle while it has started");
				p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
				return;
			}
			if(e.getSlot() == 10) {
				try {
					BattleSideSetupService.setSpawn(b, side, p.getLocation());
					BattlePersistenceService.persistBattle(b);
					p.sendMessage("§aSide §e" + side.getId() + " §aspawn set!");
					p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
					inv.updateSideEditView(p, b, side, e.getClickedInventory());
				} catch (IllegalArgumentException ex) {
					p.sendMessage("§c" + ex.getMessage());
					p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
				}
			} else if(e.getSlot() == 12) {
				try {
					BattleSideSetupService.setJail(b, side, p.getLocation());
					BattlePersistenceService.persistBattle(b);
					p.sendMessage("§aSide §e" + side.getId() + " §ajail set!");
					p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
					inv.updateSideEditView(p, b, side, e.getClickedInventory());
				} catch (IllegalArgumentException ex) {
					p.sendMessage("§c" + ex.getMessage());
					p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
				}
			} else if(e.getSlot() == 14 && b.isCapturePointsEnabled()) {
				try {
					CapturePoint point = BattleSideSetupService.addCapturePoint(b, side, p.getLocation());
					BattlePersistenceService.persistBattle(b);
					p.sendMessage("§aPoint §e" + point.getId() + " §acreated!");
					p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
					inv.updateSideEditView(p, b, side, e.getClickedInventory());
				} catch (IllegalArgumentException | IllegalStateException ex) {
					p.sendMessage("§c" + ex.getMessage());
					p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
				}
			} else if (e.getSlot() == BattleInventoryManager.SIDE_EDIT_LIVES_SLOT) {
				try {
					int current = side.getMaxLives() > 0 ? side.getMaxLives() : side.getLives();
					int next = BattleInventoryManager.cycleDefenderLives(current);
					BattleSideSetupService.setSideLives(b, side, next);
					BattlePersistenceService.persistBattle(b);
					p.sendMessage("§aSide lives set to §e" + next);
					p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
					inv.updateSideEditView(p, b, side, e.getClickedInventory());
				} catch (IllegalArgumentException | IllegalStateException ex) {
					p.sendMessage("§c" + ex.getMessage());
					p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
				}
			}
		} else if(e.getView().getTitle().equalsIgnoreCase("§7Point View")) {
			if(!currentBattle.containsKey(p)) return;
			Battle b = currentBattle.get(p);
			BattleInventoryManager inv = new BattleInventoryManager();
			e.setCancelled(true);
			if(e.getSlot() == 26) {
				inv.battleView(p, b);
				return;
			}
			if (b.hasStarted()) {
				p.sendMessage("§cCannot edit capture points while the battle has started");
				p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
				return;
			}
			String pointId = BattleInventoryManager.getPointIdFromItem(e.getCurrentItem());
			if (pointId == null) {
				return;
			}
			CapturePoint point = b.getPointById(pointId);
			if (point == null) {
				return;
			}
			if (BattleCapturePoints.removePoint(b, point)) {
				BattlePersistenceService.persistBattle(b);
				p.sendMessage("§aDeleted capture point §e" + pointId);
				p.playSound(p.getLocation(), Sound.ENTITY_ITEM_BREAK, 1f, 1f);
				inv.pointView(p, b);
			}
		} else if(e.getView().getTitle().equalsIgnoreCase("§7Contest View")) {
			if(!currentBattle.containsKey(p)) return;
			Battle b = currentBattle.get(p);
			BattleInventoryManager inv = new BattleInventoryManager();
			e.setCancelled(true);
			if(e.getSlot() == 26) {
				inv.battleView(p, b);
				return;
			}
			if(b.hasStarted()) {
				return;
			}
			if(e.getSlot() == 0) {
				try {
					BattleContestSetup.setContestMin(b, p.getLocation());
					BattlePersistenceService.persistBattle(b);
					p.sendMessage("§aContest area min set!");
					p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
					inv.contestView(p, b);
				} catch (IllegalArgumentException ex) {
					p.sendMessage("§c" + ex.getMessage());
					p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
				}
			} else if(e.getSlot() == 1) {
				try {
					BattleContestSetup.setContestMax(b, p.getLocation());
					BattlePersistenceService.persistBattle(b);
					p.sendMessage("§aContest area max set!");
					p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
					inv.contestView(p, b);
				} catch (IllegalArgumentException ex) {
					p.sendMessage("§c" + ex.getMessage());
					p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
				}
			} else if(e.getSlot() == 2) {
				int next = BattleInventoryManager.cycleContestDuration(
						BattleContestSetup.getEffectiveDurationSeconds(b));
				b.setContestDurationSeconds(next);
				p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
				inv.contestView(p, b);
			}
		} else if(e.getView().getTitle().equalsIgnoreCase("§7Raid Target View")) {
			if(!currentBattle.containsKey(p)) return;
			Battle b = currentBattle.get(p);
			BattleInventoryManager inv = new BattleInventoryManager();
			e.setCancelled(true);
			if(e.getSlot() == 26) {
				inv.battleView(p, b);
			}
		} else if(e.getView().getTitle().equalsIgnoreCase("§7Template Selection")) {
			if(!currentBattle.containsKey(p)) return;
			Battle b = currentBattle.get(p);
			BattleInventoryManager inv = new BattleInventoryManager();
			e.setCancelled(true);
			if(e.getSlot() == 26) {
				inv.battleView(p, b);
				return;
			}
			if(b.hasStarted()) {
				p.sendMessage("§cCannot edit a battle while it has started");
				p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
				return;
			}
			if(e.getCurrentItem() == null) return;
			String templateId = BattleInventoryManager.getTemplateIdFromItem(e.getCurrentItem());
			if (templateId == null) return;
			try {
				if (BattleInventoryManager.TEMPLATE_NONE_ID.equals(templateId)) {
					BattleFactory.resetToBase(b);
					p.sendMessage("§aReset to base");
				} else {
					BattleFactory.applyTemplate(b, templateId);
					p.sendMessage("§aTemplate set to §e" + templateId);
				}
				p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
				inv.battleView(p, b);
				BattlePersistenceService.persistBattle(b);
			} catch (IllegalArgumentException | IllegalStateException ex) {
				p.sendMessage("§c" + ex.getMessage());
				p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
			}
		} else if(e.getView().getTitle().equalsIgnoreCase("§7Battle List")) {
			BattleInventoryManager inv = new BattleInventoryManager();
			e.setCancelled(true);
			if(e.getCurrentItem() == null) return;
			ItemStack battleItem = e.getCurrentItem();
			Battle b = getBattleByItem(battleItem);
			if(b == null) return;
			currentBattle.put(p, b);
			inv.sideSelection(p, b);
		} else if(e.getView().getTitle().equalsIgnoreCase("§7Respawn Points")) {
			e.setCancelled(true);
			if(e.getCurrentItem() == null) return;
			ItemStack pointItem = e.getCurrentItem();
			if(pointItem.getType().equals(Material.GREEN_CONCRETE)) {
				Battle b = getBattleByPlayer(p);
				if(b == null) return;
				CapturePoint point = getPointByItem(b, pointItem);
				spawnTeleport(p, point);
				p.closeInventory();
			} else {
				p.sendMessage("§cPoint not controlled");
				p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
				return;
			}
		} else if(e.getView().getTitle().equalsIgnoreCase("§7Side Selection")) {
			BattleInventoryManager inv = new BattleInventoryManager();
			e.setCancelled(true);
			if(e.getSlot() == 26) {
				inv.battleList(p);
				return;
			} else {
				if(!currentBattle.containsKey(p)) return;
				Battle b = currentBattle.get(p);
				if(b.hasStarted()) {
					p.sendMessage("§cBattle has started");
					p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
					return;
				}
				if(b.isLocked()) {
					p.sendMessage("§cBattle is locked");
					p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
					return;
				}
				if(e.getCurrentItem() == null) return;
				ItemStack sideItem = e.getCurrentItem();
				BattleSide s = getBattleSideByItem(sideItem, b);
				if(s == null) return;
				if(s.hasPlayer(p)) {
					s.removeBand(WarbandManager.getByLeader(p));
					p.sendMessage("§cLeft "+s.getId()+" in the battle "+b.getId());
					p.closeInventory();
					return;
				}
				String joinError = BattleJoinService.join(p, b, s.getId());
				if (joinError != null) {
					p.sendMessage("§c" + joinError);
					p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
					return;
				}
				p.sendMessage("§aJoined "+s.getId()+" in the battle "+b.getId());
				p.closeInventory();
			}
		}
	}
}
