package net.tfminecraft.simplefactions.vehicles;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import net.tfminecraft.simplefactions.war.freeze.PreparationFreeze;
import net.tfminecraft.vfbuilders.core.Blueprint;
import net.tfminecraft.vfbuilders.events.BeginVehicleConstructionEvent;

class VehicleConstructionFreezeTest {

	@Test
	void aFrozenBuilderCannotStartAVehicle() {
		Player builder = mock(Player.class);
		when(builder.getName()).thenReturn("Builder");
		Blueprint blueprint = mock(Blueprint.class);
		when(blueprint.getId()).thenReturn("ship");
		BeginVehicleConstructionEvent event = new BeginVehicleConstructionEvent(builder, blueprint, null, null);
		try (MockedStatic<PreparationFreeze> freeze = mockStatic(PreparationFreeze.class, CALLS_REAL_METHODS)) {
			freeze.when(() -> PreparationFreeze.refusalForMember("Builder", "vehicles"))
					.thenReturn("Your war is in its buildup. No new vehicles can be started until the first battle.");

			new VehicleIntegrationListener().onBeginVehicleConstruction(event);
		}

		assertTrue(event.isCancelled());
		verify(builder).sendMessage("§cYour war is in its buildup. No new vehicles can be started until the first battle.");
	}
}
