package net.tfminecraft.simplefactions.plots;

import static org.junit.jupiter.api.Assertions.*;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class PlotConfigTest {
    @AfterEach
    void restore() { PlotConfig.load(new YamlConfiguration()); }

    @Test
    void bundledDefaultsMatchTheDocumentedRules() {
        PlotConfig.load(new YamlConfiguration());
        assertEquals("simplefactions.plots.staff", PlotConfig.staffPermission());
        assertEquals(7 * PlotConfig.DAY, PlotConfig.requestExpiry());
        assertEquals(7 * PlotConfig.DAY, PlotConfig.recruiting());
        assertEquals(7 * PlotConfig.DAY, PlotConfig.retargetCooldown());
        assertEquals(8, PlotConfig.inviteRadius());
        assertEquals(600_000, PlotConfig.inviteDuration());
        assertEquals(86_400_000, PlotConfig.approachCooldown());
        assertEquals(86_400_000, PlotConfig.defenderCooldown());
        assertEquals(15, PlotConfig.offenseBonus(PlotApproach.OFFENSIVE));
        assertEquals(0, PlotConfig.defenseBonus(PlotApproach.OFFENSIVE));
        assertEquals(8, PlotConfig.offenseBonus(PlotApproach.NEUTRAL));
        assertEquals(8, PlotConfig.defenseBonus(PlotApproach.NEUTRAL));
        assertEquals(15, PlotConfig.defenseBonus(PlotApproach.DEFENSIVE));
        assertEquals(.5, PlotConfig.aloneShare());
        assertEquals(.75, PlotConfig.helperShare());
        assertEquals(7, PlotConfig.buildDays());
        assertEquals(.5, PlotConfig.luckMin());
        assertEquals(1.5, PlotConfig.luckMax());
        assertEquals(.5, PlotConfig.progressPerCollaborator());
        assertEquals(.1, PlotConfig.strengthWeight(PlotRole.MASTER, null));
        assertEquals(.06, PlotConfig.strengthWeight(PlotRole.ASSASSIN, null));
        assertEquals(.15, PlotConfig.strengthWeight(PlotRole.COLLABORATOR, CollaboratorTask.STRENGTH));
        assertEquals(.03, PlotConfig.strengthWeight(PlotRole.COLLABORATOR, CollaboratorTask.HIDE));
        assertEquals(0, PlotConfig.strengthWeight(PlotRole.UNASSIGNED, null));
        assertEquals(10, PlotConfig.requiredBase());
        assertEquals(1, PlotConfig.requiredPerProtection());
        assertEquals(4, PlotConfig.dailyLoss());
        assertEquals(2, PlotConfig.lossPerMember());
        assertEquals(6, PlotConfig.hidePerCollaborator());
        assertEquals(1, PlotConfig.minimumLoss());
        assertEquals(3, PlotConfig.leakLoss());
        assertEquals(60, PlotConfig.revealMembers());
        assertEquals(40, PlotConfig.revealProgress());
        assertEquals(20, PlotConfig.revealAssassins());
        assertEquals(2, PlotConfig.betrayalSpeed());
        assertEquals(40, PlotConfig.betrayalPenalty(Betrayal.SPYMASTER));
        assertEquals(20, PlotConfig.betrayalPenalty(Betrayal.HELPER));
        assertEquals(0, PlotConfig.betrayalPenalty(Betrayal.NONE));
        assertEquals(3 * PlotConfig.DAY, PlotConfig.investigation());
        assertEquals(6, PlotConfig.radius());
        assertEquals(5, PlotConfig.scanSeconds());
        assertEquals(2, PlotConfig.maxInvestigators());
        assertEquals(15, PlotConfig.knownBonus());
        assertEquals(15, PlotConfig.unknownPenalty());
        assertEquals(40, PlotConfig.strongMargin());
        assertEquals(30, PlotConfig.difficulty(PlotRole.MASTER));
        assertEquals(10, PlotConfig.difficulty(PlotRole.UNASSIGNED));
        assertEquals(0, PlotConfig.difficulty(PlotRole.INFORMED));
        assertEquals(7 * PlotConfig.DAY, PlotConfig.ready());
        assertEquals(4, PlotConfig.huntDays());
        assertEquals(30 * 60_000, PlotConfig.findable());
        assertEquals(21 * PlotConfig.DAY, PlotConfig.maxHunt());
        assertEquals(16, PlotConfig.maxDepth());
        assertEquals(java.util.List.of(), PlotConfig.worlds());
        assertEquals(120_000, PlotConfig.areaInterval());
        assertEquals(250, PlotConfig.areaSize());
        assertEquals(120_000, PlotConfig.hitWindow());
        assertEquals(2, PlotConfig.maxAssassins());
        assertEquals(2, PlotConfig.maxCollaborators());
        assertEquals(4, PlotConfig.maxMembers());
        assertEquals(30, PlotConfig.noticeLimit());
        assertEquals(34, PlotConfig.earlyProgress());
        assertEquals(67, PlotConfig.lateProgress());
        assertEquals(60_000, PlotConfig.huntWarmup());
        assertEquals(660_000, PlotConfig.huntMinimum());
        assertEquals(40, PlotConfig.huntAlertSecrecy());
        assertEquals("&7&oSomething about Ana unsettles you.", PlotConfig.hint(PlotRole.ASSASSIN, false, "Ana"));
    }

    @Test
    void invalidValuesFallBackAndCustomValuesApply() {
        var config = new YamlConfiguration();
        config.set("plots.staff-permission", " ");
        config.set("plots.invite-radius", 0);
        config.set("plots.building.days", Double.NaN);
        config.set("plots.building.luck-min", 2.0);
        config.set("plots.building.luck-max", 1.0);
        config.set("plots.approach.offensive.offense", 30);
        config.set("plots.roles.max-assassins", 3);
        config.set("plots.execution.findable.worlds", java.util.List.of("TFMC_Map"));
        config.set("plots.building.reveal.early-progress", 50);
        config.set("plots.building.reveal.late-progress", 20);
        config.set("plots.investigation.hints.master.strong", "{name} knows.");
        PlotConfig.load(config);
        assertEquals("simplefactions.plots.staff", PlotConfig.staffPermission());
        assertEquals(8, PlotConfig.inviteRadius());
        assertEquals(7, PlotConfig.buildDays());
        assertEquals(2, PlotConfig.luckMin());
        assertEquals(2, PlotConfig.luckMax());
        assertEquals(30, PlotConfig.offenseBonus(PlotApproach.OFFENSIVE));
        assertEquals(5, PlotConfig.maxMembers());
        assertEquals(java.util.List.of("TFMC_Map"), PlotConfig.worlds());
        assertEquals(50, PlotConfig.lateProgress());
        assertEquals("Ana knows.", PlotConfig.hint(PlotRole.MASTER, true, "Ana"));
        config.set("plots.staff-permission", "custom.node");
        PlotConfig.load(config);
        assertEquals("custom.node", PlotConfig.staffPermission());
    }

    @Test
    void aMissingHintUsesTheBundledTextOrAPlainFallback() {
        var config = new YamlConfiguration();
        config.set("plots.investigation.hints", null);
        PlotConfig.load(config);
        assertEquals("&7&oAna is acting strangely around you.", PlotConfig.hint(PlotRole.COLLABORATOR, false, "Ana"));
        assertEquals("&7&oAna is acting strangely.", PlotConfig.hint(PlotRole.UNASSIGNED, true, "Ana"));
    }
}
