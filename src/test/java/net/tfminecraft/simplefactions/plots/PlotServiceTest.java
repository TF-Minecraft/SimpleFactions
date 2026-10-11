package net.tfminecraft.simplefactions.plots;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.tfminecraft.simplefactions.espionage.EspionageService;
import net.tfminecraft.simplefactions.espionage.EspionageState;
import net.tfminecraft.simplefactions.espionage.SpecialPositionAssignment;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.objects.Faction;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PlotServiceTest {
    @TempDir Path directory;
    private PlotTestWorld world;
    private Player master, assassin, helper, target, guard, stranger;
    private CommandSender staff;
    private List<String> staffSaid;

    @BeforeEach
    void setup() throws Exception {
        world = new PlotTestWorld(directory);
        master = world.player("Master", "c-master", 50);
        assassin = world.player("Assassin", "c-assassin", 60);
        helper = world.player("Helper", "c-helper", 80);
        target = world.player("Target", "c-target", 40);
        guard = world.player("Guard", "c-guard", 70);
        stranger = world.player("Stranger", null, null);
        staff = mock(CommandSender.class);
        staffSaid = new java.util.ArrayList<>();
        org.mockito.Mockito.doAnswer(call -> staffSaid.add(call.getArgument(0))).when(staff).sendMessage(anyString());
    }

    @AfterEach
    void close() { world.close(); }

    private Plot approved() {
        Plot plot = PlotService.request(master);
        assertTrue(PlotService.approve(staff, plot.code, "Target"));
        return plot;
    }

    private Plot recruited() {
        Plot plot = approved();
        assertTrue(PlotService.invite(master, assassin));
        assertTrue(PlotService.accept(assassin, plot.code));
        assertTrue(PlotService.assign(master, "c-assassin", PlotRole.ASSASSIN, null));
        return plot;
    }

    private Plot building() {
        Plot plot = recruited();
        assertTrue(PlotService.begin(master));
        return plot;
    }

    /** The assassin's hunt has already taken effect. */
    private void hunting(Plot plot, Player blade) {
        plot.member(world.character(blade).characterId).huntingSince = world.clock.get() - PlotConfig.huntWarmup();
    }

    private Faction factionGuardedBy(String characterId, int office) {
        Faction faction = mock(Faction.class);
        EspionageState state = new EspionageState();
        SpecialPositionAssignment holder = new SpecialPositionAssignment();
        holder.playerName = "Guard";
        holder.characterId = characterId;
        state.appoint(holder, office);
        when(faction.getEspionage()).thenReturn(state);
        world.factions.when(() -> FactionManager.getByMember("Target")).thenReturn(faction);
        world.espionage.when(() -> EspionageService.hasSpymaster(faction)).thenReturn(true);
        world.espionage.when(() -> EspionageService.effectiveAptitude(faction, holder)).thenReturn(office);
        return faction;
    }

    @Test
    void intrigueUsesTheRollRollsOnFirstPlayAndOtherwiseTheBase() {
        assertEquals(50, PlotService.intrigue(world.character(master)));
        world.intrigue.remove("c-master");
        assertEquals(40, PlotService.intrigue(world.character(master)));
        world.intrigue.remove("c-master");
        world.espionage.when(() -> EspionageService.intrigue(any(), anyString())).thenThrow(new IOException("disk"));
        assertEquals(50, PlotService.intrigue(world.character(master)));
        world.logOff(helper);
        world.intrigue.remove("c-helper");
        assertEquals(50, PlotService.intrigue(new PlotCharacter("c-helper", helper.getUniqueId(), "Helper", "H")));
        assertEquals(58, PlotService.offense(world.character(master)));
        assertEquals(58, PlotService.defense(world.character(master)));
    }

    @Test
    void approachAndDefenderChoicesHaveCooldownsAndChecks() {
        assertFalse(PlotService.setApproach(stranger, PlotApproach.OFFENSIVE));
        assertTrue(world.heard(stranger, "living active character"));
        assertFalse(PlotService.setApproach(master, PlotApproach.NEUTRAL));
        assertTrue(PlotService.setApproach(master, PlotApproach.OFFENSIVE));
        assertTrue(world.last(master).contains("+15 Intrigue when plotting, +0 when defending"));
        assertFalse(PlotService.setApproach(master, PlotApproach.DEFENSIVE));
        assertTrue(world.last(master).contains("again in"));
        world.pass(PlotConfig.approachCooldown());
        assertTrue(PlotService.setApproach(master, PlotApproach.DEFENSIVE));

        assertFalse(PlotService.setDefender(stranger, DefenderChoice.SELF, null));
        assertFalse(PlotService.setDefender(target, DefenderChoice.SPYMASTER, null));
        assertTrue(world.last(target).contains("no Spymaster"));
        assertFalse(PlotService.setDefender(target, DefenderChoice.CHARACTER, null));
        assertFalse(PlotService.setDefender(target, DefenderChoice.CHARACTER, stranger));
        assertFalse(PlotService.setDefender(target, DefenderChoice.CHARACTER, target));
        world.at(helper, 50);
        assertFalse(PlotService.setDefender(target, DefenderChoice.CHARACTER, helper));
        assertTrue(world.last(target).contains("within 8 blocks"));
        world.elsewhere(helper);
        assertFalse(PlotService.setDefender(target, DefenderChoice.CHARACTER, helper));
        world.at(helper, 3);
        when(helper.getWorld()).thenReturn(world.gui.world);
        world.intrigue.remove("c-helper");
        assertTrue(PlotService.setDefender(target, DefenderChoice.CHARACTER, helper));
        assertEquals(40, world.intrigue.get("c-helper"));
        assertTrue(world.last(helper).contains("trusts you"));
        assertFalse(PlotService.setDefender(target, DefenderChoice.SELF, null));
        world.pass(PlotConfig.defenderCooldown());
        factionGuardedBy("c-guard", 70);
        assertTrue(PlotService.setDefender(target, DefenderChoice.SPYMASTER, null));
        assertTrue(world.last(target).contains("Your Spymaster"));
        world.pass(PlotConfig.defenderCooldown());
        assertTrue(PlotService.setDefender(target, DefenderChoice.SELF, null));
    }

    @Test
    void protectionComesFromTheChosenDefenderUnlessTheyBetrayTheTarget() {
        Plot plot = building();
        PlotCharacter victim = world.character(target);
        assertEquals(new PlotService.Protection(24, Betrayal.NONE), PlotService.protection(plot));
        var settings = world.store().settings("c-target");
        settings.defender = DefenderChoice.CHARACTER;
        settings.helper = world.character(helper);
        assertEquals(new PlotService.Protection(57, Betrayal.NONE), PlotService.protection(victim, null));
        world.dead.add("c-helper");
        assertEquals(24, PlotService.protection(plot).value());
        world.dead.clear();
        settings.helper = world.character(assassin);
        assertEquals(new PlotService.Protection(0, Betrayal.HELPER), PlotService.protection(plot));
        settings.defender = DefenderChoice.SPYMASTER;
        assertEquals(24, PlotService.protection(plot).value());
        Faction faction = mock(Faction.class);
        world.factions.when(() -> FactionManager.getByMember("Target")).thenReturn(faction);
        assertNull(PlotService.spymaster(victim));
        world.espionage.when(() -> EspionageService.hasSpymaster(faction)).thenReturn(true);
        EspionageState state = new EspionageState();
        state.appoint(new SpecialPositionAssignment(), 10);
        when(faction.getEspionage()).thenReturn(state);
        assertNull(PlotService.spymaster(victim));
        factionGuardedBy("c-guard", 70);
        assertEquals(new PlotService.Protection(78, Betrayal.NONE), PlotService.protection(plot));
        factionGuardedBy("c-assassin", 70);
        assertEquals(new PlotService.Protection(0, Betrayal.SPYMASTER), PlotService.protection(plot));
        assertEquals(78, PlotService.protection(victim, null).value());
    }

    @Test
    void requestsNeedStaffApprovalThroughTheirCode() {
        assertNull(PlotService.request(stranger));
        Plot plot = PlotService.request(master);
        assertEquals("P-ABCDE", plot.code);
        assertTrue(world.last(master).contains("P-ABCDE"));
        assertNull(PlotService.request(master));
        assertTrue(world.last(master).contains("already part of a plot"));
        assertFalse(PlotService.approve(staff, "P-NONE", "Target"));
        assertFalse(PlotService.approve(staff, plot.code, "Nobody"));
        assertFalse(PlotService.approve(staff, plot.code, "Master"));
        world.store().cooldowns.put("c-master|c-target", world.clock.get() + 1000);
        assertFalse(PlotService.approve(staff, plot.code, "Target"));
        assertTrue(staffSaid.getLast().contains("cannot target"));
        world.store().cooldowns.put("c-master|c-target", world.clock.get() - 1);
        world.logOff(target);
        world.offline.put("Target", new PlotCharacter("c-target", target.getUniqueId(), "Target", "Char Target"));
        world.players.remove(target.getUniqueId());
        assertTrue(PlotService.approve(staff, plot.code, "Target"));
        assertEquals(PlotPhase.RECRUITING, plot.phase);
        assertTrue(world.last(master).contains("has been approved"));
        assertFalse(PlotService.approve(staff, plot.code, "Target"));
        assertFalse(PlotService.deny(staff, plot.code));
        assertFalse(PlotService.deny(staff, "P-NONE"));

        Plot second = PlotService.request(helper);
        assertEquals("P-FGHJK", second.code);
        assertTrue(PlotService.deny(staff, second.code));
        assertEquals(PlotPhase.DISSOLVED, second.phase);
        assertTrue(world.last(helper).contains("was not approved"));
        Plot colliding = new Plot();
        colliding.code = "P-LMNPQ";
        colliding.master = new PlotCharacter("x", null, "x", "x");
        colliding.phase = PlotPhase.FAILED;
        world.store().plots.add(colliding);
        assertEquals("P-RSTUV", PlotService.request(guard).code);
    }

    @Test
    void staffCanEndAPlotOrPassItsDays() {
        assertFalse(PlotService.cancel(staff, "P-NONE"));
        Plot request = PlotService.request(master);
        assertTrue(PlotService.cancel(staff, request.code));
        assertTrue(world.last(master).contains("The plot has come to nothing"));
        assertFalse(PlotService.cancel(staff, request.code));
        assertFalse(PlotService.passDay(staff, request.code));
        Plot plot = building();
        assertTrue(PlotService.passDay(staff, plot.code));
        assertEquals(1, plot.day);
        assertTrue(staffSaid.getLast().contains("day 1"));
        Plot other = PlotService.request(helper);
        assertTrue(PlotService.passDay(staff, other.code));
        assertEquals(PlotPhase.REQUESTED, other.phase);
    }

    @Test
    void recruitingIsInPersonAndRolesAreLimited() {
        assertFalse(PlotService.invite(stranger, assassin));
        assertFalse(PlotService.invite(helper, assassin));
        assertTrue(world.last(helper).contains("not leading a plot"));
        Plot plot = approved();
        assertFalse(PlotService.invite(master, target));
        assertTrue(world.last(master).contains("cannot invite"));
        assertFalse(PlotService.invite(master, master));
        Player alt = world.player("Alt", null, null);
        world.active.put(alt.getUniqueId(), new PlotCharacter("c-alt", target.getUniqueId(), "Target", "Alt of Target"));
        assertFalse(PlotService.invite(master, alt));
        assertTrue(world.last(master).contains("cannot invite"));
        world.at(helper, 30);
        assertFalse(PlotService.invite(master, helper));
        world.at(helper, 0);
        assertTrue(PlotService.invite(master, assassin));
        assertTrue(world.last(assassin).contains("[Accept] [Decline]"));
        assertTrue(world.last(assassin).contains("Char Target"));
        assertFalse(PlotService.accept(stranger, plot.code));
        assertFalse(PlotService.accept(helper, plot.code));
        assertTrue(world.last(helper).contains("no longer open"));
        assertTrue(PlotService.accept(assassin, plot.code));
        assertTrue(world.last(master).contains("has joined your plot"));
        assertFalse(PlotService.invite(master, assassin));

        assertTrue(PlotService.invite(master, helper));
        assertFalse(PlotService.decline(stranger, plot.code));
        assertFalse(PlotService.decline(guard, plot.code));
        assertTrue(PlotService.decline(helper, plot.code));
        assertTrue(world.last(master).contains("turned down"));
        assertTrue(PlotService.invite(master, helper));
        world.pass(PlotConfig.inviteDuration() + 1);
        assertFalse(PlotService.accept(helper, plot.code));

        Player third = world.player("Third", "c-third", 30), fourth = world.player("Fourth", "c-fourth", 30);
        Player fifth = world.player("Fifth", "c-fifth", 30);
        for (Player recruit : List.of(helper, third, fourth)) {
            assertTrue(PlotService.invite(master, recruit));
            assertTrue(PlotService.accept(recruit, plot.code));
        }
        assertFalse(PlotService.invite(master, fifth));
        assertTrue(world.last(master).contains("no room"));
        plot.invitations.put("c-fifth", world.clock.get() + 1000);
        assertFalse(PlotService.accept(fifth, plot.code));
        assertTrue(world.last(fifth).contains("cannot join"));

        assertFalse(PlotService.assign(master, "nobody", PlotRole.ASSASSIN, null));
        assertTrue(PlotService.assign(master, "c-assassin", PlotRole.ASSASSIN, null));
        assertTrue(PlotService.assign(master, "c-helper", PlotRole.ASSASSIN, null));
        assertFalse(PlotService.assign(master, "c-third", PlotRole.ASSASSIN, null));
        assertTrue(PlotService.assign(master, "c-third", PlotRole.COLLABORATOR, null));
        assertEquals(CollaboratorTask.HIDE, plot.member("c-third").task);
        assertTrue(PlotService.assign(master, "c-third", PlotRole.COLLABORATOR, CollaboratorTask.PROGRESS));
        assertTrue(PlotService.assign(master, "c-fourth", PlotRole.COLLABORATOR, CollaboratorTask.STRENGTH));
        assertTrue(world.last(fourth).contains("Collaborator (Strengthen the plot)"));
        assertTrue(PlotService.assign(master, "c-helper", PlotRole.UNASSIGNED, null));
        assertFalse(PlotService.assign(master, "c-helper", PlotRole.COLLABORATOR, CollaboratorTask.HIDE));
        assertNull(plot.member("c-helper").task);

        assertFalse(PlotService.dismiss(master, "nobody"));
        assertTrue(PlotService.dismiss(master, "c-helper"));
        assertTrue(world.last(helper).contains("no longer part of the plot against"));
        assertFalse(PlotService.dismiss(stranger, "c-third"));
    }

    @Test
    void conspiratorsMarkWhoElseKnows() {
        assertFalse(PlotService.inform(stranger, helper));
        assertFalse(PlotService.inform(master, helper));
        PlotService.request(master);
        assertFalse(PlotService.inform(master, helper));
        assertTrue(world.last(master).contains("not part of a running plot"));
        Plot plot = world.store().plotOf("c-master");
        PlotService.cancel(staff, plot.code);
        Plot running = recruited();
        assertFalse(PlotService.inform(assassin, stranger));
        assertFalse(PlotService.inform(assassin, target));
        assertFalse(PlotService.inform(assassin, master));
        assertTrue(PlotService.inform(assassin, helper));
        assertTrue(world.last(helper).contains("You now know of a plot"));
        assertEquals(PlotRole.INFORMED, running.role("c-helper"));
    }

    @Test
    void thePlotStartsWithAnAssassinAndLaunchesWhenReady() {
        assertFalse(PlotService.begin(stranger));
        Plot plot = approved();
        assertFalse(PlotService.launch(master));
        assertFalse(PlotService.begin(master));
        assertTrue(world.last(master).contains("at least one conspirator"));
        PlotService.invite(master, assassin);
        PlotService.accept(assassin, plot.code);
        PlotService.assign(master, "c-assassin", PlotRole.ASSASSIN, null);
        assertTrue(PlotService.begin(master));
        assertEquals(PlotPhase.BUILDING, plot.phase);
        assertEquals(34, plot.required);
        assertTrue(world.last(assassin).contains("is under way"));
        assertFalse(PlotService.begin(master));
        assertFalse(PlotService.launch(stranger));
        assertFalse(PlotService.launch(master));
        plot.phase = PlotPhase.READY;
        plot.members.getFirst().role = PlotRole.UNASSIGNED;
        assertFalse(PlotService.launch(master));
        assertTrue(world.last(master).contains("no assassin left"));
        plot.members.getFirst().role = PlotRole.ASSASSIN;
        assertTrue(PlotService.launch(master));
        assertEquals(PlotPhase.INVESTIGATION, plot.phase);
        assertFalse(world.heard(target, "danger"));
        plot.phase = PlotPhase.READY;
        plot.discovered = true;
        assertTrue(PlotService.launch(master));
        assertTrue(world.last(target).contains("danger closing in"));
    }

    @Test
    void membersMayWithdrawUntilTheFinalPhaseAndMastersAbandon() {
        assertFalse(PlotService.leave(stranger));
        assertFalse(PlotService.leave(helper));
        Plot plot = recruited();
        assertFalse(PlotService.leave(master));
        assertTrue(world.last(master).contains("abandons it instead"));
        plot.phase = PlotPhase.INVESTIGATION;
        assertFalse(PlotService.leave(assassin));
        assertTrue(world.last(assassin).contains("too late"));
        plot.phase = PlotPhase.READY;
        assertTrue(PlotService.leave(assassin));
        assertTrue(world.last(master).contains("has withdrawn"));
        assertFalse(PlotService.abandon(stranger));
        PlotService.invite(master, helper);
        plot.phase = PlotPhase.BUILDING;
        PlotService.invite(master, helper);
        PlotService.accept(helper, plot.code);
        assertTrue(PlotService.abandon(master));
        assertEquals(PlotPhase.DISSOLVED, plot.phase);
        assertTrue(world.last(helper).contains("has abandoned the plot against"));
        assertTrue(world.store().cooldowns.get("c-master|c-target") > world.clock.get());
    }

    @Test
    void messagesWaitForTheCharacterAndRollTheirIntrigue() {
        PlotCharacter away = world.character(helper);
        world.logOff(helper);
        for (int index = 0; index < 32; index++) PlotService.tell(away, "note " + index);
        assertEquals(30, world.store().notices.get("c-helper").size());
        PlotService.deliver(helper);
        assertFalse(world.heard(helper, "note"));
        world.active.put(helper.getUniqueId(), away);
        world.intrigue.remove("c-helper");
        PlotService.deliver(helper);
        assertTrue(world.heard(helper, "note 31"));
        assertFalse(world.heard(helper, "note 1 "));
        assertEquals(40, world.intrigue.get("c-helper"));
        assertNull(world.store().notices.get("c-helper"));
        PlotService.deliver(helper);
    }

    @Test
    void timersExpireRequestsRecruitingInvitationsAndFinalPhases() {
        Plot request = PlotService.request(helper);
        world.pass(PlotConfig.requestExpiry());
        PlotService.tick();
        assertEquals(PlotPhase.DISSOLVED, request.phase);
        assertTrue(world.last(helper).contains("expired without approval"));

        Plot plot = approved();
        PlotService.invite(master, assassin);
        world.pass(PlotConfig.inviteDuration() + 1);
        PlotService.tick();
        assertTrue(plot.invitations.isEmpty());
        world.pass(PlotConfig.recruiting());
        PlotService.tick();
        assertEquals(PlotPhase.DISSOLVED, plot.phase);
        assertTrue(world.last(master).contains("never got under way"));
        assertNotNull(world.store().cooldowns.get("c-master|c-target"));

        world.store().cooldowns.clear();
        Plot running = building();
        running.phase = PlotPhase.READY;
        PlotService.tick();
        assertEquals(PlotPhase.READY, running.phase);
        running.phase = PlotPhase.INVESTIGATION;
        running.phaseStartedAt = world.clock.get();
        PlotService.tick();
        assertEquals(PlotPhase.INVESTIGATION, running.phase);
        world.pass(PlotConfig.investigation());
        PlotService.tick();
        assertEquals(PlotPhase.EXECUTION, running.phase);
        assertTrue(world.last(assassin).contains("may now hunt"));
        PlotService.tick();
        world.pass(PlotConfig.execution());
        PlotService.tick();
        assertEquals(PlotPhase.FAILED, running.phase);
        assertFalse(world.heard(target, "seems to have passed"));

        world.store().cooldowns.clear();
        Plot known = PlotService.request(guard);
        PlotService.approve(staff, known.code, "Target");
        known.phase = PlotPhase.EXECUTION;
        known.phaseStartedAt = world.clock.get() - PlotConfig.execution();
        known.discovered = true;
        PlotService.tick();
        assertTrue(world.last(target).contains("seems to have passed"));
    }

    @Test
    void buildingDaysLeakToAWatchfulDefenderAndEndReadyOrFailed() {
        Plot plot = building();
        var settings = world.store().settings("c-target");
        settings.defender = DefenderChoice.CHARACTER;
        settings.helper = world.character(guard);
        world.intrigue.put("c-target", 100);
        world.intrigue.put("c-guard", 100);
        plot.secrecy = 60;
        world.pass(PlotConfig.DAY);
        PlotService.tick();
        assertTrue(plot.discovered);
        assertTrue(world.heard(target, "Someone is plotting against your life."));
        assertTrue(world.heard(target, "counts 2 conspirators"));
        assertTrue(world.last(master).contains("Whispers reach you"));
        int heard = world.said(target).size();
        plot.secrecy = 60;
        world.pass(PlotConfig.DAY);
        PlotService.tick();
        assertEquals(heard + 1, world.said(target).size());
        assertTrue(world.last(target).contains("Someone is plotting"));

        plot.secrecy = 5;
        plot.progress = 99;
        plot.strength = 1000;
        world.pass(PlotConfig.DAY);
        PlotService.tick();
        assertTrue(plot.exposed);
        assertTrue(world.heard(target, "Char Master (plot master), Char Assassin"));
        assertTrue(world.heard(assassin, "The plot is exposed"));
        assertEquals(PlotPhase.READY, plot.phase);
        assertTrue(world.heard(master, "Begin the final phase"));
        assertTrue(world.last(assassin).contains("is ready to strike"));

        world.store().cooldowns.clear();
        PlotService.abandon(master);
        world.store().cooldowns.clear();
        Plot weak = building();
        weak.progress = 99;
        weak.secrecy = 100;
        world.intrigue.put("c-target", 0);
        world.pass(PlotConfig.DAY);
        PlotService.tick();
        assertEquals(PlotPhase.FAILED, weak.phase);
        assertTrue(world.last(assassin).contains("without enough strength"));
    }

    @Test
    void aBetrayingSpymasterLeavesNoTraceBeforeTheFinalPhase() {
        Plot plot = building();
        factionGuardedBy("c-assassin", 100);
        world.store().settings("c-target").defender = DefenderChoice.SPYMASTER;
        plot.secrecy = 1;
        world.pass(PlotConfig.DAY);
        PlotService.tick();
        assertEquals(0, plot.secrecy);
        assertFalse(plot.discovered || plot.exposed);
        assertTrue(world.said(target).isEmpty());
        assertEquals(0, plot.required);
        assertTrue(PlotService.summary(plot).contains("secrecy 0%"));
    }

    @Test
    void investigatorsRollOncePerConspiratorTheyComeNear() {
        Plot plot = building();
        plot.informed.add(world.character(helper));
        plot.phase = PlotPhase.INVESTIGATION;
        Player watcher = world.player("Watcher", "c-watcher", 100);
        world.store().settings("c-target").investigators.add(world.character(watcher));
        world.store().settings("c-target").investigators.add(new PlotCharacter("c-gone", null, "Gone", "Gone"));
        world.at(master, 100);
        world.at(helper, 100);
        world.at(assassin, 100);
        world.logOff(target);
        PlotService.scan();
        assertTrue(plot.investigated.isEmpty());
        plot.discovered = true;
        world.intrigue.put("c-assassin", 0);
        world.at(assassin, 2);
        PlotService.scan();
        assertEquals(List.of("c-assassin"), List.copyOf(plot.investigated));
        assertTrue(world.last(watcher).contains("watches you the way a hunter watches prey"));
        world.intrigue.put("c-helper", 100);
        world.at(helper, 2);
        world.intrigue.put("c-watcher", 0);
        PlotService.scan();
        assertTrue(plot.investigated.contains("c-helper"));
        assertFalse(world.heard(watcher, "Char Helper"));
        world.active.put(target.getUniqueId(), new PlotCharacter("c-target", target.getUniqueId(), "Target", "Char Target"));
        world.at(target, 98);
        world.at(watcher, 500);
        world.intrigue.put("c-target", 60);
        world.intrigue.put("c-master", 32);
        PlotService.scan();
        assertTrue(world.last(target).contains("Char Master takes an unusual interest"));
        PlotService.scan();
        plot.phase = PlotPhase.READY;
        PlotService.scan();
    }

    @Test
    void anInvestigatorNeverSuspectsThemselves() {
        Plot plot = building();
        plot.phase = PlotPhase.INVESTIGATION;
        plot.discovered = true;
        world.store().settings("c-target").investigators.add(world.character(assassin));
        world.at(target, 500);
        world.at(master, 500);
        PlotService.scan();
        assertFalse(plot.investigated.contains("c-assassin"));
    }

    @Test
    void theTargetDiesForGoodToAnAssassinsBlow() {
        Plot plot = building();
        PlotService.died(target, assassin);
        assertEquals(PlotPhase.BUILDING, plot.phase);
        plot.phase = PlotPhase.EXECUTION;
        PlotService.died(stranger, assassin);
        PlotService.died(target, null);
        PlotService.died(target, helper);
        assertEquals(PlotPhase.EXECUTION, plot.phase);
        PlotService.damaged(stranger, assassin);
        PlotService.damaged(target, helper);
        PlotService.damaged(target, assassin);
        world.pass(PlotConfig.hitWindow() + 1);
        PlotService.died(target, null);
        assertEquals(PlotPhase.EXECUTION, plot.phase);
        PlotService.damaged(target, assassin);
        PlotService.died(target, null);
        assertEquals(PlotPhase.EXECUTION, plot.phase, "only a hunting assassin's kill counts");
        assertTrue(world.permakilled.isEmpty());
        plot.member("c-assassin").huntingSince = world.clock.get();
        PlotService.died(target, assassin);
        assertEquals(PlotPhase.EXECUTION, plot.phase, "not while the hunt is warming up");
        hunting(plot, assassin);
        PlotService.damaged(target, assassin);
        PlotService.died(target, null);
        assertEquals(PlotPhase.SUCCEEDED, plot.phase);
        assertEquals(List.of("c-target"), world.permakilled);
        assertTrue(world.last(master).contains("is dead. The plot has succeeded"));

        // Two plots against one target: a blow counts only for the plot whose assassin struck.
        world.store().cooldowns.clear();
        Plot idle = PlotService.request(helper);
        PlotService.approve(staff, idle.code, "Target");
        idle.phase = PlotPhase.EXECUTION;
        Plot striking = PlotService.request(guard);
        PlotService.approve(staff, striking.code, "Target");
        striking.phase = PlotPhase.EXECUTION;
        PlotMember blade = new PlotMember(world.character(assassin));
        blade.role = PlotRole.ASSASSIN;
        striking.members.add(blade);
        hunting(striking, assassin);
        PlotService.damaged(target, assassin);
        PlotService.died(target, null);
        assertEquals(PlotPhase.SUCCEEDED, striking.phase);
        assertEquals(PlotPhase.EXECUTION, idle.phase);
    }

    @Test
    void aFallenAssassinDiesForGoodAndIsRevealed() {
        Plot plot = building();
        Player second = world.player("Second", "c-second", 50);
        PlotService.invite(master, second);
        PlotService.accept(second, plot.code);
        PlotService.assign(master, "c-second", PlotRole.ASSASSIN, null);
        plot.phase = PlotPhase.EXECUTION;
        PlotService.died(assassin, null);
        PlotService.died(assassin, helper);
        assertEquals(2, plot.withRole(PlotRole.ASSASSIN).size(), "an assassin who is not hunting dies like anyone else");
        assertTrue(world.permakilled.isEmpty());
        hunting(plot, assassin);
        hunting(plot, second);
        PlotService.died(assassin, null);
        assertEquals(2, plot.withRole(PlotRole.ASSASSIN).size());
        PlotService.died(assassin, helper);
        assertEquals(List.of("c-assassin"), world.permakilled);
        assertTrue(world.last(helper).contains("fallen assassin is revealed") && world.last(helper).contains("Char Assassin"));
        assertTrue(world.last(target).contains("Char Assassin"));
        assertTrue(world.last(master).contains("has fallen"));
        assertEquals(PlotPhase.EXECUTION, plot.phase);
        int targetHeard = world.said(target).size();
        PlotService.damaged(second, target);
        PlotService.tick();
        PlotService.died(second, null);
        assertEquals(targetHeard + 1, world.said(target).size());
        assertEquals(PlotPhase.FAILED, plot.phase);
        world.store().cooldowns.clear();
        Plot quiet = building();
        quiet.phase = PlotPhase.EXECUTION;
        hunting(quiet, assassin);
        world.logOff(target);
        PlotService.died(assassin, helper);
        assertEquals(PlotPhase.FAILED, quiet.phase);
    }

    @Test
    void assassinsHuntAndAKnowingTargetFeelsIt() {
        assertFalse(PlotService.hunt(stranger));
        assertFalse(PlotService.hunt(helper));
        Plot plot = building();
        assertFalse(PlotService.hunt(assassin));
        assertTrue(world.last(assassin).contains("Only an assassin can hunt"));
        plot.phase = PlotPhase.EXECUTION;
        assertFalse(PlotService.hunt(master));
        assertTrue(PlotService.hunt(assassin));
        assertTrue(world.last(assassin).contains("You set out to hunt"));
        assertFalse(PlotService.hunting(plot.member("c-assassin")));
        assertFalse(PlotService.hunting(null));
        PlotService.tick();
        assertFalse(world.heard(assassin, "The hunt is on"));
        world.pass(PlotConfig.huntWarmup());
        PlotService.tick();
        assertTrue(world.last(assassin).contains("The hunt is on"));
        assertTrue(world.said(target).isEmpty(), "a target who knows nothing feels nothing");
        PlotService.tick();
        assertFalse(PlotService.hunt(assassin));
        assertTrue(world.last(assassin).contains("committed to the hunt"));
        world.pass(PlotConfig.huntMinimum());
        assertTrue(PlotService.hunt(assassin));
        assertTrue(world.last(assassin).contains("call off the hunt"));
        assertTrue(world.said(target).isEmpty());

        plot.discovered = true;
        plot.secrecy = PlotConfig.huntAlertSecrecy() + 1;
        assertFalse(PlotService.alerted(plot));
        plot.secrecy = PlotConfig.huntAlertSecrecy();
        assertTrue(PlotService.alerted(plot));
        assertTrue(PlotService.hunt(assassin));
        world.pass(PlotConfig.huntWarmup());
        PlotService.tick();
        assertTrue(world.last(target).contains("You feel hunted"));
        PlotService.deliver(target);
        assertTrue(world.last(target).contains("You feel hunted"));
        world.pass(PlotConfig.huntMinimum());
        assertTrue(PlotService.hunt(assassin));
        assertTrue(world.last(target).contains("feeling of being hunted fades"));
        int heard = world.said(target).size();
        PlotService.deliver(target);
        assertEquals(heard, world.said(target).size());

        assertTrue(PlotService.hunt(assassin));
        world.pass(PlotConfig.huntWarmup());
        PlotService.tick();
        world.logOff(assassin);
        PlotService.tick();
        assertEquals(0, plot.member("c-assassin").huntingSince, "logging off ends the hunt");
        assertTrue(world.last(target).contains("fades"));
        world.active.put(assassin.getUniqueId(), new PlotCharacter("c-assassin", assassin.getUniqueId(), "Assassin", "Char Assassin"));
        PlotService.hunt(assassin);
        world.logOff(target);
        world.logOff(assassin);
        PlotService.tick();
        assertEquals(0, plot.member("c-assassin").huntingSince);
    }

    @Test
    void permanentDeathsEndOrThinThePlotsAroundTheCharacter() {
        Plot plot = building();
        plot.informed.add(world.character(helper));
        world.store().settings("c-target").investigators.add(world.character(helper));
        PlotService.characterDied("c-helper");
        assertTrue(plot.informed.isEmpty());
        assertTrue(world.store().peek("c-target").investigators.isEmpty());
        PlotService.characterDied("c-nobody");
        Player second = world.player("Second", "c-second", 50);
        PlotService.invite(master, second);
        PlotService.accept(second, plot.code);
        PlotService.characterDied("c-second");
        assertNull(plot.member("c-second"));
        assertTrue(world.last(master).contains("is dead"));
        plot.phase = PlotPhase.INVESTIGATION;
        PlotService.characterDied("c-assassin");
        assertEquals(PlotPhase.FAILED, plot.phase);
        PlotService.characterDied("c-master");

        world.store().cooldowns.clear();
        Plot led = building();
        PlotService.characterDied("c-master");
        assertEquals(PlotPhase.DISSOLVED, led.phase);
        assertTrue(world.last(assassin).contains("With the death of"));
        Plot aimed = building();
        PlotService.characterDied("c-target");
        assertEquals(PlotPhase.DISSOLVED, aimed.phase);
        assertTrue(world.last(assassin).contains("Fate has claimed"));
        Plot thinned = building();
        Player third = world.player("Third", "c-third", 50);
        PlotService.invite(master, third);
        PlotService.accept(third, thinned.code);
        thinned.phase = PlotPhase.EXECUTION;
        PlotService.characterDied("c-third");
        assertEquals(PlotPhase.EXECUTION, thinned.phase);
    }

    @Test
    void investigatorsAreChosenOnlyByTargetsWhoKnowOfAPlot() {
        assertFalse(PlotService.toggleInvestigator(stranger, helper));
        assertFalse(PlotService.toggleInvestigator(target, helper));
        assertTrue(world.last(target).contains("know of no plot"));
        Plot plot = building();
        plot.discovered = true;
        assertTrue(PlotService.threatened("c-target"));
        assertFalse(PlotService.toggleInvestigator(target, stranger));
        assertTrue(PlotService.toggleInvestigator(target, helper));
        assertTrue(world.last(helper).contains("look into a plot"));
        assertTrue(PlotService.toggleInvestigator(target, guard));
        Player third = world.player("Third", "c-third", 50);
        assertFalse(PlotService.toggleInvestigator(target, third));
        assertTrue(world.last(target).contains("at most 2"));
        assertTrue(PlotService.toggleInvestigator(target, helper));
        assertTrue(world.last(target).contains("no longer investigates"));
        assertFalse(PlotService.toggleInvestigator(target, null));
    }

    @Test
    void aFailedSaveIsLoggedAndTheGameCarriesOn() throws Exception {
        Files.writeString(directory.resolve("not-a-folder"), "");
        PlotService.load(directory.resolve("not-a-folder/plots.json"));
        var logger = mock(java.util.logging.Logger.class);
        when(world.gui.plugin.getLogger()).thenReturn(logger);
        assertNotNull(PlotService.request(master));
        verify(logger).log(org.mockito.ArgumentMatchers.eq(java.util.logging.Level.SEVERE), anyString(), any(IOException.class));
        world.intrigue.remove("c-master");
        world.espionage.when(() -> EspionageService.intrigue(any(), anyString())).thenThrow(new IllegalStateException("x"));
        assertEquals(50, PlotService.intrigue(world.character(master)));
        PlotConfig.load(new YamlConfiguration());
    }
}
