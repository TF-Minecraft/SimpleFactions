package net.tfminecraft.simplefactions.mercenary.company;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import net.tfminecraft.rpcharacters.enums.Status;
import net.tfminecraft.rpcharacters.managers.PlayerManager;
import net.tfminecraft.rpcharacters.objects.PlayerData;
import net.tfminecraft.rpcharacters.objects.RPCharacter;
import net.tfminecraft.rpcharacters.objects.trait.Trait;

class RpCharactersMercenaryTraitProbeTest {

    @TempDir
    Path folder;

    private final File previousRoot = RpCharactersMercenaryTraitProbe.characterRoot;

    @AfterEach
    void restoreRoot() {
        RpCharactersMercenaryTraitProbe.characterRoot = previousRoot;
        RpCharactersMercenaryTraitProbe.diskCacheMillis = 60_000L;
        RpCharactersMercenaryTraitProbe.clearDiskCache();
    }

    @Test
    void diskLookupCountsOnlyAliveMercenariesAndRefusesDamagedFiles() throws IOException {
        assertEquals(MercenaryEligibility.Status.INELIGIBLE,
                RpCharactersMercenaryTraitProbe.livingOnDisk(null));
        assertEquals(MercenaryEligibility.Status.INELIGIBLE,
                RpCharactersMercenaryTraitProbe.livingOnDisk(folder.resolve("missing").toFile()));

        Path notDirectory = folder.resolve("not-a-dir");
        Files.writeString(notDirectory, "x", StandardCharsets.UTF_8);
        assertEquals(MercenaryEligibility.Status.UNKNOWN,
                RpCharactersMercenaryTraitProbe.livingOnDisk(notDirectory.toFile()));

        Path empty = folder.resolve("empty");
        Files.createDirectory(empty);
        assertEquals(MercenaryEligibility.Status.INELIGIBLE,
                RpCharactersMercenaryTraitProbe.livingOnDisk(empty.toFile()));

        Path chars = folder.resolve("chars");
        Files.createDirectory(chars);
        Files.createDirectory(chars.resolve("nested"));
        write(chars, ".hidden.json", "{\"status\":\"ALIVE\",\"traits\":[\"mercenary\"]}");
        write(chars, ".rpcharacters-1.tmp", "{\"status\":\"ALIVE\",\"traits\":[\"mercenary\"]}");
        write(chars, "dead.json", "{\"status\":\"DEAD\",\"traits\":[\"mercenary\"]}");
        write(chars, "missing.json", "{\"status\":\"MISSING\",\"traits\":[\"Mercenary\"]}");
        write(chars, "merchant.json", "{\"status\":\"alive\",\"traits\":[null, \"merchant\"]}");
        write(chars, "broken.json", "{");
        assertEquals(MercenaryEligibility.Status.UNKNOWN,
                RpCharactersMercenaryTraitProbe.livingOnDisk(chars.toFile()));

        write(chars, "hero.json", "{\"status\":\"ALIVE\",\"traits\":[true, \"mercenary\"]}");
        assertEquals(MercenaryEligibility.Status.ELIGIBLE,
                RpCharactersMercenaryTraitProbe.livingOnDisk(chars.toFile()));

        Path plain = folder.resolve("plain");
        Files.createDirectory(plain);
        write(plain, "notes.json", "[\"mercenary\"]");
        write(plain, "no-status.json", "{\"traits\":[\"mercenary\"]}");
        write(plain, "object-status.json", "{\"status\":{\"value\":\"ALIVE\"},\"traits\":[\"mercenary\"]}");
        assertEquals(MercenaryEligibility.Status.UNKNOWN,
                RpCharactersMercenaryTraitProbe.livingOnDisk(plain.toFile()));

        Path traitless = folder.resolve("traitless");
        Files.createDirectory(traitless);
        write(traitless, "a.json", "{\"status\":\"ALIVE\"}");
        write(traitless, "b.json", "{\"status\":\"ALIVE\",\"traits\":\"mercenary\"}");
        assertEquals(MercenaryEligibility.Status.INELIGIBLE,
                RpCharactersMercenaryTraitProbe.livingOnDisk(traitless.toFile()));
    }

    @Test
    void onlineAndOfflineCharactersDecideALivingMercenary() throws IOException {
        RpCharactersMercenaryTraitProbe probe = new RpCharactersMercenaryTraitProbe();
        RpCharactersMercenaryTraitProbe.characterRoot = folder.toFile();
        UUID mercenary = UUID.fromString("11111111-1111-1111-1111-111111111111");
        Path saved = folder.resolve(mercenary.toString());
        Files.createDirectory(saved);
        write(saved, "alt.json", "{\"status\":\"ALIVE\",\"traits\":[\"mercenary\"]}");

        try (var bukkit = mockStatic(Bukkit.class)) {
            assertEquals(MercenaryEligibility.Status.UNKNOWN, probe.livingMercenary("Sigrun"));
            PluginManager plugins = mock(PluginManager.class);
            bukkit.when(Bukkit::getServer).thenReturn(mock(Server.class));
            bukkit.when(Bukkit::getPluginManager).thenReturn(plugins);
            assertEquals(MercenaryEligibility.Status.UNKNOWN, probe.livingMercenary("Sigrun"));
            when(plugins.isPluginEnabled("RPCharacters")).thenReturn(true);

            Player player = mock(Player.class);
            bukkit.when(() -> Bukkit.getPlayerExact("Sigrun")).thenReturn(player);
            try (var manager = mockStatic(PlayerManager.class)) {
                manager.when(() -> PlayerManager.get(player)).thenReturn(null);
                assertEquals(MercenaryEligibility.Status.UNKNOWN, probe.livingMercenary("Sigrun"));

                PlayerData data = mock(PlayerData.class);
                manager.when(() -> PlayerManager.get(player)).thenReturn(data);
                when(data.getCharacters()).thenReturn(null);
                assertEquals(MercenaryEligibility.Status.UNKNOWN, probe.livingMercenary("Sigrun"));
                when(data.getCharacters()).thenReturn(List.of());
                assertEquals(MercenaryEligibility.Status.INELIGIBLE, probe.livingMercenary("Sigrun"));

                RPCharacter dead = character(Status.DEAD, "mercenary");
                RPCharacter missing = character(Status.MISSING, "mercenary");
                RPCharacter alive = character(Status.ALIVE, "merchant");
                when(data.getCharacters()).thenReturn(Arrays.asList(null, dead, missing, alive));
                assertEquals(MercenaryEligibility.Status.INELIGIBLE, probe.livingMercenary("Sigrun"));
                assertEquals(MercenaryEligibility.Status.INELIGIBLE, probe.check("Sigrun"));

                when(alive.getTraits()).thenReturn(null);
                assertEquals(MercenaryEligibility.Status.INELIGIBLE, probe.livingMercenary("Sigrun"));

                Trait mercenaryTrait = mock(Trait.class);
                when(mercenaryTrait.getId()).thenReturn("MERCENARY");
                when(alive.getTraits()).thenReturn(List.of(mercenaryTrait));
                when(data.getActiveCharacter()).thenReturn(alive);
                when(data.hasActiveCharacter()).thenReturn(true);
                assertEquals(MercenaryEligibility.Status.ELIGIBLE, probe.livingMercenary("Sigrun"));
                assertEquals(MercenaryEligibility.Status.ELIGIBLE, probe.check("Sigrun"));
            }

            bukkit.when(() -> Bukkit.getPlayerExact("Offline")).thenReturn(null);
            assertEquals(MercenaryEligibility.Status.UNKNOWN, probe.livingMercenary("Offline"));
            OfflinePlayer cached = mock(OfflinePlayer.class);
            when(cached.getUniqueId()).thenReturn(mercenary);
            bukkit.when(() -> Bukkit.getOfflinePlayerIfCached("Offline")).thenReturn(cached);
            assertEquals(MercenaryEligibility.Status.ELIGIBLE, probe.livingMercenary("Offline"));
            Files.delete(saved.resolve("alt.json"));
            RpCharactersMercenaryTraitProbe.diskCacheMillis = 0L;
            assertEquals(MercenaryEligibility.Status.INELIGIBLE, probe.livingMercenary("Offline"));
            RpCharactersMercenaryTraitProbe.diskCacheMillis = 60_000L;
            write(saved, "alt.json", "{\"status\":\"ALIVE\",\"traits\":[\"mercenary\"]}");
            assertEquals(MercenaryEligibility.Status.INELIGIBLE, probe.livingMercenary("Offline"));

            OfflinePlayer empty = mock(OfflinePlayer.class);
            when(empty.getUniqueId()).thenReturn(UUID.fromString("22222222-2222-2222-2222-222222222222"));
            bukkit.when(() -> Bukkit.getOfflinePlayerIfCached("Empty")).thenReturn(empty);
            bukkit.when(() -> Bukkit.getPlayerExact("Empty")).thenReturn(null);
            assertEquals(MercenaryEligibility.Status.INELIGIBLE, probe.livingMercenary("Empty"));

            when(plugins.isPluginEnabled("RPCharacters")).thenReturn(false);
            assertEquals(MercenaryEligibility.Status.UNKNOWN, probe.livingMercenary("Offline"));
            bukkit.when(Bukkit::getPluginManager).thenReturn(null);
            assertEquals(MercenaryEligibility.Status.UNKNOWN, probe.check("Sigrun"));
            bukkit.when(Bukkit::getServer).thenReturn(null);
            assertEquals(MercenaryEligibility.Status.UNKNOWN, probe.check("Sigrun"));
        }
    }

    @Test
    void aMissingCharacterDataRootIsUnknownAndAMissingPlayerFolderIsNot() throws IOException {
        RpCharactersMercenaryTraitProbe probe = new RpCharactersMercenaryTraitProbe();
        RpCharactersMercenaryTraitProbe.characterRoot = folder.resolve("absent").toFile();
        UUID id = UUID.fromString("33333333-3333-3333-3333-333333333333");
        OfflinePlayer cached = mock(OfflinePlayer.class);
        when(cached.getUniqueId()).thenReturn(id);

        try (var bukkit = mockStatic(Bukkit.class)) {
            PluginManager plugins = mock(PluginManager.class);
            bukkit.when(Bukkit::getServer).thenReturn(mock(Server.class));
            bukkit.when(Bukkit::getPluginManager).thenReturn(plugins);
            when(plugins.isPluginEnabled("RPCharacters")).thenReturn(true);
            bukkit.when(() -> Bukkit.getPlayerExact("Offline")).thenReturn(null);
            bukkit.when(() -> Bukkit.getOfflinePlayerIfCached("Offline")).thenReturn(cached);
            assertEquals(MercenaryEligibility.Status.UNKNOWN, probe.livingMercenary("Offline"));

            RpCharactersMercenaryTraitProbe.characterRoot = null;
            assertEquals(MercenaryEligibility.Status.UNKNOWN, probe.livingMercenary("Offline"));
            Plugin plugin = mock(Plugin.class);
            when(plugins.getPlugin("RPCharacters")).thenReturn(plugin);
            assertEquals(MercenaryEligibility.Status.UNKNOWN, probe.livingMercenary("Offline"));
            when(plugin.getDataFolder()).thenReturn(folder.toFile());
            assertEquals(MercenaryEligibility.Status.UNKNOWN, probe.livingMercenary("Offline"));

            Path root = folder.resolve("data/characterdata");
            Files.createDirectories(root);
            assertEquals(MercenaryEligibility.Status.INELIGIBLE, probe.livingMercenary("Offline"));
            Files.createDirectories(root.resolve(id.toString()));
            write(root.resolve(id.toString()), "alt.json",
                    "{\"status\":\"ALIVE\",\"traits\":[\"mercenary\"]}");
            RpCharactersMercenaryTraitProbe.clearDiskCache();
            assertEquals(MercenaryEligibility.Status.ELIGIBLE, probe.livingMercenary("Offline"));
        }
    }

    private static RPCharacter character(Status status, String traitId) {
        RPCharacter character = mock(RPCharacter.class);
        Trait trait = mock(Trait.class);
        when(character.getStatus()).thenReturn(status);
        when(trait.getId()).thenReturn(traitId);
        when(character.getTraits()).thenReturn(Arrays.asList(null, trait));
        return character;
    }

    private static void write(Path directory, String name, String json) throws IOException {
        Files.writeString(directory.resolve(name), json, StandardCharsets.UTF_8);
    }
}
