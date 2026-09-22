package me.Plugins.SimpleFactions.government.movement;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.Objects.Handler.LawHandler;
import me.Plugins.SimpleFactions.government.Council;
import me.Plugins.SimpleFactions.government.Government;
import me.Plugins.SimpleFactions.laws.Law;
import me.Plugins.SimpleFactions.laws.LawGroup;

class CoupServiceTest {

	@Test
	void autocracy_promotesAndLeavesCouncil() {
		Fixture fx = fixture("autocracy", true);
		CoupService.apply(fx.faction, "alice");
		verify(fx.faction).promoteToLeader("alice");
		verify(fx.faction, never()).applyLaw(any(), any());
		verify(fx.council, never()).clearMembers();
	}

	@Test
	void community_promotesAndLeavesCouncil() {
		Fixture fx = fixture("community", true);
		CoupService.apply(fx.faction, "alice");
		verify(fx.faction).promoteToLeader("alice");
		verify(fx.faction, never()).applyLaw(any(), any());
		verify(fx.council, never()).clearMembers();
	}

	@Test
	void oligarchy_promotesAndClearsCouncilWithoutApplyLaw() {
		Fixture fx = fixture("oligarchy", true);
		CoupService.apply(fx.faction, "alice");
		verify(fx.faction).promoteToLeader("alice");
		verify(fx.faction, never()).applyLaw(any(), any());
		verify(fx.council).clearMembers();
	}

	@Test
	void plutocracy_switchesToOligarchyThenClears() {
		Fixture fx = fixture("plutocracy", true);
		CoupService.apply(fx.faction, "alice");
		InOrder order = inOrder(fx.faction, fx.council);
		order.verify(fx.faction).promoteToLeader("alice");
		order.verify(fx.faction).applyLaw(fx.oligarchy, fx.group);
		order.verify(fx.council).clearMembers();
	}

	@Test
	void democracy_switchesToOligarchyThenClears() {
		Fixture fx = fixture("democracy", true);
		CoupService.apply(fx.faction, "alice");
		verify(fx.faction).promoteToLeader("alice");
		verify(fx.faction).applyLaw(fx.oligarchy, fx.group);
		verify(fx.council).clearMembers();
	}

	@Test
	void cannotBecomeLeader_doesNothing() {
		Fixture fx = fixture("oligarchy", false);
		CoupService.apply(fx.faction, "alice");
		verify(fx.faction, never()).promoteToLeader(any());
		verify(fx.faction, never()).applyLaw(any(), any());
		verify(fx.council, never()).clearMembers();
	}

	@Test
	void nullTarget_doesNothing() {
		Fixture fx = fixture("oligarchy", true);
		CoupService.apply(fx.faction, null);
		verify(fx.faction, never()).promoteToLeader(any());
		verify(fx.council, never()).clearMembers();
	}

	@Test
	void unknownGovernment_promotesOnly() {
		Fixture fx = fixture("theocracy", true);
		CoupService.apply(fx.faction, "alice");
		verify(fx.faction).promoteToLeader("alice");
		verify(fx.faction, never()).applyLaw(any(), any());
		verify(fx.council, never()).clearMembers();
	}

	@Test
	void plutocracy_missingOligarchyLaw_stillClears() {
		Fixture fx = fixture("plutocracy", true);
		when(fx.group.getLaw("oligarchy")).thenReturn(null);
		CoupService.apply(fx.faction, "alice");
		verify(fx.faction).promoteToLeader("alice");
		verify(fx.faction, never()).applyLaw(any(), any());
		verify(fx.council).clearMembers();
	}

	private static Fixture fixture(String governmentId, boolean canLead) {
		Fixture fx = new Fixture();
		fx.faction = mock(Faction.class);
		fx.handler = mock(LawHandler.class);
		fx.group = mock(LawGroup.class);
		fx.current = mock(Law.class);
		fx.oligarchy = mock(Law.class);
		fx.government = mock(Government.class);
		fx.council = mock(Council.class);
		when(fx.faction.canBecomeLeader("alice")).thenReturn(canLead);
		when(fx.faction.getLawHandler()).thenReturn(fx.handler);
		when(fx.faction.getGovernment()).thenReturn(fx.government);
		when(fx.government.getCouncil()).thenReturn(fx.council);
		when(fx.handler.getGroup("government")).thenReturn(fx.group);
		when(fx.group.getCurrent()).thenReturn(fx.current);
		when(fx.current.getId()).thenReturn(governmentId);
		when(fx.group.getLaw("oligarchy")).thenReturn(fx.oligarchy);
		return fx;
	}

	private static final class Fixture {
		Faction faction;
		LawHandler handler;
		LawGroup group;
		Law current;
		Law oligarchy;
		Government government;
		Council council;
	}
}
