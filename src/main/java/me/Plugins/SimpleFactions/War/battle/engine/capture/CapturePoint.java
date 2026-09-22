package me.Plugins.SimpleFactions.War.battle.engine.capture;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import me.Plugins.SimpleFactions.Cache;
import me.Plugins.SimpleFactions.War.battle.engine.core.Battle;
import me.Plugins.SimpleFactions.War.battle.engine.core.BattleSide;
import me.Plugins.SimpleFactions.War.battle.template.BattleTemplate;

public class CapturePoint {
	private String id;
	private Location loc;
	private BattleSide controller;
	private int captureProgress;
	private Collection<Entity> e;
	private HashMap<BattleSide, Integer> playerSide = new HashMap<>();
	private int sequenceIndex;
	private String advanceSideId;
	
	public Collection<Entity> getNearbyEntities(){
		return e;
	}
	public void updateNearbyEntities() {
		e = loc.getWorld().getNearbyEntities(loc, 10, 5, 10);
	}
	public void updateSides(List<BattleSide> sides) {
		playerSide.clear();
		for(Entity en : e) {
			if(en instanceof Player) {
				Player p = (Player) en;
				for(BattleSide s : sides) {
					if(s.hasPlayer(p)) {
						if(!playerSide.containsKey(s)) {
							playerSide.put(s, 1);
						} else {
							playerSide.put(s, playerSide.get(s)+1);
						}
					}
				}
			}
		}
		processCaptureTicks();
	}

	void processCaptureTicks() {
		if (playerSide.isEmpty()) {
			return;
		}
		int maxValueInMap = Collections.max(playerSide.values());

		for (Entry<BattleSide, Integer> entry : playerSide.entrySet()) {
			if (entry.getValue() == maxValueInMap && entry.getValue() >= Cache.battleCaptureMinPlayers) {
				tickCapture(entry.getKey());
			}
		}
	}
	public BattleSide getController() {
		return controller;
	}

	public Location getLoc() {
		return loc;
	}
	public String getId() {
		return this.id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public int getCaptureProgress() {
		return captureProgress;
	}

	public void setCaptureProgress(int captureProgress) {
		this.captureProgress = Math.max(0, Math.min(100, captureProgress));
	}

	public int getSequenceIndex() {
		return sequenceIndex;
	}

	public void setSequenceIndex(int sequenceIndex) {
		this.sequenceIndex = sequenceIndex;
	}

	public String getAdvanceSideId() {
		return advanceSideId;
	}

	public void setAdvanceSideId(String advanceSideId) {
		this.advanceSideId = advanceSideId;
	}

	public void setController(BattleSide controller) {
		this.controller = controller;
	}

	public boolean isFullyControlledBy(String sideId) {
		return controller != null
				&& sideId != null
				&& controller.getId().equalsIgnoreCase(sideId)
				&& captureProgress >= 100;
	}

	boolean isContested() {
		int sidesMeetingMin = 0;
		for (Integer count : playerSide.values()) {
			if (count != null && count >= Cache.battleCaptureMinPlayers) {
				sidesMeetingMin++;
			}
		}
		if (sidesMeetingMin >= 2) {
			return true;
		}
		return !playerSide.isEmpty() && captureProgress > 0 && captureProgress < 100;
	}

	public static boolean isFrontPoint(CapturePoint point, List<CapturePoint> points) {
		return isFrontPoint(point, points, false);
	}

	public static boolean isFrontPoint(CapturePoint point, List<CapturePoint> points, Battle battle) {
		if (point == null || points == null || points.isEmpty()) {
			return true;
		}
		if (battle == null || !battle.isSequentialCapture()) {
			return true;
		}
		return isFrontPointAtMeet(
				point,
				points,
				BattleTemplate.ATTACKER_SIDE,
				BattleTemplate.DEFENDER_SIDE);
	}

	public static boolean isFrontPoint(CapturePoint point, List<CapturePoint> points, boolean globalSequential) {
		if (point == null) {
			return true;
		}
		if (!globalSequential) {
			if (point.getAdvanceSideId() == null) {
				return true;
			}
			return isFrontPointPerSide(point, points);
		}
		return isFrontPointAtMeet(
				point,
				points,
				BattleTemplate.ATTACKER_SIDE,
				BattleTemplate.DEFENDER_SIDE);
	}

	/**
	 * Linear frontline: the one or two chain points where defender-held and attacker-held
	 * territory meet (A = defender spawn end, last letter = attacker spawn end).
	 */
	static boolean isFrontPointAtMeet(
			CapturePoint point,
			List<CapturePoint> points,
			String attackerId,
			String defenderId) {
		if (point == null || points == null || points.isEmpty()) {
			return true;
		}
		for (int index : resolveFrontlineIndices(points, attackerId, defenderId)) {
			if (point.getSequenceIndex() == index) {
				return true;
			}
		}
		return false;
	}

	static int[] resolveFrontlineIndices(List<CapturePoint> points, String attackerId, String defenderId) {
		if (points == null || points.isEmpty()) {
			return new int[0];
		}
		List<CapturePoint> ordered = new ArrayList<>(points);
		ordered.sort(Comparator.comparingInt(CapturePoint::getSequenceIndex));

		int minIndex = ordered.get(0).getSequenceIndex();
		int maxIndex = ordered.get(ordered.size() - 1).getSequenceIndex();

		boolean anyAttackerHeld = false;
		boolean anyDefenderHeld = false;
		for (CapturePoint candidate : ordered) {
			if (candidate.isFullyControlledBy(attackerId)) {
				anyAttackerHeld = true;
			}
			if (candidate.isFullyControlledBy(defenderId)) {
				anyDefenderHeld = true;
			}
		}

		if (!anyAttackerHeld) {
			return new int[] { maxIndex };
		}
		if (!anyDefenderHeld) {
			return new int[] { minIndex };
		}

		int lowFront = maxIndex;
		for (CapturePoint candidate : ordered) {
			if (!candidate.isFullyControlledBy(defenderId)) {
				lowFront = candidate.getSequenceIndex();
				break;
			}
		}

		int highFront = minIndex;
		for (int i = ordered.size() - 1; i >= 0; i--) {
			CapturePoint candidate = ordered.get(i);
			if (!candidate.isFullyControlledBy(attackerId)) {
				highFront = candidate.getSequenceIndex();
				break;
			}
		}

		if (lowFront == highFront) {
			return new int[] { lowFront };
		}
		return new int[] { highFront, lowFront };
	}

	private static boolean isFrontPointPerSide(CapturePoint point, List<CapturePoint> points) {
		String sideId = point.getAdvanceSideId();
		int frontIndex = Integer.MAX_VALUE;
		for (CapturePoint candidate : points) {
			if (candidate.getAdvanceSideId() == null
					|| !candidate.getAdvanceSideId().equalsIgnoreCase(sideId)) {
				continue;
			}
			if (!candidate.isFullyControlledBy(sideId)) {
				frontIndex = Math.min(frontIndex, candidate.getSequenceIndex());
			}
		}
		if (frontIndex == Integer.MAX_VALUE) {
			return true;
		}
		return point.getSequenceIndex() == frontIndex;
	}
	private void tickCapture(BattleSide s) {
		if(controller.getId().equals(s.getId())) {
			if(this.captureProgress < 100) {
				this.captureProgress++;
			}
		} else {
			this.captureProgress--;
			if(this.captureProgress < 0) {
				this.controller = s;
				this.captureProgress++;
			}
		}
	}
	public CapturePoint(String id, Location l, BattleSide c, int prog) {
		this.id = id;
		this.loc = l;
		this.controller = c;
		this.captureProgress = prog;
	}
	public CapturePoint(CapturePoint another) {
		this.id = another.getId();
		this.loc = another.getLoc();
		this.controller = another.getController();
		this.captureProgress = another.getCaptureProgress();
		this.sequenceIndex = another.getSequenceIndex();
		this.advanceSideId = another.getAdvanceSideId();
	}
}
