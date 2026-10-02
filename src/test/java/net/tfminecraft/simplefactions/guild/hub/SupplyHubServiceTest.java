package net.tfminecraft.simplefactions.guild.hub;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.database.FactionData;
import net.tfminecraft.simplefactions.database.GuildData;
import net.tfminecraft.simplefactions.database.InstallationData;
import net.tfminecraft.simplefactions.database.JsonUtil;
import net.tfminecraft.simplefactions.database.SupplyHubData;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.enums.GuildModifier;
import net.tfminecraft.simplefactions.enums.Rules;
import net.tfminecraft.simplefactions.guild.hub.SupplyHubService.BuildFailure;
import net.tfminecraft.simplefactions.guild.hub.SupplyHubService.DormantReason;
import net.tfminecraft.simplefactions.guild.hub.SupplyHubService.HubCandidate;
import net.tfminecraft.simplefactions.guild.hub.SupplyHubService.HubStanding;
import net.tfminecraft.simplefactions.guild.hub.SupplyHubService.PlacedCandidate;
import net.tfminecraft.simplefactions.guild.hub.SupplyHubService.RemoveOutcome;
import net.tfminecraft.simplefactions.guild.income.Cashflow;
import net.tfminecraft.simplefactions.guild.income.Ledger;
import net.tfminecraft.simplefactions.guild.income.TradeBreakdown;
import net.tfminecraft.simplefactions.guild.loans.LoanHandler;
import net.tfminecraft.simplefactions.installation.InstallationKind;
import net.tfminecraft.simplefactions.installation.handler.InstallationHandler;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.objects.Bank;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.objects.handler.GuildHandler;
import net.tfminecraft.simplefactions.utils.DailyGuildTransfers;

class SupplyHubServiceTest {
    private final List<Faction> previousFactions = new ArrayList<>(FactionManager.factions);

    @AfterEach
    void restore() {
        FactionManager.factions = new ArrayList<>(previousFactions);
    }

    @Test
    void forbiddenEconomyRefusesBuildBeforeCheckingTheInstallation() {
        boolean enabled = Cache.provincesEnabled;
        Cache.provincesEnabled = true;
        Guild guild = mock(Guild.class);
        Faction faction = mock(Faction.class);
        Player player = mock(Player.class);
        when(player.getName()).thenReturn("leader");
        when(guild.getLeader()).thenReturn("leader");
        when(guild.getFaction()).thenReturn(faction);
        when(faction.hasFactionRule(Rules.SUPPLY_HUBS)).thenReturn(false);
        try (MockedStatic<FactionManager> factions = mockStatic(FactionManager.class)) {
            factions.when(() -> FactionManager.getGuildByMember("leader")).thenReturn(guild);
            assertTrue(SupplyHubCommands.guild(player, new String[] {"hub", "build"}));
            verify(player).sendMessage("§cYour faction's economy does not allow supply hubs");
            verify(player, never()).getLocation();
        } finally {
            Cache.provincesEnabled = enabled;
        }
    }

    @Test
    void forbiddenEconomyMakesExistingHubsDormantButKeepsUpkeepAndSlots() {
        SupplyHub hub = hub("decentralized", "station", 1);
        Guild guild = guildWith(hub);
        Faction faction = mock(Faction.class);
        when(guild.getFaction()).thenReturn(faction);
        when(guild.getModifier(GuildModifier.HUB_LIMIT)).thenReturn(2.0);
        HubStanding standing = SupplyHubService.standing(guild, hub, true, true, 1, List.of(hub));
        assertFalse(standing.active());
        assertEquals(DormantReason.ECONOMY_DISALLOWS, standing.reason());
        assertEquals("§cDormant §7(your faction's economy does not allow supply hubs)",
                SupplyHubService.statusText(standing));
        when(guild.getModifier(GuildModifier.HUB_UPKEEP)).thenReturn(15.0);
        assertEquals(15.0, SupplyHubService.dailyCost(guild));
        assertEquals(1, SupplyHubService.countAt("decentralized", "station", List.of(guild)));

        // The host's economy does not forbid a foreign guild that may build hubs.
        when(faction.hasFactionRule(Rules.SUPPLY_HUBS)).thenReturn(true);
        assertTrue(SupplyHubService.standing(guild, hub, true, true, 1, List.of(hub)).active());
    }

    @Test
    void build_eachPreconditionFailsOnItsOwn() {
        assertEquals(BuildFailure.KIND_DISALLOWS, SupplyHubService.checkBuild(0, false, 0, 2, 0, 1, true));
        assertEquals(BuildFailure.ALREADY_HAS_HUB, SupplyHubService.checkBuild(2, true, 0, 2, 0, 1, true));
        assertEquals(BuildFailure.HUB_LIMIT, SupplyHubService.checkBuild(2, false, 2, 2, 0, 1, true));
        assertEquals(BuildFailure.NO_FREE_SLOT, SupplyHubService.checkBuild(2, false, 0, 2, 2, 1, true));
        assertEquals(BuildFailure.NO_TRADE, SupplyHubService.checkBuild(2, false, 0, 2, 0, 0, true));
        assertEquals(BuildFailure.NO_PERMIT, SupplyHubService.checkBuild(2, false, 0, 2, 0, 1, false));
        assertNull(SupplyHubService.checkBuild(2, false, 1, 2, 1, 0.01, true));
    }

    @Test
    void build_failureMessagesAreDistinct() {
        Set<String> messages = new HashSet<>();
        for (BuildFailure failure : BuildFailure.values()) {
            String message = SupplyHubService.buildFailureMessage(failure, "port", 2);
            assertFalse(message.isBlank());
            assertTrue(messages.add(message));
        }
    }

    @Test
    void build_ownFactionNeedsNoPermit_otherFactionDoes() {
        assertTrue(SupplyHubService.ownerAllows("rome", "rome", false));
        assertFalse(SupplyHubService.ownerAllows("venice", "rome", false));
        assertTrue(SupplyHubService.ownerAllows("venice", "rome", true));
    }

    @Test
    void build_freeSlotCountsDormantHubs() {
        SupplyHub older = hub("rome", "harbour", 1);
        SupplyHub newer = hub("rome", "harbour", 2);
        List<Guild> guilds = List.of(guildWith(older), guildWith(newer));
        assertEquals(2, SupplyHubService.countAt("rome", "harbour", guilds));
        assertEquals(BuildFailure.NO_FREE_SLOT, SupplyHubService.checkBuild(2, false, 0, 2, 2, 1, true));
    }

    @Test
    void chooseSite_prefersNearestThatAllowsHubs() {
        PlacedCandidate fort = placed("rome", "fort", 1, 0);
        PlacedCandidate port = placed("rome", "harbour", 8, 2);
        assertEquals("harbour", SupplyHubService.chooseAllowed(List.of(fort, port)).installationId());
        assertNull(SupplyHubService.chooseAllowed(List.of(fort)));
        assertEquals("fort", SupplyHubService.nearest(List.of(fort, port)).installationId());
    }

    @Test
    void hubSlots_missingKeyDefaults() {
        assertEquals(0, SupplyHubService.defaultHubSlots(InstallationKind.FORT, 1));
        assertEquals(2, SupplyHubService.defaultHubSlots(InstallationKind.PORT, 1));
        assertEquals(0, SupplyHubService.defaultHubSlots(InstallationKind.PORT, 2));
        assertEquals(1, SupplyHubService.defaultHubSlots(InstallationKind.AIRPORT, 1));
        assertEquals(1, SupplyHubService.defaultHubSlots(InstallationKind.TRAIN_STATION, 1));
        assertEquals(2, SupplyHubService.defaultHubSlots(InstallationKind.TRAIN_STATION, 2));
        assertEquals(4, SupplyHubService.defaultHubSlots(InstallationKind.TRAIN_STATION, 3));
        assertEquals(0, SupplyHubService.defaultHubSlots(InstallationKind.TRAIN_STATION, 4));
    }

    @Test
    void upkeepIsFlatForEveryHub() {
        assertEquals(0.0, SupplyHubService.totalUpkeep(0, 15), 1e-9);
        assertEquals(45.0, SupplyHubService.totalUpkeep(3, 15), 1e-9);
        assertEquals(15.0, SupplyHubService.totalUpkeep(3, 5), 1e-9);
    }

    @Test
    void shed_removesNewestFirstUntilTheRestCanBePaid() {
        List<SupplyHub> hubs = new ArrayList<>();
        hubs.add(hub("rome", "a", 1));
        hubs.add(hub("rome", "b", 2));
        hubs.add(hub("rome", "c", 3));
        List<SupplyHub> removed = SupplyHubService.shedUnpaid(hubs, 40, 15);
        assertEquals(List.of("c"), ids(removed));
        assertEquals(List.of("a", "b"), ids(hubs));
        assertEquals(30.0, SupplyHubService.totalUpkeep(hubs.size(), 15), 1e-9);

        removed = SupplyHubService.shedUnpaid(hubs, 15, 15);
        assertEquals(List.of("b"), ids(removed));
        assertEquals(List.of("a"), ids(hubs));

        removed = SupplyHubService.shedUnpaid(hubs, 14.99, 15);
        assertEquals(List.of("a"), ids(removed));
        assertTrue(hubs.isEmpty());
    }

    @Test
    void shed_keepsHubsTheGuildCanPayFor() {
        List<SupplyHub> hubs = new ArrayList<>();
        hubs.add(hub("rome", "a", 1));
        hubs.add(hub("rome", "b", 2));
        assertTrue(SupplyHubService.shedUnpaid(hubs, 30, 15).isEmpty());
        assertEquals(2, hubs.size());
    }

    @Test
    void standing_eachDormantReason_andActive() {
        SupplyHub first = hub("rome", "harbour", 1);
        SupplyHub second = hub("rome", "harbour", 2);
        SupplyHub third = hub("rome", "harbour", 3);
        List<SupplyHub> oldest = List.of(first, second, third);

        HubStanding active = SupplyHubService.standing(first, true, true, 2, oldest);
        assertTrue(active.active());
        assertNull(active.reason());

        HubStanding alsoActive = SupplyHubService.standing(second, true, true, 2, oldest);
        assertTrue(alsoActive.active());

        HubStanding beyond = SupplyHubService.standing(third, true, true, 2, oldest);
        assertFalse(beyond.active());
        assertEquals(DormantReason.BEYOND_HUB_SLOTS, beyond.reason());

        HubStanding missing = SupplyHubService.standing(first, false, true, 2, oldest);
        assertEquals(DormantReason.INSTALLATION_GONE, missing.reason());

        HubStanding noPermit = SupplyHubService.standing(first, true, false, 2, oldest);
        assertEquals(DormantReason.NO_PERMIT, noPermit.reason());

        assertTrue(SupplyHubService.statusText(active).contains("Active"));
        assertTrue(SupplyHubService.statusText(beyond).contains("hub slots"));
        assertTrue(SupplyHubService.statusText(missing).contains("no longer exists"));
        assertTrue(SupplyHubService.statusText(noPermit).contains("hub permit"));
    }

    @Test
    void guildLimitIncludesWholeBranchBonus_andExcessHubsAreNewestFirst() {
        Guild guild = mock(Guild.class);
        List<SupplyHub> hubs = new ArrayList<>(List.of(
                hub("rome", "old", 1), hub("rome", "middle", 2), hub("rome", "new", 3)));
        when(guild.getSupplyHubs()).thenReturn(hubs);
        when(guild.getModifier(GuildModifier.HUB_LIMIT)).thenReturn(3.5);

        assertEquals(3, SupplyHubService.limit(guild));
        assertFalse(SupplyHubService.beyondGuildLimit(guild, hubs.get(0)));
        assertFalse(SupplyHubService.beyondGuildLimit(guild, hubs.get(1)));
        assertFalse(SupplyHubService.beyondGuildLimit(guild, hubs.get(2)));

        when(guild.getModifier(GuildModifier.HUB_LIMIT)).thenReturn(0.5);
        assertEquals(0, SupplyHubService.limit(guild));
        assertTrue(SupplyHubService.beyondGuildLimit(guild, hubs.get(0)));
        assertTrue(SupplyHubService.statusText(new HubStanding(
                false, DormantReason.BEYOND_GUILD_LIMIT)).contains("guild's supply hub limit"));
    }

    @Test
    void hubLimitIsNotScaledWithInactivity() {
        assertTrue(GuildModifier.HUB_LIMIT.isPositive());
        assertFalse(GuildModifier.HUB_LIMIT.scalesWithInactivity());
        assertFalse(GuildModifier.HUB_UPKEEP.isPositive());
    }

    @Test
    void dormantHub_stillCostsUpkeepAndOccupiesASlot() {
        SupplyHub first = hub("rome", "harbour", 1);
        SupplyHub dormant = hub("rome", "harbour", 2);
        List<SupplyHub> guildHubs = List.of(first, dormant);
        assertEquals(15.0, SupplyHubService.upkeepOf(dormant, guildHubs, 15), 1e-9);
        assertEquals(30.0, SupplyHubService.totalUpkeep(guildHubs.size(), 15), 1e-9);
        assertEquals(2, SupplyHubService.countAt("rome", "harbour", List.of(guildWith(first, dormant))));
    }

    @Test
    void lifecycle_deconstructRemovesHubs_transferKeepsThem() {
        SupplyHub hub = hub("rome", "harbour", 4);
        Guild guild = guildWith(hub);
        assertEquals(1, SupplyHubService.removeInstallation(List.of(guild), "rome", "harbour"));
        assertTrue(guild.getSupplyHubs().isEmpty());

        guild.getSupplyHubs().add(hub("rome", "harbour", 4));
        assertEquals(1, SupplyHubService.retarget(List.of(guild), "rome", "venice", "harbour"));
        SupplyHub moved = guild.getSupplyHubs().get(0);
        assertEquals("venice", moved.ownerFactionId());
        assertEquals("harbour", moved.installationId());
        assertEquals(4L, moved.createdAt());
        HubStanding afterMove = SupplyHubService.standing(
                moved, true, SupplyHubService.ownerAllows("rome", "venice", false), 2, List.of(moved));
        assertEquals(DormantReason.NO_PERMIT, afterMove.reason());
        HubStanding permitted = SupplyHubService.standing(
                moved, true, SupplyHubService.ownerAllows("rome", "venice", true), 2, List.of(moved));
        assertTrue(permitted.active());
    }

    @Test
    void lifecycle_disbandedGuildIsNotScanned_movedGuildFollowsTheNewFaction() {
        SupplyHub hub = hub("rome", "harbour", 1);
        Guild gone = guildWith(hub);
        Guild staying = guildWith();
        assertEquals(0, SupplyHubService.countAt("rome", "harbour", List.of(staying)));
        assertEquals(1, SupplyHubService.countAt("rome", "harbour", List.of(gone, staying)));

        HubStanding atHome = SupplyHubService.standing(
                hub, true, SupplyHubService.ownerAllows("rome", "rome", false), 1, List.of(hub));
        assertTrue(atHome.active());
        HubStanding moved = SupplyHubService.standing(
                hub, true, SupplyHubService.ownerAllows("venice", "rome", false), 1, List.of(hub));
        assertEquals(DormantReason.NO_PERMIT, moved.reason());
    }

    @Test
    void saveLoad_dropsAHubWhoseInstallationIsGone() {
        SupplyHubData kept = new SupplyHubData();
        kept.faction = "rome";
        kept.installation = "harbour";
        kept.createdAt = 10L;
        SupplyHubData gone = new SupplyHubData();
        gone.faction = "rome";
        gone.installation = "missing";
        gone.createdAt = 11L;
        GuildData data = new GuildData();
        data.supplyHubs = List.of(kept, gone);
        String json = JsonUtil.GSON.toJson(data);
        assertTrue(json.contains("supply hubs"));
        GuildData restored = JsonUtil.GSON.fromJson(json, GuildData.class);
        List<SupplyHub> loaded = SupplyHubService.fromData(restored.supplyHubs);
        assertEquals(2, loaded.size());

        Faction faction = mock(Faction.class);
        when(faction.getId()).thenReturn("rome");
        InstallationHandler handler = new InstallationHandler(faction);
        InstallationData installation = new InstallationData();
        installation.id = "harbour";
        installation.name = "Harbour";
        installation.kind = "port";
        installation.province = 1;
        handler.load(List.of(installation));
        when(faction.getInstallationHandler()).thenReturn(handler);
        Guild guild = guildWith(loaded.toArray(SupplyHub[]::new));
        GuildHandler guilds = mock(GuildHandler.class);
        when(guilds.getGuilds()).thenReturn(List.of(guild));
        when(faction.getGuildHandler()).thenReturn(guilds);
        FactionManager.factions = new ArrayList<>();
        FactionManager.factions.add(faction);

        SupplyHubService.dropMissingLoaded();
        assertEquals(1, guild.getSupplyHubs().size());
        assertEquals("harbour", guild.getSupplyHubs().get(0).installationId());
        assertEquals(10L, guild.getSupplyHubs().get(0).createdAt());
    }

    @Test
    void permits_toggleAndRoundTripWithTheFaction() {
        List<String> permits = new ArrayList<>();
        assertTrue(SupplyHubService.togglePermit(permits, "merchants"));
        assertTrue(SupplyHubService.hasPermit(permits, "Merchants"));
        assertFalse(SupplyHubService.togglePermit(permits, "merchants"));
        assertFalse(SupplyHubService.hasPermit(permits, "merchants"));
        SupplyHubService.togglePermit(permits, "merchants");

        FactionData data = new FactionData();
        data.hubPermits = new ArrayList<>(permits);
        String json = JsonUtil.GSON.toJson(data);
        assertTrue(json.contains("hub permits"));
        FactionData restored = JsonUtil.GSON.fromJson(json, FactionData.class);
        assertEquals(List.of("merchants"), restored.hubPermits);
    }

    @Test
    void oldUpkeepAndLimitConfigKeysAreIgnored() {
        YamlConfiguration old = new YamlConfiguration();
        old.set("supply-hubs.base-limit", 4);
        old.set("supply-hubs.base-upkeep", 0);
        old.set("supply-hubs.upkeep-growth", 3.0);
        SupplyHubService.loadConfig(old);
        assertEquals(50.0, Cache.supplyHubMaxTax, 1e-9);
    }

    @Test
    void remove_isAmbiguousAcrossFactionsUntilQualified() {
        List<SupplyHub> hubs = List.of(hub("rome", "harbour", 1), hub("venice", "harbour", 2));
        assertEquals(RemoveOutcome.AMBIGUOUS, SupplyHubService.matchRemove(hubs, "harbour").outcome());
        assertEquals(RemoveOutcome.FOUND, SupplyHubService.matchRemove(hubs, "rome:harbour").outcome());
        assertEquals("rome", SupplyHubService.matchRemove(hubs, "rome:harbour").hub().ownerFactionId());
        assertEquals(RemoveOutcome.MISSING, SupplyHubService.matchRemove(hubs, "milan:harbour").outcome());
        assertEquals(RemoveOutcome.FOUND, SupplyHubService.matchRemove(List.of(hubs.get(0)), "harbour").outcome());
    }

    @Test
    void removalMessage_namesTheHub() {
        assertTrue(SupplyHubService.removalMessage("Harbour").contains("Harbour"));
        assertTrue(SupplyHubService.removalMessage("Harbour").contains("unable to pay upkeep"));
    }

    @Test
    void notify_tellsTheLeaderWhenOnline() {
        Guild guild = mock(Guild.class);
        when(guild.getLeader()).thenReturn("Ada");
        Player leader = mock(Player.class);
        Server server = mock(Server.class);
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getServer).thenReturn(server);
            bukkit.when(() -> Bukkit.getPlayerExact("Ada")).thenReturn(leader);
            SupplyHubCommands.notifyRemoved(guild, List.of(hub("rome", "harbour", 1)));
        }
        verify(leader).sendMessage(org.mockito.ArgumentMatchers.contains("unable to pay upkeep"));
    }

    @Test
    void ledger_supplyHubsIsItsOwnSinkLine() {
        Guild guild = mock(Guild.class);
        Faction faction = mock(Faction.class);
        List<SupplyHub> hubs = new ArrayList<>();
        hubs.add(hub("rome", "a", 1));
        hubs.add(hub("rome", "b", 2));
        hubs.add(hub("rome", "c", 3));
        when(guild.getSupplyHubs()).thenReturn(hubs);
        when(guild.getModifier(GuildModifier.HUB_UPKEEP)).thenReturn(15.0);
        Bank bank = mock(Bank.class);
        when(bank.getWealth()).thenReturn(40.0);
        Ledger ledger = ledger(faction, guild, bank);

        assertTrue(Cashflow.SUPPLY_HUBS.getDisplay().contains("Supply hubs"));
        assertEquals(-45.0, ledger.getIncome(Cashflow.SUPPLY_HUBS), 1e-9);

        DailyGuildTransfers buffer = new DailyGuildTransfers();
        ledger.populateDailyTransfers(buffer);
        assertEquals(List.of("a", "b"), ids(hubs));
        assertEquals(-30.0, buffer.getExternalDeltas().get(guild), 1e-9);
        assertTrue(buffer.getTransfers().isEmpty());
        assertEquals(-30.0, ledger.getIncome(Cashflow.SUPPLY_HUBS), 1e-9);
        assertEquals(-30.0, ledger.getNetIncome(), 1e-9);
    }

    @Test
    void ledger_removesEveryHubWhenNoneCanBePaid() {
        Guild guild = mock(Guild.class);
        Faction faction = mock(Faction.class);
        List<SupplyHub> hubs = new ArrayList<>();
        hubs.add(hub("rome", "a", 1));
        when(guild.getSupplyHubs()).thenReturn(hubs);
        when(guild.getModifier(GuildModifier.HUB_UPKEEP)).thenReturn(15.0);
        Bank bank = mock(Bank.class);
        when(bank.getWealth()).thenReturn(10.0);
        Ledger ledger = ledger(faction, guild, bank);

        DailyGuildTransfers buffer = new DailyGuildTransfers();
        ledger.populateDailyTransfers(buffer);
        assertTrue(hubs.isEmpty());
        assertTrue(buffer.getExternalDeltas().isEmpty());
        assertEquals(0.0, ledger.getIncome(Cashflow.SUPPLY_HUBS), 1e-9);
    }

    private static SupplyHub hub(String factionId, String installationId, long createdAt) {
        return new SupplyHub(factionId, installationId, createdAt);
    }

    private static Guild guildWith(SupplyHub... hubs) {
        Guild guild = mock(Guild.class);
        when(guild.getSupplyHubs()).thenReturn(new ArrayList<>(List.of(hubs)));
        return guild;
    }

    private static PlacedCandidate placed(String factionId, String installationId, double distance, int slots) {
        return new PlacedCandidate(new HubCandidate(factionId, installationId, distance), slots, installationId, factionId);
    }

    private static List<String> ids(List<SupplyHub> hubs) {
        List<String> ids = new ArrayList<>();
        for (SupplyHub hub : hubs) {
            ids.add(hub.installationId());
        }
        return ids;
    }

    private static Ledger ledger(Faction faction, Guild guild, Bank bank) {
        when(guild.isBankrupt()).thenReturn(false);
        when(guild.getBank()).thenReturn(bank);
        when(guild.isBase()).thenReturn(false);
        when(guild.getFaction()).thenReturn(faction);
        when(guild.getTradeBreakdown()).thenReturn(new TradeBreakdown());
        when(guild.getLeader()).thenReturn("Ada");
        LoanHandler loans = mock(LoanHandler.class);
        when(loans.getLoansTaken()).thenReturn(List.of());
        when(loans.getLoansGiven()).thenReturn(List.of());
        when(guild.getLoanHandler()).thenReturn(loans);
        when(guild.getUpgrades()).thenReturn(List.of());
        when(faction.getPenalty()).thenReturn(0.0);
        when(faction.getId()).thenReturn("rome");
        GuildHandler guildHandler = mock(GuildHandler.class);
        when(faction.getGuildHandler()).thenReturn(guildHandler);
        when(guildHandler.getGuilds()).thenReturn(List.of(guild));
        return new Ledger(guild);
    }
}
