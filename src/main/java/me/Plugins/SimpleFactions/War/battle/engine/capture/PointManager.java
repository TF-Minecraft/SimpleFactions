package me.Plugins.SimpleFactions.War.battle.engine.capture;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;

import net.tfminecraft.VehicleFramework.VehicleFramework;
import me.Plugins.SimpleFactions.War.battle.engine.core.Battle;
import me.Plugins.SimpleFactions.War.battle.engine.capture.CapturePoint;
import me.Plugins.SimpleFactions.War.battle.engine.core.BattleSide;
import me.Plugins.SimpleFactions.War.battle.warband.Warband;

public class PointManager {
	private Battle b;
	private final CapturePointMarkerService markers = new CapturePointMarkerService();
	public List<CapturePoint> points = new ArrayList<CapturePoint>();
	public List<CapturePoint> getPoints() {
		return points;
	}
	public void setPoints(List<CapturePoint> points) {
		this.points.clear();
		for(CapturePoint p : points) {
			this.points.add(new CapturePoint(p));
		}
	}
	public PointManager(Battle b) {
		this.b = b;
	}
	public void tick() {
		for(Player p : b.getPlayers()) {
			if(VehicleFramework.getVehicleManager().get(p) != null) {
				Scoreboard board = p.getScoreboard();
				Objective obj = board.getObjective("pointDummy");
				if(obj != null) obj.unregister();
				continue;
			}
			scoreboard(p);
		}
		for(CapturePoint p : points) {
			p.updateNearbyEntities();
			if (!b.isSequentialCapture() || CapturePoint.isFrontPoint(p, points, b)) {
				p.updateSides(b.getSides());
			}
			subtitle(p);
		}
		markers.tick(b, this);
	}
	private void scoreboard(Player p) {
		ScoreboardManager manager = Bukkit.getScoreboardManager();
		Scoreboard board = manager.getNewScoreboard();
		
		Objective obj = board.registerNewObjective("pointDummy", Criteria.DUMMY, "§e§lCapture Points");
		obj.setDisplaySlot(DisplaySlot.SIDEBAR);
		int i = 0;
		for(CapturePoint point : points) {
			String message = "";
			if(isOnSide(point.getController(), p)) {
				message = "§a"+point.getId()+" - Friendly §f: "+point.getCaptureProgress()+"%";
			} else {
				message = "§c"+point.getId()+" - Enemy §f: "+point.getCaptureProgress()+"%";
			}
			Score s = obj.getScore(message);
			s.setScore(i);
			i++;
		}
		Score scoreDivider = obj.getScore(ChatColor.RED + "=-=-=-=-=-=-=-=-=-=-=");
		scoreDivider.setScore(i);
		p.setScoreboard(board);
	}
	public boolean isOnSide(BattleSide s, Player p) {
		for(Warband w : s.getBands()) {
			for(Player wp : w.getPlayers()) {
				if(wp.equals(p)) return true;
			}
		}
		return false;
	}
	public void end(List<Player> list) {
		markers.reset();
		ScoreboardManager manager = Bukkit.getScoreboardManager();
		for (Player p : list) {
			if (p == null) {
				continue;
			}
			Scoreboard board = p.getScoreboard();
			Objective obj = board.getObjective("pointDummy");
			if (obj != null) {
				obj.unregister();
			}
			if (manager != null) {
				p.setScoreboard(manager.getMainScoreboard());
			}
		}
	}
	public void subtitle(CapturePoint p) {
		for(Entity en : p.getNearbyEntities()) {
			if(en instanceof Player) {
				Player player = (Player) en;
				if(isOnSide(p.getController(), player)) {
					player.sendTitle(" ", "§a"+p.getId()+" - FRIENDLY §f: §e"+p.getCaptureProgress()+"%", 0, 20, 0); 
				} else {
					player.sendTitle(" ", "§c"+p.getId()+" - ENEMY §f: §e"+p.getCaptureProgress()+"%", 0, 20, 0); 
				}
			}
		}
	}
}
