package net.tfminecraft.simplefactions.economy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.bukkit.entity.Player;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import net.tfminecraft.denareconomy.api.Denars;
import net.tfminecraft.simplefactions.espionage.EspionageService;
import net.tfminecraft.simplefactions.espionage.EspionageState;
import net.tfminecraft.simplefactions.espionage.SpecialPositionAssignment;
import net.tfminecraft.simplefactions.government.StabilityModifier;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.guild.income.BranchIncomePreview;
import net.tfminecraft.simplefactions.guild.income.Cashflow;
import net.tfminecraft.simplefactions.guild.income.EconomicPreview;
import net.tfminecraft.simplefactions.guild.income.Ledger;
import net.tfminecraft.simplefactions.identity.Members;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.managers.ProvinceManager;
import net.tfminecraft.simplefactions.objects.Bank;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.objects.handler.GuildHandler;
import net.tfminecraft.tlibs.character.CharacterInfo;
import net.tfminecraft.tlibs.character.CharacterRef;
import net.tfminecraft.tlibs.character.CharacterSession;
import net.tfminecraft.tlibs.character.CharacterStatus;

class FrozenReadsTest {
    private final List<Faction> savedFactions = new ArrayList<>();
    private Guild guild;

    private org.mockito.MockedStatic<Denars> mockDenars() {
        var denars = mockStatic(Denars.class, CALLS_REAL_METHODS);
        denars.when(() -> Denars.taxContributors(any())).thenReturn(Map.of());
        return denars;
    }

    @BeforeEach
    void setUp() {
        savedFactions.addAll(FactionManager.factions);
        FactionManager.factions.clear();
        guild = mock(Guild.class);
        when(guild.getIncarnationId()).thenReturn(UUID.fromString("00000000-0000-0000-0000-000000000042"));
        when(guild.getBank()).thenAnswer(invocation -> new Bank(guild, null));
        GuildHandler handler = mock(GuildHandler.class);
        when(handler.getGuilds()).thenReturn(new ArrayList<>(List.of(guild)));
        Faction faction = mock(Faction.class);
        when(faction.getGuildHandler()).thenReturn(handler);
        FactionManager.factions.add(faction);
    }

    @AfterEach
    void tearDown() {
        Ledger.setNodeUpkeepLookup(null);
        FactionManager.factions.clear();
        FactionManager.factions.addAll(savedFactions);
    }

    @Test
    void asyncReadsUseBalancesCapturedOnTheMainThread() throws Exception {
        FrozenReads frozen;
        try (var denars = mockDenars()) {
            denars.when(() -> Denars.balance(anyString())).thenReturn(1234L);
            frozen = FrozenReads.capture();
        }

        // Static mocks stay on the test thread, so only the frozen balance can give the worker 12.34.
        assertEquals(12.34, CompletableFuture.supplyAsync(() -> frozen.read(() -> guild.getBank().getWealth())).get());
        assertEquals(0L, frozen.read(() -> FrozenReads.balance("unknown")));
        assertEquals(Map.of(), frozen.read(() -> FrozenReads.taxContributors(UUID.randomUUID())));
        assertEquals(Map.of(), FrozenReads.taxContributors(null));
    }

    @Test
    void asyncCitizenTaxUsesTheHoldingBalanceCapturedOnTheMainThread() throws Exception {
        when(guild.isBase()).thenReturn(true);
        when(guild.getFaction()).thenReturn(FactionManager.factions.getFirst());
        Ledger ledger = new Ledger(guild);
        String holding = Denars.guildTax(guild.getIncarnationId());
        FrozenReads frozen;
        UUID contributor = UUID.randomUUID();
        var contributors = new java.util.HashMap<>(Map.of(contributor, 1000L));
        try (var denars = mockDenars()) {
            denars.when(() -> Denars.balance(anyString())).thenReturn(0L);
            denars.when(() -> Denars.balance(holding)).thenReturn(1234L);
            denars.when(() -> Denars.taxContributors(guild.getIncarnationId())).thenReturn(contributors);
            frozen = FrozenReads.capture();
            denars.when(() -> Denars.balance(holding)).thenReturn(5000L);
            contributors.put(contributor, 2000L);
            assertEquals(50.0, ledger.getIncome(Cashflow.CITIZENS));
            assertEquals(Map.of(contributor.toString(), 20.0, "Other citizen taxes", 30.0), ledger.getCitizenTaxBreakdown());
        }

        assertEquals(12.34, CompletableFuture.supplyAsync(() -> frozen.read(() -> ledger.getIncome(Cashflow.CITIZENS))).get());
        assertEquals(Map.of(contributor.toString(), 10.0, "Other citizen taxes", 2.34), CompletableFuture
                .supplyAsync(() -> frozen.read(ledger::getCitizenTaxBreakdown)).get());
    }

    @Test
    void readRestoresTheLiveLedgerAfterwards() {
        try (var denars = mockDenars()) {
            denars.when(() -> Denars.balance(anyString())).thenReturn(500L);
            FrozenReads frozen = FrozenReads.capture();
            denars.when(() -> Denars.balance(anyString())).thenReturn(700L);

            assertEquals(5.0, frozen.read(() -> guild.getBank().getWealth()));
            assertEquals(7.0, guild.getBank().getWealth());
        }
    }

    @Test
    void nestedReadsRestoreTheOuterBalances() {
        try (var denars = mockDenars()) {
            denars.when(() -> Denars.balance(anyString())).thenReturn(100L);
            FrozenReads outer = FrozenReads.capture();
            denars.when(() -> Denars.balance(anyString())).thenReturn(200L);
            FrozenReads inner = FrozenReads.capture();

            assertEquals(1.0, outer.read(() -> {
                assertEquals(2.0, inner.read(() -> guild.getBank().getWealth()));
                return guild.getBank().getWealth();
            }));
        }
    }

    @Test
    void previewCopiesCarryTheBalancesFromWhenTheyWereMade() {
        ProvinceManager live = new ProvinceManager();
        live.start(Map.of());
        try (var denars = mockDenars()) {
            denars.when(() -> Denars.balance(anyString())).thenReturn(-300L);
            EconomicPreview.Prepared economic = EconomicPreview.prepare(live);
            BranchIncomePreview.Prepared branch = BranchIncomePreview.prepare(live);
            denars.when(() -> Denars.balance(anyString())).thenReturn(0L);

            assertEquals(-3.0, economic.reads().read(() -> guild.getBank().getWealth()));
            assertEquals(-3.0, branch.reads().read(() -> guild.getBank().getWealth()));
        }
    }

    @Test
    void asyncStabilityUsesSpymasterDeathsCapturedOnTheMainThread() throws Exception {
        CharacterRef spy = new CharacterRef(UUID.randomUUID(), UUID.randomUUID());
        Members.setResolver(new MainThreadCharacters(Thread.currentThread(), spy));
        SpecialPositionAssignment holder = new SpecialPositionAssignment();
        holder.playerId = spy.ownerId();
        holder.characterId = spy.characterId().toString();
        EspionageState state = new EspionageState();
        state.appoint(holder, 80);
        Faction realm = mock(Faction.class);
        when(realm.getId()).thenReturn("realm");
        when(realm.getEspionage()).thenReturn(state);
        when(realm.isMember(Members.key(spy))).thenReturn(true);
        FactionManager.factions.add(realm);
        long now = System.currentTimeMillis();

        // Off the main thread the live check hits the character guard, as it did on Main.
        ExecutionException live = assertThrows(ExecutionException.class,
                () -> CompletableFuture.supplyAsync(() -> EspionageService.stabilityModifiers(realm, now)).get());
        assertInstanceOf(IllegalStateException.class, live.getCause());

        FrozenReads frozen;
        try (var denars = mockDenars()) {
            denars.when(() -> Denars.balance(anyString())).thenReturn(0L);
            frozen = FrozenReads.capture();
        }
        List<StabilityModifier> modifiers = CompletableFuture
                .supplyAsync(() -> frozen.read(() -> FrozenReads.espionage(realm, now))).get();
        assertEquals(1, modifiers.size());
        assertEquals("Vacant Spymaster", modifiers.getFirst().getName());

        Faction later = mock(Faction.class);
        when(later.getId()).thenReturn("later");
        assertEquals(List.of(), frozen.read(() -> FrozenReads.espionage(later, now)));
    }

    @Test
    void asyncLedgerUsesNodeUpkeepCapturedOnTheMainThread() throws Exception {
        Thread main = Thread.currentThread();
        Ledger.setNodeUpkeepLookup(g -> {
            if (Thread.currentThread() != main) {
                throw new IllegalStateException("Character identity must be used on the main thread");
            }
            return g == guild ? 200 : 0;
        });
        when(guild.getFaction()).thenReturn(FactionManager.factions.getFirst());
        Ledger ledger = new Ledger(guild);

        // Dowsing's lookup asks SimpleFactions' API for readiness, which only answers on the main thread.
        ExecutionException live = assertThrows(ExecutionException.class,
                () -> CompletableFuture.supplyAsync(() -> ledger.getIncome(Cashflow.NODES)).get());
        assertInstanceOf(IllegalStateException.class, live.getCause());

        FrozenReads frozen;
        try (var denars = mockDenars()) {
            denars.when(() -> Denars.balance(anyString())).thenReturn(0L);
            frozen = FrozenReads.capture();
        }
        assertEquals(-200.0, CompletableFuture.supplyAsync(() -> frozen.read(() -> ledger.getIncome(Cashflow.NODES))).get());
        assertEquals(-200.0, ledger.getIncome(Cashflow.NODES));
    }

    /** Knows one dead character and refuses lookups off the capturing thread, like TLibs. */
    private record MainThreadCharacters(Thread main, CharacterRef dead) implements Members.Resolver {
        @Override
        public Optional<CharacterInfo> info(String key) {
            if (Thread.currentThread() != main) {
                throw new IllegalStateException("Character identity must be used on the main thread");
            }
            return Optional.of(new CharacterInfo(dead, "Spy", CharacterStatus.DEAD, "human", Set.of(), 0L, Instant.EPOCH))
                    .filter(info -> key.equals(Members.key(info.ref())));
        }

        @Override
        public Optional<CharacterSession> session(Player player) {
            return Optional.empty();
        }

        @Override
        public boolean isCurrent(CharacterSession session) {
            return false;
        }

        @Override
        public String key(Player player) {
            return null;
        }

        @Override
        public Optional<Player> online(String key) {
            return Optional.empty();
        }
    }
}
