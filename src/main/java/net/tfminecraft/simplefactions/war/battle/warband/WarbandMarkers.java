package net.tfminecraft.simplefactions.war.battle.warband;

import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;

/**
 * The dot over a friendly soldier's head. It sits above the top of the player's hitbox, so tall
 * helmets and sneaking do not hide it. One packet a second per viewer and target, as before, but
 * the packet now carries three bigger overlapping specks so the dot stays visible between sends.
 * Targets in another world, out of range, dead or spectating get no packet at all.
 */
public final class WarbandMarkers {
	static final double RANGE = 64.0;
	public static final double ABOVE_HEAD = 0.6;
	static final int SPECKS = 3;
	static final double SPREAD = 0.06;
	public static final Particle.DustOptions FRIEND = new Particle.DustOptions(Color.LIME, 1.6f);
	public static final Particle.DustOptions ALLY = new Particle.DustOptions(Color.fromRGB(70, 150, 255), 1.6f);

	private WarbandMarkers() {}

	/** Shows {@code target}'s marker to {@code viewer} only. */
	public static void show(Player viewer, Player target, Particle.DustOptions dust) {
		if (viewer.equals(target) || target.isDead() || target.getGameMode() == GameMode.SPECTATOR
				|| !viewer.getWorld().equals(target.getWorld())) {
			return;
		}
		Location at = target.getLocation();
		if (viewer.getLocation().distanceSquared(at) > RANGE * RANGE) {
			return;
		}
		// Forced so it still draws past the client's normal 32-block particle range.
		viewer.spawnParticle(Particle.DUST, at.getX(), target.getBoundingBox().getMaxY() + ABOVE_HEAD, at.getZ(),
				SPECKS, SPREAD, SPREAD, SPREAD, 0, dust, true);
	}
}
