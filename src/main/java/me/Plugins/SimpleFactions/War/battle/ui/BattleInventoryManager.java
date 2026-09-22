package me.Plugins.SimpleFactions.War.battle.ui;


import me.Plugins.SimpleFactions.War.campaign.raid.CampaignRaidWarbandService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.TLibs.TLibs;
import me.Plugins.TLibs.Enums.APIType;
import me.Plugins.TLibs.Objects.API.ItemAPI;
import me.Plugins.TLibs.Objects.API.SubAPI.StringFormatter;
import me.Plugins.SimpleFactions.SimpleFactions;
import me.Plugins.SimpleFactions.Loaders.BattleTemplateLoader;
import me.Plugins.SimpleFactions.Managers.WarManager;
import me.Plugins.SimpleFactions.War.core.War;
import me.Plugins.SimpleFactions.War.battle.campaign.CampaignBattleJoinService;
import me.Plugins.SimpleFactions.War.battle.campaign.CampaignBattleJoinService.CampaignBattleContext;
import me.Plugins.SimpleFactions.War.battle.campaign.warband.CampaignWarbandSignupService;
import me.Plugins.SimpleFactions.War.campaign.runtime.CampaignClock;
import me.Plugins.SimpleFactions.War.battle.enums.BattleType;
import me.Plugins.SimpleFactions.War.battle.enums.DefenderRespawnMode;
import me.Plugins.SimpleFactions.War.battle.military.BattleLivesService;
import me.Plugins.SimpleFactions.War.battle.military.BattleLivesService.SideLivesPreview;
import me.Plugins.SimpleFactions.War.battle.template.BattleTemplate;
import me.Plugins.SimpleFactions.War.battle.engine.core.BattleContestSetup;
import me.Plugins.SimpleFactions.War.battle.engine.raid.BattleRaidSetup;
import me.Plugins.SimpleFactions.War.battle.engine.core.Battle;
import me.Plugins.SimpleFactions.War.battle.engine.core.BattleSide;
import me.Plugins.SimpleFactions.War.battle.engine.capture.CapturePoint;
import me.Plugins.SimpleFactions.War.battle.engine.core.BattleManager;
import org.bukkit.Location;
import me.Plugins.SimpleFactions.War.battle.warband.Warband;
import me.Plugins.SimpleFactions.War.battle.warband.WarbandManager;
import me.Plugins.SimpleFactions.War.battle.template.BattleLocation;
import me.Plugins.SimpleFactions.War.battle.template.ContestArea;


public class BattleInventoryManager {
	public static final String TEMPLATE_NONE_ID = "__none__";
	public static final String SIDE_EDIT_TITLE = "§7Side Edit";
	public static final int SIDE_EDIT_LIVES_SLOT = 16;

	private static NamespacedKey templateKey() {
		return new NamespacedKey(SimpleFactions.plugin, "battle_template");
	}

	private static NamespacedKey sideKey() {
		return new NamespacedKey(SimpleFactions.plugin, "battle_side_id");
	}

	private static NamespacedKey pointKey() {
		return new NamespacedKey(SimpleFactions.plugin, "battle_point_id");
	}

	public void battleView(Player player, Battle b) {
		Inventory i = SimpleFactions.plugin.getServer().createInventory(null, 27, "§7Battle View");
		populateBattleView(i, b);
		player.openInventory(i);
	}
	public void battleList(Player player) {
		Inventory i = SimpleFactions.plugin.getServer().createInventory(null, 27, "§7Battle List");
		populateBattleList(i);
		player.openInventory(i);
	}

	public void populateBattleList(Inventory i) {
		i.clear();
		for (int y = 0; y < BattleManager.get().size(); y++) {
			i.setItem(y, createBattleItem(BattleManager.get().get(y)));
		}
	}
	public void spawnList(Player player, Battle b) {
		Inventory i = SimpleFactions.plugin.getServer().createInventory(null, 27, "§7Respawn Points");
		for(int y = 0; y<b.getPointManager().getPoints().size();y++) {
			i.setItem(y, createSpawnPointItem(b.getPointManager().getPoints().get(y), b.getSideByPlayer(player)));
		}
		player.openInventory(i);
	}
	public static final String WARBAND_LIST_TITLE = "§7Warband List";

	public void warbandList(Player player) {
		Inventory i = SimpleFactions.plugin.getServer().createInventory(null, 27, WARBAND_LIST_TITLE);
		populateWarbandList(i, player);
		player.openInventory(i);
	}

	public void populateWarbandList(Inventory i) {
		populateWarbandList(i, null);
	}

	public void populateWarbandList(Inventory i, Player viewer) {
		for (int slot = 0; slot < i.getSize(); slot++) {
			i.setItem(slot, null);
		}
		int slot = 0;
		for (Warband warband : WarbandManager.get()) {
			if (slot >= i.getSize()) {
				break;
			}
			if (me.Plugins.SimpleFactions.War.campaign.raid.CampaignRaidWarbandService
					.isRaidWarbandHiddenFromPlayer(warband, viewer)) {
				continue;
			}
			i.setItem(slot, createWarbandItem(warband));
			slot++;
		}
	}
	public void updateView(Player player, Battle b, Inventory i) {
		populateBattleView(i, b);
	}

	private void populateBattleView(Inventory i, Battle b) {
		i.setItem(0, createSideButton(b));
		i.setItem(1, createLockButton(b));
		i.setItem(2, createLifeButton(b));
		if (b.getBattleType() == BattleType.SIEGE) {
			i.setItem(3, createContestDurationButton(b));
			i.setItem(8, null);
		} else if (b.getBattleType() == BattleType.RAID) {
			i.setItem(3, createDefenderRespawnModeButton(b));
			if (BattleRaidSetup.getEffectiveDefenderRespawnMode(b) == DefenderRespawnMode.LIVES) {
				i.setItem(8, createDefenderLivesButton(b));
			} else {
				i.setItem(8, null);
			}
		} else {
			if (usesCampaignLivesFormula(b)) {
				i.setItem(3, createCampaignLivesSummaryItem(b));
			} else {
				i.setItem(3, createManualSideLivesHintItem(b));
			}
			if (b.isCapturePointsEnabled()) {
				i.setItem(8, createSequentialCaptureButton(b));
			} else {
				i.setItem(8, null);
			}
		}
		if (b.isCapturePointsEnabled()) {
			i.setItem(23, createPointButton(b));
		} else if (b.getBattleType() == BattleType.SIEGE) {
			i.setItem(23, createContestButton(b));
		} else if (b.getBattleType() == BattleType.RAID) {
			i.setItem(23, createRaidTargetButton(b));
		} else {
			i.setItem(23, null);
		}
		i.setItem(4, createGameRuleButton("Friendly Fire", b.hasFriendlyFire()));
		i.setItem(5, createGameRuleButton("Keep Inventory", b.hasKeepInventory()));
		i.setItem(6, createGameRuleButton("TP on start", b.hasTeleport()));
		i.setItem(7, createTemplateButton(b));
		i.setItem(14, createGameRuleButton("Battle Loot", b.hasLootEnabled()));
		if(b.hasStarted()) {
			i.setItem(18, createStopButton(b));
		} else {
			i.setItem(18, createStartButton(b));
		}
		i.setItem(13, createBattleInfoItem(b));
		if (b.getWarId() == null && !b.hasStarted()) {
			i.setItem(22, createDeleteBattleButton(b));
		} else if (b.getWarId() != null && !b.hasStarted()) {
			if (b.isCampaignRaid()) {
				i.setItem(22, createCampaignRaidResetHintItem(b));
			} else {
				i.setItem(22, createCampaignBattleResetHintItem(b));
			}
		} else {
			i.setItem(22, null);
		}
	}

	public ItemStack createDeleteBattleButton(Battle b) {
		ItemStack i = new ItemStack(Material.TNT, 1);
		ItemMeta meta = i.getItemMeta();
		meta.setDisplayName("§cDelete battle");
		List<String> lore = new ArrayList<>();
		lore.add("§7Click to delete this manual battle");
		meta.setLore(lore);
		i.setItemMeta(meta);
		return i;
	}

	public ItemStack createCampaignBattleResetHintItem(Battle b) {
		ItemStack i = new ItemStack(Material.BARRIER, 1);
		ItemMeta meta = i.getItemMeta();
		meta.setDisplayName("§cCampaign battle");
		List<String> lore = new ArrayList<>();
		lore.add("§7Cannot delete from here");
		if (b.getWarId() != null) {
			lore.add("§7Use §e/war admin schedule " + b.getWarId() + " battledelete");
			lore.add("§7to reset battle setup and warbands");
		}
		meta.setLore(lore);
		i.setItemMeta(meta);
		return i;
	}

	public ItemStack createCampaignRaidResetHintItem(Battle b) {
		ItemStack i = new ItemStack(Material.BARRIER, 1);
		ItemMeta meta = i.getItemMeta();
		meta.setDisplayName("§cCampaign raid");
		List<String> lore = new ArrayList<>();
		lore.add("§7Cannot delete from here");
		lore.add("§7Raids end when the timer expires");
		lore.add("§7or via war admin teardown");
		meta.setLore(lore);
		i.setItemMeta(meta);
		return i;
	}

	public ItemStack createBattleInfoItem(Battle b) {
		ItemStack i = new ItemStack(Material.NAME_TAG, 1);
		ItemMeta meta = i.getItemMeta();
		meta.setDisplayName("§fBattle: §e" + b.getDisplayName());
		List<String> lore = new ArrayList<>();
		lore.add("§7Id: " + b.getId());
		meta.setLore(lore);
		i.setItemMeta(meta);
		return i;
	}

	public void contestView(Player player, Battle b) {
		Inventory i = SimpleFactions.plugin.getServer().createInventory(null, 27, "§7Contest View");
		i.setItem(0, createContestCornerItem("Min", b.getContestArea() != null ? b.getContestArea().getMin() : null));
		i.setItem(1, createContestCornerItem("Max", b.getContestArea() != null ? b.getContestArea().getMax() : null));
		i.setItem(2, createContestDurationButton(b));
		if (b.hasStarted()) {
			i.setItem(3, createContestHoldItem(b));
		}
		i.setItem(26, createBackButton());
		player.openInventory(i);
	}

	public void raidTargetView(Player player, Battle b) {
		Inventory i = SimpleFactions.plugin.getServer().createInventory(null, 27, "§7Raid Target View");
		i.setItem(0, createRaidTargetLocationItem(b));
		if (b.hasStarted() && !b.getPointManager().getPoints().isEmpty()) {
			i.setItem(1, createPointItem(b.getPointManager().getPoints().get(0), b));
		}
		i.setItem(26, createBackButton());
		player.openInventory(i);
	}
	public void sideSelection(Player player, Battle b) {
		Inventory i = SimpleFactions.plugin.getServer().createInventory(null, 27, "§7Side Selection");
		for(int y = 0; y<b.getSides().size();y++) {
			i.setItem(y, createSideItem(b, b.getSides().get(y)));
		}
		i.setItem(26, createBackButton());
		player.openInventory(i);
	}
	public void sideView(Player player, Battle b) {
		Inventory i = SimpleFactions.plugin.getServer().createInventory(null, 27, "§7Side View");
		for(int y = 0; y<b.getSides().size();y++) {
			i.setItem(y, createSideItem(b, b.getSides().get(y)));
		}
		i.setItem(26, createBackButton());
		player.openInventory(i);
	}

	public void sideEditView(Player player, Battle b, BattleSide side) {
		Inventory i = SimpleFactions.plugin.getServer().createInventory(null, 27, SIDE_EDIT_TITLE);
		populateSideEditView(i, b, side);
		player.openInventory(i);
	}

	public void populateSideEditView(Inventory i, Battle b, BattleSide side) {
		for (int slot = 0; slot < i.getSize(); slot++) {
			i.setItem(slot, null);
		}
		i.setItem(4, createSideItem(b, side));
		i.setItem(10, createSetSpawnButton(side));
		i.setItem(12, createSetJailButton(side));
		if (b.isCapturePointsEnabled()) {
			i.setItem(14, createAddPointButton(side));
		}
		if (b.getWarId() == null && supportsPerSideLives(b) && !b.hasStarted()) {
			i.setItem(SIDE_EDIT_LIVES_SLOT, createSetSideLivesButton(side));
		}
		i.setItem(26, createBackButton());
	}

	public void updateSideEditView(Player player, Battle b, BattleSide side, Inventory inventory) {
		populateSideEditView(inventory, b, side);
	}
	public void pointView(Player player, Battle b) {
		Inventory i = SimpleFactions.plugin.getServer().createInventory(null, 27, "§7Point View");
		List<CapturePoint> sorted = new ArrayList<>(b.getPoints());
		sorted.sort(Comparator.comparingInt(CapturePoint::getSequenceIndex));
		for (int y = 0; y < sorted.size(); y++) {
			i.setItem(y, createPointItem(sorted.get(y), b));
		}
		i.setItem(26, createBackButton());
		player.openInventory(i);
	}
	public void templateView(Player player, Battle b) {
		Inventory i = SimpleFactions.plugin.getServer().createInventory(null, 27, "§7Template Selection");
		i.setItem(0, createNoneTemplateItem());
		int slot = 1;
		if (b.getBattleType() != null) {
			for (Map.Entry<String, BattleTemplate> entry : BattleTemplateLoader.getAll().entrySet()) {
				if (entry.getValue().getType() != b.getBattleType()) {
					continue;
				}
				if (slot >= 26) {
					break;
				}
				i.setItem(slot++, createTemplateItem(entry.getKey()));
			}
		}
		i.setItem(26, createBackButton());
		player.openInventory(i);
	}
	public ItemStack createSequentialCaptureButton(Battle b) {
		return createGameRuleButton("Sequential capture", b.isSequentialCapture());
	}

	public ItemStack createTemplateButton(Battle b) {
		ItemStack i = new ItemStack(Material.WRITABLE_BOOK, 1);
		ItemMeta meta = i.getItemMeta();
		String templateLabel = b.getTemplateName() != null ? b.getTemplateName() : "None";
		meta.setDisplayName("§fTemplate: §e" + templateLabel);
		List<String> lore = new ArrayList<String>();
		lore.add("§7Click to change template");
		meta.setLore(lore);
		i.setItemMeta(meta);
		return i;
	}
	public ItemStack createNoneTemplateItem() {
		ItemStack i = new ItemStack(Material.BARRIER, 1);
		ItemMeta meta = i.getItemMeta();
		meta.setDisplayName("§7None (base)");
		List<String> lore = new ArrayList<String>();
		lore.add("§7Reset battle layout to base defaults");
		meta.setLore(lore);
		meta.getPersistentDataContainer().set(templateKey(), PersistentDataType.STRING, TEMPLATE_NONE_ID);
		i.setItemMeta(meta);
		return i;
	}
	public ItemStack createTemplateItem(String templateId) {
		ItemStack i = new ItemStack(Material.BOOK, 1);
		ItemMeta meta = i.getItemMeta();
		meta.setDisplayName("§e" + templateId);
		List<String> lore = new ArrayList<String>();
		lore.add("§7Apply this template");
		lore.add("§7Applies battle rules only");
		lore.add("§cWipes current battle layout");
		meta.setLore(lore);
		meta.getPersistentDataContainer().set(templateKey(), PersistentDataType.STRING, templateId);
		i.setItemMeta(meta);
		return i;
	}
	public static String getTemplateIdFromItem(ItemStack item) {
		if (item == null || !item.hasItemMeta()) {
			return null;
		}
		return item.getItemMeta().getPersistentDataContainer().get(templateKey(), PersistentDataType.STRING);
	}
	public ItemStack createBackButton() {
		ItemStack i = new ItemStack(Material.BARRIER, 1);
		ItemMeta meta = i.getItemMeta();
		meta.setDisplayName("§cBACK");
		i.setItemMeta(meta);
		return i;
	}
	public ItemStack createSpawnPointItem(CapturePoint point, BattleSide s) {
		ItemStack i = new ItemStack(Material.YELLOW_CONCRETE, 1);
		List<String> lore = new ArrayList<String>();
		if(!point.getController().getId().equals(s.getId())) {
			i.setType(Material.RED_CONCRETE);
			ItemMeta meta = i.getItemMeta();
			meta.setDisplayName("§c"+point.getId());
			lore.add("§cEnemy Control");
			lore.add("§7x"+Math.round(point.getLoc().getX())+", y"+Math.round(point.getLoc().getY())+", z"+Math.round(point.getLoc().getZ()));
			meta.setLore(lore);
			i.setItemMeta(meta);
		} else {
			if(point.getCaptureProgress() >= 50) {
				i.setType(Material.GREEN_CONCRETE);
				ItemMeta meta = i.getItemMeta();
				meta.setDisplayName("§a"+point.getId());
				lore.add("§aFriendly Control");
				lore.add("§7x"+Math.round(point.getLoc().getX())+", y"+Math.round(point.getLoc().getY())+", z"+Math.round(point.getLoc().getZ()));
				meta.setLore(lore);
				i.setItemMeta(meta);
			} else {
				ItemMeta meta = i.getItemMeta();
				meta.setDisplayName("§e"+point.getId());
				lore.add("§eContested");
				lore.add("§7x"+Math.round(point.getLoc().getX())+", y"+Math.round(point.getLoc().getY())+", z"+Math.round(point.getLoc().getZ()));
				meta.setLore(lore);
				i.setItemMeta(meta);
			}
		}
		return i;
	}
	public ItemStack createWarbandItem(Warband w) {
		ItemStack i = new ItemStack(Material.SHIELD, 1);
		ItemMeta meta = i.getItemMeta();
		if(w.isFaction()) meta.setCustomModelData(2);
		else meta.setCustomModelData(1);
		meta.setDisplayName("§e"+w.getName());
		List<String> lore = new ArrayList<String>();
		if(w.isFaction()) {
			lore.add(StringFormatter.formatHex("#ba4b3a§lFaction Warband"));
		}
		lore.add("§aLeader: "+w.getLeaderDisplayName());
		lore.add(" ");
		lore.add("§7Soldiers: §e" + w.getMemberCount());
		int onlineCount = w.getOnlineMemberCount();
		if (onlineCount > 0) {
			lore.add("§7Online: §e" + onlineCount);
		}
		List<String> shownNames = w.getMemberDisplayNamesForLore(5);
		for (String memberName : shownNames) {
			lore.add("§8- " + memberName);
		}
		if (w.getMemberCount() > shownNames.size()) {
			lore.add("§8- and " + (w.getMemberCount() - shownNames.size()) + " more");
		}
		lore.add(" ");
		if(!w.isFaction()) {
			if(w.isLocked()) {
				lore.add("§cLOCKED");
			} else {
				lore.add("§aOPEN");
			}
		} else {
			if (w.getCampaignSideId() != null) {
				lore.add(StringFormatter.formatHex("#7fbd73Side: #d4c9ae" + w.getCampaignSideId()));
			}
			CampaignBattleContext ctx = CampaignBattleJoinService.findCampaignBattleForWarband(w);
			if (ctx != null) {
				if (ctx.battle().hasStarted()) {
					BattleSide side = ctx.battle().getSideById(ctx.sideId());
					if (side != null) {
						lore.add("§7Lives: §e" + side.getLives());
					}
				} else {
					SideLivesPreview preview = BattleLivesService.previewCampaignSideLives(
							ctx.war(), ctx.battle(), ctx.sideId());
					lore.add("§7Side lives: §e" + preview.sideLives());
					if (preview.committedRegiments() > 0) {
						lore.add("§8Pool §e" + preview.poolLives()
								+ " §8- §e" + preview.rosterFighters() + " §8soldiers");
					}
					int roster = CampaignBattleJoinService.countSideRoster(ctx.battle(), ctx.sideId());
					int maxRoster = CampaignBattleJoinService.previewSidePoolLives(
							ctx.war(), ctx.battle(), ctx.sideId());
					lore.add("§7Soldiers: §e" + roster + "§7/§e" + maxRoster);
				}
				if (!CampaignWarbandSignupService.isSignupOpen(ctx.war(), CampaignClock.now())) {
					lore.add("§cSignup closed until 20:00");
				}
			}
		}
		
		meta.setLore(lore);
		NamespacedKey key = new NamespacedKey(SimpleFactions.plugin, "id");
		meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, w.getId());
		i.setItemMeta(meta);
		return i;
	}
	public ItemStack createBattleItem(Battle b) {
		ItemStack i = new ItemStack(Material.IRON_SWORD, 1);
		ItemMeta meta = i.getItemMeta();
		meta.setDisplayName("§e"+b.getDisplayName());
		List<String> lore = new ArrayList<String>();
		lore.add("§7Id: "+b.getId());
		int rosterCount = 0;
		for (BattleSide side : b.getSides()) {
			if (side != null) {
				rosterCount += side.getAllParticipants();
			}
		}
		lore.add("§7Participants: "+rosterCount);
		meta.setLore(lore);
		i.setItemMeta(meta);
		return i;
	}
	public ItemStack createSideItem(BattleSide s) {
		return createSideItem(null, s);
	}

	public ItemStack createSideItem(Battle battle, BattleSide s) {
		ItemStack i = new ItemStack(Material.EMERALD, 1);
		ItemMeta meta = i.getItemMeta();
		int count = 0;
		List<String> lore = new ArrayList<String>();
		for(Warband w : s.getBands()) {
			lore.add("§f"+w.getName()+": "+w.getMemberCount());
			count = count+w.getMemberCount();
		}
		if (battle != null && !battle.hasStarted()) {
			if (usesCampaignLivesFormula(battle)) {
				War war = WarManager.getById(battle.getWarId());
				if (war != null) {
					lore.addAll(formatSideLivesPreviewLore(
							BattleLivesService.previewCampaignSideLives(war, battle, s.getId())));
				}
			} else if (battle.getWarId() == null && supportsPerSideLives(battle)) {
				lore.add("§7Lives: §e" + s.getLives());
			}
		}
		if (s.getSpawn() != null) {
			lore.add("§7Spawn: " + formatLocation(s.getSpawn()));
		}
		if (s.getJail() != null) {
			lore.add("§7Jail: " + formatLocation(s.getJail()));
		}
		meta.setDisplayName("§e"+s.getId()+": §f"+count);
		meta.setLore(lore);
		meta.getPersistentDataContainer().set(sideKey(), PersistentDataType.STRING, s.getId());
		i.setItemMeta(meta);
		return i;
	}

	public static String getSideIdFromItem(ItemStack item) {
		if (item == null || !item.hasItemMeta()) {
			return null;
		}
		return item.getItemMeta().getPersistentDataContainer().get(sideKey(), PersistentDataType.STRING);
	}

	public ItemStack createSetSpawnButton(BattleSide side) {
		ItemStack i = new ItemStack(Material.RED_BED, 1);
		ItemMeta meta = i.getItemMeta();
		meta.setDisplayName("§aSet spawn");
		List<String> lore = new ArrayList<>();
		lore.add("§7Uses your current location");
		if (side.getSpawn() != null) {
			lore.add("§7Current: " + formatLocation(side.getSpawn()));
		} else {
			lore.add("§7Current: not set");
		}
		meta.setLore(lore);
		i.setItemMeta(meta);
		return i;
	}

	public ItemStack createSetJailButton(BattleSide side) {
		ItemStack i = new ItemStack(Material.IRON_BARS, 1);
		ItemMeta meta = i.getItemMeta();
		meta.setDisplayName("§aSet jail");
		List<String> lore = new ArrayList<>();
		lore.add("§7Uses your current location");
		if (side.getJail() != null) {
			lore.add("§7Current: " + formatLocation(side.getJail()));
		} else {
			lore.add("§7Current: not set");
		}
		meta.setLore(lore);
		i.setItemMeta(meta);
		return i;
	}

	public ItemStack createAddPointButton(BattleSide side) {
		ItemStack i = new ItemStack(Material.GRAY_BANNER, 1);
		ItemMeta meta = i.getItemMeta();
		meta.setDisplayName("§aAdd capture point");
		List<String> lore = new ArrayList<>();
		lore.add("§7Uses your current location");
		lore.add("§7Auto-names next global chain letter (A, B, C...)");
		meta.setLore(lore);
		i.setItemMeta(meta);
		return i;
	}

	private static String formatLocation(Location location) {
		if (location == null) {
			return "-";
		}
		return "x" + Math.round(location.getX())
				+ ", y" + Math.round(location.getY())
				+ ", z" + Math.round(location.getZ());
	}
	public ItemStack createPointItem(CapturePoint p) {
		return createPointItem(p, null);
	}

	public ItemStack createPointItem(CapturePoint p, Battle b) {
		ItemStack i = new ItemStack(Material.RED_BANNER, 1);
		ItemMeta meta = i.getItemMeta();
		meta.setDisplayName("§e"+p.getId()+": §f"+p.getController().getId()+ " §7("+p.getCaptureProgress()+"%)");
		List<String> lore = new ArrayList<String>();
		lore.add("§7Chain #" + (p.getSequenceIndex() + 1) + " (" + p.getId() + ")");
		lore.add("§7x"+Math.round(p.getLoc().getX())+", y"+Math.round(p.getLoc().getY())+", z"+Math.round(p.getLoc().getZ()));
		if (b != null && b.isSequentialCapture()) {
			lore.add("§7Order synced defender to attacker spawns");
		}
		lore.add("§cClick to delete");
		meta.setLore(lore);
		meta.getPersistentDataContainer().set(pointKey(), PersistentDataType.STRING, p.getId());
		i.setItemMeta(meta);
		return i;
	}

	public static String getPointIdFromItem(ItemStack item) {
		if (item == null || !item.hasItemMeta()) {
			return null;
		}
		return item.getItemMeta().getPersistentDataContainer().get(pointKey(), PersistentDataType.STRING);
	}
	public ItemStack createStartButton(Battle b) {
		ItemStack i = new ItemStack(Material.IRON_SWORD, 1);
		ItemMeta meta = i.getItemMeta();
		meta.setDisplayName("§aSTART BATTLE");
		if(b.hasStarted()) {
			meta.addEnchant(Enchantment.UNBREAKING, 1, false);
			meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
		}
		i.setItemMeta(meta);
		return i;
	}
	public ItemStack createStopButton(Battle b) {
		ItemStack i = new ItemStack(Material.BARRIER, 1);
		ItemMeta meta = i.getItemMeta();
		meta.setDisplayName("§cEnd Battle");
		List<String> lore = new ArrayList<String>();
		lore.add("§7Stops the battle");
		meta.setLore(lore);
		i.setItemMeta(meta);
		return i;
	}
	public ItemStack createLifeCount(Battle b) {
		ItemStack i = new ItemStack(Material.RED_DYE, 1);
		ItemMeta meta = i.getItemMeta();
		meta.setDisplayName("§eLives: §c"+b.getLives());
		List<String> lore = new ArrayList<String>();
		lore.add("§7Collective: Each side has "+b.getLives()+" respawns in total");
		meta.setLore(lore);
		i.setItemMeta(meta);
		return i;
	}

	public ItemStack createCampaignLivesSummaryItem(Battle b) {
		ItemStack i = new ItemStack(Material.RED_DYE, 1);
		ItemMeta meta = i.getItemMeta();
		meta.setDisplayName("§eCampaign lives");
		List<String> lore = new ArrayList<>();
		War war = WarManager.getById(b.getWarId());
		if (war == null) {
			lore.add("§7War not found");
		} else {
			appendCampaignSideLivesLine(lore, "Attacker", war, b, BattleTemplate.ATTACKER_SIDE);
			appendCampaignSideLivesLine(lore, "Defender", war, b, BattleTemplate.DEFENDER_SIDE);
		}
		lore.add(" ");
		lore.add("§8Computed from war commitment and roster");
		meta.setLore(lore);
		i.setItemMeta(meta);
		return i;
	}

	public ItemStack createManualSideLivesHintItem(Battle b) {
		ItemStack i = new ItemStack(Material.RED_DYE, 1);
		ItemMeta meta = i.getItemMeta();
		meta.setDisplayName("§ePer-side lives");
		List<String> lore = new ArrayList<>();
		lore.add("§7Configure each side in §eSides");
		if (supportsPerSideLives(b)) {
			for (BattleSide side : b.getSides()) {
				lore.add("§7" + side.getId() + ": §e" + side.getLives());
			}
		}
		meta.setLore(lore);
		i.setItemMeta(meta);
		return i;
	}

	public ItemStack createSetSideLivesButton(BattleSide side) {
		ItemStack i = new ItemStack(Material.APPLE, 1);
		ItemMeta meta = i.getItemMeta();
		int lives = side.getMaxLives() > 0 ? side.getMaxLives() : side.getLives();
		meta.setDisplayName("§fSide lives: §e" + lives);
		List<String> lore = new ArrayList<>();
		lore.add("§7Click to cycle pool size");
		lore.add("§7Current: §e" + side.getLives() + "§7/§e" + lives);
		meta.setLore(lore);
		i.setItemMeta(meta);
		return i;
	}

	private static void appendCampaignSideLivesLine(
			List<String> lore,
			String label,
			War war,
			Battle battle,
			String sideId) {
		SideLivesPreview preview = BattleLivesService.previewCampaignSideLives(war, battle, sideId);
		if (preview.committedRegiments() <= 0) {
			lore.add("§7" + label + ": §e0");
			return;
		}
		lore.add("§7" + label + ": §e" + preview.sideLives()
				+ " §8(" + preview.poolLives() + " - " + preview.rosterFighters() + ")");
	}

	public static List<String> formatSideLivesPreviewLore(SideLivesPreview preview) {
		List<String> lore = new ArrayList<>();
		if (preview.committedRegiments() <= 0) {
			lore.add("§7Lives: §e0 §8(no committed regiments)");
			return lore;
		}
		lore.add("§7Lives: §e" + preview.sideLives());
		lore.add("§8Regiments §e" + preview.committedRegiments()
				+ " §8| Pool §e" + preview.poolLives()
				+ " §8- §e" + preview.rosterFighters() + " §8soldiers");
		return lore;
	}

	private static boolean usesCampaignLivesFormula(Battle battle) {
		if (battle == null || battle.getWarId() == null) {
			return false;
		}
		BattleType type = battle.getBattleType();
		return type == BattleType.FIELD || type == BattleType.SIEGE;
	}

	private static boolean supportsPerSideLives(Battle battle) {
		if (battle == null) {
			return false;
		}
		BattleType type = battle.getBattleType();
		return type == BattleType.FIELD || type == BattleType.SIEGE;
	}
	public ItemStack createLifeButton(Battle b) {
		ItemStack i = new ItemStack(Material.GOLDEN_APPLE, 1);
		ItemMeta meta = i.getItemMeta();
		meta.setDisplayName("§eLife Mode: §aCollective");
		List<String> lore = new ArrayList<String>();
		lore.add("§7Each side has a common pool of lives");
		meta.setLore(lore);
		i.setItemMeta(meta);
		return i;
	}
	public ItemStack createSideButton(Battle b) {
		ItemStack i = new ItemStack(Material.NETHER_STAR, 1);
		ItemMeta meta = i.getItemMeta();
		meta.setDisplayName("§fSides: §e"+b.getSides().size());
		i.setItemMeta(meta);
		return i;
	}
	public ItemStack createContestButton(Battle b) {
		ItemStack i = new ItemStack(Material.IRON_BLOCK, 1);
		ItemMeta meta = i.getItemMeta();
		meta.setDisplayName("§fContest Area");
		List<String> lore = new ArrayList<String>();
		if (b.getContestArea() != null && b.getContestArea().isConfigured()) {
			lore.add("§aConfigured");
		} else {
			lore.add("§cNot configured");
		}
		lore.add("§7Click to view contest setup");
		meta.setLore(lore);
		i.setItemMeta(meta);
		return i;
	}

	public ItemStack createContestDurationButton(Battle b) {
		ItemStack i = new ItemStack(Material.CLOCK, 1);
		ItemMeta meta = i.getItemMeta();
		int duration = BattleContestSetup.getEffectiveDurationSeconds(b);
		meta.setDisplayName("§eContest duration: §f" + duration + "s");
		List<String> lore = new ArrayList<String>();
		if (!b.hasStarted()) {
			lore.add("§7Click to cycle duration");
		} else {
			lore.add("§7Duration locked after start");
		}
		meta.setLore(lore);
		i.setItemMeta(meta);
		return i;
	}

	public ItemStack createContestHoldItem(Battle b) {
		ItemStack i = new ItemStack(Material.GOLD_BLOCK, 1);
		ItemMeta meta = i.getItemMeta();
		meta.setDisplayName("§eHold remaining: §f" + b.getContestHoldRemainingSeconds() + "s");
		i.setItemMeta(meta);
		return i;
	}

	public ItemStack createContestCornerItem(String label, BattleLocation corner) {
		ItemStack i = new ItemStack(Material.STONE_BRICKS, 1);
		ItemMeta meta = i.getItemMeta();
		meta.setDisplayName("§eContest " + label);
		List<String> lore = new ArrayList<String>();
		if (corner == null) {
			lore.add("§cNot set");
		} else {
			lore.add("§7x" + Math.round(corner.getX()) + ", y" + Math.round(corner.getY()) + ", z" + Math.round(corner.getZ()));
		}
		lore.add("§7Click to set at your location");
		meta.setLore(lore);
		i.setItemMeta(meta);
		return i;
	}

	public static int cycleContestDuration(int current) {
		int[] options = { 60, 120, 180, 240, 300 };
		for (int i = 0; i < options.length; i++) {
			if (options[i] == current) {
				return options[(i + 1) % options.length];
			}
		}
		return 180;
	}

	public static int cycleDefenderLives(int current) {
		int[] options = { 5, 10, 15, 20, 25, 50 };
		for (int i = 0; i < options.length; i++) {
			if (options[i] == current) {
				return options[(i + 1) % options.length];
			}
		}
		return 25;
	}

	public ItemStack createDefenderRespawnModeButton(Battle b) {
		DefenderRespawnMode mode = BattleRaidSetup.getEffectiveDefenderRespawnMode(b);
		ItemStack i = new ItemStack(Material.RESPAWN_ANCHOR, 1);
		ItemMeta meta = i.getItemMeta();
		meta.setDisplayName("§fDefender respawn: §e" + mode.toJson());
		List<String> lore = new ArrayList<String>();
		lore.add("§7Click to toggle infinite / lives");
		meta.setLore(lore);
		i.setItemMeta(meta);
		return i;
	}

	public ItemStack createDefenderLivesButton(Battle b) {
		ItemStack i = new ItemStack(Material.APPLE, 1);
		ItemMeta meta = i.getItemMeta();
		meta.setDisplayName("§fDefender lives: §e" + BattleRaidSetup.getEffectiveDefenderLives(b));
		List<String> lore = new ArrayList<String>();
		lore.add("§7Click to cycle pool size");
		meta.setLore(lore);
		i.setItemMeta(meta);
		return i;
	}

	public ItemStack createRaidTargetButton(Battle b) {
		ItemStack i = new ItemStack(Material.TARGET, 1);
		ItemMeta meta = i.getItemMeta();
		String label = b.getRaidTarget() != null && b.getRaidTarget().getId() != null
				? b.getRaidTarget().getId()
				: "unset";
		meta.setDisplayName("§fRaid Target: §e" + label);
		List<String> lore = new ArrayList<String>();
		lore.add("§7Open raid target view");
		meta.setLore(lore);
		i.setItemMeta(meta);
		return i;
	}

	public ItemStack createRaidTargetLocationItem(Battle b) {
		ItemStack i = new ItemStack(Material.ENDER_PEARL, 1);
		ItemMeta meta = i.getItemMeta();
		meta.setDisplayName("§fTarget location");
		List<String> lore = new ArrayList<String>();
		if (b.getRaidTarget() != null && b.getRaidTarget().getLocation() != null) {
			BattleLocation location = b.getRaidTarget().getLocation();
			lore.add("§7x" + Math.round(location.getX()) + ", y" + Math.round(location.getY())
					+ ", z" + Math.round(location.getZ()));
		} else {
			lore.add("§7Not set");
		}
		meta.setLore(lore);
		i.setItemMeta(meta);
		return i;
	}

	public ItemStack createPointButton(Battle b) {
		ItemStack i = new ItemStack(Material.RED_BANNER, 1);
		ItemMeta meta = i.getItemMeta();
		meta.setDisplayName("§fCapture Points: §e"+b.getPoints().size());
		i.setItemMeta(meta);
		return i;
	}
	public ItemStack createGameRuleButton(String s, boolean b) {
		if(b) {
			ItemStack i = new ItemStack(Material.LIME_DYE, 1);
			ItemMeta meta = i.getItemMeta();
			meta.setDisplayName("§f"+s+": §bTRUE");
			i.setItemMeta(meta);
			return i;
		} else {
			ItemStack i = new ItemStack(Material.GRAY_DYE, 1);
			ItemMeta meta = i.getItemMeta();
			meta.setDisplayName("§f"+s+": §cFALSE");
			i.setItemMeta(meta);
			return i;
		}
		
	}
	ItemStack createLockButton(Battle b) {
		ItemAPI api = (ItemAPI) TLibs.getApiInstance(APIType.ITEM_API);
		ItemStack i = api.getCreator().getItemsAdderItem("mcicons:icon_unlock");;
		ItemMeta m = i.getItemMeta();
		m.setDisplayName("§aUnlocked");
		List<String> lore = new ArrayList<String>();
		if(b.isLocked()) {
			i = api.getCreator().getItemsAdderItem("mcicons:icon_lock");
			m = i.getItemMeta();
			m.setDisplayName("§cLocked");
			lore.add("§7Warbands cannot join sides");
		} else {
			lore.add("§7Warbands can join sides");
		}
		m.setLore(lore);
		i.setItemMeta(m);
		return i;
	}
	
}
