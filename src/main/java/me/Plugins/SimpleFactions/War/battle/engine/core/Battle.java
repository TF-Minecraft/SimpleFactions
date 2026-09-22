package me.Plugins.SimpleFactions.War.battle.engine.core;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import me.Plugins.SimpleFactions.SimpleFactions;
import me.Plugins.SimpleFactions.War.battle.enums.BattleType;
import me.Plugins.SimpleFactions.War.battle.enums.DefenderRespawnMode;
import me.Plugins.SimpleFactions.War.battle.enums.LifeType;
import me.Plugins.SimpleFactions.War.battle.events.BattleStartedEvent;
import me.Plugins.SimpleFactions.War.battle.military.BattleCasualtyLedger;
import me.Plugins.SimpleFactions.War.battle.military.BattleLivesService;
import me.Plugins.SimpleFactions.War.battle.engine.capture.BattleCapturePoints;
import me.Plugins.SimpleFactions.War.battle.engine.capture.CapturePoint;
import me.Plugins.SimpleFactions.War.battle.engine.capture.PointManager;
import me.Plugins.SimpleFactions.War.battle.engine.raid.BattleRaidSetup;
import me.Plugins.SimpleFactions.War.battle.engine.raid.RaidAttackerEliminationService;
import me.Plugins.SimpleFactions.War.battle.engine.raid.RaidWinService;
import me.Plugins.SimpleFactions.War.battle.engine.win.FieldWinService;
import me.Plugins.SimpleFactions.War.battle.engine.win.SiegeContestService;
import me.Plugins.SimpleFactions.War.battle.engine.win.SiegeWinService;
import me.Plugins.SimpleFactions.War.battle.template.CapturePointDefinition;
import me.Plugins.SimpleFactions.War.battle.template.ContestArea;
import me.Plugins.SimpleFactions.War.battle.ui.BattleInventoryManager;
import me.Plugins.SimpleFactions.War.campaign.raid.fight.CampaignRaidBossBarService;
import me.Plugins.SimpleFactions.War.battle.warband.Warband;

public class Battle {
	private String id;
	private String displayName;
	private List<BattleSide> sides = new ArrayList<BattleSide>();
	private List<CapturePoint> points = new ArrayList<CapturePoint>();
	private boolean friendlyFire;
	private boolean keepInventory;
	private boolean lootEnabled;
	private boolean teleport;
	private boolean started;
	private Instant startedAt;
	private boolean locked;
	private int lives;
	private LifeType lt;
	private PointManager pm;
	private Integer provinceId;
	private BattleType battleType;
	private Integer warId;
	private String templateName;
	private ContestArea contestArea;
	private int contestDurationSeconds;
	private DefenderRespawnMode defenderRespawnMode;
	private Integer defenderLives;
	private CapturePointDefinition raidTarget;
	private boolean navalVariant;
	private Location navalSpawn;
	private Set<Integer> allowedProvinceIds = Collections.emptySet();
	private boolean sequentialCapture;
	private boolean capturePointsEnabled;
	private int contestHoldRemainingSeconds;
	private boolean campaignRaid;
	public List<CapturePoint> getPoints() {
		return points;
	}
	public void setPoints(List<CapturePoint> points) {
		this.points = points;
	}
	public boolean isLocked() {
		return locked;
	}
	public void setLocked(boolean locked) {
		this.locked = locked;
	}
	public Battle(String id) {
		this.id = id;
		this.friendlyFire = true;
		this.keepInventory = true;
		this.lootEnabled = true;
		this.teleport = false;
		this.started = false;
		this.locked = true;
		this.lt = LifeType.COLLECTIVE;
		this.lives = 25;
		pm = new PointManager(this);
	}
	public LifeType getLifeType() {
		return lt;
	}
	public void setLifeType(LifeType lt) {
		this.lt = lt;
	}
	public String getId() {
		return this.id;
	}

	public String getDisplayName() {
		return displayName != null && !displayName.isBlank() ? displayName : id;
	}

	public void setDisplayName(String displayName) {
		this.displayName = displayName;
	}

	public void setLives(int l) {
		this.lives = l;
	}
	public int getLives() {
		return this.lives;
	}
	public void addSide(BattleSide s) {
		this.sides.add(s);
	}
	public void removeSide(String s) {
		for(int i = 0; i<sides.size(); i++) {
			if(sides.get(i).getId().equalsIgnoreCase(s)) {
				sides.remove(i);
			}
		}
	}
	
	public void addPoint(CapturePoint p) {
		this.points.add(p);
	}
	public void removePoint(CapturePoint p) {
		if (p != null) {
			removePointById(p.getId());
		}
	}

	public boolean removePointById(String id) {
		if (id == null) {
			return false;
		}
		for (int i = 0; i < points.size(); i++) {
			if (points.get(i).getId().equalsIgnoreCase(id)) {
				points.remove(i);
				return true;
			}
		}
		return false;
	}
	public PointManager getPointManager() {
		return this.pm;
	}
	public List<Player> getPlayers(){
		List<Player> players = new ArrayList<Player>();
		for(BattleSide s : sides) {
			for(Warband w : s.getBands()) {
				for(Player p : w.getPlayers()) {
					players.add(p);
				}
			}
		}
		return players;
	}
	public BattleSide getSideById(String id) {
		for(BattleSide s : sides) {
			if(s.getId().equalsIgnoreCase(id)) return s;
		}
		return null;
	}
	public BattleSide getSideByMemberId(java.util.UUID memberId) {
		for (BattleSide s : sides) {
			for (Warband w : s.getBands()) {
				if (w.hasMember(memberId)) return s;
			}
		}
		return null;
	}

	public BattleSide getSideByPlayer(Player p) {
		for(BattleSide s : sides) {
			if(s.hasPlayer(p)) return s;
		}
		return null;
	}
	public CapturePoint getPointById(String id) {
		for(CapturePoint p : points) {
			if(p.getId().equalsIgnoreCase(id)) return p;
		}
		return null;
	}
	public boolean hasFriendlyFire() {
		return this.friendlyFire;
	}
	public boolean hasKeepInventory() {
		return this.keepInventory;
	}
	public boolean hasLootEnabled() {
		return this.lootEnabled;
	}
	public boolean hasTeleport() {
		return teleport;
	}
	public void setTeleport(boolean teleport) {
		this.teleport = teleport;
	}
	public boolean hasStarted() {
		return this.started;
	}

	public void setStarted(boolean started) {
		this.started = started;
	}

	public Instant getStartedAt() {
		return startedAt;
	}

	public void setStartedAt(Instant startedAt) {
		this.startedAt = startedAt;
	}
	
	public void setFriendlyFire(boolean b) {
		this.friendlyFire = b;
	}
	public void setKeepInventory(boolean b) {
		this.keepInventory = b;
	}
	public void setLootEnabled(boolean b) {
		this.lootEnabled = b;
	}
	public String start() {
		if(this.started) return null;
		if (battleType != BattleType.RAID) {
			String startError = BattlePlacementValidator.validateForStart(this);
			if (startError != null) {
				return startError;
			}
		}
		this.started = true;
		this.startedAt = Instant.now();
		fireStartedEvent();
		if (battleType == BattleType.RAID) {
			BattleRaidSetup.onStart(this);
		} else {
			BattleBoundsService.resolveAllowedProvinces(this);
			if (warId != null && (battleType == BattleType.FIELD || battleType == BattleType.SIEGE)) {
				BattleLivesService.applyCampaignLives(this);
			}
			if (battleType == BattleType.SIEGE) {
				contestHoldRemainingSeconds = BattleContestSetup.getEffectiveDurationSeconds(this);
			}
			if (battleType == BattleType.FIELD) {
				FieldWinService.checkFieldWin(this);
			} else if (battleType == BattleType.SIEGE) {
				SiegeWinService.checkSiegeWin(this);
			}
		}
		if (battleType == BattleType.FIELD && isCapturePointsEnabled() && isSequentialCapture()) {
			BattleCapturePoints.syncLinearChain(this);
		}
		startTitle();
		pm.setPoints(this.points);
		if(teleport) {
			for(Player p : getAllParticipants()) {
				p.teleport(getSideByPlayer(p).getSpawn());
				if(battleType == BattleType.FIELD && pm.getPoints().size() > 0) {
					Battle b = this;
					new BukkitRunnable()
					{
						public void run()
						{
							BattleInventoryManager inv = new BattleInventoryManager();
							inv.spawnList(p, b);
						}
					}.runTaskLater(SimpleFactions.plugin, 2L);	
				}
			}
		}
		return null;
	}

	private void fireStartedEvent() {
		if (SimpleFactions.plugin == null) {
			return;
		}
		org.bukkit.plugin.PluginManager plugins = Bukkit.getPluginManager();
		if (plugins == null) {
			return;
		}
		plugins.callEvent(new BattleStartedEvent(
				getId(),
				getBattleType(),
				getWarId(),
				BattleParticipantCollector.collect(this)));
	}

	public void end() {
		if(!this.started) return;
		this.started = false;
		allowedProvinceIds = Collections.emptySet();
		contestHoldRemainingSeconds = 0;
		SiegeContestService.clearBattleState(this);
		RaidAttackerEliminationService.clearBattleState(this);
		BattleCasualtyLedger.clear(this);
		pm.end(getAllParticipants());
		if (campaignRaid) {
			CampaignRaidBossBarService.clear(this);
		}
		for(BattleSide s : sides) {
			s.removeBossBar();
		}
	}
	public List<Player> getAllParticipants(){
		List<Player> list = new ArrayList<Player>();
		for(BattleSide s : sides) {
			for(Warband w : s.getBands()) {
				for(Player p : w.getPlayers()) {
					list.add(p);
				}
			}
		}
		return list;
	}
	public void endTitle() {
		for(Player p : getAllParticipants()) {
			p.sendTitle("§aBATTLE OVER", " ", 5, 40, 5);	
			p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1f, 1f);
		}
	}
	public void startTitle() {
		for(Player p : getAllParticipants()) {
			p.sendTitle("§aBATTLE STARTED", " ", 5, 40, 5);
			new BukkitRunnable()
			{
				public void run()
				{
					p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1f, 1f);
				}
			}.runTaskLater(SimpleFactions.plugin, 1L);		
		}
	}
	private void tickPoints() {
		if (capturePointsEnabled && !pm.getPoints().isEmpty()) {
			pm.tick();
		}
	}
	
	public void tick() {
		if(!started) return;
		if (campaignRaid) {
			CampaignRaidBossBarService.tickCampaignRaid(this);
		} else {
			for(BattleSide s : sides) {
				s.updateBossBar(getAllParticipants());
			}
		}
		tickPoints();
		if (battleType == BattleType.FIELD) {
			FieldWinService.checkFieldWin(this);
		}
		if (battleType == BattleType.SIEGE) {
			SiegeContestService.tick(this);
			SiegeWinService.checkSiegeWin(this);
		}
		if (battleType == BattleType.RAID) {
			RaidWinService.checkRaidWin(this);
		}
	}
	public List<BattleSide> getSides() {
		return this.sides;
	}

	public Integer getProvinceId() {
		return provinceId;
	}

	public void setProvinceId(Integer provinceId) {
		this.provinceId = provinceId;
	}

	public BattleType getBattleType() {
		return battleType;
	}

	public void setBattleType(BattleType battleType) {
		this.battleType = battleType;
	}

	public Integer getWarId() {
		return warId;
	}

	public void setWarId(Integer warId) {
		this.warId = warId;
	}

	public String getTemplateName() {
		return templateName;
	}

	public void setTemplateName(String templateName) {
		this.templateName = templateName;
	}

	public ContestArea getContestArea() {
		return contestArea;
	}

	public void setContestArea(ContestArea contestArea) {
		this.contestArea = contestArea;
	}

	public int getContestDurationSeconds() {
		return contestDurationSeconds;
	}

	public void setContestDurationSeconds(int contestDurationSeconds) {
		this.contestDurationSeconds = contestDurationSeconds;
	}

	public DefenderRespawnMode getDefenderRespawnMode() {
		return defenderRespawnMode;
	}

	public void setDefenderRespawnMode(DefenderRespawnMode defenderRespawnMode) {
		this.defenderRespawnMode = defenderRespawnMode;
	}

	public Integer getDefenderLives() {
		return defenderLives;
	}

	public void setDefenderLives(Integer defenderLives) {
		this.defenderLives = defenderLives;
	}

	public CapturePointDefinition getRaidTarget() {
		return raidTarget;
	}

	public void setRaidTarget(CapturePointDefinition raidTarget) {
		this.raidTarget = raidTarget;
	}

	public boolean isNavalVariant() {
		return navalVariant;
	}

	public void setNavalVariant(boolean navalVariant) {
		this.navalVariant = navalVariant;
	}

	public Location getNavalSpawn() {
		return navalSpawn;
	}

	public void setNavalSpawn(Location navalSpawn) {
		this.navalSpawn = navalSpawn;
	}

	public boolean isCampaignRaid() {
		return campaignRaid;
	}

	public void setCampaignRaid(boolean campaignRaid) {
		this.campaignRaid = campaignRaid;
	}

	public void clearSides() {
		for (BattleSide side : sides) {
			side.removeBossBar();
		}
		sides.clear();
	}

	public void clearPoints() {
		points.clear();
	}

	public void clearTemplateMetadata() {
		contestArea = null;
		contestDurationSeconds = 0;
		defenderRespawnMode = null;
		defenderLives = null;
		raidTarget = null;
		navalVariant = false;
		navalSpawn = null;
		campaignRaid = false;
	}

	public void resetBaseSettings() {
		friendlyFire = true;
		keepInventory = true;
		lootEnabled = true;
		teleport = false;
		locked = true;
		lt = LifeType.COLLECTIVE;
		lives = 25;
		sequentialCapture = false;
		capturePointsEnabled = false;
	}

	public Set<Integer> getAllowedProvinceIds() {
		return allowedProvinceIds;
	}

	public void setAllowedProvinceIds(Set<Integer> allowedProvinceIds) {
		if (allowedProvinceIds == null || allowedProvinceIds.isEmpty()) {
			this.allowedProvinceIds = Collections.emptySet();
		} else {
			this.allowedProvinceIds = Collections.unmodifiableSet(new HashSet<>(allowedProvinceIds));
		}
	}

	public boolean isSequentialCapture() {
		return sequentialCapture;
	}

	public void setSequentialCapture(boolean sequentialCapture) {
		this.sequentialCapture = sequentialCapture;
	}

	public boolean isCapturePointsEnabled() {
		return capturePointsEnabled;
	}

	public void setCapturePointsEnabled(boolean capturePointsEnabled) {
		this.capturePointsEnabled = capturePointsEnabled;
	}

	public int getContestHoldRemainingSeconds() {
		return contestHoldRemainingSeconds;
	}

	public void setContestHoldRemainingSeconds(int contestHoldRemainingSeconds) {
		this.contestHoldRemainingSeconds = Math.max(0, contestHoldRemainingSeconds);
	}
}
