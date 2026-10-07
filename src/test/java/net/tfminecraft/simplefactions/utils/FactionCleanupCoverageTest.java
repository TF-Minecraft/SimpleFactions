package net.tfminecraft.simplefactions.utils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.testsupport.GuiTestFixture;
import net.tfminecraft.simplefactions.testsupport.PersistenceFilesFixture;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.*;

class FactionCleanupCoverageTest {
  PersistenceFilesFixture disk;
  GuiTestFixture ui;
  Field daysField;
  Object previous;

  @BeforeEach void setUp() throws Exception {
    new FactionCleanup();disk=new PersistenceFilesFixture();ui=new GuiTestFixture();
    when(ui.plugin.getLogger()).thenReturn(mock(java.util.logging.Logger.class));
    daysField=FactionCleanup.class.getDeclaredField("offlineDays");daysField.setAccessible(true);
    previous=daysField.get(null);daysField.set(null,null);
  }
  @AfterEach void close() throws Exception {
    try {daysField.set(null,previous);ui.close();}finally{disk.close();}
  }

  @Test void missingEmptyAndNullFilesInitializeWithoutInventingOfflineTime() throws Exception {
    assertEquals(0,FactionCleanup.daysOffline(null));assertFalse(Files.exists(disk.root.resolve("Cache")));
    assertEquals(0,FactionCleanup.daysOffline("Alice"));assertTrue(Files.isRegularFile(disk.root.resolve("Cache/logins.json")));
    for(String contents:List.of("","null")) {
      disk.write("Cache/logins.json",contents);daysField.set(null,null);
      assertEquals(0,FactionCleanup.daysOffline("Alice"));
    }
    FactionCleanup.ping(null);FactionCleanup.ping(" ");
    assertEquals("null",Files.readString(disk.root.resolve("Cache/logins.json")));
    FactionCleanup.ping("ALICE");
    assertEquals(0,FactionCleanup.daysOffline("alice"));
    assertTrue(Files.readString(disk.root.resolve("Cache/logins.json")).contains("\"alice\":0"));
  }

  @Test void dailyAccountingMarksInactiveMembersWithoutRemovingThemAndResetsOnlinePlayers() throws Exception {
    disk.write("Cache/logins.json","{\"alice\":20,\"bob\":null,\"cara\":30,\"former\":5}");
    Faction faction=mock(Faction.class);when(faction.getMembers()).thenReturn(Arrays.asList("Alice","Bob","Cara","New",null," ","dummy_guard"));
    Player bob=ui.player("BOB");when(Bukkit.getOnlinePlayers()).thenAnswer(call -> Arrays.asList(null,bob));
    FactionCleanup.advanceOfflineDays(Arrays.asList(null,faction));
    assertEquals(21,FactionCleanup.daysOffline("ALICE"));assertEquals(0,FactionCleanup.daysOffline("Bob"));
    assertEquals(31,FactionCleanup.daysOffline("Cara"));assertEquals(6,FactionCleanup.daysOffline("former"));
    assertEquals(1,FactionCleanup.daysOffline("New"));assertEquals(0,FactionCleanup.daysOffline("dummy_guard"));
    verify(faction,never()).forceRemoveMember(anyString());
    verify(ui.plugin.getLogger()).log(eq(Level.INFO),contains("alice is inactive after 21 days"));
    FactionCleanup.ping("Alice");assertEquals(0,FactionCleanup.daysOffline("alice"));
  }

  @Test void unreadableOrMalformedHistoryIsPreservedWhenAPlayerLogsIn() throws Exception {
    String original="{\"alice\":18, broken}";
    disk.write("Cache/logins.json",original);
    assertEquals(0,FactionCleanup.daysOffline("Alice"));
    FactionCleanup.advanceOfflineDays(null);assertEquals(original,Files.readString(disk.root.resolve("Cache/logins.json")));
    FactionCleanup.ping("Bob");
    assertEquals(original,Files.readString(disk.root.resolve("Cache/logins.json")),"Login must not replace unreadable history with one player's record");
  }

  @Test void missingServerAndUnavailableOnlineLookupTreatPlayersAsOffline() throws Exception {
    disk.write("Cache/logins.json","{\"alice\":2}");
    when(Bukkit.getServer()).thenReturn(null);
    FactionCleanup.advanceOfflineDays(null);assertEquals(3,FactionCleanup.daysOffline("alice"));
    when(Bukkit.getServer()).thenReturn(ui.server);
    when(Bukkit.getOnlinePlayers()).thenThrow(new IllegalStateException("server stopping"));
    FactionCleanup.advanceOfflineDays(List.of());assertEquals(4,FactionCleanup.daysOffline("alice"));
  }

  @Test void storageFailuresDoNotOverwriteConflictingPathsOrEscapeIntoTheTick() throws Exception {
    disk.write("Cache","occupied");
    assertEquals(0,FactionCleanup.daysOffline("Alice"));
    assertDoesNotThrow(() -> FactionCleanup.advanceOfflineDays(null));
    assertDoesNotThrow(() -> FactionCleanup.ping("Alice"));
    assertEquals("occupied",Files.readString(disk.root.resolve("Cache")));
    disk.remove("Cache");daysField.set(null,null);FactionCleanup.ping("Alice");
    disk.remove("Cache/logins.json");Files.createDirectory(disk.root.resolve("Cache/logins.json"));
    assertDoesNotThrow(() -> FactionCleanup.advanceOfflineDays(null));
    assertDoesNotThrow(() -> FactionCleanup.ping("Bob"));
    assertTrue(Files.isDirectory(disk.root.resolve("Cache/logins.json")));
  }

  @Test void cachedHistoryCanRecreateADeletedCacheFolderAndLogWithoutALivePlugin() throws Exception {
    disk.write("Cache/logins.json","{\"alice\":20}");assertEquals(20,FactionCleanup.daysOffline("Alice"));
    disk.remove("Cache");Faction faction=mock(Faction.class);when(faction.getMembers()).thenReturn(List.of("Alice"));
    SimpleFactions previousPlugin=SimpleFactions.plugin;SimpleFactions.plugin=null;
    try {FactionCleanup.advanceOfflineDays(List.of(faction));}finally{SimpleFactions.plugin=previousPlugin;}
    assertEquals(21,FactionCleanup.daysOffline("Alice"));assertTrue(Files.isRegularFile(disk.root.resolve("Cache/logins.json")));
  }
}
