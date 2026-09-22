package me.Plugins.SimpleFactions.War.civilwar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

import me.Plugins.SimpleFactions.War.enums.WarGoalType;
import me.Plugins.SimpleFactions.government.movement.Action;
import me.Plugins.SimpleFactions.government.movement.Movement;
import me.Plugins.SimpleFactions.government.movement.cause.Cause;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

class CivilWarGoalMapperTest {

	@Test
	void mapsFirstCauseActions() {
		assertEquals(WarGoalType.OVERTHROW, CivilWarGoalMapper.fromAction(Action.CHANGE_LEADER));
		assertEquals(WarGoalType.CHANGE_LAW, CivilWarGoalMapper.fromAction(Action.LAW_CHANGE));
		assertEquals(WarGoalType.CHANGE_TAX, CivilWarGoalMapper.fromAction(Action.TAX_CHANGE));
		assertEquals(WarGoalType.FORCE_PEACE, CivilWarGoalMapper.fromAction(Action.WHITE_PEACE));
		assertEquals(WarGoalType.FORCE_PEACE, CivilWarGoalMapper.fromAction(Action.SURRENDER));
		assertNull(CivilWarGoalMapper.fromAction(Action.INDEPENDENCE));
		assertNull(CivilWarGoalMapper.fromAction(Action.NATIONHOOD));
	}

	@Test
	void fromFirstCause_usesIndexZero() {
		Cause first = mock(Cause.class);
		Cause second = mock(Cause.class);
		when(first.getAction()).thenReturn(Action.CHANGE_LEADER);
		when(second.getAction()).thenReturn(Action.INDEPENDENCE);
		Movement movement = mock(Movement.class);
		when(movement.getCauses()).thenReturn(List.of(first, second));
		assertEquals(WarGoalType.OVERTHROW, CivilWarGoalMapper.fromFirstCause(movement));
	}
}