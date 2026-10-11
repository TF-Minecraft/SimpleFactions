package net.tfminecraft.simplefactions.plots;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.tfminecraft.simplefactions.espionage.EspionageService;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.testsupport.GuiTestFixture;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.mockito.MockedStatic;

/** Characters, Intrigue rolls, factions and Bukkit lookups for plot tests, on a real plot store. */
final class PlotTestWorld implements AutoCloseable {
    final GuiTestFixture gui = new GuiTestFixture();
    final MockedStatic<PlotCharacters> characters = mockStatic(PlotCharacters.class);
    final MockedStatic<EspionageService> espionage = mockStatic(EspionageService.class, CALLS_REAL_METHODS);
    final MockedStatic<FactionManager> factions = mockStatic(FactionManager.class);
    final MockedStatic<Bukkit> bukkit;
    final Map<UUID, PlotCharacter> active = new HashMap<>();
    final Map<UUID, Player> players = new HashMap<>();
    final Map<String, Integer> intrigue = new HashMap<>();
    final Map<String, PlotCharacter> offline = new HashMap<>();
    final Set<String> dead = new HashSet<>();
    final List<String> permakilled = new ArrayList<>();
    final Map<Player, List<String>> messages = new HashMap<>();
    final AtomicLong clock = new AtomicLong(1_000_000_000_000L);
    final ScriptedRandom random = new ScriptedRandom();
    final World otherWorld = mock(World.class);
    private final java.util.function.LongSupplier previousClock = PlotService.clock;
    private final java.util.function.Supplier<java.util.random.RandomGenerator> previousRandom = PlotService.random;

    @SuppressWarnings("unchecked")
    PlotTestWorld(Path directory) throws Exception {
        Field field = GuiTestFixture.class.getDeclaredField("bukkit");
        field.setAccessible(true);
        bukkit = (MockedStatic<Bukkit>) field.get(gui);
        PlotConfig.load(new YamlConfiguration());
        PlotService.clock = clock::get;
        PlotService.random = () -> random;
        PlotService.load(directory.resolve("plots.json"));
        characters.when(PlotCharacters::enabled).thenReturn(true);
        characters.when(() -> PlotCharacters.active(any())).thenAnswer(call -> {
            Player player = call.getArgument(0);
            return player == null ? null : active.get(player.getUniqueId());
        });
        characters.when(() -> PlotCharacters.online(any())).thenAnswer(call -> {
            PlotCharacter character = call.getArgument(0);
            PlotCharacter playing = character == null ? null : active.get(character.playerId);
            return playing != null && playing.is(character.characterId) ? players.get(character.playerId) : null;
        });
        characters.when(() -> PlotCharacters.dead(any())).thenAnswer(call -> {
            PlotCharacter character = call.getArgument(0);
            return character != null && dead.contains(character.characterId);
        });
        characters.when(() -> PlotCharacters.offline(any())).thenAnswer(call -> {
            OfflinePlayer account = call.getArgument(0);
            return account == null ? null : offline.get(account.getName());
        });
        characters.when(() -> PlotCharacters.seenAs(any(), any())).thenAnswer(call -> ((PlotCharacter) call.getArgument(1)).name);
        characters.when(() -> PlotCharacters.permakill(any(), anyString(), any())).thenAnswer(call -> {
            permakilled.add(call.getArgument(1));
            return true;
        });
        espionage.when(() -> EspionageService.knownIntrigue(any())).thenAnswer(call -> intrigue.get((String) call.getArgument(0)));
        espionage.when(() -> EspionageService.intrigue(any(), anyString())).thenAnswer(call -> {
            String id = call.getArgument(1);
            intrigue.putIfAbsent(id, 40);
            return intrigue.get(id);
        });
        bukkit.when(() -> Bukkit.getPlayer(any(UUID.class))).thenAnswer(call -> players.get((UUID) call.getArgument(0)));
        bukkit.when(() -> Bukkit.getPlayerExact(anyString())).thenAnswer(call -> players.values().stream()
                .filter(player -> player.getName().equals(call.getArgument(0))).findFirst().orElse(null));
        bukkit.when(() -> Bukkit.getOfflinePlayerIfCached(anyString())).thenAnswer(call -> {
            String name = call.getArgument(0);
            if (!offline.containsKey(name)) return null;
            OfflinePlayer account = mock(OfflinePlayer.class);
            when(account.getName()).thenReturn(name);
            return account;
        });
    }

    /** An online player at x on the shared world, playing the character with this id and Intrigue. */
    Player player(String name, String characterId, Integer intrigueValue) {
        Player player = gui.player(name);
        when(player.getWorld()).thenReturn(gui.world);
        List<String> log = new ArrayList<>();
        messages.put(player, log);
        doAnswer(call -> log.add(call.getArgument(0))).when(player).sendMessage(anyString());
        doAnswer(call -> log.add(PlainTextComponentSerializer.plainText().serialize(call.getArgument(0))))
                .when(player).sendMessage(any(Component.class));
        players.put(player.getUniqueId(), player);
        if (characterId != null) active.put(player.getUniqueId(),
                new PlotCharacter(characterId, player.getUniqueId(), name, "Char " + name));
        if (intrigueValue != null) intrigue.put(characterId, intrigueValue);
        return player;
    }

    PlotCharacter character(Player player) { return active.get(player.getUniqueId()); }

    void at(Player player, double x) { when(player.getLocation()).thenReturn(new Location(gui.world, x, 64, 0)); }

    void elsewhere(Player player) {
        when(player.getWorld()).thenReturn(otherWorld);
        when(player.getLocation()).thenReturn(new Location(otherWorld, 0, 64, 0));
    }

    void logOff(Player player) { active.remove(player.getUniqueId()); }

    List<String> said(Player player) { return messages.get(player); }

    String last(Player player) {
        List<String> log = messages.get(player);
        return log.isEmpty() ? null : log.getLast();
    }

    boolean heard(Player player, String fragment) {
        return messages.get(player).stream().anyMatch(line -> line.contains(fragment));
    }

    void pass(long millis) { clock.addAndGet(millis); }

    PlotStore store() { return PlotService.store(); }

    @Override
    public void close() {
        PlotService.clock = previousClock;
        PlotService.random = previousRandom;
        PlotConfig.load(new YamlConfiguration());
        factions.close();
        espionage.close();
        characters.close();
        gui.close();
    }
}
