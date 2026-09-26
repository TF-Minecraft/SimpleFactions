package net.tfminecraft.simplefactions.managers;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

import net.tfminecraft.simplefactions.army.Military;
import net.tfminecraft.simplefactions.database.Database;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.war.core.War;
import net.tfminecraft.simplefactions.war.enums.WarEndReason;

class WarNoticeTest {
	private List<War> savedWars;
	private List<Faction> savedFactions;
	private Consumer<Player> savedHorn;
	private final List<Player> players = new ArrayList<>();
	private final List<Player> horns = new ArrayList<>();

	@BeforeEach
	void setUp() {
		savedWars = new ArrayList<>(WarManager.get());
		WarManager.get().clear();
		savedFactions = new ArrayList<>(FactionManager.factions);
		FactionManager.factions.clear();
		savedHorn = WarManager.declaredHorn;
		WarManager.declaredHorn = horns::add;
	}

	@AfterEach
	void tearDown() {
		WarManager.declaredHorn = savedHorn;
		for (Player player : players) {
			RequestManager.remove(player);
		}
		WarManager.get().clear();
		WarManager.get().addAll(savedWars);
		FactionManager.factions.clear();
		FactionManager.factions.addAll(savedFactions);
	}

	@Test
	void warDeclaredAndEnded_reachEveryParticipatingFaction() {
		Faction attacker = faction("atk", "AtkLead", "Attackers", List.of("AtkLead"));
		Faction defender = faction("def", "DefLead", "Defenders", List.of("DefLead"));
		Faction subject = faction("sub", "SubLead", "Subjects", List.of("SubjectMember"));
		Faction joined = faction("ally", "AllyLead", "Allies", List.of("JoinedMember"));
		Faction pending = faction("pending", "PendingLead", "Pending", List.of("PendingMember"));
		Map<String, Player> online = new HashMap<>();
		online.put("AtkLead", online("AtkLead"));
		online.put("DefLead", online("DefLead"));
		online.put("SubjectMember", online("SubjectMember"));
		online.put("JoinedMember", online("JoinedMember"));
		online.put("PendingMember", online("PendingMember"));

		try (MockedStatic<RelationManager> relations = mockStatic(RelationManager.class);
				MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
				MockedConstruction<Database> ignored = mockConstruction(Database.class)) {
			relations.when(() -> RelationManager.getSubjects(attacker)).thenReturn(List.of(subject));
			relations.when(() -> RelationManager.getSubjects(defender)).thenReturn(List.of());
			relations.when(() -> RelationManager.getAllies(attacker)).thenReturn(List.of(joined, pending));
			relations.when(() -> RelationManager.getAllies(defender)).thenReturn(List.of());
			bukkit.when(() -> Bukkit.getPlayerExact(anyString()))
					.thenAnswer(invocation -> online.get(invocation.getArgument(0)));

			War war = new War(41, attacker, defender);
			WarManager.addWar(war);

			verifyDeclared(online.get("AtkLead"));
			verifyDeclared(online.get("DefLead"));
			verifyDeclared(online.get("SubjectMember"));
			verify(online.get("JoinedMember"), never()).sendTitle(anyString(), anyString(), anyInt(), anyInt(), anyInt());
			verify(online.get("PendingMember"), never()).sendTitle(anyString(), anyString(), anyInt(), anyInt(), anyInt());

			war.getAttackers().getMainParticipants().get(0).getAllies().put(joined, true);
			WarManager.endWar(war, WarEndReason.ADMIN_END);

			verify(online.get("AtkLead")).sendMessage("§7The war has ended.");
			verify(online.get("DefLead")).sendMessage("§7The war has ended.");
			verify(online.get("SubjectMember")).sendMessage("§7The war has ended.");
			verify(online.get("JoinedMember")).sendMessage("§7The war has ended.");
			verify(online.get("PendingMember"), never()).sendMessage("§7The war has ended.");
			assertFalse(war.isActive());
		}
	}

	@Test
	void callToArms_promptsDeclineAndNotifiesTheJoiningFaction() {
		Faction attacker = faction("atk", "AtkLead", "Attackers", List.of("AtkLead"));
		Faction defender = faction("def", "DefLead", "Defenders", List.of("DefLead"));
		Faction ally = faction("ally", "AllyLead", "Allies", List.of("AllyLead", "AllyGrunt"));
		Military military = mock(Military.class);
		when(military.getRegiments()).thenReturn(List.of());
		when(ally.getMilitary()).thenReturn(military);
		Guild guild = mock(Guild.class);
		when(guild.getFaction()).thenReturn(attacker);
		when(attacker.getOrCreateMainGuild()).thenReturn(guild);
		FactionManager.factions.add(attacker);
		FactionManager.factions.add(ally);

		Player sender = online("AtkLead");
		Player allyLeader = online("AllyLead");
		Player allyGrunt = online("AllyGrunt");
		Map<String, Player> online = new HashMap<>();
		online.put("AtkLead", sender);
		online.put("AllyLead", allyLeader);
		online.put("AllyGrunt", allyGrunt);

		try (MockedStatic<RelationManager> relations = mockStatic(RelationManager.class);
				MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
				MockedConstruction<Database> ignored = mockConstruction(Database.class)) {
			relations.when(() -> RelationManager.getSubjects(attacker)).thenReturn(List.of());
			relations.when(() -> RelationManager.getSubjects(defender)).thenReturn(List.of());
			relations.when(() -> RelationManager.getSubjects(ally)).thenReturn(List.of());
			relations.when(() -> RelationManager.getAllies(attacker)).thenReturn(List.of(ally));
			relations.when(() -> RelationManager.getAllies(defender)).thenReturn(List.of());
			bukkit.when(() -> Bukkit.getPlayerExact(anyString()))
					.thenAnswer(invocation -> online.get(invocation.getArgument(0)));

			War war = new War(42, attacker, defender);
			WarManager.sendRequest(sender, attacker, ally, war);

			verify(allyLeader).sendMessage("§7Type §c/faction decline §7to decline");
			assertTrue(RequestManager.hasRequest(allyLeader));

			RequestManager.accept(allyLeader);

			verify(allyLeader).sendMessage(startsWith("§aYour faction has joined the "));
			verifyDeclared(allyLeader);
			verifyDeclared(allyGrunt);
			verify(sender, never()).sendTitle(anyString(), anyString(), anyInt(), anyInt(), anyInt());
			verify(sender).sendMessage("Allies §aaccepted your call to arms");
			assertFalse(RequestManager.hasRequest(allyLeader));
		}
	}

	private void verifyDeclared(Player player) {
		verify(player).sendTitle("§cWar Declared!", "§e/war list §7to view", 10, 120, 10);
		assertTrue(horns.contains(player));
	}

	private Player online(String name) {
		Player player = mock(Player.class);
		when(player.getName()).thenReturn(name);
		when(player.isOnline()).thenReturn(true);
		players.add(player);
		return player;
	}

	private static Faction faction(String id, String leader, String name, List<String> members) {
		Faction faction = mock(Faction.class);
		when(faction.getId()).thenReturn(id);
		when(faction.getLeader()).thenReturn(leader);
		when(faction.getName()).thenReturn(name);
		when(faction.getMembers()).thenReturn(members);
		return faction;
	}
}
