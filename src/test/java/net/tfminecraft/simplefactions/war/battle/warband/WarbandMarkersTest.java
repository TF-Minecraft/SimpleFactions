package net.tfminecraft.simplefactions.war.battle.warband;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WarbandMarkersTest {
	private World world;
	private Player viewer;
	private Player target;

	@BeforeEach
	void setUp() {
		world = mock(World.class);
		viewer = player(0);
		target = player(10);
	}

	private Player player(double x) {
		Player player = mock(Player.class);
		when(player.getWorld()).thenReturn(world);
		when(player.getLocation()).thenReturn(new Location(world, x, 64, 0));
		when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
		when(player.getBoundingBox()).thenReturn(new BoundingBox(x - 0.3, 64, -0.3, x + 0.3, 65.5, 0.3));
		return player;
	}

	private void verifyNothingSent() {
		verify(viewer, never()).spawnParticle(any(Particle.class), anyDouble(), anyDouble(), anyDouble(), anyInt(),
				anyDouble(), anyDouble(), anyDouble(), anyDouble(), any(), anyBoolean());
	}

	@Test
	void theDotSitsAboveTheHitboxAndIsForcedSoItShowsFurtherAway() {
		WarbandMarkers.show(viewer, target, WarbandMarkers.FRIEND);

		verify(viewer).spawnParticle(Particle.DUST, 10.0, 65.5 + WarbandMarkers.ABOVE_HEAD, 0.0, WarbandMarkers.SPECKS,
				WarbandMarkers.SPREAD, WarbandMarkers.SPREAD, WarbandMarkers.SPREAD, 0.0, WarbandMarkers.FRIEND, true);
	}

	@Test
	void noDotForYourselfTheDeadOrSpectators() {
		WarbandMarkers.show(viewer, viewer, WarbandMarkers.FRIEND);
		when(target.isDead()).thenReturn(true);
		WarbandMarkers.show(viewer, target, WarbandMarkers.ALLY);
		when(target.isDead()).thenReturn(false);
		when(target.getGameMode()).thenReturn(GameMode.SPECTATOR);
		WarbandMarkers.show(viewer, target, WarbandMarkers.ALLY);
		verifyNothingSent();
	}

	@Test
	void noPacketForAnotherWorldOrBeyondRange() {
		when(target.getWorld()).thenReturn(mock(World.class));
		WarbandMarkers.show(viewer, target, WarbandMarkers.FRIEND);
		Player far = player(WarbandMarkers.RANGE + 1);
		WarbandMarkers.show(viewer, far, WarbandMarkers.FRIEND);
		verifyNothingSent();
	}
}
